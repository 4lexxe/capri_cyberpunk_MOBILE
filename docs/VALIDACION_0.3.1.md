# Validación de Capri Android 0.3.1

29 de octubre de 2026. Código 4, paquete `com.capri.companion`.

## Problema informado

Después de iniciar sesión, el usuario recibió “ChatGPT no devolvió una respuesta en streaming”. Ese aviso se originaba en el cliente: ante una respuesta HTTP exitosa cuyo `Content-Type` no coincidía exactamente con `text/event-stream`, rechazaba la respuesta sin examinar el contenido. El aviso no demostraba falta de saldo ni una respuesta vacía del modelo.

No había un teléfono conectado por ADB ni acceso a la respuesta HTTP de la cuenta del usuario. Por eso no se afirma cuál fue el contenido exacto recibido en su teléfono. Se reprodujo el mismo aviso con un servidor local que devuelve SSE válido sin encabezado o con encabezado genérico, y con una respuesta Responses JSON completa. Las dos pruebas fallaron con el código de 0.3.0 antes de corregirlo.

## Corrección

El cliente inspecciona un prefijo acotado del contenido sin consumirlo. Si detecta eventos SSE, procesa el stream aunque el encabezado falte, sea genérico o tenga distinta capitalización. Si recibe JSON, acepta únicamente un objeto Responses con `object=response`, `status=completed`, salida y ausencia de error o resultado incompleto. Nunca convierte una respuesta parcial o una página HTML en texto para Capri.

Los errores JSON se interpretan antes de intentar extraer texto. Una respuesta HTML, cuerpo vacío o formato inesperado recibe un diagnóstico distinto (`CG-RSP-HTML`, `CG-RSP-EMPTY`, `CG-RSP-JSON`, `CG-RSP-JSON-STATE`, `CG-RSP-FORMAT`). Un `x-request-id` con formato seguro puede incluirse en el mensaje. No se muestran cuerpos de error, cookies ni tokens.

La solicitud sigue usando el endpoint público, OAuth, `stream=true` y `store=false`. No hay solicitudes adicionales para negociar formatos ni cambio a la facturación con clave API. No se cambió el almacenamiento de sesiones, el catálogo ni la voz.

## Verificaciones

- **48 pruebas unitarias aprobadas**, incluidas seis pruebas nuevas sobre encabezados, JSON completado, errores de permisos HTTP 200, HTML, cuerpo vacío, JSON malformado y estados incompletos. Las pruebas de red usan MockWebServer y credenciales ficticias.
- APK Debug compilado y firma verificada. Certificado SHA-256 idéntico a 0.3.0: `1bc053afaca9d965f3b54f5578522c94ab7c8bd8eb97fad1c39a3d11fc817895`.
- Lint: **0 errores**, 18 advertencias y 2 sugerencias.
- Comando: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- No se repitieron las pruebas de interfaz ni síntesis nativa de 0.3.0; la corrección está limitada a la lectura de respuestas HTTP. No se probó inferencia con la cuenta real del usuario.

## APK

`artifacts/Capri-Android-0.3.1-debug.apk`, 28.733.688 bytes.

SHA-256: `02fc01c36dc2fa0f855dc42b191aa8233d74161278bc9f019cf3b287542b52b0`.

Instalar sobre la versión anterior, conservar la sesión y tocar **Probar conexión**. Si el servicio devuelve otro contenido, el mensaje nuevo permitirá distinguirlo; esto sigue pendiente de comprobar en el teléfono.
