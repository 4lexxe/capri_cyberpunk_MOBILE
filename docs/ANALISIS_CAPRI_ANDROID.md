# Capri para Android: análisis y propuesta

Fecha: 29 de octubre de 2026.

Requisito confirmado: la referencia de voz que recuerda el usuario era en inglés, pero la aplicación debe escuchar, conversar y hablar en español. El repositorio exacto de esa voz todavía no está identificado.

## Conclusión

Es viable crear una compañera Capri para Android con personaje flotante, animaciones, conversación, memoria y voz en español. Recomiendo una implementación nativa en Kotlin. El proyecto de escritorio sirve como referencia de comportamiento y aporta recursos visuales, pero su interfaz, integración con el sistema y ejecución local necesitan adaptación.

La primera versión debería concentrarse en el personaje y la conversación. El control de otras aplicaciones y la observación de pantalla tienen restricciones adicionales y deben evaluarse como funciones posteriores.

Este documento analiza código y documentación. No se compiló una app Android, no se ejecutó código de los repositorios y no se hicieron pruebas de calidad de audio ni mediciones en un teléfono.

## Proyecto de referencia

Se inspeccionó `lachydotmcg/capri-desktop` en el commit `72d6b8374905689d8d49736079f322c9429bd52e`. Las búsquedas web mostraron algunas versiones anteriores; las observaciones del código corresponden al commit fijado.

La implementación Windows usa C# 5, .NET Framework 4.8 y WinForms. Hay otra implementación para Mac/Linux con .NET 10 y Avalonia. No se encontró un proyecto Android en el árbol inspeccionado. Fuente: [guía de contribución](https://github.com/lachydotmcg/capri-desktop/blob/72d6b8374905689d8d49736079f322c9429bd52e/CONTRIBUTING.md) y [proyecto multiplataforma](https://github.com/lachydotmcg/capri-desktop/blob/72d6b8374905689d8d49736079f322c9429bd52e/cross/Capri.Cross.csproj).

| Parte | Qué aporta | Adaptación propuesta |
| --- | --- | --- |
| `assets/sprites/` | Tiras PNG para reposo, parpadeo, sonrisa, glitch, arrastre, sueño, despertar y otras poses | Reutilizar los recursos conservando avisos y condiciones; implementar un reproductor Android |
| `app/Capri.cs` | Ventana y comportamiento del personaje | Rehacer la ventana, gestos y ciclo de animación |
| `app/Brain.cs` | Personalidad, memoria y conexiones con modelos | Adaptar el diseño; quitar referencias a escritorio, mouse y solución de problemas de Windows |
| `app/Voice.cs` y `VoicePack.cs` | Síntesis local y remota | Crear proveedores Android intercambiables |
| `app/Ears.cs` | Captura y reconocimiento de voz | Usar APIs Android o un motor local compatible |
| `app/Hands.cs` y `Computer.cs` | Acciones y automatización del escritorio | Sustituir por capacidades específicas de Android |

