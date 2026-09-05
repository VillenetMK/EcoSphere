/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <Preferences.h>
#include <WiFiClientSecure.h>
#include <esp_system.h>
#include <mbedtls/md.h>

// Identity and Supabase RPC client shared by every physical ESP32 replacement.
// The sketch contains no per-board identifier: the eFuse MAC and a random secret
// stored in NVS make each board unique on its first boot.
class EcoSphereControllerClient {
 public:
  EcoSphereControllerClient(
      const char* supabaseUrl,
      const char* rootCa)
      : supabaseUrl_(supabaseUrl),
        rootCa_(rootCa) {}

  // Backward-compatible constructor for sketches that still pass the public
  // Supabase key. The controller gateway authenticates with the per-board
  // secret, so the key is intentionally ignored and never sent over the wire.
  EcoSphereControllerClient(
      const char* supabaseUrl,
      const char* /* publishableKey */,
      const char* rootCa)
      : EcoSphereControllerClient(supabaseUrl, rootCa) {}

  bool begin() {
    const uint64_t chipId = ESP.getEfuseMac();
    snprintf(hardwareUid_, sizeof(hardwareUid_), "%012llX",
             static_cast<unsigned long long>(chipId & 0xFFFFFFFFFFFFULL));

    if (!preferences_.begin("eco_ctrl", false)) return false;
    if (preferences_.getBytesLength("secret") != sizeof(secret_)) {
      esp_fill_random(secret_, sizeof(secret_));
      if (preferences_.putBytes("secret", secret_, sizeof(secret_)) != sizeof(secret_)) {
        return false;
      }
    } else if (preferences_.getBytes("secret", secret_, sizeof(secret_)) != sizeof(secret_)) {
      return false;
    }

    esp_fill_random(bootNonce_, sizeof(bootNonce_));
    encodeHex(secret_, sizeof(secret_), secretHex_);
    encodeHex(bootNonce_, sizeof(bootNonce_), bootNonceHex_);
    return buildPairingClaimProof();
  }

  const char* hardwareUid() const { return hardwareUid_; }
  const char* pairingClaimProof() const { return pairingClaimProof_; }

  // Call only when the operator explicitly requests pairing. The returned code
  // expires after 5 minutes and is safe to display on Serial or a local screen.
  String beginPairing(const char* firmwareVersion) {
    JsonDocument payload;
    payload["operation"] = "begin_pairing";
    payload["p_hardware_uid"] = hardwareUid_;
    payload["p_device_secret"] = secretHex_;
    payload["p_firmware_version"] = firmwareVersion;

    JsonDocument response;
    if (!postGateway(payload, response)) return String();
    if (!response.is<JsonArray>() || response.size() == 0) return String();
    const String pairingCode = response[0]["pairing_code"] | String();
    return isValidPairingCode(pairingCode) ? pairingCode : String();
  }

  // Sends heartbeat and optional telemetry. Only the controller selected by the
  // administrator is accepted; a standby board receives HTTP 403.
  bool sync(
      uint64_t heartbeatSequence,
      const char* firmwareVersion,
      bool hasTelemetry,
      JsonObjectConst telemetry,
      JsonDocument& commands) {
    JsonDocument payload;
    payload["operation"] = "sync";
    payload["p_hardware_uid"] = hardwareUid_;
    payload["p_device_secret"] = secretHex_;
    payload["p_heartbeat_seq"] = heartbeatSequence;
    payload["p_firmware_version"] = firmwareVersion;
    payload["p_has_telemetry"] = hasTelemetry;
    payload["p_boot_nonce"] = bootNonceHex_;

    copyTelemetry(payload, telemetry, "temperature", "p_temperature");
    copyTelemetry(payload, telemetry, "air_humidity", "p_air_humidity");
    copyTelemetry(payload, telemetry, "soil_humidity", "p_soil_humidity");
    copyTelemetry(payload, telemetry, "light_lux", "p_light_lux");
    copyTelemetry(payload, telemetry, "water_level", "p_water_level");
    copyTelemetry(payload, telemetry, "fan_on", "p_fan_on");
    copyTelemetry(payload, telemetry, "pump_on", "p_pump_on");
    copyTelemetry(payload, telemetry, "led_on", "p_led_on");
    copyTelemetry(payload, telemetry, "auto_mode", "p_reported_auto_mode");
    copyTelemetry(payload, telemetry, "fan_power", "p_reported_fan_power");
    copyTelemetry(payload, telemetry, "led_power", "p_reported_led_power");

    JsonDocument response;
    if (!postGateway(payload, response)) return false;
    if (!response.is<JsonArray>() || response.size() == 0) return false;
    commands.set(response[0]);
    return true;
  }

