# Validación de Capri Android 1.0.0

29 de octubre de 2026. Paquete `com.capri.companion`, código 8. Fuentes Kotlin/Compose, C++/GGML, ARM64 y x86_64.

## Cambios solicitados

- Retirados los paneles Origen y Esta versión. Se conserva el acceso discreto a Licencias y los avisos de atribución en el fuente.
- Voz pública reducida a Android y Qwen, sin selector de referencias, carga de archivos, ElevenLabs ni eliminación/descarga manual de modelos.
- Perfil latino combinado fijo y precalculado. Ritmo inicial 1,09, mezcla robótica 0,182 (52% del control) y volumen 0,73. Una migración de preferencias aplica estos valores al actualizar desde las pruebas anteriores; los cambios posteriores del usuario se conservan.
- Prompt base común para chat y reacciones visuales: español latino natural, calidez, humor, respuestas habitualmente de 12–24 palabras y sin apodos inventados. Se elimina el apodo anterior del nombre predeterminado, el placeholder y las pruebas de voz; los nuevos textos de IA se limpian antes de mostrarse/reproducirse. Se migran personalidades predeterminadas conocidas y se conserva el contexto personalizado.
- × en cada globo para cerrarlo sin detener la mascota ni la voz.
- Retirada completa de MediaProjection, VirtualDisplay, ImageReader y del permiso/servicio foreground de mediaProjection. AccessibilityService.takeScreenshot obtiene imágenes puntuales, sin vídeo y sin lectura del árbol de ventanas ni gestos. El permiso se habilita en Android; una sesión necesita además iniciarse expresamente en Capri. Las capturas son de la pantalla visible completa, no de una sola app.
- Edición completa con dos GGUF incluidos y verificados, instalación local sin red y voz ya configurada. Los modelos permanecen fuera de Git y del ZIP del fuente.
- Variante ARM64 DOTPROD/FP16, seleccionada mediante HWCAP y con reserva genérica, O3 efectivo y alineación ELF de 16 KB.
- Firma de producción, documentación, CI y flujo manual de preparación de Release, versiones fijadas y paquete fuente mediante lista explícita.

## Capturas puntuales

El servicio de Accesibilidad no se anuncia como herramienta asistiva. Solicita sólo la capacidad de captura, no lectura de controles ni gestos. Su callback de eventos no procesa datos. Habilitarlo no inicia capturas automáticamente.

Al iniciar una sesión en Entorno, la primera imagen se obtiene después de salir de Capri. Después se usa un temporizador de tres minutos. Una solicitud manual respeta el mínimo de diez segundos entre análisis y se realiza al salir de la app si se pidió desde Entorno. No se captura la ventana propia. La mascota se oculta brevemente antes de la captura para no aparecer en ella. Se libera el HardwareBuffer también al cancelar, se reduce la imagen a 960 píxeles y se envía JPEG 72 al proveedor elegido. No se guardan imágenes. El bloqueo del teléfono, Detener o la revocación del servicio cancelan la sesión y descartan resultados tardíos.

## Verificación automatizada

- **58 pruebas unitarias aprobadas**, tanto en Debug como en Release. Son los mismos casos en dos compilaciones, no 116 casos distintos.
- **15 pruebas Android finales aprobadas**, `OK (15 tests)` en 73,322 s, emulador API 35/x86_64. Se ejecutaron PointScreenshotTest, MinimalOverlayTest, CapriUiTest, QwenPlaybackTest, NativeSynthesisTest y la prueba de reutilización de PCM de QwenStreamingTest. No hubo omisiones.
- La prueba de capturas habilita el servicio sólo en el dispositivo de prueba y restaura la configuración. Verifica que no se capture Mainframe, que llegue una imagen JPEG al servidor local optativo, que no haya una secuencia de envíos continuos, que Detener invalide el contexto y que el manifiesto ya no pida mediaProjection. No envía imágenes a servicios externos.
- Se comprueban los valores iniciales, la ausencia de carga de WAV/ElevenLabs, el acceso a las animaciones, el globo y su ×, controles transitorios, encuadre sentado, arrastre, migración de personalidad, cancelación, continuidad de reproducción y síntesis Qwen real con los pesos instalados.
- Lint Release: **0 errores, 25 advertencias y 2 sugerencias**.
- Se verifican las sumas de ambos modelos dentro del propio APK, contra models.lock.json.
- La totalidad de bibliotecas `.so` del APK, incluidas libc++ y las de Compose, tiene segmentos LOAD con alineación mínima de 16 KB. zipalign `-c -P 16` aprobó.
- Se verificó la firma de ambos APK. El DEX y las bibliotecas nativas de la edición completa y de la actualización son idénticos; cambian los pesos incorporados y la firma.

