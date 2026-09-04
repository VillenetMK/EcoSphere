/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

// Two-phase ESP32 credential rotation gateway. Request bodies contain active
// or pending device secrets, so this function intentionally has no logging.

const allowedOrigins = new Set([
  "https://villenetmk.github.io",
  "https://ecospherecontrol.com",
  "https://www.ecospherecontrol.com",
  "http://localhost:8000",
  "http://127.0.0.1:8000",
]);

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const MAX_REQUEST_BYTES = 2048;
const MAX_RPC_RESPONSE_BYTES = 2048;
const MAX_JSON_NESTING = 2;
const SAFE_ERROR = Object.freeze({ error: "Controller credential request rejected." });

type JsonObject = Record<string, unknown>;
type Operation = "prepare" | "commit";

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
      if (escaped) escaped = false;
      else if (character === "\\") escaped = true;
      else if (character === '"') insideString = false;
      continue;
    }
    if (character === '"') insideString = true;
    else if (character === "{" || character === "[") {
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
  if (!request.body) throw new SyntaxError("missing body");
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let byteLength = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      byteLength += value.byteLength;
      if (byteLength > MAX_REQUEST_BYTES) {
        await reader.cancel("body too large");
        throw new PayloadTooLargeError("body too large");
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
  if (!isSafeJsonNesting(text)) throw new SyntaxError("invalid nesting");
  return JSON.parse(text);
}

async function readBoundedResponse(response: Response): Promise<unknown | undefined> {
  const contentLength = response.headers.get("content-length");
  if (contentLength && (!/^\d+$/.test(contentLength)
      || Number(contentLength) > MAX_RPC_RESPONSE_BYTES)) {
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
        await reader.cancel("response too large");
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

function isPlainObject(value: unknown): value is JsonObject {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function hasExactlyFields(body: JsonObject, fields: readonly string[]) {
  const keys = Object.keys(body).sort();
  const expected = [...fields].sort();
  return keys.length === expected.length
    && keys.every((key, index) => key === expected[index]);
}

function isHex(value: unknown, length: number) {
  return typeof value === "string"
    && value.length === length
    && /^[0-9a-f]+$/.test(value);
}

function isHardwareUid(value: unknown) {
  return typeof value === "string" && /^[0-9A-F]{12}$/.test(value);
}

function operationFor(body: unknown): Operation | undefined {
  if (!isPlainObject(body) || typeof body.operation !== "string") return undefined;
  if (body.operation === "prepare") {
    const fields = [
      "operation", "p_hardware_uid", "p_current_secret", "p_new_secret",
      "p_prepare_nonce",
    ];
    if (!hasExactlyFields(body, fields)
        || !isHardwareUid(body.p_hardware_uid)
        || !isHex(body.p_current_secret, 64)
        || !isHex(body.p_new_secret, 64)
        || body.p_current_secret === body.p_new_secret
        || !isHex(body.p_prepare_nonce, 32)) return undefined;
    return "prepare";
  }
  if (body.operation === "commit") {
    const fields = [
      "operation", "p_hardware_uid", "p_new_secret", "p_rotation_id",
      "p_challenge", "p_commit_nonce",
    ];
    if (!hasExactlyFields(body, fields)
        || !isHardwareUid(body.p_hardware_uid)
        || !isHex(body.p_new_secret, 64)
        || typeof body.p_rotation_id !== "string"
        || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
          body.p_rotation_id,
        )
        || !isHex(body.p_challenge, 64)
        || !isHex(body.p_commit_nonce, 32)) return undefined;
    return "commit";
  }
  return undefined;
}

function rpcArguments(body: JsonObject) {
  const { operation: _operation, ...argumentsForRpc } = body;
  return argumentsForRpc;
}

async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(
    new Uint8Array(digest),
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");
}

function sourceAddress(request: Request) {
  // Trust only the right-most value appended by the hosted Supabase gateway.
  // If no gateway-managed peer is available, every request shares one strict
  // bucket; no client-supplied alternate IP header is trusted.
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

function isPrepareResponse(value: unknown) {
  if (!Array.isArray(value) || value.length !== 1 || !isPlainObject(value[0])) return false;
  const row = value[0];
  return hasExactlyFields(row, ["rotation_id", "challenge", "expires_at"])
    && typeof row.rotation_id === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
      row.rotation_id,
    )
    && isHex(row.challenge, 64)
    && typeof row.expires_at === "string"
    && row.expires_at.length >= 20
    && row.expires_at.length <= 40;
}

function isCommitResponse(value: unknown) {
  if (!Array.isArray(value) || value.length !== 1 || !isPlainObject(value[0])) return false;
  const row = value[0];
  return hasExactlyFields(row, ["committed", "already_committed"])
    && row.committed === true
    && typeof row.already_committed === "boolean";
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

  const contentType = request.headers.get("content-type")?.split(";", 1)[0]
    .trim().toLowerCase();
  const contentEncoding = request.headers.get("content-encoding");
  const contentLength = request.headers.get("content-length");
  if (contentType !== "application/json"
      || (contentEncoding && contentEncoding.toLowerCase() !== "identity")
      || (contentLength !== null
        && (!/^\d+$/.test(contentLength) || Number(contentLength) > MAX_REQUEST_BYTES))) {
    return safeError(origin, 400);
  }

  try {
    const sourceBucket = await sha256(
      `controller-credentials:v1:source:${sourceAddress(request)}`,
    );
    const sourceRate = rateResult(await callRpc("controller_credential_take_rate_limit", {
      p_bucket_scope: "source",
      p_bucket_key: sourceBucket,
    }));
    if (!sourceRate) return safeError(origin, 503);
    if (!sourceRate.allowed) {
      return safeError(origin, 429, { "Retry-After": String(sourceRate.retryAfter) });
    }

    const body = await readBoundedJson(request);
    const operation = operationFor(body);
    if (!operation || !isPlainObject(body)) return safeError(origin, 400);

    const hardwareBucket = await sha256(
      `controller-credentials:v1:hardware:${operation}:${body.p_hardware_uid}`,
    );
    const hardwareRate = rateResult(await callRpc("controller_credential_take_rate_limit", {
      p_bucket_scope: "hardware",
      p_bucket_key: hardwareBucket,
    }));
    if (!hardwareRate) return safeError(origin, 503);
    if (!hardwareRate.allowed) {
      return safeError(origin, 429, { "Retry-After": String(hardwareRate.retryAfter) });
    }

    const rpcName = operation === "prepare"
      ? "controller_credential_prepare"
      : "controller_credential_commit";
    const result = await callRpc(rpcName, rpcArguments(body));
    if (operation === "prepare" ? !isPrepareResponse(result) : !isCommitResponse(result)) {
      return safeError(origin, 400);
    }
    return json(origin, 200, result);
  } catch (error) {
    // Parsing, authentication, replay, expiry and database failures are
    // deliberately indistinguishable. Never log an error object or payload.
    if (error instanceof PayloadTooLargeError) return safeError(origin, 413);
    return safeError(origin, 400);
  }
});

