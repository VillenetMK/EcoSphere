/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

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
const MAX_REQUEST_BYTES = 4096;
const DECOY_EMAIL_POOL = Object.freeze([
  "login-decoy-00@ecosphere.invalid",
  "login-decoy-01@ecosphere.invalid",
  "login-decoy-02@ecosphere.invalid",
  "login-decoy-03@ecosphere.invalid",
  "login-decoy-04@ecosphere.invalid",
  "login-decoy-05@ecosphere.invalid",
  "login-decoy-06@ecosphere.invalid",
  "login-decoy-07@ecosphere.invalid",
  "login-decoy-08@ecosphere.invalid",
  "login-decoy-09@ecosphere.invalid",
  "login-decoy-10@ecosphere.invalid",
  "login-decoy-11@ecosphere.invalid",
  "login-decoy-12@ecosphere.invalid",
  "login-decoy-13@ecosphere.invalid",
  "login-decoy-14@ecosphere.invalid",
  "login-decoy-15@ecosphere.invalid",
]);

class PayloadTooLargeError extends Error {}

async function readBoundedJson(request: Request) {
  if (!request.body) throw new SyntaxError("missing request body");

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let byteLength = 0;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      byteLength += value.byteLength;
      if (byteLength > MAX_REQUEST_BYTES) {
        await reader.cancel("request body too large");
        throw new PayloadTooLargeError("request body too large");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const bytes = new Uint8Array(byteLength);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  } catch {
    throw new SyntaxError("invalid JSON request body");
  }
}

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
      "X-Content-Type-Options": "nosniff",
    },
  });
}

async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

function clientAddress(request: Request) {
  // The hosted gateway appends the verified peer to X-Forwarded-For. Trust
  // only that right-most, bounded address; the other common IP headers can be
  // supplied by an untrusted caller and must not select a rate-limit bucket.
  const candidate = request.headers.get("x-forwarded-for")
    ?.split(",")
    .at(-1)
    ?.trim();
  return candidate && candidate.length <= 64 && /^[0-9A-Fa-f:.]+$/.test(candidate)
    ? candidate.toLowerCase()
    : "unavailable";
}

function genericLoginError(origin: string | null) {
  return json(origin, 400, { error: "Usuario o contraseña incorrectos." });
}

function decoyEmailForIp(ipBucket: string) {
  const poolIndex = Number.parseInt(ipBucket.slice(0, 8), 16) % DECOY_EMAIL_POOL.length;
  return DECOY_EMAIL_POOL[poolIndex];
}

function randomFailureTargetMs() {
  const sample = crypto.getRandomValues(new Uint32Array(1))[0];
  return 450 + (sample % 301);
}

async function waitForFailureJitter(startedAt: number, targetMs: number) {
  const remaining = targetMs - (Date.now() - startedAt);
  if (remaining > 0) await new Promise(resolve => setTimeout(resolve, remaining));
}

async function beginIpLoginGate(ipBucket: string) {
  return await admin.rpc("username_login_begin_ip_v2", {
    p_ip_bucket: ipBucket,
  });
}

async function beginAccountLoginGate(ipBucket: string, accountBucket: string) {
  return await admin.rpc("username_login_begin_account_v2", {
    p_ip_bucket: ipBucket,
    p_account_bucket: accountBucket,
  });
}

async function recordLoginFailure(ipBucket: string, accountBucket?: string) {
  return await admin.rpc("username_login_failure_v2", {
    p_ip_bucket: ipBucket,
    p_account_bucket: accountBucket ?? null,
  });
}

async function releaseLoginReservation(ipBucket: string, accountBucket?: string) {
  return await admin.rpc("username_login_success_v2", {
    p_ip_bucket: ipBucket,
    p_account_bucket: accountBucket ?? null,
  });
}

