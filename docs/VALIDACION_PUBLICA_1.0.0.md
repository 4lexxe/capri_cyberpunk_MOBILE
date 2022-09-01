# Validación de la distribución pública 1.0.0

Validación realizada el 29 de octubre de 2026. El nombre público es **1.0.0**, con código Android **10**, para actualizar las compilaciones de prueba anteriores sin desinstalar. Paquete `com.capri.companion`, minSdk 26, targetSdk 36, ARM64 y x86_64.

## Artefactos

| Archivo | Bytes | SHA-256 |
| --- | --- | --- |
| Capri-Android-1.0.0-completa.apk | 910106951 | 5723a074e63a56dd14834e8fc01ccce560f77df708dd51286a12f2f17db195f7 |
| Capri-Android-1.0.0-actualizacion.apk | 26226821 | 0fc00f9ef6f7dc8627516dcb7df535dad12eb24adf7c8424d96c2d3060b5e2d2 |

La edición completa incluye ambos GGUF de models.lock.json: tamaño y SHA-256 verificados directamente dentro del APK, sin compresión. Incluye las referencias y el perfil latino. La actualización pequeña reutiliza modelos existentes o permite voz Android.

Ambos APK pasan apksigner con v2 y v3 e historial de rotación original. Zipalign con páginas de 16 KB pasó en ambos; los segmentos ELF también conservan alineación mínima de 16 KB. El DEX y las bibliotecas nativas coinciden por hash entre la edición completa y el actualizador.

Se verificó el manifest compilado: versión 1.0.0/código 10, servicio de captura manual mediaProjection y servicio de chat de voz microphone, con sus permisos. No declara BIND_ACCESSIBILITY_SERVICE ni el servicio automático ScreenshotService.

## Pruebas de lógica y dispositivo

- Debug y Release: 58 pruebas unitarias aprobadas.
- Lint Debug y Release: cero errores, 27 advertencias y dos sugerencias no bloqueantes.
- Emulador API 35: **14 pruebas de dispositivo aprobadas** en 97,792 segundos, con la misma configuración manual de la release.
- El caso de voz tocó el botón central real de la mascota, inició el servicio de micrófono desde su actividad transparente, recibió texto a través de un servicio Android de reconocimiento de prueba, lo envió a un modelo HTTP local y recibió respuesta. Volvió a escuchar el siguiente turno, verificó que MainActivity permanecía en segundo plano y detuvo la sesión tocando Cerrar chat de voz. No se grabó audio de micrófono ni se usaron claves reales en ese caso.
- La prueba de voz midió la anchura del avatar a 100 dp. El valor inicial del modelo de configuración también se verifica en las pruebas unitarias; los tamaños guardados de instalaciones anteriores se conservan.
- Las pruebas de captura verificaron un JPEG real no uniforme de hasta 960 píxeles, cierre de la proyección, autorización nueva para la siguiente petición y cero envíos al cancelar. El servidor de prueba fue exclusivamente local.
- Se verificaron controles flotantes, globos con ×, disposición, arrastre, ajustes, interfaz de configuración y migración de personalidad.

El servicio de reconocimiento de pruebas usa Java para poder ejecutarse en el proceso separado del APK de pruebas sin depender del runtime Kotlin del APK principal. El APK de pruebas no se distribuye. La voz de Android marca ahora su preparación hasta el inicio real de la reproducción para evitar que el chat reabra la escucha mientras TTS sigue preparándose.

## Prueba de producción e imágenes

El APK completo firmado de producción se instaló con `adb install -r`: `Success`. Su inicio normal devolvió `Status: ok`. El dispositivo emulado mostró `versionCode=10` y `versionName=1.0.0`, y la configuración de voz llegó al estado `Qwen local · Capri latino · ambos audios` desde los archivos incluidos.

Las imágenes de `screenshots-v1.0-public/` son capturas de la app y del inicio del emulador. Mainframe, voz, entorno y mascota se capturaron con el APK completo de producción; la imagen de conexión muestra la misma interfaz Debug, sin cuenta ni credenciales reales. La mascota se configuró a 100 dp para esa imagen porque el emulador conservaba un tamaño anterior guardado.

## Límites

No se midió audio de micrófono real ni rendimiento Qwen en el Samsung A55. La prueba de reconocimiento valida la integración y el ciclo de vida, no la calidad de reconocimiento de un proveedor de voz concreto. No se promete generación instantánea.

No hay prueba de instalación con Play Protect en el A55 Android 16 ni aprobación de Google. La release manual elimina Accesibilidad, permiso relacionado con el bloqueo reportado en una prueba anterior. Los usuarios deben mantener Play Protect activo y reportar cualquier bloqueo de esta release.

Las claves privadas de firma, credenciales, adjuntos, modelos externos al APK e intermedios se excluyen del repositorio y del ZIP de fuentes. Los informes de versiones anteriores corresponden a builds de prueba y no sustituyen este informe.
