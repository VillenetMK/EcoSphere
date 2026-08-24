# Controladores ESP32 reemplazables

Este módulo permite cargar **el mismo firmware** en todos los ESP32. Cada placa obtiene automáticamente:

- un identificador físico derivado de su eFuse MAC;
- un secreto aleatorio de 256 bits guardado en NVS, que nunca se envía a la app;
- un código temporal para que el administrador decida cuál placa controla el EcoSphere principal.

## Integración en el sketch existente

Requiere `ArduinoJson`, `HTTPClient`, `WiFiClientSecure` y `Preferences`.

```cpp
#include "EcoSphereControllerClient.h"

EcoSphereControllerClient controller(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, ROOT_CA);
uint64_t heartbeatSequence = 0;

void setup() {
  Serial.begin(115200);
  // Conectar Wi-Fi como ya lo hace el firmware actual.
  if (!controller.begin()) Serial.println("No se pudo crear la identidad del ESP32");
}

void requestPairing() {
  const String code = controller.beginPairing(FIRMWARE_VERSION);
  if (code.length()) Serial.printf("Código EcoSphere: %s\n", code.c_str());
}

void sendState() {
  JsonDocument telemetry;
  telemetry["temperature"] = temperature;
  telemetry["air_humidity"] = airHumidity;
  telemetry["soil_humidity"] = soilHumidity;
  telemetry["light_lux"] = lightLux;
  telemetry["water_level"] = waterLevel; // "low" o "high"
  telemetry["fan_on"] = fanOn;
  telemetry["pump_on"] = pumpOn;
  telemetry["led_on"] = ledOn;
  telemetry["auto_mode"] = autoMode;
  telemetry["fan_power"] = fanPower;
  telemetry["led_power"] = ledPower;

  JsonDocument commands;
  if (controller.sync(++heartbeatSequence, FIRMWARE_VERSION, true, telemetry.as<JsonObjectConst>(), commands)) {
    // Aplicar fan_target, fan_power, led_target, led_power, auto_mode,
    // pump_request y pump_duration_ms usando la lógica actual del sketch.
  }
}
```

`ROOT_CA` debe contener el certificado raíz válido para `*.supabase.co`. No use `setInsecure()` y no coloque una clave `service_role` en el ESP32.

## Reemplazo

1. Encienda el ESP32 de reserva y solicite su código de vinculación.
2. Entre como administrador con el autenticador habilitado.
3. Abra **Diagnóstico del sistema → Controlador ESP32 reemplazable**.
4. Ingrese el código de 12 caracteres y pulse **Usar como reemplazo**.
5. La primera sincronización segura desactiva automáticamente el acceso anónimo antiguo.

El controlador anterior pasa a reserva y deja de escribir. Las órdenes, usuarios y registros históricos continúan perteneciendo al mismo EcoSphere.
