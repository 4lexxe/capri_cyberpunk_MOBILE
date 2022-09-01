# Validación de Capri Android 0.4.0

29 de octubre de 2026. Paquete `com.capri.companion`, versión 0.4.0, código 5.

## APK

`artifacts/Capri-Android-0.4.0-debug.apk`, 36.723.377 bytes.

SHA-256: `4ff28ba94258e44bb7b755788c1290ef94538a44894b72cad1ee5ff94ce25858`.

Certificado de depuración idéntico a las versiones anteriores: `1bc053afaca9d965f3b54f5578522c94ab7c8bd8eb97fad1c39a3d11fc817895`. Instalar encima conserva modelos, sesión de ChatGPT y ajustes.

## Voz

Los dos WAV de `VOZ_CAPRI` se incorporan sin modificar como assets. La app permite elegir uno, ambos o importar otro WAV. La conversión acepta PCM16/24/32 y float32, mono o estéreo, hasta 30 MB y frecuencias de 8 a 96 kHz; entrega mono PCM16 a 24 kHz. Rechaza audio malformado, demasiado corto, silencioso o con muestras no finitas. La referencia y el perfil se guardan localmente por contenido. El perfil combinado suministrado fue extraído con los mismos GGUF y evita recalcularlo en el primer uso. Los perfiles ingleses anteriores no se reutilizan como si fueran los nuevos.

Se conecta la API de streaming nativa al AudioTrack. Un primer paquete de unos 320 ms permite comenzar antes; los siguientes pueden contener hasta 1,28 s. Se conserva un segundo de contexto del vocoder para las uniones. En Android CPU se reutiliza el caché de su grafo; otras rutas mantienen su limpieza anterior. No se acumula un WAV completo nativo para la reproducción normal. El modelo se conserva cargado y puede prepararse antes de hablar.

Se usan hasta cuatro núcleos disponibles, incluyendo dos en equipos de dos núcleos. La detención cierra la reproducción y cancela el cálculo pendiente. Una carrera hallada en la prueba de cancelación devolvía un error ordinario; ahora la generación invalidada termina con CancellationException y no produce audio tardío.

## Medición de latencia

Emulador Android 15/API 35, x86_64, dos CPU virtuales, 4 GB de RAM, modelos Q4_K_M 0.6B y voz combinada. Texto de prueba: “Hola, choom. ¿Qué estás mirando?”. Se limitó cada ejecución a 32 frames para acotar la prueba; no es una evaluación de completitud o inteligibilidad de la frase. El muestreo no es determinista, así que las dos ejecuciones no constituyen una comparación de audio idéntico.

| Medida | Resultado |
| --- | ---: |
| Espera de síntesis completa antes de reproducir, ruta buffered | 45.830 ms |
| Primer PCM entregado por streaming | 7.848 ms |
| Tiempo de generación del stream | 71.523 ms |
| Duración del audio de prueba | 2.560 ms |
| Relación generación/audio | 27,94 |
| Paquetes de audio | 3 |

Se verificó que llega PCM finito antes de terminar la síntesis, que AudioTrack empieza mientras el generador sigue activo y que detenerlo no reanuda audio después. La espera inicial se reduce, pero la generación **no sostiene tiempo real en este emulador**. El coste total del streaming sigue siendo mayor que el buffered en esta prueba. El usuario informó 8 GB de RAM en el teléfono; no se conoce su procesador ni se midió un teléfono físico. La RAM no basta para asegurar reproducción continua. El panel muestra el tiempo al primer audio y la relación de generación para comprobar el equipo real.

Datos y muestra de prueba: `artifacts/validation-0.4.0/qwen-benchmark.json` y `qwen-stream-latino-probe.wav`. No se ha verificado subjetivamente el parecido ni el acento exacto del resultado. Los textos se generan en español latino y Qwen recibe el idioma español y las referencias proporcionadas.

## Capturas y comentarios

Durante una sesión autorizada se solicita un cuadro inicial y después uno con un intervalo mínimo de 180.000 ms, aunque la imagen no cambie. MIRAR mantiene su mínimo manual de diez segundos. Las pausas por contenido no visible no reinician el intervalo para enviar imágenes demasiado seguido. El tiempo exacto depende de la entrega de cuadros por Android y de la disponibilidad del análisis anterior.

Con **Comentarios cada 3 minutos** activado, la política admite comentarios en esa cadencia, hasta 20 por hora, sin exigir una respuesta después de dos intervenciones. El intervalo se compara por la hora de captura para que pequeñas variaciones de latencia del modelo no hagan saltar un ciclo. Se mantiene la supresión de contenido sensible, baja confianza, frase repetida y conversación ocupada. No se prometen comentarios cuando no hay contenido apropiado ni se acumulan comentarios pendientes. El consentimiento de Android, notificación y detención al bloquear el equipo permanecen vigentes.

## Controles ejecutados

- **51 pruebas unitarias aprobadas**, incluyendo conversión/resample de WAV, referencias inválidas, intervalo de tres minutos, continuidad de comentarios, límites y latencia variable de IA, además de los proveedores y OAuth existentes.
- **20 pruebas instrumentadas aprobadas**, sin omisiones, en el emulador. Incluyen las referencias reales, importación de WAV, selección visible de audios, cifrado, superposición, MediaProjection, generación nativa, entrega incremental, AudioTrack y cancelación. La ejecución final terminó `OK (20 tests)` en 173,196 segundos.
- Debug y Release con R8 compilan para ARM64 y x86_64. La ejecución nativa se comprobó en x86_64; ARM64 sólo se compiló.
- Lint: 0 errores, 19 advertencias y 2 sugerencias.

Comandos: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease`; instalación de ambos APK en el emulador y `adb shell am instrument -w -r com.capri.companion.test/androidx.test.runner.AndroidJUnitRunner` con modelos preparados en su almacenamiento privado.

No se realizaron llamadas reales a ChatGPT ni se consumió uso del plan durante estas pruebas. El análisis de pantalla usa un servidor de prueba; el permiso y la captura de Android sí fueron reales. La emisión periódica se verifica por límites temporales y política; no se dejó una sesión real de ChatGPT funcionando durante horas.
