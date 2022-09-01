# Revisión de Google Play Protect

## Estado confirmado el 29 de octubre de 2026

El usuario mostró en un Samsung Galaxy A55 con Android 16 el aviso de Google Play Protect: «Se bloqueó la app para proteger tu dispositivo», acompañado de la advertencia sobre datos sensibles, identidad y fraude financiero. Había identificado el archivo de actualización; el nombre visible en la captura empieza por `Capri-Android-1.0.0-actuali`. No se leyó el hash del archivo en el teléfono.

Esto confirma un bloqueo de Play Protect en ese intento. La corrección del historial de firmas resuelve otro problema reproducido localmente, pero no demuestra que este bloqueo esté resuelto. No hay aprobación de Google ni instalación validada en ese teléfono.

La [guía oficial de Google](https://developers.google.com/android/play-protect/warning-dev-guidance#app-blocked-to-protect-your-device) describe el mismo aviso para APK de fuentes de Internet que declaran permisos sensibles, incluida Accesibilidad. El manifest compilado de Capri contiene `BIND_ACCESSIBILITY_SERVICE` en `ScreenshotService`. Es la explicación más probable del bloqueo; no se dispone de un diagnóstico individual de Google. No se declaran permisos de SMS ni un servicio de escucha de notificaciones.

El usuario eligió conservar las capturas automáticas y preparar una revisión. Se mantiene el comportamiento actual. No se cambian el identificador, la firma ni los permisos para intentar ocultar la funcionalidad, y no se indica desactivar Play Protect.

## Archivos que se revisan

| APK | Uso | SHA-256 del archivo |
| --- | --- | --- |
| `Capri-Android-1.0.0-actualizacion.apk` | Actualizador original identificado por el usuario; firma anterior | `7424f8ad017895918cbaeece097913adc5c3a916ecf88acdcf7289a0ed12a985` |
| `Capri-Android-1.0.0-instalar.apk` | Actualizador con historial de firmas corregido; su instalación en el A55 no está validada | `269b0bb4f86dd178a4277c1ea21496a9ffb48dcc23dd85a4a65843f47a9f891e` |
| `Capri-Android-1.0.0-completa-migracion.apk` | Edición completa con modelos; APK diferente que no debe confundirse con los anteriores | `90f60dbaf2349adbab03715bfa845016ba5a3579ee072429ded51bd5abc06734` |

Todos corresponden a `com.capri.companion`, versión 1.0.0, código 8, minSdk 26 y targetSdk 36. El SHA-256 solicitado por el formulario es el del **APK**, no el del certificado. El actualizador original no incorpora los modelos Qwen y su voz necesita modelos ya instalados o elegir Android.

## Información para la revisión

Capri es una mascota animada con conversación y voz. Las capturas son una función opcional de contexto visual, no una función asistiva para personas con discapacidad. Se declara `isAccessibilityTool=false`. El servicio solicita capacidad de captura, sin lectura del árbol de ventanas ni gestos, y no procesa eventos de accesibilidad.

Para iniciar: configurar un proveedor y modelo visual en Entorno, guardar, pulsar Activar capturas puntuales, aceptar el aviso, habilitar el servicio en Ajustes, volver a Entorno y pulsar de nuevo. Conceder Accesibilidad por sí solo no inicia una sesión. La primera imagen se solicita al salir de Capri y las siguientes a intervalos de tres minutos; existe una petición manual con límite de frecuencia.

Se captura la pantalla visible completa, incluidas otras apps y notificaciones, salvo superficies protegidas. No existe selección de una sola app. Las imágenes pueden contener datos personales: no se promete excluirlos antes del envío. Se reduce la imagen a un máximo de 960 píxeles y se envía como JPEG al proveedor elegido. No se guarda un archivo de captura ni video. Los resúmenes de actividad son opcionales, están apagados por defecto, caducan a los siete días y pueden borrarse. Véase [privacidad](PRIVACIDAD.md).

Se puede detener desde Entorno o la notificación. Bloquear el teléfono, revocar el servicio o cerrar la mascota también termina la sesión. Los resultados de análisis pendientes se invalidan. Pausar comentarios sólo silencia las reacciones; no detiene los envíos. El micrófono se activa por una acción del usuario en la actividad visible; no se usa para escuchar otras apps.

La validación local de captura se hizo en un emulador API 35 contra un servidor de prueba local. No equivale a una revisión antimalware, una certificación de Google ni una prueba de instalación con Play Protect en el A55.

## Presentación

El [formulario oficial de apelación de Play Protect](https://support.google.com/googleplay/android-developer/contact/protectappeals) pide correo, paquete, hash SHA-256 del APK subido a VirusTotal e información adicional. El nombre del desarrollador también aparece como campo. A la fecha consultada, el formulario advierte que las decisiones son finales y que no se recibirá una respuesta; no se promete un plazo de resolución.

El paquete local `artifacts/release-1.0.0/play-protect-review/` incluye los textos para pegar, los APK pequeños exactos y las verificaciones. No se ha enviado el formulario, subido un APK a VirusTotal ni publicado un enlace. Subir a VirusTotal implica compartir ese binario con un tercero; el usuario debe elegir el archivo exacto que desea presentar y completar sus datos de contacto. Cada texto indica su hash: no intercambiar hashes, certificados ni archivos entre solicitudes. Una revisión del actualizador original no garantiza aprobación de los otros APK.

Si se desea distribuir otra variante, verificar ese APK por separado. Mantener estables las firmas y el historial de actualizaciones. Si Google rechaza la clasificación, hay que resolver la causa o reconsiderar el diseño de captura; no se garantiza que mantener esta implementación sea aceptado.
