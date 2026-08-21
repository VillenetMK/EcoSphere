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

Estado: iniciada.

- Mantener el módulo Android actual operativo.
- Introducir navegación adaptativa.
- Evitar que el dashboard se estire de forma indefinida en pantallas grandes.
- Preparar ChromeOS, tablets y ventanas redimensionables.

### Fase 2 — Núcleo compartido Kotlin Multiplatform

- Crear un módulo compartido separado del launcher Android.
- Migrar modelos de dominio y estado.
- Reemplazar dependencias JVM-only en código común.
- Mantener Android como aplicación de entrada separada para compatibilidad con AGP 9+.

### Fase 3 — Red compartida

- Sustituir Retrofit/Gson del núcleo común por Ktor Client + kotlinx.serialization.
- Mantener el mismo backend Supabase.
- Compartir lectura de telemetría, historial y órdenes de control.

### Fase 4 — Compose Multiplatform UI

- Mover componentes reutilizables a commonMain.
- Mantener adaptaciones específicas solo donde sean necesarias.
- Añadir Desktop JVM para Windows y Linux.

### Fase 5 — Web / PWA

- Añadir target Wasm/JS.
- Mantener diseño responsive.
- Preparar instalación PWA para Chromebook y navegadores compatibles.

## Reglas de compatibilidad

- No eliminar el Android actual hasta que la nueva capa compartida compile y ejecute correctamente.
- Cada fase debe dejar `main` ejecutable.
- No duplicar lógica de Supabase entre plataformas una vez creada la capa común.
- Diagnóstico, historial y control remoto deben utilizar la misma fuente de datos en todas las plataformas.
