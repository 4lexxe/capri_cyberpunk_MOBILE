# Compilar y distribuir Capri Android 1.0.0

## Herramientas

JDK 17+ compatible con Gradle 8.14.3, Python 3.11+, Android SDK 36, build-tools 35.0.0, NDK 27.2.12479018 y CMake 3.22.1. Los fuentes C++, GGML y el adaptador JNI están incluidos. Configura ANDROID_HOME o un local.properties privado.

La versión pública se muestra como 1.0.0 y usa código interno 10, superior a las pruebas 1.0.0/1.0.1-manual. Las capturas manuales son el valor por defecto. No hace falta pasar -PmanualCapture=true.

## Pruebas y compilación liviana

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

En Linux/macOS usa `bash ./gradlew`. No se necesitan claves de IA para compilar ni ejecutar las pruebas con servidores locales. El APK sin pesos sirve para desarrollo, para actualizar una instalación con modelos existentes o para usar voz Android. No debe presentarse como edición completa.

Las pruebas instrumentadas se compilan con `:app:assembleDebugAndroidTest` y se ejecutan sobre Debug. Incluyen captura manual y un servicio de reconocimiento ficticio para probar voz flotante sin grabar audio ni contactar un proveedor externo. El APK de pruebas no se distribuye.

## Edición completa con Qwen

```powershell
python scripts/fetch-qwen.py
./gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease -PbundleQwen=true
```

Los pesos se fijan por repositorio, revisión, bytes y SHA-256 en models.lock.json. Para usar modelos verificados en otro disco añade `-PqwenModelsDir=RUTA_ABSOLUTA`. Los dos GGUF suman unos 884 MB y quedan dentro del APK completo sin compresión. Reserva cerca de 2 GB para instalarlo y preparar la voz.

Se incluyen ARM64 compatible, ARM64 DOTPROD/FP16 y x86_64. La biblioteca rápida sólo se selecciona si el procesador soporta sus instrucciones. Las bibliotecas y el APK deben conservar alineación de 16 KB.

En equipos con memoria limitada se puede añadir:

```powershell
./gradlew.bat :app:assembleRelease -PbundleQwen=true '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' --max-workers=2 --no-daemon
```

Para evitar ocupar el disco del proyecto, `-PcapriBuildDir=RUTA_ABSOLUTA` mueve los intermedios y las salidas del módulo app. El empaquetado de los pesos requiere espacio temporal adicional.

## Firma de esta distribución

Los almacenes privados originales, signing.properties y los modelos están excluidos de Git. No se publican ni se deben enviar por issues. signing.lineage sólo contiene el historial público de certificados y pruebas de rotación.

Para actualizar las instalaciones existentes, el mantenedor restaura sus dos almacenes originales y signing.properties y ejecuta:

```powershell
./scripts/sign-release.ps1 -InputApk app/build/outputs/apk/release/app-release.apk -OutputApk artifacts/Capri-Android-1.0.0-completa.apk -SdkPath RUTA_SDK
```

El helper conserva v2 y v3 y verifica la salida. No genera otra identidad de firma. Antes de distribuir verifica `apksigner verify`, `zipalign -c -P 16 -v 4` y los hashes.

Un fork que use sus propias claves **no debe usar el historial de Capri para firmar**. Puede crear un almacén propio con scripts/init-signing.ps1, compilar con su signing.properties y verificar el APK producido por Gradle. No podrá actualizar una instalación firmada por este proyecto; usa tu propio applicationId si deseas instalar ambas apps.

## Fuentes y CI

```powershell
python scripts/package-source.py --output artifacts/Capri-Android-1.0.0-source.zip
```

El script usa una lista explícita y excluye almacenes, credenciales, adjuntos, pesos, builds y borradores. Los modelos se entregan como parte del APK de Releases y se descargan al compilar; no se suben a Git.

La CI normal compila y comprueba la variante manual sin pesos. El workflow manual release.yml prepara la edición completa con los secretos CAPRI_KEYSTORE_BASE64, CAPRI_STORE_PASSWORD, CAPRI_KEY_ALIAS, CAPRI_KEY_PASSWORD y CAPRI_LEGACY_KEYSTORE_BASE64; CAPRI_LEGACY_STORE_PASSWORD es opcional. Produce artefactos y no publica versiones automáticamente.

Los informes previos describen builds de prueba. La referencia de la distribución actual es [VALIDACION_PUBLICA_1.0.0.md](VALIDACION_PUBLICA_1.0.0.md).
