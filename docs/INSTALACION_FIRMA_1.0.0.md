# Corrección del instalador 1.0.0

Actualización del 29 de octubre: una captura del usuario confirmó que **Google Play Protect bloquea la instalación** en su A55 con Android 16. La migración de firmas documentada aquí no resuelve ese bloqueo. Se conservan las capturas automáticas por elección del usuario y se prepara una revisión de Google. Ver [diagnóstico y revisión](PLAY_PROTECT.md). La distribución aún no tiene aprobación de Play Protect.

El usuario informó que Android rechazó el archivo de actualización con el mensaje genérico “no se pudo instalar”. Ese texto no permite confirmar la causa en su teléfono. La inspección local verificó que el APK original de actualización tenía el certificado anterior, versión superior, firma v2 válida y alineación correcta. También se reprodujo un rechazo `INSTALL_FAILED_UPDATE_INCOMPATIBLE` al mezclar la edición de producción y el actualizador firmado con la clave de pruebas.

La distribución corregida incorpora APK Signature Scheme v3 y una prueba de rotación desde la firma de pruebas a la de producción. `signing.lineage` contiene certificados públicos y la prueba criptográfica; no contiene claves privadas. Se firmó con ambos almacenes usando apksigner y `--rotation-min-sdk-version 28`.

- `Capri-Android-1.0.0-instalar.apk`: actualización de aproximadamente 26 MB para instalaciones con los modelos locales existentes.
- `Capri-Android-1.0.0-completa-migracion.apk`: instalación completa con modelos y la misma migración de firma.
- Los archivos anteriores se conservan como referencia; deben preferirse los corregidos para nuevas entregas.

## Pruebas realizadas

En el emulador API 35 se instaló el actualizador corregido sobre la producción 1.0.0: `Success`. Después se reinstaló únicamente en el emulador la antigua 0.5.0, y el actualizador corrigido también devolvió `Success` sin desinstalar entre esas dos versiones. El UID permaneció en 10160 antes y después de actualizar desde 0.5.0, y MainActivity abrió con `Status: ok`.

Se creó un marcador privado antes de actualizar, pero no se afirmó leerlo después: la versión de producción impide `run-as` y la lectura directa desde adb shell. No se probaron credenciales reales. La migración usa la capacidad instalada de Android para conservar los datos; falta confirmar el resultado en el teléfono del usuario.

apksigner verificó v2 para API 26–27 y v3 para API 28+. En Android 9+ la clave seleccionada es la de producción y se acepta el historial anterior. Android 8 no implementa rotación v3: utiliza el firmante v2 anterior, por lo que no se promete actualizar una instalación de Android 8 que ya estuviera firmada sólo con la clave nueva. Qwen requiere Android 12+.

Zipalign con páginas de 16 KB pasó para ambos APK corregidos. Los archivos de código, recursos y bibliotecas son los mismos que los paquetes previamente validados: sólo cambia el bloque de firma. No se modifica la configuración ni el contenido de los modelos.

## Distribución futura

Ejecutar `scripts/sign-release.ps1` después de Gradle o usar el flujo manual de GitHub actualizado. Conservar privadamente `signing/capri-legacy.keystore`, `signing/capri-release.p12` y `signing.properties`. Publicar `signing.lineage` con los fuentes. CI necesita `CAPRI_LEGACY_KEYSTORE_BASE64` además de los secretos de producción; la contraseña del almacén anterior puede suministrarse mediante `CAPRI_LEGACY_STORE_PASSWORD`.

La captura posterior identifica Play Protect como causa del intento mostrado. La instalación del APK corregido sigue pendiente en el teléfono; no se presenta la prueba del emulador como solución al bloqueo de Google. No se recomienda desinstalar la app como primer paso, porque borraría sus datos privados y modelos.
