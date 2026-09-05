# Controladores ESP32 reemplazables

Copyright (c) 2026 Gabriel Enrique Villenet Montero. Todos los derechos
reservados. Uso sujeto al archivo `LICENSE` del repositorio.

Este módulo permite cargar **el mismo firmware** en todos los ESP32. Cada placa obtiene automáticamente:

- un identificador físico derivado de su eFuse MAC;
- un secreto aleatorio de 256 bits guardado en NVS, que nunca se envía a la app;
- un código temporal para que el administrador decida cuál placa controla el EcoSphere principal.

## Integración en el sketch existente

Requiere `ArduinoJson`, `HTTPClient`, `WiFiClientSecure` y `Preferences`.

```cpp
#include "EcoSphereControllerClient.h"

EcoSphereControllerClient controller(SUPABASE_URL, ROOT_CA);
uint64_t heartbeatSequence = 0;

void setup() {
  Serial.begin(115200);
  // Conectar Wi-Fi como ya lo hace el firmware actual.
  if (!controller.begin()) {
    Serial.println("No se pudo crear la identidad del ESP32");
    return;
  }
  // Estos dos valores identifican físicamente esta placa para que un
  // administrador autorice el reemplazo. No revelan el secreto del dispositivo.
  Serial.printf("UID EcoSphere: %s\n", controller.hardwareUid());
  Serial.printf("Prueba EcoSphere: %s\n", controller.pairingClaimProof());
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

`ROOT_CA` debe contener el certificado raíz válido para `*.supabase.co`. No use `setInsecure()` y no coloque una clave `service_role` ni una clave publicable en el ESP32. La biblioteca llama exclusivamente a `controller-gateway`; la identidad se comprueba con el secreto único de la placa.

## Versión mínima y señales desconectadas

Use firmware `2.1.0+replaceable` o posterior. En cada arranque, la biblioteca
genera un nonce aleatorio de 128 bits y lo combina con una secuencia creciente;
Supabase rechaza repeticiones y órdenes de arranques anteriores. La telemetría
de suelo sólo se acepta para compilaciones `replaceable` compatibles y se
guarda como `null` para firmware inseguro, evitando presentar un GPIO34
flotante como una medición física.

GPIO34 del ESP32 no dispone de pull-up/pull-down interno. Instale una
resistencia de **47 kΩ a 100 kΩ entre GPIO34 y GND**, cerca del ESP32. Sin esa
resistencia, un sensor capacitivo desconectado puede producir porcentajes
aleatorios aunque el firmware filtre múltiples muestras. Después de instalarla,
recalibre `SUELO_SECO_ADC` y `SUELO_MOJADO_ADC` con el sensor real.

El flotador de GPIO32 usa `INPUT_PULLUP` y lógica activa en bajo. Un contacto
abierto y un cable desconectado son eléctricamente indistinguibles con sólo dos
hilos; ambos se reportan como `low`. En EcoSphere ese valor significa **agua no
confirmada**, no confirma la presencia física del sensor y siempre bloquea la
bomba. Para mostrar un estado separado de “sensor desconectado” se necesita un
circuito supervisado con resistencia de fin de línea.

## Reemplazo

1. Encienda el ESP32 de reserva y copie de su puerto serie el **UID EcoSphere** y la **Prueba EcoSphere**.
2. Entre como administrador con el autenticador habilitado.
3. Abra **Diagnóstico del sistema → Controlador ESP32 reemplazable**, ingrese ambos valores y pulse **Autorizar este ESP32**.
4. Antes de que pasen dos minutos, solicite localmente el código con `beginPairing()`.
5. Ingrese el código temporal de 12 dígitos hexadecimales y pulse **Usar como reemplazo**. Supabase lo muestra agrupado como `XXXX-XXXX-XXXX` (14 caracteres al contar los dos guiones); el cliente acepta tanto la forma agrupada como los 12 dígitos sin guiones.
6. La primera sincronización 2.1 válida activa de forma irreversible el protocolo estricto.

La prueba tiene 24 caracteres hexadecimales y se deriva localmente del secreto de
la placa. La aplicación no guarda la prueba y el secreto de 256 bits nunca se
muestra ni sale del ESP32 salvo hacia el gateway, protegido por TLS.

El controlador anterior pasa a reserva y deja de escribir. Las órdenes, usuarios y registros históricos continúan perteneciendo al mismo EcoSphere.

## Seguridad

No suba al repositorio contraseñas Wi-Fi, secretos del controlador ni claves
privadas. El sketch listo para flashear debe mantenerse fuera del repositorio
público.
