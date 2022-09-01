# Validación de Capri Android 0.5.0

29 de octubre de 2026. Paquete `com.capri.companion`, código 7.

## Mascota flotante

El usuario pidió reducir los paneles permanentes, mostrar las respuestas como globos y corregir el espacio al sentarse. La mascota ahora usa una ventana propia, sin paneles de estado. Cada respuesta nueva abre otra ventana con un globo angular cian, texto monoespaciado, etiqueta CAPRI roja y una punta orientada al personaje. No vuelve a mostrar mensajes históricos al arrancar ni confunde avisos de ajustes con diálogo.

El globo dura 6,5–20 segundos según longitud y se mantiene mientras se prepara/reproduce voz. Al terminar la voz quedan al menos 2,5 segundos de lectura. Tiene un máximo visual de cinco líneas; tocarlo abre la conversación completa. Un toque en la mascota muestra una barra de tres iconos de 44 dp (escribir, hablar, opciones) durante cinco segundos. Mantener pulsado abre un menú temporal de doce segundos. Las acciones de observación sólo aparecen durante una sesión activa; el acceso a detener observación también continúa en la notificación de esa sesión.

La ventana del personaje conserva su posición al aparecer un mensaje. Globo y controles se posicionan por separado y se retiran de WindowManager al cerrarse. Los controles buscan espacio arriba, abajo o al costado; el globo se mantiene dentro de pantalla. El arrastre cierra las opciones y sigue ajustando la mascota a los bordes.

Las tiras sentadas originales tienen mucho espacio transparente. Sólo en la superposición se usa un viewport de origen `(64,52)-(280,384)` y una altura proporcional 332/216. Se conserva la fuente de arte original y el encuadre de la galería. El viewport se reutiliza sin asignar objetos en cada cuadro. Las reacciones a respuestas/toques utilizan las animaciones originales de sonrisa y parpadeo/glitch sentado.

Capturas reales de las pruebas, con mensajes simulados:

- [De pie](screenshots-v0.5/standing.png)
- [Sentada](screenshots-v0.5/seated.png)
- [Iconos temporales](screenshots-v0.5/actions.png)
- [Menú con mascota junto al borde superior](screenshots-v0.5/top-menu.png)

## Personalidad y voz

Se añadió un estilo de español latino, tuteo, expresiones coloquiales moderadas y energía juguetona a conversación y reacciones visuales. La personalidad predeterminada anterior se migra por coincidencia exacta; las personalidades personalizadas se conservan. El texto de estilo orienta al modelo, sin garantizar que cualquier proveedor lo siga siempre.

Qwen conserva la generación completa y AudioTrack estático de 0.4.1. Un procesamiento PCM posterior aplica por defecto ritmo 1,08x y mezcla robótica 0,12. El ritmo se limita a 1–1,2x, con una ligera subida de tono por remuestreo lineal. El efecto mezcla modulación a 85 Hz y una pequeña textura digital, sin llevar las muestras fuera de [-1,1]. Los controles se guardan; el filtro puede quedar en cero. El audio original del caché no se modifica ni se acumula el efecto al repetir. La animación de boca sigue las muestras procesadas que se reproducen. Android TTS solicita español de México con ritmo 1,12 y tono 1,10; la voz concreta depende de las voces que tenga el dispositivo.

Estos cambios no aceleran la inferencia ni entrenan un acento nuevo. La referencia y las capacidades del modelo siguen determinando el parecido y la pronunciación. No se hizo una evaluación subjetiva en el teléfono del usuario ni una conversación con un proveedor real.

## Verificación

- **56 pruebas unitarias aprobadas**. Las nuevas pruebas comprueban duración del PCM, energía, límites, silencio, conservación del caché y duración de lectura del globo.
- **6 pruebas Android finales aprobadas**, `OK (6 tests)` en 42,82 segundos, emulador API 35/x86_64. Incluyen la aparición y retirada de globos, persistencia durante preparación de voz, ausencia de avisos de ajustes, estabilidad de posición, cercanía al sentarse, iconos y cierre automático, menú en borde superior, arrastre dentro de pantalla, migración de personalidad y controles, reproducción continua/cancelación de Qwen con productor simulado.
- Las **5 pruebas de interfaz existentes** también aprobaron en la iteración anterior de este cambio: galería, cuenta ChatGPT, referencias WAV, error de cerebro no configurado y servicio flotante sin micrófono. No se volvió a ejecutar toda la suite de captura ni el benchmark nativo, porque esas rutas no cambiaron.
- Debug, AndroidTest y Release con R8 compilados para ARM64/x86_64. Ejecución verificada sólo en emulador x86_64.
- Lint: **0 errores**, 22 advertencias, 2 sugerencias.
- Revisión visual de las capturas anteriores. No se enviaron capturas ni credenciales a un proveedor externo.
- APK firmado con el mismo certificado de depuración anterior. El empaquetado final se regeneró desde un APK vacío para eliminar bloques sobrantes del empaquetado incremental. El contenido no incluye Piper ni sherpa-onnx.

Piper quedó en pausa por indicación del usuario; su borrador se conserva según las notas locales del experimento Piper. No hay una voz Capri entrenada con Piper en esta entrega.

## Entrega

`artifacts/Capri-Android-0.5.0-debug.apk`, **34.032.523 bytes**.

SHA-256: `c914ac4142d44b940a73ed00e202a649ff1ea8a29ef0aa32a84959f7951e155b`.

Certificado SHA-256: `1bc053afaca9d965f3b54f5578522c94ab7c8bd8eb97fad1c39a3d11fc817895`.

Instalar sobre la versión anterior conserva los modelos, las referencias WAV, los ajustes y la sesión. El ritmo y el filtro se ajustan en Mainframe → Voz → Qwen; se prueban con Probar voz y se conservan con Guardar ajustes.
