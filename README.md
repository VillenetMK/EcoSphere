# EcoSphere
### Microclima inteligente · Software e IoT

Supervisión y control de un microclima desde una aplicación conectada a un ESP32. El proyecto reúne telemetría, historial y órdenes remotas en clientes para móvil, escritorio y navegador.

**Qué resuelve:** consultar las condiciones del ecosistema y operar riego, ventilación e iluminación desde una misma plataforma.

| En un vistazo | Detalle |
|---|---|
| Área | IoT y aplicaciones multiplataforma |
| Tecnologías | Kotlin, Jetpack Compose, Compose Desktop, JavaScript, ESP32 y Supabase |
| Funciones | Lecturas de sensores, historial, control remoto, autenticación y diagnóstico |
| Plataformas del repositorio | Android, Windows, Linux y web |
| Alcance | Proyecto en desarrollo; la operación completa requiere configurar el backend y conectar el hardware |

**Explorar:** [código Android](app/) · [aplicación web](webApp/) · [firmware](firmware/) · [arquitectura de las interfaces](docs/PLATFORM_UI_ARCHITECTURE.md) · [compilaciones](https://github.com/VillenetMK/EcoSphere/actions)

Las comprobaciones automáticas y el estado de cada compilación pueden consultarse en Actions. El comportamiento físico debe validarse con sensores y actuadores conectados.

---

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

## Autenticación nativa

Android, Windows y Linux usan la misma autenticación de Supabase que la web: correo o usuario y contraseña, Google, GitHub, aprobación de perfiles y MFA TOTP para administradores. Las consultas de telemetría y las órdenes remotas usan el JWT de la sesión; la clave publicable nunca concede por sí sola acceso al panel.

Antes de probar Google o GitHub en los instaladores, agrega estas URL en **Supabase → Authentication → URL Configuration → Redirect URLs**:

- `https://villenetmk.github.io/EcoSphere/?ecosphere_client=android` para Android; esa página entrega el código PKCE a `ecosphere://auth-callback` dentro de la aplicación.
- `http://localhost:54321` para Windows y Linux.

El callback local de escritorio sólo escucha durante el inicio OAuth y se cierra después de recibir la respuesta o al cumplirse el tiempo de espera.

## Reglas protegidas

- El flotador horizontal de GPIO32 sólo admite `high` y `low`.
- El riego manual se bloquea sin lecturas válidas, con agua baja o con humedad del suelo igual o superior a 60 %.
- La potencia del ventilador y del LED se limita al rango de 0 a 100 %.
- El estado online exige un heartbeat válido dentro de 30 segundos.

## Verificación

```bash
# Web, seguridad estática y firmware cliente
npm test --prefix webApp
npm run check:copyright --prefix webApp

# Núcleo compartido y APK
./gradlew :sharedCore:test :app:testDebugUnitTest :app:assembleDebug

# Desktop, desde la raíz
gradle -p desktopApp build
```

Los instaladores se generan como artefactos descargables de GitHub Actions. La planificación completa está en [docs/MULTIPLATFORM_MIGRATION.md](docs/MULTIPLATFORM_MIGRATION.md).

## Licencia

Código propietario; consulte [LICENSE](LICENSE). Todos los derechos reservados.
