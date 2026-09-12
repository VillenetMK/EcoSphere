/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

export type JsonObject = Record<string, unknown>;

export function isPlainObject(value: unknown): value is JsonObject {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function hasOnlyFields(body: JsonObject, fields: readonly string[]) {
  const allowed = new Set(fields);
  return Object.keys(body).every((field) => allowed.has(field));
}

function isNullableBoolean(value: unknown) {
  return value === undefined || value === null || typeof value === "boolean";
}

function isHardwareUid(value: unknown) {
  return typeof value === "string" && /^[0-9A-Fa-f]{12}$/.test(value);
}

function isDeviceSecret(value: unknown) {
  return typeof value === "string" && /^[0-9A-Fa-f]{64}$/.test(value);
}

function isFirmwareVersion(value: unknown) {
  return typeof value === "string" && value.length >= 1 && value.length <= 40;
}

function isBootNonce(value: unknown) {
  return typeof value === "string" && /^[0-9A-Fa-f]{32}$/.test(value);
}

function isNullableNumberInRange(value: unknown, minimum: number, maximum: number) {
  return value === undefined
    || value === null
    || (typeof value === "number" && Number.isFinite(value) && value >= minimum && value <= maximum);
}

function isNullableIntegerInRange(value: unknown, minimum: number, maximum: number) {
  return value === undefined
    || value === null
    || (typeof value === "number"
      && Number.isSafeInteger(value)
      && value >= minimum
      && value <= maximum);
}

function isNullableWaterLevel(value: unknown) {
  return value === undefined || value === null || value === "low" || value === "high";
}

function isNonNegativeBigint(value: unknown) {
  if (typeof value === "number") return Number.isSafeInteger(value) && value >= 0;
  return typeof value === "string"
    && /^(?:0|[1-9][0-9]{0,18})$/.test(value)
    && (value.length < 19 || value <= "9223372036854775807");
}

const pumpDiagnosticFields = [
  "request_id", "result", "reason", "elapsed_ms", "gpio26_start", "gpio26_end",
  "led_power_before", "led_power_after", "led_duty_before", "led_duty_during", "led_duty_after",
] as const;

const completedPumpReasons = new Set([
  "duration_elapsed", "authorization_expired", "mode_changed", "water_unavailable",
  "soil_invalid", "soil_wet", "manual_stop",
]);
const rejectedPumpReasons = new Set(["remote_gate", "local_gate"]);

function isIntegerInRange(value: unknown, minimum: number, maximum: number) {
  return typeof value === "number"
    && Number.isSafeInteger(value)
    && value >= minimum
    && value <= maximum;
}

function isNullablePumpDiagnostic(value: unknown) {
  // Omission and null preserve compatibility with controllers without diagnostics.
  if (value === undefined || value === null) return true;
  if (!isPlainObject(value)
      || Object.keys(value).length !== pumpDiagnosticFields.length
      || !hasOnlyFields(value, pumpDiagnosticFields)) return false;

  const validReason = typeof value.reason === "string"
    && (value.result === "completed"
      ? completedPumpReasons.has(value.reason)
      : value.result === "rejected" && rejectedPumpReasons.has(value.reason));
  return validReason
    && isIntegerInRange(value.request_id, 1, Number.MAX_SAFE_INTEGER)
    // Firmware elapsed time is an unsigned 32-bit millis() difference.
    && isIntegerInRange(value.elapsed_ms, 0, 4294967295)
    && (value.gpio26_start === null || isIntegerInRange(value.gpio26_start, 0, 1))
    && (value.gpio26_end === null || isIntegerInRange(value.gpio26_end, 0, 1))
    && isIntegerInRange(value.led_power_before, 0, 100)
    && isIntegerInRange(value.led_power_after, 0, 100)
    && isIntegerInRange(value.led_duty_before, 0, 256)
    && (value.led_duty_during === null || isIntegerInRange(value.led_duty_during, 0, 256))
    && isIntegerInRange(value.led_duty_after, 0, 256);
}

export function controllerOperation(body: unknown): "begin_pairing" | "sync" | undefined {
  if (!isPlainObject(body) || typeof body.operation !== "string") return undefined;

  if (body.operation === "begin_pairing") {
    const allowed = ["operation", "p_hardware_uid", "p_device_secret", "p_firmware_version"];
    if (!hasOnlyFields(body, allowed)
        || !isHardwareUid(body.p_hardware_uid)
        || !isDeviceSecret(body.p_device_secret)
        || !isFirmwareVersion(body.p_firmware_version)) return undefined;
    return "begin_pairing";
  }

  if (body.operation !== "sync") return undefined;
  const allowed = [
    "operation", "p_hardware_uid", "p_device_secret", "p_heartbeat_seq",
    "p_firmware_version", "p_has_telemetry", "p_temperature", "p_air_humidity",
    "p_soil_humidity", "p_light_lux", "p_water_level", "p_fan_on", "p_pump_on",
    "p_led_on", "p_reported_auto_mode", "p_reported_fan_power", "p_reported_led_power",
    "p_boot_nonce", "p_pump_diagnostic",
  ];
  if (!hasOnlyFields(body, allowed)
      || !isHardwareUid(body.p_hardware_uid)
      || !isDeviceSecret(body.p_device_secret)
      || !isNonNegativeBigint(body.p_heartbeat_seq)
      || !isFirmwareVersion(body.p_firmware_version)
      || typeof body.p_has_telemetry !== "boolean"
      || !isNullableNumberInRange(body.p_temperature, -40, 85)
      || !isNullableNumberInRange(body.p_air_humidity, 0, 100)
      || !isNullableNumberInRange(body.p_soil_humidity, 0, 100)
      || !isNullableNumberInRange(body.p_light_lux, 0, 200000)
      || !isNullableWaterLevel(body.p_water_level)
      || !isNullableBoolean(body.p_fan_on)
      || !isNullableBoolean(body.p_pump_on)
      || !isNullableBoolean(body.p_led_on)
      || !isNullableBoolean(body.p_reported_auto_mode)
      || !isNullableIntegerInRange(body.p_reported_fan_power, 0, 100)
      || !isNullableIntegerInRange(body.p_reported_led_power, 0, 100)
      || !isBootNonce(body.p_boot_nonce)
      || !isNullablePumpDiagnostic(body.p_pump_diagnostic)) return undefined;
  return "sync";
}

export function rpcArguments(body: JsonObject) {
  const { operation: _operation, ...argumentsForRpc } = body;
  return argumentsForRpc;
}