 private:
  static void encodeHex(const uint8_t* bytes, size_t length, char* output) {
    static constexpr char HEX_DIGITS_LOWER[] = "0123456789abcdef";
    for (size_t i = 0; i < length; ++i) {
      output[i * 2] = HEX_DIGITS_LOWER[(bytes[i] >> 4) & 0x0F];
      output[i * 2 + 1] = HEX_DIGITS_LOWER[bytes[i] & 0x0F];
    }
    output[length * 2] = '\0';
  }

  static void encodeHexUpper(const uint8_t* bytes, size_t length, char* output) {
    static constexpr char HEX_DIGITS_UPPER[] = "0123456789ABCDEF";
    for (size_t i = 0; i < length; ++i) {
      output[i * 2] = HEX_DIGITS_UPPER[(bytes[i] >> 4) & 0x0F];
      output[i * 2 + 1] = HEX_DIGITS_UPPER[bytes[i] & 0x0F];
    }
    output[length * 2] = '\0';
  }

  static bool isHexCharacter(char value) {
    return (value >= '0' && value <= '9')
        || (value >= 'A' && value <= 'F')
        || (value >= 'a' && value <= 'f');
  }

  static bool isValidPairingCode(const String& code) {
    if (code.length() == 12) {
      for (size_t i = 0; i < 12; ++i) {
        if (!isHexCharacter(code[i])) return false;
      }
      return true;
    }

    if (code.length() != 14 || code[4] != '-' || code[9] != '-') {
      return false;
    }
    for (size_t i = 0; i < 14; ++i) {
      if (i == 4 || i == 9) continue;
      if (!isHexCharacter(code[i])) return false;
    }
    return true;
  }

  bool buildPairingClaimProof() {
    const String material = String("ecosphere-pairing-v1:")
        + hardwareUid_ + ":" + secretHex_;
    const mbedtls_md_info_t* sha256 =
        mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    uint8_t digest[32]{};
    if (sha256 == nullptr
        || mbedtls_md(
               sha256,
               reinterpret_cast<const unsigned char*>(material.c_str()),
               material.length(),
               digest) != 0) {
      pairingClaimProof_[0] = '\0';
      return false;
    }

    // Supabase compares the first 12 SHA-256 bytes as 24 uppercase hex chars.
    encodeHexUpper(digest, 12, pairingClaimProof_);
    return true;
  }

  static void copyTelemetry(
      JsonDocument& destination,
      JsonObjectConst source,
      const char* sourceKey,
      const char* destinationKey) {
    if (!source[sourceKey].isNull()) destination[destinationKey] = source[sourceKey];
  }

  bool postGateway(JsonDocument& payload, JsonDocument& response) {
    WiFiClientSecure secureClient;
    secureClient.setCACert(rootCa_);

    HTTPClient http;
    const String url = String(supabaseUrl_) + "/functions/v1/controller-gateway";
    if (!http.begin(secureClient, url)) return false;
    http.setTimeout(15000);
    http.addHeader("Content-Type", "application/json");

    String body;
    serializeJson(payload, body);
    const int status = http.POST(body);
    const String responseBody = http.getString();
    http.end();

    if (status < 200 || status >= 300) {
      Serial.printf("EcoSphere controller gateway failed: HTTP %d\n", status);
      return false;
    }
    return deserializeJson(response, responseBody) == DeserializationError::Ok;
  }

  const char* supabaseUrl_;
  const char* rootCa_;
  Preferences preferences_;
  uint8_t secret_[32]{};
  uint8_t bootNonce_[16]{};
  char secretHex_[65]{};
  char bootNonceHex_[33]{};
  char hardwareUid_[13]{};
  char pairingClaimProof_[25]{};
};
