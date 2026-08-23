# Decisiones, correcciones y brechas entre hardware y software

> Registro de precedencia reconstruido del historial “Compra placa ESP32” y del repositorio en `main` (`355890f6`) al 23 de agosto de 2026.

Los documentos canónicos de montaje son [HARDWARE_ELECTRICO.md](HARDWARE_ELECTRICO.md) y [MONTAJE_FISICO.md](MONTAJE_FISICO.md). Si una conversación antigua los contradice, prevalece aquí la decisión más reciente que esté marcada como confirmada.

## Correcciones incorporadas

| Tema | Indicación antigua o ambigua | Decisión canónica |
|---|---|---|
| ESP32 | Interpretación fotográfica de 30 pines | Compra final: placa de 38 pines; variante exacta pendiente |
| BME280 de 6 pines | Dejar CSB/SDO flotantes | CSB→3V3 y SDO→GND; dirección esperada 0x76 |
| Nivel de agua | Dos sensores en GPIO27 y GPIO32; en otro momento se prefirió el vertical | Solo flotador horizontal en GPIO32; GPIO27 libre |
| Polaridad del flotador | Activo en bajo tratado como hecho | `true` es configuración inicial pendiente de prueba física |
| LED grow | Descrito alternativamente como 5 V y 12 V | Tensión nominal sin confirmar; leer etiqueta y medir |
| MOSFET | Añadir pull-down externo de 10 kΩ | No añadirlo por defecto al módulo AOD4184A seleccionado; verificar placa recibida |
| Diodos | “Dos diodos flyback” como compra directa | Protección aún por dimensionar según cada carga; no asumir bomba y ventilador brushless iguales |
| Fusibles | 5 A principal y ~1 A por rama tratados como lista | Valores propuestos, no dimensionados ni instalados |
| Cable AWG | 1,0 mm²=AWG18 y 1,5 mm²=AWG16 exactos | La sección métrica manda; AWG era aproximado |
| Manguera | 6/8 mm como medida final | Medida pendiente de la boquilla real |
| Caja | Varias dimensiones recomendadas | Adquirida: 53×36×30 cm, 37,5 L |
| Malla | Malla de hierro | Sustituir por plástico |
| Solar/18650 | Integración futura mezclada con el montaje | Fuera del alcance actual |
| Retorno de agua | Segunda bomba automática | Diferido; versión actual usa una bomba |
| Diagnóstico de quemado | Inferir desde telemetría | La app solo detecta comunicación/coherencia; tensión y corriente requieren medición física |

## Mapa hardware ↔ software actual

| Hardware/decisión | Android actual | Desktop actual | Web/PWA actual | Brecha |
|---|---|---|---|---|
| BME280 I2C GPIO21/22 | Diagnóstico muestra pines | Solo muestra telemetría | Solo muestra telemetría | El firmware no está versionado |
| BH1750 I2C compartido | Diagnóstico lógico | Diagnóstico lógico básico | Diagnóstico lógico básico | No hay prueba I2C desde las apps |
| Suelo GPIO34 | Diagnóstico y regla manual | Regla manual duplicada | Regla manual duplicada | Calibración vive fuera del repo |
| Flotador horizontal GPIO32 | Reflejado correctamente | Reflejado en UI | No muestra el pin | Polaridad real pendiente |
| Ventilador GPIO25 PWM | Orden 0–100 % | Lógica duplicada | Lógica duplicada | Frecuencia PWM real no verificable sin firmware |
| Bomba GPIO26 | Riego manual 3 s y bloqueos | Misma regla duplicada | Misma regla duplicada | Auto-riego reside en firmware ausente |
| LED GPIO33 PWM | Voltaje no fijado en diagnóstico | No fija voltaje | No fija voltaje | Modelo eléctrico pendiente |
| ESP32 offline >30 s | Implementado | Implementado por código propio | Implementado por JavaScript | Misma regla duplicada tres veces |

## Firmware histórico confirmado, pero ausente del repositorio

El historial describe una versión posterior al firmware original con:

- un solo flotador horizontal en GPIO32 y GPIO27 libre;
- ventilador y LED con potencia 0–100 %;
- riego automático cuando la humedad de suelo es ≤35 %;
- riego manual denegado cuando la humedad es ≥60 %;
- corte del riego al alcanzar 60 %;
- bloqueo/corte por nivel bajo;
- `fan_power`, `led_power` y `heartbeat_seq`;
- telemetría cada 2 s, control cada 1 s y heartbeat cada 10 s;
- compatibilidad declarada con Arduino-ESP32 2.x/3.x;
- `WATER_SENSOR_ACTIVE_LOW = true` como valor inicial;
- calibración inicial del suelo 3000/1400.

No se encontró un `.ino` o proyecto PlatformIO en el repositorio. Hasta recuperarlo, el historial no puede comprobarse contra el binario cargado en el ESP32 y el firmware no tiene una fuente de verdad reproducible.

Acción requerida: recuperar el archivo exacto que se cargó, retirar credenciales Wi-Fi, añadir configuración mediante archivo ignorado/secretos y versionarlo en un directorio de firmware.

## Contrato Supabase observado

### `sensor_records`

- `id`
- `created_at`
- `temperature`
- `air_humidity`
- `soil_humidity`
- `light_lux`
- `water_level`
- `fan_on`
- `fan_power`
- `pump_on`
- `led_on`
- `led_power`
- `auto_mode`

### `device_control`

- `id`
- `fan_target`
- `fan_power`
- `led_target`
- `led_power`
- `auto_mode`
- `pump_request`
- `pump_duration_ms`
- `esp32_online`
- `heartbeat_seq`
- `last_seen_at`
- `updated_at`

También se referencia `sensor_history_months`. El repositorio no contiene migraciones SQL que permitan reconstruir, validar o auditar este esquema.

## Reglas de control confirmadas

- Potencia de ventilador y LED: 0–100 %.
- Solicitud manual de bomba: 3 000 ms desde las aplicaciones.
- Denegar riego manual si no existe lectura válida de humedad.
- Denegar riego manual si suelo ≥60 %.
- Denegar riego manual si `water_level == "low"`.
- Modo automático: las aplicaciones deshabilitan controles manuales y el ESP32 toma la decisión.
- ESP32 online: `esp32_online` verdadero y `last_seen_at` dentro de 30 s.
- Refresco de aplicaciones: 2 s.
- Heartbeat histórico del ESP32: 10 s.
- Umbral histórico de riego automático: suelo ≤35 %; reside en firmware, no en la base de datos.

Los umbrales 35 % y 60 % son decisiones lógicas, no una validación agronómica ni sustituyen calibrar el sensor.

## Estado de la migración multiplataforma al comparar el código

### Disponible

- Android/ChromeOS: aplicación Compose, navegación adaptativa y controles Supabase.
- Windows/Linux: aplicación Compose Desktop independiente y workflows para MSI/DEB.
- Web: PWA estática y workflow de GitHub Pages.

### Diferencia frente al plan documentado

El plan declaraba un núcleo Kotlin Multiplatform y prohibía duplicar la lógica de Supabase una vez creado. En el código actual:

- `settings.gradle.kts` solo incluye `:app`;
- Desktop es un build Gradle separado;
- Desktop contiene modelos, red, estado, reglas de riego y UI en un archivo de gran tamaño;
- Web repite constantes, reglas y llamadas REST en JavaScript;
- no existe `commonMain`, módulo compartido ni contrato generado;
- no hay pruebas de paridad para las reglas de control;
- el workflow Web empaqueta archivos, pero no ejecuta pruebas;
- los builds de Desktop y Web no demuestran compatibilidad de lógica con Android.

La siguiente migración debe ser incremental: conservar las tres aplicaciones, extraer primero reglas puras y pruebas de contrato, y luego compartir red/modelos sin bloquear los instaladores existentes.

## Datos sensibles y configuración

La clave Supabase presente es una clave publicable y forma parte de un cliente público. Su seguridad depende de RLS y políticas correctas, no de ocultarla. Las credenciales Wi-Fi que aparecieron en el chat no deben copiarse a documentación, commits ni ejemplos.

Quedan pendientes:

- versionar y revisar políticas RLS/migraciones SQL;
- separar configuración por entorno;
- comprobar que la clave pública solo tenga permisos mínimos;
- añadir un mecanismo seguro de configuración Wi-Fi para firmware.