## Instalación nueva y revisión visual

Se desinstaló la app de prueba del emulador, se instaló el APK completo de producción y se desactivaron Wi-Fi y datos antes del primer arranque. La app abrió correctamente, preparó los modelos y mostró `Qwen local · Capri latino · ambos audios`. Se comprobaron visualmente y mediante la jerarquía de interfaz los valores 109%, 52% y 73%. No se configuró un proveedor externo ni se usaron credenciales reales.

Capturas reales de la edición firmada:

- [Configuración de voz](screenshots-v1.0/voice.png)
- [Archivo visual](screenshots-v1.0/archive.png)
- [Entorno de capturas puntuales](screenshots-v1.0/environment.png)

La instalación completa se probó desde cero. La edición de actualización conserva el certificado anterior y excluye los pesos; debe usarse sólo en instalaciones de prueba que ya tengan los modelos. El cambio de certificado de producción impide instalar esa edición completa encima de una app firmada con la antigua clave de depuración. No se afirma que se haya probado una migración directa entre certificados diferentes.

## Rendimiento: alcance real

Se comprobó la compilación ARM con `-march=armv8.2-a+dotprod+fp16`, la detección HWCAP para ASIMDDP/ASIMDHP y la presencia de las dos bibliotecas ARM. El emulador ejercita la reserva x86_64. **No se ha ejecutado la variante rápida en un teléfono ARM ni se ha medido un porcentaje de aceleración en el teléfono del usuario.** No se promete tiempo real universal.

Se corrige la sustitución de O3 por O2 en RelWithDebInfo. Los pesos siguen en Q4 y la salida del códec mantiene su frecuencia original. Las respuestas breves reducen el trabajo de síntesis; bajar la frecuencia del archivo final no evita el cálculo del modelo. Se mantienen la onda completa y el caché de última respuesta para impedir cortes entre bloques. El efecto y el ritmo se aplican al PCM sin modificar el original almacenado en memoria.

La voz Android depende de su motor y sus modelos instalados. Un perfil/embedding de Qwen no se convierte en una voz de Android copiando sus parámetros. Exponer Qwen como motor TTS del sistema requeriría implementar ese motor y mantendría su coste de inferencia; no se implementó ese registro en esta entrega.

## Artefactos

- `Capri-Android-1.0.0-completa.apk`: **910.089.139 bytes**. SHA-256 `3ff7bd68c31e3dd131ddcdb735c5d256072e977b583e2d2f063c75bbb7004455`.
- `Capri-Android-1.0.0-actualizacion.apk`: **26.208.977 bytes**, sin pesos y con la firma anterior. SHA-256 `7424f8ad017895918cbaeece097913adc5c3a916ecf88acdcf7289a0ed12a985`.
- Certificado de producción SHA-256: `ca81d3a10b864b678897575fde2a454ef967f8d2916aeee58ed0c830e5efba60`.
- Certificado de prueba anterior: `1bc053afaca9d965f3b54f5578522c94ab7c8bd8eb97fad1c39a3d11fc817895`.
- `Capri-Android-1.0.0-source.zip`: fuente con Gradle Wrapper, dependencias nativas, scripts, CI, documentos y licencias. Excluye modelos, claves, almacenes de firma, adjuntos, compilaciones y borradores. Su suma se publica en SHA256SUMS.txt, fuera del ZIP para evitar una referencia circular.

El directorio local `artifacts/release-1.0.0` apunta a almacenamiento en D: para no saturar C:. No se ha publicado un repositorio ni una Release remota; los archivos están preparados para subirlos. La clave privada queda únicamente en signing/ y signing.properties, fuera de todos los paquetes; debe conservarse en privado para futuras actualizaciones.
