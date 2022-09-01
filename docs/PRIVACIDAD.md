# Privacidad de Capri Android 1.0.0

La release pública usa **capturas manuales** y no declara un servicio de Accesibilidad. La observación automática de algunas ediciones de prueba anteriores no forma parte de este APK.

## Conversación y proveedores

Al enviar un mensaje, el texto y el historial necesario se envían al proveedor de IA que hayas configurado. Puede incluir contexto de una captura que autorizaste y resúmenes locales opcionales. El proveedor aplica sus propias condiciones y políticas; Capri no incorpora una cuenta ni claves compartidas.

El historial de conversación se guarda localmente. Las claves y sesiones se cifran con Android Keystore en el almacenamiento privado. Las copias de seguridad de la app están deshabilitadas. Cerrar sesión retira los tokens locales e intenta revocar la sesión remota; si no se confirma esa revocación, la app lo informa. Nunca publiques claves o tokens en issues.

## Captura manual

Cada captura empieza por una acción del usuario, un aviso que identifica el proveedor y una autorización de Android. Obtiene una sola imagen, libera MediaProjection/VirtualDisplay/ImageReader y después envía un JPEG de hasta 960 píxeles al proveedor elegido. No se guarda un archivo de captura ni un video y no se conserva un token para capturas futuras.

En Android 14+ el diálogo del sistema permite elegir una app o toda la pantalla; en versiones anteriores se captura la pantalla completa. La imagen puede contener información personal visible. No se promete eliminar datos sensibles antes de enviarla. Se respetan las superficies protegidas; Capri no evita FLAG_SECURE ni DRM. Cancelar el aviso o la autorización no envía una imagen.

El contexto visual caduca a los dos minutos. Olvidar captura cancela el análisis pendiente y descarta el contexto local, pero no retira datos ya recibidos por el proveedor. Los resúmenes persistentes son opcionales, están apagados por defecto, caducan a los siete días y pueden borrarse en Entorno. Olvidar captura no borra por sí solo resúmenes ya guardados.

## Micrófono y chat de voz flotante

El botón Hablar de la app reconoce una intervención mientras su actividad está visible. El botón central de la mascota inicia una sesión de voz por turnos en un servicio de primer plano de tipo micrófono, con notificación Detener voz y permiso explícito de Android. Una actividad transparente gestiona el permiso y el inicio del servicio; no abre la conversación escrita.

Se reconoce una frase, se envía el texto al modelo y se pausa la escucha mientras se genera y reproduce la respuesta. Después puede comenzar otro turno. La espera por frase vence a los veinte segundos y cada sesión dura como máximo cinco minutos. El usuario puede cerrarla desde la mascota o la notificación. Ocultar a Capri, bloquear el dispositivo, abrir la app principal o terminar el proceso la detiene. No se reinicia al encender el teléfono y no hay palabra de activación ni escucha permanente.

El reconocimiento utiliza el servicio de voz instalado en Android. Ese servicio puede transmitir audio a sus servidores y necesitar internet. Capri no guarda grabaciones de micrófono: almacena el texto reconocido en el historial cuando se envía como mensaje. La ruta de conversación usa texto; la captura usa imágenes. No se presenta el reconocimiento como completamente local.

## Síntesis de voz

La voz Qwen se genera localmente, con los modelos y el perfil incluidos en el APK completo. La voz Android depende del motor del sistema y de sus ajustes, que pueden permitir voces en red. No se envían audios de referencia a un servicio de clonación desde esta release.

Consulta [el uso de capturas](CAPTURAS_MANUALES.md), [las instrucciones de conexión](CONECTAR_CHATGPT.md) y [la validación pública](VALIDACION_PUBLICA_1.0.0.md). Los informes de versiones anteriores son históricos y no describen necesariamente los permisos de esta release.
