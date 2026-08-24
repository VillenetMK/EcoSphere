# EcoSphere: acceso y dominio

## Dominio elegido

`ecospherecontrol.com`

El nombre cubre el panel web, Android, Windows, Linux y el sistema IoT. La disponibilidad observada no queda garantizada hasta completar la compra. Un dominio personalizado mejora identidad y confianza, pero no vuelve privado el repositorio ni reemplaza la autenticación.

## Flujo de cuenta

- Usuario, correo y contraseña: nombre de usuario, nombres y apellidos, DNI, teléfono, correo, contraseña de al menos 12 caracteres y confirmación.
- Google o GitHub: primero nombre de usuario, nombres y apellidos, DNI, teléfono y correo; después el proveedor verifica el correo. No se solicita una contraseña adicional.
- Toda cuenta nueva queda `pending` y `viewer` hasta su aprobación.
- `viewer`: lectura; `operator`: lectura y controles; `admin`: lectura, controles y administración futura de accesos.

Los datos personales se almacenan en `private.user_profiles`; no se incluyen en metadata editable del usuario ni en el JWT.

El inicio tradicional acepta el nombre de usuario o el correo. La resolución del usuario ocurre en una Edge Function con origen restringido, mensajes genéricos y bloqueo temporal después de cinco fallos. El correo asociado no se expone al navegador antes de validar la contraseña.

Toda cuenta `admin` exige un segundo factor TOTP. En el primer acceso administrativo se muestra un QR compatible con Google Authenticator; en los siguientes accesos se solicita el código de seis dígitos. Las políticas RLS bloquean la telemetría y los controles administrativos mientras la sesión no alcance `aal2`.

## Configuración pendiente

1. Comprar `ecospherecontrol.com` con el registrador elegido.
2. En DNS, apuntar el dominio a GitHub Pages siguiendo la documentación oficial. Para `www`, usar un CNAME hacia `villenetmk.github.io`.
3. En GitHub Pages, establecer `ecospherecontrol.com` como dominio personalizado y mantener HTTPS obligatorio.
4. En Supabase Auth, establecer como Site URL `https://ecospherecontrol.com` y conservar temporalmente `https://villenetmk.github.io/EcoSphere/` entre las redirecciones permitidas durante la transición.
5. Crear las aplicaciones OAuth de Google y GitHub. En ambos proveedores, la URI de retorno es:

   `https://kslzmrddrhfyyrxyfmbw.supabase.co/auth/v1/callback`

6. Introducir los Client ID y Client Secret directamente en Supabase. Nunca guardarlos en Git, JavaScript, capturas o conversaciones.
7. Registrar la primera cuenta y promoverla manualmente a `approved` + `admin` después de verificar su correo.
8. Escanear el QR de Google Authenticator y verificar el primer código TOTP.
9. Activar CAPTCHA y revisar límites de solicitudes antes de abrir el registro al público.

`VillenetADMIN` está reservado exclusivamente para `gabrielvilenet@gmail.com`. La contraseña inicial debe escribirse únicamente en el formulario HTTPS; cualquier contraseña enviada por chat debe considerarse comprometida y no utilizarse.

Referencias oficiales: [dominio personalizado en GitHub Pages](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/managing-a-custom-domain-for-your-github-pages-site), [OAuth con Supabase](https://supabase.com/docs/guides/auth/social-login), [CAPTCHA en Supabase Auth](https://supabase.com/docs/guides/auth/auth-captcha).

## Transición del ESP32

Las políticas anónimas actuales se mantienen únicamente para no desconectar el ESP32 y las aplicaciones instaladas. Para cerrarlas sin detener el sistema hace falta incorporar el firmware actual, asignar una identidad separada al dispositivo y migrar la inserción de telemetría. Las credenciales del dispositivo no deben reutilizar cuentas humanas.
