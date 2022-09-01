# Validación de Capri Android 0.1.0

Fecha: 29 de octubre de 2026.

## Artefacto

- APK: `artifacts/Capri-Android-0.1.0-debug.apk`.
- Tamaño: 21.316.313 bytes, aproximadamente 20,3 MiB.
- SHA-256: `08e91799d911f0ea944136ba694f3cc72ae80eb39cd8c2140fd2bd8fcb72e59c`.
- Firma: certificado Android Debug; verificación con `apksigner` satisfactoria.
- `minSdk`: 26. `targetSdk` y `compileSdk`: 36.

## Compilación y controles

Se ejecutó mediante el wrapper incluido:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

Resultado: **BUILD SUCCESSFUL**.

- 9 pruebas unitarias aprobadas: política de URLs, construcción y lectura de solicitudes de chat, errores HTTP sin reflejar claves, rechazo de redirecciones, respuestas vacías o inválidas, cancelación y límites de cuadros de animación.
- 6 pruebas instrumentadas aprobadas en un emulador AOSP Android 15 / API 35: dimensiones de sprites, cifrado mediante Keystore, historial, navegación a la galería, aviso por cerebro sin configurar e inicio/detención del overlay sin micrófono.
- Lint: 0 errores, 12 advertencias y 2 sugerencias. Las advertencias restantes corresponden a versiones más recientes de dependencias, constructor de una vista creada exclusivamente desde código y sugerencias de extensiones KTX.
- Los 21 PNG de sprites coinciden por SHA-256 con el commit de referencia `72d6b8374905689d8d49736079f322c9429bd52e`.

## Revisión visual y manual

La app se instaló y abrió en el emulador. Se inspeccionaron las capturas de Mainframe, conversación y overlay sobre el launcher. Se corrigió el contraste de las barras del sistema y se verificó que las pantallas mostraran sus controles sin superposición del sistema.

El arrastre manual llevó el overlay al borde izquierdo; la ventana pasó a `x=0` y conservó una posición dentro de pantalla. Se alternó la orientación a horizontal y de vuelta a vertical sin registrar excepciones de AndroidRuntime. Esta comprobación no sustituye pruebas exhaustivas de orientación y tamaños de teléfono.

Capturas reales, sin respuestas de conversación simuladas:

- [Mainframe](screenshots/mainframe.png)
- [Conversación](screenshots/conversation.png)
- [Overlay sobre el launcher](screenshots/overlay.png)

## Pendiente de probar

- Conversación con un proveedor real y credenciales del usuario. Las pruebas HTTP usan MockWebServer.
- Calidad audible, acento y timbre de la voz del sistema y de ElevenLabs.
- Reconocimiento de voz con el motor instalado en el teléfono del usuario.
- Batería, consumo sostenido, fabricantes y versiones Android distintas de la probada.
- El timbre de Capri en español; no se ha incorporado un modelo de voz específico ni Kokoro.

No se incorpora observación de pantalla ni automatización de otras apps en esta versión.
