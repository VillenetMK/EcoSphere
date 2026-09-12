/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import { controllerOperation, isPlainObject, rpcArguments, type JsonObject } from "./validation.ts";

// This function intentionally has no request logging. Controller payloads
// contain a long-lived device secret and must never enter Edge logs or errors.

const allowedOrigins = new Set([
  "https://villenetmk.github.io",
  "https://ecospherecontrol.com",
  "https://www.ecospherecontrol.com",
  "http://localhost:8000",
  "http://127.0.0.1:8000",
]);

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const MAX_REQUEST_BYTES = 4096;
const MAX_RPC_RESPONSE_BYTES = 8192;
const MAX_JSON_NESTING = 4;
const SAFE_ERROR = Object.freeze({ error: "Controller request rejected." });

class PayloadTooLargeError extends Error {}

function corsHeaders(origin: string | null) {
  const headers: Record<string, string> = {
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Cache-Control": "no-store",
    "Vary": "Origin",
  };
  if (origin && allowedOrigins.has(origin)) headers["Access-Control-Allow-Origin"] = origin;
  return headers;
}

function json(origin: string | null, status: number, body: unknown, extra: HeadersInit = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders(origin),
      ...Object.fromEntries(new Headers(extra).entries()),
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function safeError(origin: string | null, status: number, extra: HeadersInit = {}) {
  return json(origin, status, SAFE_ERROR, extra);
}

function isSafeJsonNesting(text: string) {
  let depth = 0;
  let insideString = false;
  let escaped = false;

  for (const character of text) {
    if (insideString) {
      if (escaped) {
        escaped = false;
      } else if (character === "\\") {
        escaped = true;
      } else if (character === '"') {
        insideString = false;
      }
      continue;
    }
    if (character === '"') {
      insideString = true;
    } else if (character === "{" || character === "[") {
      depth += 1;
      if (depth > MAX_JSON_NESTING) return false;
    } else if (character === "}" || character === "]") {
      depth -= 1;
      if (depth < 0) return false;
    }
  }
  return !insideString && !escaped && depth === 0;
}

async function readBoundedJson(request: Request): Promise<unknown> {
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

  const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  if (!isSafeJsonNesting(text)) throw new SyntaxError("invalid JSON nesting");
  return JSON.parse(text);
}

async function readBoundedResponse(response: Response): Promise<unknown | undefined> {
  const contentLength = response.headers.get("content-length");
  if (contentLength && (!/^\d+$/.test(contentLength) || Number(contentLength) > MAX_RPC_RESPONSE_BYTES)) {
    await response.body?.cancel();
    return undefined;
  }
  if (!response.body) return undefined;

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let byteLength = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      byteLength += value.byteLength;
      if (byteLength > MAX_RPC_RESPONSE_BYTES) {
        await reader.cancel("RPC response too large");
        return undefined;
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
    return undefined;
  }
}

async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function sourceAddress(request: Request) {
  // Supabase's gateway appends the verified peer address to X-Forwarded-For.
  // Therefore only the right-most valid value is trusted. Never prefer
  // client-supplied CF-Connecting-IP, X-Real-IP or a left-most XFF value. If a
  // deployment does not supply the gateway-managed value, all such requests
  // share one conservative bucket instead of choosing an attacker-supplied one.
  const forwardedFor = request.headers.get("x-forwarded-for");
  const candidate = forwardedFor?.split(",").at(-1)?.trim();
  if (candidate && candidate.length <= 64 && /^[0-9A-Fa-f:.]+$/.test(candidate)) {
    return candidate;
  }
  return "unavailable";
}

async function callRpc(functionName: string, argumentsForRpc: JsonObject) {
  const response = await fetch(`${supabaseUrl}/rest/v1/rpc/${functionName}`, {
    method: "POST",
    headers: {
      "Accept": "application/json",
      "apikey": serviceRoleKey,
      "Authorization": `Bearer ${serviceRoleKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(argumentsForRpc),
  });
  if (!response.ok) {
    await response.body?.cancel();
    return undefined;
  }
  return await readBoundedResponse(response);
}

function rateResult(value: unknown) {
  const row = Array.isArray(value) ? value[0] : value;
  if (!isPlainObject(row) || typeof row.allowed !== "boolean") return undefined;
  const retryAfter = Number(row.retry_after_seconds ?? 60);
  return {
    allowed: row.allowed,
    retryAfter: Number.isSafeInteger(retryAfter) && retryAfter > 0 ? retryAfter : 60,
  };
}

Deno.serve(async (request) => {
  const origin = request.headers.get("origin");
  if (origin && !allowedOrigins.has(origin)) return safeError(origin, 403);
  if (request.method === "OPTIONS") {
    return origin && allowedOrigins.has(origin)
      ? new Response(null, { status: 204, headers: corsHeaders(origin) })
      : safeError(origin, 403);
  }
  if (request.method !== "POST") return safeError(origin, 405);
  if (!supabaseUrl || !serviceRoleKey) return safeError(origin, 503);

  const contentType = request.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase();
  const contentEncoding = request.headers.get("content-encoding");
  const contentLength = request.headers.get("content-length");
  if (contentType !== "application/json"
      || (contentEncoding && contentEncoding.toLowerCase() !== "identity")
      || (contentLength !== null
        && (!/^\d+$/.test(contentLength) || Number(contentLength) > MAX_REQUEST_BYTES))) {
    return safeError(origin, 400);
  }

  try {
    // The rate-limit RPC is called before parsing attacker-controlled JSON so
    // valid-size malformed payloads consume the same bounded request budget.
    const sourceBucket = await sha256(`controller-gateway:v1:source:${sourceAddress(request)}`);
    const rateLimit = rateResult(await callRpc("controller_gateway_take_rate_limit", {
      p_source_bucket: sourceBucket,
    }));
    if (!rateLimit) return safeError(origin, 503);
    if (!rateLimit.allowed) {
      return safeError(origin, 429, { "Retry-After": String(rateLimit.retryAfter) });
    }

    const body = await readBoundedJson(request);
    const operation = controllerOperation(body);
    if (!operation || !isPlainObject(body)) return safeError(origin, 400);

    const rpcName = operation === "begin_pairing"
      ? "controller_begin_pairing"
      : "controller_sync";
    const result = await callRpc(rpcName, rpcArguments(body));
    if (!Array.isArray(result)) return safeError(origin, 400);
    return json(origin, 200, result);
  } catch (error) {
    // Do not expose or log payload parsing, service-role, database, device
    // authentication or replay errors. Their status/body are intentionally
    // indistinguishable to untrusted clients.
    if (error instanceof PayloadTooLargeError) return safeError(origin, 413);
    return safeError(origin, 400);
  }
});