Deno.serve(async request => {
  const startedAt = Date.now();
  const failureTargetMs = randomFailureTargetMs();
  let reservedIpBucket: string | undefined;
  let reservedAccountBucket: string | undefined;
  let downstreamAttempted = false;
  const origin = request.headers.get("origin");
  if (origin && !allowedOrigins.has(origin)) return json(origin, 403, { error: "Origen no permitido." });
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders(origin) });
  if (request.method !== "POST") return json(origin, 405, { error: "Método no permitido." });
  if (!supabaseUrl || !publishableKey || !serviceRoleKey) {
    return json(origin, 503, { error: "Servicio de acceso no configurado." });
  }

  const contentLength = Number(request.headers.get("content-length") ?? 0);
  if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BYTES) {
    return json(origin, 413, { error: "Solicitud demasiado grande." });
  }

  try {
    const body = await readBoundedJson(request);
    const username = String(body?.username ?? "").trim();
    const password = String(body?.password ?? "");
    const captchaToken = typeof body?.captchaToken === "string" ? body.captchaToken : undefined;
    const address = clientAddress(request);
    const ipBucket = await sha256(`username-login:v2:ip:${address}`);

    // Reserve the global IP budget before any username validation or lookup.
    // This bounds database work even when an attacker rotates account input.
    const { data: ipGateRows, error: ipGateError } = await beginIpLoginGate(ipBucket);
    if (ipGateError) throw ipGateError;
    const ipGate = Array.isArray(ipGateRows) ? ipGateRows[0] : ipGateRows;
    if (!ipGate?.allowed) {
      const retryAfter = Math.max(1, Number(ipGate?.retry_after_seconds ?? 900));
      return json(
        origin,
        429,
        { error: "Demasiados intentos. Espera antes de volver a intentar." },
        { "Retry-After": String(retryAfter) },
      );
    }
    reservedIpBucket = ipBucket;

    if (!/^[A-Za-z][A-Za-z0-9._-]{2,31}$/.test(username) || password.length < 1 || password.length > 1024) {
      const { error: failureError } = await recordLoginFailure(ipBucket);
      if (failureError) throw failureError;
      reservedIpBucket = undefined;
      await waitForFailureJitter(startedAt, failureTargetMs);
      return genericLoginError(origin);
    }

    // Bucket derivation is identical whether the username exists or not. Its
    // hard database cap prevents attacker-controlled names growing storage.
    const accountBucket = await sha256(
      `username-login:v2:account-ip:${address}|${username.toLowerCase()}`,
    );

    const { data: email, error: lookupError } = await admin.rpc("username_login_lookup_v2", {
      p_username: username,
    });
    if (lookupError) throw lookupError;

    const { data: accountGateRows, error: accountGateError } = await beginAccountLoginGate(
      ipBucket,
      accountBucket,
    );
    if (accountGateError) throw accountGateError;
    const accountGate = Array.isArray(accountGateRows) ? accountGateRows[0] : accountGateRows;
    if (!accountGate?.allowed) {
      const { error: failureError } = await recordLoginFailure(ipBucket);
      if (failureError) throw failureError;
      reservedIpBucket = undefined;
      await waitForFailureJitter(startedAt, failureTargetMs);
      // Account-level throttling is deliberately indistinguishable from a bad
      // credential. Only the username-independent global IP gate is explicit.
      return genericLoginError(origin);
    }
    reservedAccountBucket = accountBucket;

    const authEmail = email || decoyEmailForIp(ipBucket);
    downstreamAttempted = true;
    const authResponse = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "apikey": publishableKey,
        "Authorization": `Bearer ${publishableKey}`,
      },
      body: JSON.stringify({
        email: authEmail,
        password,
        ...(captchaToken ? { gotrue_meta_security: { captcha_token: captchaToken } } : {}),
      }),
    });

    if (!email || !authResponse.ok) {
      await authResponse.body?.cancel();
      const { error: failureError } = await recordLoginFailure(ipBucket, accountBucket);
      if (failureError) throw failureError;
      reservedIpBucket = undefined;
      reservedAccountBucket = undefined;
      await waitForFailureJitter(startedAt, failureTargetMs);
      return genericLoginError(origin);
    }

    const session = await authResponse.json();
    const { error: releaseError } = await releaseLoginReservation(ipBucket, accountBucket);
    if (releaseError) throw releaseError;
    reservedIpBucket = undefined;
    reservedAccountBucket = undefined;
    return json(origin, 200, {
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_in: session.expires_in,
      expires_at: session.expires_at,
      token_type: session.token_type,
    });
  } catch (error) {
    if (reservedIpBucket) {
      const ipBucket = reservedIpBucket;
      const accountBucket = reservedAccountBucket;
      const result = await releaseLoginReservation(ipBucket, accountBucket).catch(() => null);
      if (result && !result.error) {
        reservedIpBucket = undefined;
        reservedAccountBucket = undefined;
      }
    }
    if (downstreamAttempted) {
      await waitForFailureJitter(startedAt, failureTargetMs);
      return genericLoginError(origin);
    }
    if (error instanceof PayloadTooLargeError) {
      return json(origin, 413, { error: "Solicitud demasiado grande." });
    }
    if (error instanceof SyntaxError) {
      return json(origin, 400, { error: "Solicitud inválida." });
    }
    return json(origin, 500, { error: "No se pudo procesar el acceso." });
  }
});
