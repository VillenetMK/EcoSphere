# Restauración de EcoSphere tras Eureka

La experiencia de Web, Android y escritorio vuelve a la base `d247283a324c36de2af1192deac5232cb4377cf5` del 5 de septiembre de 2026. Se conserva el historial Git mediante un cambio nuevo sobre `8c2bfc9b63cdaca1eaf1f8aed82b20a4ca5c416a`.

## Comportamiento recuperado

- Panel con lecturas reales y estado «sin datos» cuando falta telemetría vigente.
- Retirada de las lecturas de exhibición por cuenta y de la sección de voz de Eureka.
- Controles normales de LED, ventilador y bomba; registro simplificado, historial, exportación, diagnóstico y reemplazo de ESP32 conservados.
- El riego manual vuelve a exigir modo manual, suelo válido por debajo del 60 %, agua suficiente, telemetría vigente, duración limitada y pausas por sistema/operador.
- Las sesiones activas, roles aprobados, MFA administrativo y auditoría siguen siendo obligatorios.

## Estado de esta entrega

El propietario autorizó aplicar y publicar la restauración el 12 de septiembre de 2026. El cambio conserva el historial del repositorio y el de la base de datos.

Versiones de esta restauración: web **1.6.11**, Android **1.4.14** (código 19) y escritorio **1.4.8**. La web renueva sus entradas y su caché. Los instaladores se compilan y publican en GitHub Actions. Las descargas quedan fijadas a la publicación anterior hasta verificar los nuevos archivos; después se actualizan los enlaces.

## Migración de restauración

`supabase/migrations/20260912180644_restore_normal_operation_after_eureka.sql`:

1. Revoca los permisos temporales de riego y voz.
2. Cancela órdenes pendientes que tuvieran excepciones de feria sin emitir un nuevo pulso.
3. Restablece el contrato normal del control, conservando columnas adicionales para los clientes y firmware instalados.
4. Desactiva la puerta de acceso a voz que se había ampliado a todos los usuarios; conserva los registros y revoca el acceso a sus RPC y a la clave del proveedor.

No elimina usuarios, sesiones, lecturas históricas, auditorías ni controladores. Las migraciones anteriores se conservan. Se incorpora al repositorio la migración de diagnóstico `20260911024552` ya aplicada en el servidor y se conserva su validación del gateway para evitar incompatibilidades con el firmware actual.

La función de acceso por usuario desplegada coincide con el código normal. La función temporal `hever-login-setup` ya estaba cerrada. La migración `20260912180644` se aplicó correctamente y `hever-ai` se desplegó como endpoint cerrado, con HTTP 410 y sin acceso al proveedor.

## Comprobación y publicación

Pruebas locales:

```sh
npm ci --prefix webApp
npm test --prefix webApp
npm run build --prefix webApp
npm run check:copyright --prefix webApp
node --experimental-strip-types --test supabase/functions/controller-gateway/validation.test.mjs
```

Las pruebas SQL ejecutan la migración y las funciones reales de control en PostgreSQL aislado mediante PGlite. Comprueban la conservación de datos, la revocación de permisos de feria, los rechazos de riego, la independencia de las salidas y las sesiones/MFA. No envían órdenes al equipo ni escriben en producción.

Resultado de esta preparación: **107 pruebas de web/contratos/SQL aprobadas**, **11 pruebas del gateway aprobadas**, compilación web correcta y verificación de copyright correcta. La compilación nativa quedó bloqueada al descargar Gradle (`Network is unreachable`), antes de compilar el código.

Publicación autorizada: aplicar únicamente la migración de restauración, cerrar `hever-ai` con una respuesta HTTP 410 sin llamadas al proveedor, publicar la web y generar los instaladores. La comprobación del servidor es de solo lectura: no emite órdenes físicas al equipo ni requiere cambiar credenciales, usuarios o identidades ESP32.

El firmware temporal Eureka que se entregó por separado continúa omitiendo BME280/BH1750. Restaurar la aplicación no puede reactivar esos sensores en una placa sin cargar un firmware que los lea. La migración sí retira las autorizaciones de riego excepcionales que ese firmware recibía del servidor.
