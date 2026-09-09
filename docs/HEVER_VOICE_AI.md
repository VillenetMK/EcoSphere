# Asistente de voz de EcoSphere

La pantalla **IA de Hever** integra el orbe, las ilustraciones y la conversación
de Gemini Live del prototipo suministrado. Usa la sesión existente de EcoSphere.
El acceso requiere permiso explícito en `private.ai_permissions`; ser operador
o administrador por sí solo no lo concede.

## Acceso y datos

- `my_ai_access()` comprueba la sesión vigente, la aprobación del perfil y el
  permiso privado. Los administradores también necesitan AAL2.
- `ai_sensor_snapshot()` aplica la misma comprobación y devuelve únicamente
  telemetría del controlador activo. Identifica valores ausentes e históricos;
  no genera datos de demostración ni confunde órdenes con estados reportados.
- La IA tiene una sola herramienta: `consultar_biohuerto`. No recibe herramientas
  de riego, configuración o administración.
- El iframe utiliza un puente con comprobación de origen y ventana. La sesión
  Supabase permanece en la aplicación principal. Salir de la pantalla, ocultar
  la pestaña o cerrar sesión detiene micrófono, reproducción y conexión.

## Voz y credenciales

La Edge Function `hever-ai` valida cada petición usando el JWT del usuario y los
RPC protegidos. Usa autenticación propia, por lo que se despliega con
`verify_jwt=false`; esto no hace público el acceso a voz o datos.

La clave permanente de Google se guarda cifrada en Supabase Vault con el nombre
`ecosphere_gemini_api_key`. `ai_provider_key()` sólo admite `service_role`.
No se incluyen claves, contraseñas ni archivos `.env` en el repositorio.

El navegador recibe un token de un solo uso, válido para iniciar durante un
minuto y conversar durante un máximo de diez minutos. El token restringe el
modelo, la voz, las instrucciones y la herramienta. La reserva atómica permite
un inicio cada treinta segundos y veinte inicios por hora por cuenta.
El audio va directamente a Gemini; no se almacena audio ni conversación en
las tablas de EcoSphere. Un token ya emitido conserva su vigencia limitada;
revocar acceso bloquea inmediatamente nuevas consultas y nuevas emisiones.

El cuerpo REST del token usa `bidiGenerateContentSetup`, y el WebSocket usa
`BidiGenerateContentConstrained` con `access_token`. Estos nombres corresponden
al protocolo REST, no a `liveConnectConstraints` de los SDK.

## Validación

```sh
npm ci --prefix webApp
npm test --prefix webApp
node --experimental-strip-types --test supabase/functions/hever-ai/handler.test.mjs
npm run build --prefix webApp
```

Las pruebas SQL de `supabase/tests/hever_voice_ai_permission.sql` son transaccionales y
terminan con rollback. Los permisos de la cuenta elegida y la clave de Vault
se aprovisionan aparte de la migración y no se publican como datos de ejemplo.

Referencias: [Gemini Live](https://ai.google.dev/gemini-api/docs/live-api),
[tokens efímeros](https://ai.google.dev/gemini-api/docs/live-api/ephemeral-tokens),
[protocolo WebSocket](https://ai.google.dev/api/live),
[Supabase Vault](https://supabase.com/docs/guides/database/vault).
