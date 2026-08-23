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

## Estado real al 23 de agosto de 2026

### Disponible

- Android/ChromeOS: aplicación Compose y APK.
- Windows: aplicación Compose Desktop y empaquetado MSI.
- Linux: misma aplicación Desktop y empaquetado DEB.
- Web/Chromebook: PWA estática y despliegue mediante GitHub Pages.

### Deuda encontrada

Las aplicaciones Desktop y Web se añadieron como implementaciones independientes. Cada una repetía los umbrales, intervalos, criterio de conexión y lógica de riego. El módulo Kotlin Multiplatform descrito en el plan original todavía no existe y `settings.gradle.kts` continúa incluyendo solo `:app`.

## Incremento 1.1 — contrato común Windows/Linux/Web

Estado: implementado en esta rama; pendiente de que CI confirme los instaladores.

Se añadió `webApp/config/control-policy.json` como contrato único para:

- refresco: 2 000 ms;
- ESP32 offline: más de 30 000 ms sin heartbeat;
- tolerancia de reloj futuro: 60 000 ms;
- riego manual denegado con suelo ≥60 %;
- solicitud de bomba: 3 000 ms;
- nivel de agua bloqueado: `low`;
- umbral automático histórico: ≤35 %, identificado como responsabilidad del firmware.

### Desktop (Windows/Linux)

- `ControlPolicy.kt` carga y valida el contrato desde los recursos empaquetados.
- El estado online, riego manual, intervalo de sondeo y duración de bomba usan el contrato.
- Pruebas JVM cubren límites y valores canónicos.
- Los workflows ejecutan pruebas antes de producir MSI o DEB.
- Versión de paquete: 1.1.0.

### Web/PWA

- `control-policy.js` carga y valida el mismo JSON antes de habilitar controles.
- El estado online y la decisión de riego ya no tienen constantes propias.
- Pruebas Node cubren el mismo contrato y límites.
- El workflow valida sintaxis y pruebas antes de desplegar.
- El service worker incluye módulo y contrato en la caché.
- Versión de paquete: 1.1.0.

Este incremento reduce la divergencia sin retirar ninguna aplicación existente. Todavía no comparte modelos ni red.

## Estrategia de migración restante

### Fase 1 — Adaptación de UI sin romper Android

Estado: disponible.

- Mantener el módulo Android operativo.
- Usar navegación adaptativa.
- Evitar contenido indefinidamente ancho.
- Soportar ChromeOS, tablets y ventanas redimensionables.

### Fase 2 — Políticas y pruebas de contrato

Estado: en curso.

- [x] Fuente común para Windows/Linux y Web.
- [x] Pruebas Desktop.
- [x] Pruebas Web.
- [ ] Hacer que Android consuma el contrato o un módulo generado equivalente.
- [ ] Añadir casos de conformidad comunes para los tres clientes.
- [ ] Versionar esquema y políticas RLS de Supabase.

### Fase 3 — Núcleo Kotlin Multiplatform

Estado: pendiente.

- Crear un módulo compartido separado del launcher Android.
- Migrar modelos de dominio, normalización de valores y estado.
- Añadir tests en `commonTest`.
- Mantener Android como entrada separada para compatibilidad con AGP 9+.
- Integrar Desktop primero; integrar Android cuando compile en CI.

No se presenta el JSON como sustituto del futuro núcleo KMP: es el paso de estabilización que permite extraerlo sin cambiar simultáneamente todos los clientes.

### Fase 4 — Red compartida

Estado: pendiente.

- Sustituir Retrofit/Gson del núcleo común por Ktor Client + kotlinx.serialization.
- Mantener Supabase como backend.
- Compartir telemetría, historial, control y tratamiento de errores.
- Separar URL/clave publicable por entorno.
- Añadir pruebas contra respuestas REST simuladas.

### Fase 5 — UI compartida y Web Kotlin

Estado: pendiente de evaluación.

- Mover componentes reutilizables a `commonMain`.
- Mantener adaptaciones específicas solo donde sean necesarias.
- Evaluar Compose Multiplatform para Web/Wasm después de estabilizar dominio y red.
- Conservar la PWA estática hasta que la alternativa Kotlin iguale accesibilidad, instalación y funcionamiento offline.

## Reglas de compatibilidad

- No eliminar Android, Desktop ni PWA mientras su reemplazo no compile y ejecute.
- Cada fase debe dejar la rama construible.
- Una regla de control confirmada no puede copiarse como constante independiente: debe provenir del contrato o del núcleo compartido.
- Diagnóstico, historial y control remoto deben conservar la misma semántica.
- Los umbrales de riego no se cambian sin actualizar documentación, contrato y pruebas.
- El firmware no se considera reproducible hasta que su fuente exacta esté en el repositorio.
- No guardar credenciales Wi-Fi en Git.
- Una clave Supabase publicable exige RLS mínimo y auditable.

## Comandos de validación

### Web

```bash
npm --prefix webApp run check
npm --prefix webApp test
```

### Desktop

```bash
gradle -p desktopApp test
gradle -p desktopApp packageMsi   # Windows
gradle -p desktopApp packageDeb   # Linux
```

Los paquetes nativos se generan en su sistema operativo correspondiente mediante GitHub Actions.
