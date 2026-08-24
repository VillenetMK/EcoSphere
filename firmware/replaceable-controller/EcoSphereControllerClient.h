#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <Preferences.h>
#include <WiFiClientSecure.h>
#include <esp_system.h>

// Identity and Supabase RPC client shared by every physical ESP32 replacement.
// The sketch contains no per-board identifier: the eFuse MAC and a random secret
// stored in NVS make each board unique on its first boot.
class EcoSphereControllerClient {
 public:
  EcoSphereControllerClient(
      const char* supabaseUrl,
      const char* publishableKey,
      const char* rootCa)
      : supabaseUrl_(supabaseUrl),
        publishableKey_(publishableKey),
        rootCa_(rootCa) {}

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

    encodeSecret();
    return true;
  }

  const char* hardwareUid() const { return hardwareUid_; }

  // Call only when the operator explicitly requests pairing. The returned code
  // expires after 15 minutes and is safe to display on Serial or a local screen.
  String beginPairing(const char* firmwareVersion) {
    JsonDocument payload;
    payload["p_hardware_uid"] = hardwareUid_;
    payload["p_device_secret"] = secretHex_;
    payload["p_firmware_version"] = firmwareVersion;

    JsonDocument response;
    if (!postRpc("controller_begin_pairing", payload, response)) return String();
    if (!response.is<JsonArray>() || response.size() == 0) return String();
    return response[0]["pairing_code"] | String();
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
    payload["p_hardware_uid"] = hardwareUid_;
    payload["p_device_secret"] = secretHex_;
    payload["p_heartbeat_seq"] = heartbeatSequence;
    payload["p_firmware_version"] = firmwareVersion;
    payload["p_has_telemetry"] = hasTelemetry;

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
    if (!postRpc("controller_sync", payload, response)) return false;
    if (!response.is<JsonArray>() || response.size() == 0) return false;
    commands.set(response[0]);
    return true;
  }

 private:
  void encodeSecret() {
    static constexpr char HEX[] = "0123456789abcdef";
    for (size_t i = 0; i < sizeof(secret_); ++i) {
      secretHex_[i * 2] = HEX[(secret_[i] >> 4) & 0x0F];
      secretHex_[i * 2 + 1] = HEX[secret_[i] & 0x0F];
    }
    secretHex_[64] = '\0';
  }

  static void copyTelemetry(
      JsonDocument& destination,
      JsonObjectConst source,
      const char* sourceKey,
      const char* destinationKey) {
    if (!source[sourceKey].isNull()) destination[destinationKey] = source[sourceKey];
  }

  bool postRpc(const char* rpcName, JsonDocument& payload, JsonDocument& response) {
    WiFiClientSecure secureClient;
    secureClient.setCACert(rootCa_);

    HTTPClient http;
    const String url = String(supabaseUrl_) + "/rest/v1/rpc/" + rpcName;
    if (!http.begin(secureClient, url)) return false;
    http.addHeader("apikey", publishableKey_);
    http.addHeader("Authorization", String("Bearer ") + publishableKey_);
    http.addHeader("Content-Type", "application/json");

    String body;
    serializeJson(payload, body);
    const int status = http.POST(body);
    const String responseBody = http.getString();
    http.end();

    if (status < 200 || status >= 300) {
      Serial.printf("EcoSphere RPC %s failed: HTTP %d\n", rpcName, status);
      return false;
    }
    return deserializeJson(response, responseBody) == DeserializationError::Ok;
  }

  const char* supabaseUrl_;
  const char* publishableKey_;
  const char* rootCa_;
  Preferences preferences_;
  uint8_t secret_[32]{};
  char secretHex_[65]{};
  char hardwareUid_[13]{};
};
