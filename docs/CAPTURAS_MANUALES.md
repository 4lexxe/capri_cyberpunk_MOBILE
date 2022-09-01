# Capturas manuales · Capri Android 1.0.0

La distribución pública usa capturas por acción del usuario y elimina del manifest el servicio y permiso de enlace de Accesibilidad. Es la configuración por defecto. La variante de prueba 1.0.1-manual es un antecedente histórico; la release pública se llama 1.0.0 y usa código interno 10.

## Uso

1. Configura y guarda un proveedor/modelo con visión en Entorno. La captura se envía a ese proveedor.
2. Pulsa **Tomar captura** en Entorno o mantén pulsada la mascota y elige **Tomar captura**.
3. Lee el aviso de envío de una imagen y pulsa Elegir pantalla. Android presenta su autorización cada vez. En Android 14+ puedes seleccionar una app o toda la pantalla; en versiones anteriores, toda la pantalla.
4. Tras autorizar, Capri obtiene una sola imagen. La notificación y el indicador de captura de Android pueden aparecer brevemente: la proyección, el lector y la superficie se liberan antes de codificar y enviar el JPEG. No se guarda un video ni se mantiene una sesión para futuras imágenes.
5. Capri analiza la imagen y puede comentarla si no está ocupada y el contenido lo permite. El contexto visual caduca a los dos minutos. **Olvidar captura** cancela el análisis pendiente y descarta ese contexto; no puede retirar una imagen ya recibida por el proveedor.

Cancelar cualquiera de los dos avisos no inicia captura ni envío. No hay temporizador, capturas al cambiar de app ni capturas al desbloquear. No se reutilizan tokens de consentimiento. Una captura expira a los diez segundos si Android no entrega la imagen. Un cambio de conexión mientras se autoriza o captura invalida la operación. Las pantallas protegidas siguen sujetas a las restricciones de Android.

La imagen puede contener datos personales visibles. Se envía como JPEG, máximo 960 píxeles, al proveedor elegido. No se guarda un archivo de captura en Capri. Las políticas de resúmenes opcionales, retención, historial y claves se describen en [privacidad](PRIVACIDAD.md). Olvidar el contexto no borra resúmenes que ya hayas elegido guardar; puedes borrarlos desde Entorno.

## Compilación y firma

```powershell
./gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
./scripts/sign-release.ps1 -InputApk app/build/outputs/apk/release/app-release.apk -OutputApk artifacts/Capri-Android-1.0.0-actualizacion.apk -SdkPath RUTA_SDK
```

Para una instalación completa añade `-PbundleQwen=true -PqwenModelsDir=RUTA_MODELOS` al ensamblado. La actualización pequeña usa los modelos ya instalados o la voz Android. El paquete sigue siendo `com.capri.companion`, con código 10, y requiere las claves originales y `signing.lineage` para actualizar las ediciones anteriores.

La variante manual elimina el permiso asociado al bloqueo reportado, pero no está aprobada por Google ni se garantiza que Play Protect la acepte. Las pruebas de emulador no sustituyen la instalación en el Galaxy A55 con Android 16. Véase [estado de la edición automática](PLAY_PROTECT.md).

La API y la autorización por sesión se documentan en [MediaProjection de Android](https://developer.android.com/media/grow/media-projection).

El detalle de las pruebas, sus límites y los hashes de los APK están en [validación de esta edición](VALIDACION_PUBLICA_1.0.0.md).
