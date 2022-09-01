# Procedencia

- Repositorio: https://github.com/lachydotmcg/capri-desktop
- Commit: `72d6b8374905689d8d49736079f322c9429bd52e`
- Consulta: 29 de octubre de 2026.
- Aviso requerido: Copyright 2026 Lachy (https://github.com/lachydotmcg).
- Licencia: PolyForm Noncommercial 1.0.0, texto preservado en esta carpeta, en la raíz y dentro del APK.

Se copiaron sin modificar los PNG de `assets/sprites/`, `assets/chip.png`, `assets/capri.png` y la fuente `assets/fonts/ShareTechMono-Regular.ttf`. La tipografía conserva `OFL-ShareTechMono.txt`.

Los archivos C# de esta carpeta son copias de referencia de `app/Brain.cs`, `Voice.cs`, `Capri.cs`, `Mainframe.cs`, `Talk.cs` y `Presence.cs`. No forman parte de la compilación Android. El código Kotlin adapta los estados, tiempos de animación, disposición visual, configuración y comportamiento al sistema Android.

No se incorporan binarios Windows, el ZIP de Kokoro, modelos de lenguaje ni claves API. El TTS Android es una implementación nueva. ElevenLabs se conecta con las credenciales que el usuario introduce.

Desde 0.2.0 se incluyen también `hello-morning.wav`, `hello-late.wav` y `hello-miss.wav` de `assets/voice/` como referencia local para Qwen. Se conservan los archivos y su procedencia; el perfil se prepara en el teléfono. Ver `../QWEN_UPSTREAM.md`.

El parecido del timbre, la inferencia Kokoro y el rendimiento en teléfonos reales siguen pendientes de evaluación. La síntesis Qwen nativa cuenta con una prueba de ejecución en emulador documentada en 0.2.0.
