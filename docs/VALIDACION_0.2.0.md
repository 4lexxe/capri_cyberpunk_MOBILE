# Validación de Capri Android 0.2.0

Fecha: 29 de octubre de 2026.

## Entrega

- Archivo: `artifacts/Capri-Android-0.2.0-debug.apk`.
- Tamaño: 28.792.804 bytes, aproximadamente 28,8 MB.
- SHA-256: `80fbd5bbaf6ff65a3402e0128badf0e9372909c6372b61aab607bb5b71ea636f`.
- APK de depuración firmado; `apksigner verify` satisfactorio.
- Android base: API 26+ en ARM64 o x86_64. Voz Qwen habilitada desde API 31. Observación habilitada desde API 34.

Se compilaron las variantes Debug y Release, incluida la reducción con R8 de Release. El artefacto instalable entregado es Debug. La biblioteca JNI se compiló para ARM64 y x86_64; la ejecución se comprobó en x86_64, no en un teléfono ARM64 físico.

## Verificaciones

**20 pruebas unitarias aprobadas.** Cubren endpoints, autenticación y formatos de Gemini/Claude/OpenAI/compatible; imágenes en solicitudes; historial de conversación; errores sin reflejar claves; redirecciones; cancelación HTTP; salida visual estructurada; confianza y contenido sensible; caducidad; iniciativa, frecuencia y deduplicación; cuadros de animación.

**13 pruebas instrumentadas aprobadas** en un emulador AOSP Android 15 / API 35, con 4 GB de RAM y 2 núcleos virtuales:

- Sprites originales y sus dimensiones.
- Cifrado Keystore y persistencia del chat.
- Galería, conversación sin modelo e inicio/detención del personaje.
- Ausencia de contexto sin sesión; pausa al ocultar contenido; recuerdos con caducidad y borrado.
- Carga de la biblioteca nativa y funciones de cancelación.
- Generación local de audio español con los modelos GGUF reales.
- Cancelación de generación Qwen sin devolver audio tardío.
- Flujo real de divulgación y autorización de Android, captura JPEG hacia un servidor local de prueba, recepción del resumen e invalidación al detener la sesión.

La prueba de captura abre Ajustes de Android como contenido de prueba después de autorizar pantalla completa en el diálogo del sistema. No usa datos de un teléfono personal ni un proveedor remoto. El análisis visual de esa prueba es simulado: verifica transporte, formato, estado y cancelación, no la calidad de un modelo visual real ni una integración específica con TikTok.

Resultado de compilación: **BUILD SUCCESSFUL**. Lint: **0 errores, 16 advertencias y 2 sugerencias**. Quedan advertencias de versiones de dependencias, APIs desaconsejadas y sugerencias de estilo; no se ocultó un error mediante un baseline.

## Evidencia visual y audio

- [Entorno](screenshots-v0.2/environment.png): pantalla real con observación apagada.
- [Qwen](screenshots-v0.2/qwen.png): pantalla real con modelos locales presentes.
- `artifacts/qwen-es-prueba.wav`: audio generado en Android con la entrada “Hola, choom.”.

El audio se verificó como PCM finito, no vacío y con señal. No se evaluó automáticamente pronunciación, parecido con Capri o acento. La duración de las pruebas en un emulador de CPU no debe presentarse como rendimiento de un teléfono real.

## Comportamiento entregado

La observación requiere activación explícita, aviso del proveedor y autorización por sesión. Se prefiere compartir una app, aunque Android permite al usuario seleccionar pantalla completa. No se graba video ni se guardan capturas. La ventana de Capri se protege durante la observación para no compartir su configuración.

El contexto inmediato caduca a los dos minutos. Los comentarios automáticos están apagados inicialmente y dependen de una política local. La memoria persistente también es opcional y revisable. Los resultados tardíos de sesiones detenidas se descartan.

La voz Qwen es nativa, mediante C++/JNI; los pesos se descargan y verifican con SHA-256. Usa un perfil derivado de los saludos del proyecto original. Conversación y visión se conectan a las APIs configuradas por el usuario.

## Limitaciones pendientes de prueba real

- Credenciales reales y compatibilidad del modelo concreto elegido en cada proveedor. Los conectores se verificaron con MockWebServer, sin consumir créditos.
- Calidad visual y pertinencia de comentarios al ver TikTok u otras apps reales.
- Calidad audible, semejanza del timbre y reconocimiento de micrófono en el teléfono del usuario.
- Latencia, batería, temperatura y memoria sostenida en ARM64 físico.
- Diferencias entre fabricantes, superficies protegidas, rotaciones y tamaños fuera del emulador usado.

Una operación nativa de carga o decodificación ya iniciada puede tardar en detener su cómputo. La cancelación impide reproducir su resultado y solicita el corte de la generación por cuadros. No se promete una experiencia instantánea.
