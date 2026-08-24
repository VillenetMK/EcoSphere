import { createClient } from "npm:@supabase/supabase-js@2.112.3";

const allowedOrigins = new Set([
  "https://villenetmk.github.io",
  "https://ecospherecontrol.com",
  "https://www.ecospherecontrol.com",
  "http://localhost:8000",
  "http://127.0.0.1:8000",
]);

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const publishableKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

function corsHeaders(origin: string | null) {
  const headers: Record<string, string> = {
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Vary": "Origin",
  };
  if (origin && allowedOrigins.has(origin)) headers["Access-Control-Allow-Origin"] = origin;
  return headers;
}

function json(origin: string | null, status: number, body: Record<string, unknown>, extra: HeadersInit = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders(origin),
      ...Object.fromEntries(new Headers(extra).entries()),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

function clientAddress(request: Request) {
  const forwarded = request.headers.get("x-forwarded-for")
    ?.split(",")
    .map(value => value.trim())
    .filter(Boolean);
  return request.headers.get("cf-connecting-ip")?.trim()
    || request.headers.get("x-real-ip")?.trim()
    || forwarded?.at(-1)
    || "unknown";
}

function genericLoginError(origin: string | null) {
  return json(origin, 400, { error: "Usuario o contraseña incorrectos." });
}

Deno.serve(async request => {
  const origin = request.headers.get("origin");
  if (origin && !allowedOrigins.has(origin)) return json(origin, 403, { error: "Origen no permitido." });
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders(origin) });
  if (request.method !== "POST") return json(origin, 405, { error: "Método no permitido." });
  if (!supabaseUrl || !publishableKey || !serviceRoleKey) {
    return json(origin, 503, { error: "Servicio de acceso no configurado." });
  }

  const contentLength = Number(request.headers.get("content-length") ?? 0);
  if (contentLength > 4096) return json(origin, 413, { error: "Solicitud demasiado grande." });

  try {
    const body = await request.json();
    const username = String(body?.username ?? "").trim();
    const password = String(body?.password ?? "");
    const captchaToken = typeof body?.captchaToken === "string" ? body.captchaToken : undefined;
    if (!/^[A-Za-z][A-Za-z0-9._-]{2,31}$/.test(username) || password.length < 1 || password.length > 1024) {
      return genericLoginError(origin);
    }

    const attemptKey = await sha256(`${clientAddress(request)}|${username.toLowerCase()}`);
    const { data: gateRows, error: gateError } = await admin.rpc("username_login_begin", {
      p_attempt_key: attemptKey,
    });
    if (gateError) throw gateError;
    const gate = Array.isArray(gateRows) ? gateRows[0] : gateRows;
    if (!gate?.allowed) {
      const retryAfter = Math.max(1, Number(gate?.retry_after_seconds ?? 900));
      return json(
        origin,
        429,
        { error: "Demasiados intentos. Espera antes de volver a intentar." },
        { "Retry-After": String(retryAfter) },
      );
    }

    const { data: email, error: lookupError } = await admin.rpc("username_login_lookup", {
      p_username: username,
    });
    if (lookupError) throw lookupError;
    if (!email) {
      await admin.rpc("username_login_failure", { p_attempt_key: attemptKey });
      await new Promise(resolve => setTimeout(resolve, 250));
      return genericLoginError(origin);
    }

    const authResponse = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "apikey": publishableKey,
        "Authorization": `Bearer ${publishableKey}`,
      },
      body: JSON.stringify({
        email,
        password,
        ...(captchaToken ? { gotrue_meta_security: { captcha_token: captchaToken } } : {}),
      }),
    });

    if (!authResponse.ok) {
      await admin.rpc("username_login_failure", { p_attempt_key: attemptKey });
      if (authResponse.status === 429) {
        return json(
          origin,
          429,
          { error: "Demasiados intentos. Espera antes de volver a intentar." },
          { "Retry-After": authResponse.headers.get("retry-after") ?? "60" },
        );
      }
      return genericLoginError(origin);
    }

    const session = await authResponse.json();
    await admin.rpc("username_login_clear", { p_attempt_key: attemptKey });
    return json(origin, 200, {
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_in: session.expires_in,
      expires_at: session.expires_at,
      token_type: session.token_type,
    });
  } catch {
    return json(origin, 500, { error: "No se pudo procesar el acceso." });
  }
});
