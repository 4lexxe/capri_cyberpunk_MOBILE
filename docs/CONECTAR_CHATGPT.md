# Capri Android 1.0.0 · Conectar ChatGPT

## Instalar y conectar

1. Instalar el APK completo 1.0.0 desde Releases sobre la versión anterior. Conserva el paquete y usa el historial original de firmas.
2. Abrir **Mainframe**, bajar a **Cerebro** y elegir **ChatGPT · cuenta**.
3. Tocar **Continue with ChatGPT**. Capri detiene la observación y los pedidos pendientes antes de abrir el navegador del teléfono.
4. Revisar que el inicio de sesión sea en `auth.openai.com`, iniciar sesión y autorizar a **Capri Android** y el uso del plan. La autorización puede depender de la cuenta y disponibilidad del servicio.
5. Al terminar, volver a Capri. La app valida la identidad y carga los modelos habilitados para esa cuenta. **Elegir modelo ChatGPT** presenta los nombres devueltos por OpenAI; no hay que adivinar un ID.
6. Tocar **Probar conexión**. Sólo una respuesta completada confirma inferencia; ver “Cuenta conectada” no basta. La prueba consume uso del plan.
7. Tocar **Guardar ajustes** al final de Mainframe y abrir **Conversar**. Se puede escribir o usar **Hablar**.

Una cuenta habilitada para este flujo puede autorizar el uso del plan sin introducir una clave API. Se aplican sus permisos y límites; no se promete uso ilimitado ni disponibilidad para todas las cuentas. Capri no cambia automáticamente a una clave API si falla la cuenta.

## Voz y visión

La voz Qwen es local y el APK completo ya incluye sus modelos y perfil. El reconocimiento de voz corre a cargo del servicio de Android y puede necesitar internet. El chat flotante usa un servicio de micrófono explícito, con notificación y escucha por turnos. No se envía audio a la ruta de ChatGPT del plan.

Para analizar capturas: **Entorno → Habilitar análisis visual → Usar la conexión del cerebro**. Elegir un modelo que admita imágenes, guardar y tocar **Tomar captura**. Cada imagen requiere autorización de Android; no hay capturas automáticas. El catálogo no garantiza que todos sus modelos acepten imágenes. Cada análisis consume uso del plan y guardar recuerdos es opcional.

## Sesiones, cuentas y límites

- **Agregar cuenta** registra otra cuenta o espacio sin sobrescribir el registro anterior. **Elegir cuenta** muestra etiquetas distintas incluso si comparten correo. Cambiar de cuenta termina los pedidos y la sesión de observación.
- Los tokens se cifran con Android Keystore, se guardan en el almacenamiento privado y no se incluyen en copias de seguridad. Las respuestas y recuerdos conservan su almacenamiento local existente.
- La renovación de tokens se serializa y guarda junto con el nuevo token renovable. Un permiso retirado o una renovación revocada exige reconectar.
- **Cerrar sesión** detiene el uso, intenta revocar la sesión remota y elimina los tokens locales. Si no se confirmó la revocación remota, Capri lo informa; se puede desconectar la app en ChatGPT.
- **Ver uso y permisos en ChatGPT** abre `https://chatgpt.com/settings/usage`. Un límite del plan puede llegar al final de una respuesta en streaming. Capri descarta una respuesta incompleta para que Qwen no la lea como si estuviera terminada.
- La autorización abre un listener temporal únicamente en `127.0.0.1` y vence a los cinco minutos. Al cancelarla se cierra. Si Android termina el proceso mientras el navegador está abierto, volver a Capri y comenzar de nuevo.

No se importan cookies, tokens de Display CW ni historial de ChatGPT. La sesión de Capri se registra por separado. ChatGPT web no se automatiza ni se incluyen endpoints privados del sitio.

## Si aparece un error de formato

Desde 0.3.1, Capri inspecciona el contenido además del encabezado HTTP. Acepta streaming SSE aunque el encabezado sea genérico o falte, y un objeto Responses JSON con estado explícito `completed` y salida de texto. La solicitud sigue usando `stream=true` y `store=false`; no se repite para cambiar de formato.

Si el servidor devuelve una página web, cuerpo vacío o JSON que no corresponde a una respuesta completada, aparece un código `CG-RSP-HTML`, `CG-RSP-EMPTY` o `CG-RSP-JSON-STATE`. Los errores de permisos y límites se interpretan por su código. Se puede copiar ese mensaje para diagnosticarlo, sin enviar claves ni tokens. Una página HTML no se procesa como texto de Capri ni se intenta saltar su protección.

## Fuentes y verificación

Basado en `Display-CW/src/com/alexxe/displaycw/ChatConnection.java`, con adaptación a Kotlin/coroutines, el cliente de IA de Capri y su interfaz Compose. Display CW se conserva sin modificaciones.

Contrato consultado el 29 de octubre de 2026: [registro e identidad](https://developers.openai.com/siwc/token-sharing-open-source/sign-in), [cuentas y renovación](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions), [modelos e inferencia](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference) y [limitaciones de la ruta](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).

Las pruebas de protocolo usan servidores y credenciales ficticios; no se ha iniciado sesión con una cuenta personal durante el desarrollo. El acceso y una respuesta real deben comprobarse en el teléfono con **Probar conexión**. Ver [informe de validación](VALIDACION_0.3.0.md).
