# EcoSphere

EcoSphere es la aplicación multiplataforma para supervisar y controlar el microclima conectado al ESP32. Usa el mismo proyecto Supabase para telemetría, historial, heartbeat y órdenes remotas.

## Plataformas

| Plataforma | Entrega |
|---|---|
| Android y ChromeOS | APK |
| Windows | MSI |
| Linux | DEB |
| Navegador y Chromebook | PWA instalable |

## Arquitectura

- `sharedCore`: modelos, configuración y reglas críticas compartidas por Android y Desktop.
- `app`: aplicación Android adaptativa para teléfonos, tablets y ChromeOS.
- `desktopApp`: aplicación Compose Desktop para Windows y Linux.
- `webApp`: PWA responsive con pruebas de paridad para las reglas de control.
- `.github/workflows`: pruebas, empaquetado de instaladores y despliegue de GitHub Pages.

## Reglas protegidas

- El flotador horizontal de GPIO32 sólo admite `high` y `low`.
- El riego manual se bloquea sin lecturas válidas, con agua baja o con humedad del suelo igual o superior a 60 %.
- La potencia del ventilador y del LED se limita al rango de 0 a 100 %.
- El estado online exige un heartbeat válido dentro de 30 segundos.

## Verificación

```bash
# Reglas del navegador
node --test webApp/tests/control-policy.test.js

# Núcleo compartido y APK
./gradlew :sharedCore:test :app:testDebugUnitTest :app:assembleDebug

# Desktop, desde la raíz
gradle -p desktopApp build
```

Los instaladores se generan como artefactos descargables de GitHub Actions. La planificación completa está en [docs/MULTIPLATFORM_MIGRATION.md](docs/MULTIPLATFORM_MIGRATION.md).
