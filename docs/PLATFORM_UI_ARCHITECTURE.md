# Arquitectura de interfaces de EcoSphere

EcoSphere comparte contratos de datos, autenticación, reglas de control y acceso a Supabase. La presentación y la navegación pertenecen a cada plataforma y no deben copiarse entre sí.

## Android

- Módulo: `app/`
- Entrada: `MainActivity.kt`
- Shell móvil: `ui/mobile/MobileEcoSphereApp.kt`
- Autenticación móvil: `ui/mobile/MobileAuthScreen.kt`
- Patrón: navegación inferior, pantallas táctiles, teclado y barras del sistema Android.

## Web / PWA

- Módulo: `webApp/`
- Entrada: `index.html` + `app.js`
- Patrón: portal responsive con navegación lateral y soporte PWA.

## Windows y Linux

- Módulo: `desktopApp/`
- Entrada: `desktopApp/src/main/kotlin/com/example/ecosphere/desktop/Main.kt`
- Patrón: aplicación de escritorio con navegación lateral, ventanas redimensionables e instaladores MSI/DEB.

## Núcleo compartido

- Módulo: `sharedCore/`
- Contiene únicamente contratos, validaciones y reglas de negocio sin componentes visuales.
- Ningún componente de Android, HTML/CSS ni Compose Desktop debe trasladarse a este módulo.

Los cambios visuales deben limitarse al módulo de la plataforma solicitada. Un cambio de reglas o seguridad puede compartirse, pero cada interfaz debe adaptarlo con sus propios componentes.
