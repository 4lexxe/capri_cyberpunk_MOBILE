# Validación 1.0.1-manual — 29/10/2026

## Resultado

Se generaron y firmaron la actualización y la edición completa, con `com.capri.companion`, versión `1.0.1-manual`, código 9, minSdk 26 y targetSdk 36. La edición automática 1.0.0 y su material de revisión quedan archivados por separado. No se ha publicado una release ni enviado estos APK a Google o VirusTotal.

| Archivo | Bytes | SHA-256 |
| --- | --- | --- |
| Capri-Android-1.0.1-manual-actualizacion.apk | 26210437 | 161987399d65cae5b9ed6f535e017205220fab14984b3dad125c2ddb47f9a5bc |
| Capri-Android-1.0.1-manual-completa.apk | 910090567 | a2a7e03e4c5c74339aac17f2c22fa3af4fed896fc0b633e5bb9e320c00a44143 |

Ambos pasan `apksigner verify` con v2 y v3 e incorporan el historial público original de firmas. `zipalign -c -P 16 -v 4` pasó en ambos. Los segmentos ELF de las bibliotecas del actualizador mantienen alineación de al menos 16 KB. El código DEX y las bibliotecas nativas de ambos APK coinciden por hash. Los dos pesos de la edición completa coinciden en tamaño y SHA-256 con `models.lock.json`, y se almacenan sin compresión.

El manifest compilado de ambos APK no contiene `BIND_ACCESSIBILITY_SERVICE` ni `ScreenshotService`, y declara únicamente la captura manual mediante su actividad privada y servicio de primer plano `mediaProjection`. El DEX optimizado del actualizador ya no referencia la clase base `android.accessibilityservice.AccessibilityService`. No existe temporizador de captura en la ruta manual.

## Pruebas

- `testReleaseUnitTest`: 58 pruebas, cero fallos o errores.
- `lintRelease`: cero errores, 26 advertencias y dos sugerencias no bloqueantes.
- Emulador AOSP API 35: 12 pruebas de captura manual, interfaz y mascota en una ejecución de 58,109 segundos, todas correctas.
- Tras reforzar la comprobación de imagen real y añadir la interfaz manual, se ejecutaron las tres pruebas de captura y la nueva prueba de interfaz: cuatro correctas en 18,324 segundos. En total, 13 casos distintos verificados en el dispositivo emulado.
- La prueba real autorizó el diálogo de Android, recibió un único JPEG de hasta 960 píxeles en un servidor local de prueba, comprobó contenido no uniforme y confirmó `dumpsys media_projection` terminado en `null`. Otra petición volvió a abrir la autorización; cancelarla no envió otra imagen. La prueba independiente de cancelar también dejó cero peticiones.
- Se inspeccionó visualmente el JPEG obtenido: corresponde al inicio del emulador, no a una imagen vacía. Se conserva como `screenshots-v1.0.1-manual/capture-test.jpg`. La pantalla de Entorno se conserva en `screenshots-v1.0.1-manual/environment.png`.
- El actualizador de producción se instaló con `adb install -r` sobre la instalación de pruebas del emulador con el mismo historial de firmas: `Success`. Su inicio normal devolvió `Status: ok` sin desinstalar.

La primera ejecución de las pruebas encontró un problema de limpieza de `ActivityScenario` después de enviar la tarea al fondo. Se corrigió el arnés para gestionar esas actividades mediante el dispositivo; los casos posteriores pasaron. Una compilación se interrumpió por falta de memoria nativa de Java en la PC. La compilación final pasó con un proceso de 1536 MB, dos trabajadores y compilador Kotlin en proceso.

Se intentó además ejecutar el APK de pruebas Debug directamente contra producción optimizada, pero su runner depende de `kotlin.jvm.internal.Intrinsics`, eliminada/integrada por R8 en producción, y no pudo arrancar. Esa ejecución **no se cuenta como prueba aprobada**. El arranque normal de producción se verificó por separado; las pruebas instrumentadas corresponden a Debug con la misma configuración manual y certificado. No se alteraron las reglas de producción para acomodar ese experimento.

## Límites

No se ha probado instalación con Play Protect en el Galaxy A55 Android 16. Eliminar Accesibilidad elimina el permiso asociado al bloqueo reportado, pero no equivale a aprobación de Google ni garantiza aceptación del APK. La captura real y la cancelación se probaron en API 35; no se dispone de prueba física en Android 16. La implementación de voz y sus modelos no cambian respecto de 1.0.0, y no se vuelve a prometer velocidad de generación en hardware no medido.