La animación ya está resuelta visualmente como sprites, sin necesidad de un modelo 3D. Por ejemplo, reposo usa 16 cuadros a 8 fps y la boca tiene una tira independiente. Fuente: [definiciones de animación](https://github.com/lachydotmcg/capri-desktop/blob/72d6b8374905689d8d49736079f322c9429bd52e/app/Capri.cs#L506).

## Voz: hallazgos y español

El proyecto tiene dos caminos: ElevenLabs, con clave y Voice ID propios, y Kokoro mediante sherpa-onnx. El paquete local `capri-voice-1.zip` pesa 316.041.808 bytes e incluye modelos y DLL de Windows. El estilo predeterminado, “Her own”, utiliza el speaker 53 a velocidad 1,1. La configuración local selecciona `en-us` o `en`; cambiar el idioma del chat no adapta automáticamente esta voz al español. Fuentes: [implementación](https://github.com/lachydotmcg/capri-desktop/blob/72d6b8374905689d8d49736079f322c9429bd52e/app/Voice.cs#L532), [paquete](https://github.com/lachydotmcg/capri-desktop/blob/72d6b8374905689d8d49736079f322c9429bd52e/app/VoicePack.cs) y [release de voz](https://github.com/lachydotmcg/capri-desktop/releases/tag/voice-pack-1).

No se inspeccionó el interior del ZIP ni se escuchó su resultado. La etiqueta “Her own” no demuestra por sí sola el origen del timbre o su equivalencia con una interpretación concreta de Capri. Tampoco se comprobó si su embedding puede transferirse a otro paquete multilingüe.

Hay que separar tres objetivos: entender español, responder en español y conservar un timbre parecido al de la referencia. Los dos primeros no garantizan el tercero.

| Alternativa | Español | Ventaja | Pendiente |
| --- | --- | --- | --- |
| ElevenLabs con modelo multilingüe | Documentado | Camino directo para probar una voz de personaje en español | Cuenta, consumo y evaluación del acento y parecido |
| Kokoro con recursos españoles | El catálogo oficial incluye español | Posibilidad de voz local | Exportación y fonemización compatibles con el motor Android; parecido sin demostrar |
| TTS instalado en Android | Depende de los motores y voces instalados | Alternativa inicial sencilla | Verificar disponibilidad y calidad; el timbre será el del motor elegido |
| Motor más pesado en PC o servidor | Depende del modelo | Permite experimentar sin cargar todo en el teléfono | Latencia, conexión y mantenimiento del servidor |

ElevenLabs documenta que una voz puede hablar otros idiomas compatibles, pero una referencia inglesa puede conservar acento o variar de acento. Conviene probar primero frases españolas representativas. Fuentes: [idiomas y voces](https://elevenlabs.io/docs/help-center/product/core-capabilities/text-to-speech/what-languages-can-the-ai-speak) y [catálogo oficial de voces de Kokoro](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md).

Sherpa-onnx dispone de código y ejemplos Android para TTS. Eso permite explorar Kokoro en el teléfono, pero no convierte las DLL de Windows en bibliotecas Android ni garantiza el rendimiento del paquete concreto. Fuente: [ejemplos TTS Android](https://k2-fsa.github.io/sherpa/onnx/tts/apk.html).

Propuesta: definir una interfaz de voz independiente del personaje y del modelo de conversación. Probar una opción multilingüe remota y una local antes de fijar el proveedor. Evaluar pronunciación de nombres, español cotidiano, números, preguntas, frases cortas, interrupción y tiempo hasta el primer audio. No hay todavía una muestra validada de “Capri en español”.

## Búsqueda del otro repositorio

No se pudo identificar con confianza el repositorio recordado. Los siguientes son candidatos o referencias relacionadas, no coincidencias confirmadas:

- [cp00004/capri](https://github.com/cp00004/capri): su README describe conversación por voz con Gemini TTS, perfiles y efectos; ElevenLabs es opcional. Puede aportar ideas para el tratamiento del audio.
- [mochiiwuff/capri-desktop](https://github.com/mochiiwuff/capri-desktop): el archivo `src/renderer/js/voice.js` inspeccionado sintetiza sonidos y pitidos mediante Web Audio. Ese archivo no es un modelo de voz hablada.
- [TwKru/capri-reload](https://github.com/TwKru/capri-reload): describe síntesis Microsoft/edge-tts con cambios de tono y efectos; está orientado al ruso. Es una referencia menos probable porque el usuario recuerda inglés.
- El propio proyecto de Lachy publica su voz como un asset de release, sin requerir otro repositorio. Es otra posible explicación del recuerdo.

## Qué cambia en Android

**Personaje flotante.** Es técnicamente posible con `WindowManager` y `TYPE_APPLICATION_OVERLAY`. El usuario debe conceder “mostrar sobre otras aplicaciones”. El overlay no equivale a acceso al contenido de las otras apps ni garantiza aparecer sobre todas las superficies del sistema. Fuente: [permiso de overlay](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW).

**Continuidad.** Para una compañera persistente, diseñar un servicio en primer plano con notificación y acción para detenerla. La categoría concreta debe validarse según las funciones y el Android objetivo; `specialUse` es una posibilidad para casos válidos no cubiertos por otros tipos, y se revisa al publicar en Play. No asumir ejecución indefinida ni consumo aceptable sin medir. Fuente: [tipos de servicio](https://developer.android.com/develop/background-work/services/fgs/service-types).

**Micrófono.** Empezar con un botón para hablar. Comprobar disponibilidad del reconocimiento en el dispositivo y no prometer funcionamiento sin conexión para cualquier teléfono. El reconocimiento continuo requiere un diseño propio: `SpeechRecognizer` no está pensado para reconocimiento continuo. Fuente: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer).

**Pantalla.** Una función de observación basada en `MediaProjection` necesita autorización por sesión; desde Android 14 no se puede reutilizar el mismo token para nuevas sesiones. Hacerla explícita y opcional. Fuente: [MediaProjection](https://developer.android.com/media/grow/media-projection).

**Acciones.** Empezar con acciones concretas expuestas por Android y las apps: abrir enlaces o lanzar actividades compatibles. El control general mediante accesibilidad es otro alcance. La política actual de Google Play prohíbe usar esa API para iniciar, planificar y ejecutar autónomamente acciones o decisiones, con excepciones para herramientas de accesibilidad verificadas. Esto condiciona reproducir el agente de escritorio en Play. Fuente: [política de AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491).

## Arquitectura propuesta

Propuesta técnica, todavía sin implementar:

- Kotlin y Jetpack Compose para configuración y conversación.
- Servicio del personaje, ventana flotante acotada y reproductor de sprites con estados de reposo, arrastre, escucha, espera, habla y sueño.
- Animación de boca inicialmente ligada al nivel de audio; sincronización por fonemas como mejora opcional.
- Capa de conversación intercambiable: API remota o servidor del usuario al principio; inferencia en el teléfono después de medir.
- Capas separadas de reconocimiento y síntesis. Idioma español como configuración inicial.
- Memoria local y preferencias; credenciales protegidas con claves del Android Keystore. Una clave de servicio compartida no debe incorporarse al APK.
- Registro de capacidades para que el modelo sólo pueda solicitar acciones realmente implementadas.

Recomiendo Kotlin por el peso de los servicios, permisos, audio y ventanas Android en este producto. Esta es una decisión de diseño, no una imposibilidad de usar Flutter u otra tecnología.

## Primera versión y validación

1. Personaje flotante arrastrable, animaciones, tamaño, ocultar y detener.
2. Chat escrito en español, personalidad adaptada al teléfono y memoria local básica.
3. Botón para hablar, transcripción, respuesta hablada e interrupción.
4. Comparación de voces españolas y elección de la que mejor conserve el carácter buscado.
5. Pruebas en un teléfono real: rotación, teclado, bloqueo, navegación entre apps, auriculares, revocación de permisos, red interrumpida y consumo de batería.
6. Posteriormente: acciones concretas, observación de pantalla e inferencia local, cada una con validación propia.

No hay datos para prometer aún latencia, RAM mínima, autonomía o calidad de voz. El modelo de teléfono ayudará a decidir qué ejecutar localmente.

## Licencia

El repositorio de referencia usa **PolyForm Noncommercial 1.0.0** y exige conservar su aviso y las condiciones al distribuir partes del software. Su README excluye venderlo o incorporarlo a productos pagos. Antes de plantear una distribución comercial habría que resolver los permisos correspondientes. Los recursos de personaje y los modelos de voz requieren revisar también su procedencia y condiciones; una licencia de código no acredita por sí sola derechos sobre terceros. Fuente: [licencia del repositorio](https://github.com/lachydotmcg/capri-desktop/blob/72d6b8374905689d8d49736079f322c9429bd52e/LICENSE.md).

## Decisiones pendientes

- Identificar la referencia inglesa exacta o conseguir una muestra que permita comparar el timbre.
- Elegir español latino neutro o una variante específica; no asumir acento argentino por la ubicación.
- Conocer el teléfono objetivo antes de decidir modelos locales.
- Definir si la distribución será personal, pública gratuita o comercial.

Estas decisiones no impiden comenzar por el personaje flotante y el chat en español.
