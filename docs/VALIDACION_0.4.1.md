# Validación de Capri Android 0.4.1

29 de octubre de 2026. Paquete `com.capri.companion`, código 6.

## Problema y cambio

El usuario informó voz entrecortada en 0.4.0. Esa versión reproducía un primer paquete de sólo 320 ms aunque el generador pudiera tardar segundos en entregar otro. Las mediciones anteriores ya mostraban una generación más lenta que la reproducción en el emulador. El reproductor podía quedarse sin muestras entre paquetes; las uniones del vocoder por ventanas también formaban parte de esa ruta. No hubo acceso al audio ni al teléfono físico del usuario para atribuir cada corte observado a una causa específica.

0.4.1 usa síntesis de una onda completa y la carga en AudioTrack MODE_STATIC antes de play(). La carga debe devolver la cantidad total de muestras; una transferencia parcial no se reproduce. Mientras se prepara, la app muestra PREPARANDO VOZ. Una vez iniciada la reproducción, ya no espera al generador. La boca consulta la posición real de reproducción y calcula el nivel de esa zona del audio.

El último resultado completo se conserva en memoria para repetir la misma frase sin calcularla otra vez. La coincidencia incluye texto, perfil de referencia y presupuesto de generación. Se conserva sólo un resultado, no se escribe un historial de audio ni se persiste entre procesos. El caché se elimina al borrar los modelos. La selección de audios latinos, la importación de WAV y las capturas cada tres minutos siguen disponibles.

El cambio prioriza continuidad: una frase nueva puede tardar más en empezar que el primer paquete de streaming. No se afirma tiempo real continuo ni aceleración de la inferencia. El streaming nativo sigue disponible para las pruebas del motor, pero no se usa como reproducción normal de Capri.

Contrato de la reproducción estática: [AudioTrack de Android](https://developer.android.com/reference/android/media/AudioTrack). El método write(float[]) copia las muestras para reproducir después; en modo estático las escrituras comienzan en el origen del buffer. Por eso se hace una carga completa única y se exige transferencia completa antes de play().

## Verificación

- **51 pruebas unitarias aprobadas**.
- **6 pruebas Android enfocadas aprobadas**, sin omisiones, en emulador API 35/x86_64 con los GGUF instalados. Se comprobaron un generador simulado que demora antes de entregar un segundo de PCM, una sola reproducción que conserva la duración completa, cancelación incluso cuando el productor ignora inicialmente la cancelación, generación Qwen real, cancelación nativa, inicio de voz después de terminar la generación y reutilización del mismo PCM al repetir. La ejecución terminó `OK (6 tests)` en 26,495 s.
- Debug y Release con R8 compilados para ARM64 y x86_64; ejecución nativa verificada sólo en x86_64.
- Lint: **0 errores**, 19 advertencias y 2 sugerencias.
- Firma verificada, con el mismo certificado de depuración de versiones anteriores.

Comando de compilación: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease`. Las pruebas Android seleccionaron QwenPlaybackTest, NativeSynthesisTest y los métodos de reproducción y caché de QwenStreamingTest.

No se repitió toda la suite instrumentada de 0.4.0 ni su benchmark de streaming, ya que las rutas nativas y de observación no cambiaron. No se escuchó el resultado en el teléfono del usuario ni se midió su salida Bluetooth o altavoz. Las pruebas confirman la preparación previa y el avance/duración de reproducción, no la percepción subjetiva de cada dispositivo.

## APK

`artifacts/Capri-Android-0.4.1-debug.apk`, 34.086.929 bytes.

SHA-256: `6b93c6b4c136a0b24347c8e6570b0946176232b028b784c262d2a6f526a6c958`.

Certificado: `1bc053afaca9d965f3b54f5578522c94ab7c8bd8eb97fad1c39a3d11fc817895`.

Instalar sobre la versión anterior conserva modelos, referencias, sesión y ajustes. Probar una frase nueva y luego REPETIR VOZ para comprobar la preparación inicial y la reutilización del audio.
