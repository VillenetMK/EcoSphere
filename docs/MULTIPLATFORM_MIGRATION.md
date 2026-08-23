# EcoSphere — migración multiplataforma

## Plataformas objetivo

- Android teléfonos y tablets
- ChromeOS / Chromebook
- Windows
- Linux
- Web / PWA

Apple queda fuera del alcance del proyecto.

## Principio de interfaz

La interfaz no debe depender del nombre del sistema operativo para decidir su distribución. Debe responder al espacio disponible:

- Compacto: teléfonos y ventanas pequeñas. Drawer lateral por gesto.
- Medio: tablets, Chromebook pequeño y equipos 2-en-1. Navegación persistente y contenido acotado.
- Expandido: laptops, escritorio y monitores grandes. Sidebar permanente y área de trabajo amplia.

La aplicación debe seguir siendo utilizable con touch, mouse, trackpad y teclado.

## Estrategia de migración

### Fase 1 — Adaptación de UI sin romper Android

Estado: completada.

- Mantener el módulo Android actual operativo.
- Introducir navegación adaptativa.
- Evitar que el dashboard se estire de forma indefinida en pantallas grandes.
- Preparar ChromeOS, tablets y ventanas redimensionables.

### Fase 2 — Núcleo compartido Kotlin/JVM

Estado: completada como paso compatible previo a Kotlin Multiplatform.

- El módulo `sharedCore` contiene los modelos, el contrato de Supabase y las reglas críticas de control.
- Android consume `sharedCore` como dependencia Gradle.
- Desktop compila exactamente las mismas fuentes de `sharedCore` desde su build independiente.
- Las pruebas del núcleo cubren seguridad de riego, potencia y heartbeat.
- Mantener Android como aplicación de entrada separada para compatibilidad con AGP 9+.

### Fase 3 — Red compartida

Estado: en curso.

- Sustituir Retrofit/Gson del núcleo común por Ktor Client + kotlinx.serialization.
- Mantener el mismo backend Supabase.
- Compartir lectura de telemetría, historial y órdenes de control.

### Fase 4 — Compose Multiplatform UI

Estado: primera versión operativa para Windows y Linux.

- Mover componentes reutilizables a commonMain.
- Mantener adaptaciones específicas solo donde sean necesarias.
- Añadir Desktop JVM para Windows y Linux.

### Fase 5 — Web / PWA

Estado: primera versión instalable operativa.

- La PWA responsive ofrece panel, historial, diagnóstico y control remoto.
- La política de control del navegador tiene pruebas equivalentes a `sharedCore`.
- El manifest incluye iconos PNG instalables de 192 y 512 px.
- GitHub Pages se configura y despliega mediante el flujo oficial de Actions.

## Matriz actual

| Plataforma | Entrega | Núcleo de reglas | Automatización |
|---|---|---|---|
| Android / ChromeOS | APK | `sharedCore` | GitHub Actions |
| Windows | MSI | `sharedCore` | GitHub Actions |
| Linux | DEB | `sharedCore` | GitHub Actions |
| Web / Chromebook | PWA | `control-policy.js`, con pruebas de paridad | GitHub Pages |

## Contrato de seguridad

- El flotador horizontal de GPIO32 sólo admite `high` y `low`.
- Una lectura ausente o desconocida bloquea el riego manual.
- El riego manual se bloquea con nivel `low` o humedad de suelo igual o superior a 60 %.
- Potencia de ventilador y LED siempre queda entre 0 y 100 %.
- El equipo se considera online sólo con heartbeat válido dentro de 30 segundos.

## Reglas de compatibilidad

- No eliminar el Android actual hasta que la nueva capa compartida compile y ejecute correctamente.
- Cada fase debe dejar `main` ejecutable.
- No duplicar lógica de Supabase entre plataformas una vez creada la capa común.
- Diagnóstico, historial y control remoto deben utilizar la misma fuente de datos en todas las plataformas.
