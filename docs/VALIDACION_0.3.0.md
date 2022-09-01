# Validación de Capri Android 0.3.0

29 de octubre de 2026. Paquete `com.capri.companion`, código 3. Mantiene Android 8+ para la aplicación, Android 12+ para Qwen y Android 14+ para la observación.

## Entrega

- APK: `artifacts/Capri-Android-0.3.0-debug.apk`, 28.780.184 bytes.
- SHA-256: `a36d4f10e689c331989da475fdd641177205640fa1e717404bce4640399456b3`.
- Firma verificada con apksigner; certificado de depuración idéntico al de 0.2.0: `1bc053afaca9d965f3b54f5578522c94ab7c8bd8eb97fad1c39a3d11fc817895`.
- Actualizable sobre el APK anterior sin desinstalar. La compilación Release con R8 también completó; el APK entregado es Debug firmado.

## Cambios comprobados

Se agregó el proveedor **ChatGPT · cuenta** al chat y a visión. El usuario autoriza su propio registro de Capri mediante el navegador y OAuth con PKCE, state y nonce. El callback escucha únicamente en loopback con plazo de cinco minutos. Se verifica la firma RS256, emisor, audiencia, vencimiento, nonce e identidad previa antes de activar una cuenta. La identidad sin consentimiento para usar el plan se conserva, pero no permite inferencia.

Los registros de cuentas, sus tokens y su host estable se guardan cifrados con Android Keystore, sin exportación a backups. La renovación se serializa y guarda los tokens rotados juntos. Cambiar cuenta, iniciar autorización o cerrar sesión detiene las respuestas y la observación. El cierre intenta revocación remota y distingue si no pudo confirmarla.

El catálogo se obtiene para la cuenta activa y excluye modelos ocultos. La inferencia utiliza el endpoint público Responses con `stream=true`, `store=false`, instrucciones e historial explícitos. No envía `max_output_tokens` ni otros parámetros prohibidos de esta ruta. No hay fallback automático a las claves API. Sólo se acepta una respuesta después de `response.completed`; se descartan cortes, respuestas incompletas y errores tardíos de límites. Se conservan los proveedores por clave y la voz local.

## Pruebas

- **42 pruebas unitarias aprobadas**: 20 anteriores y 22 de ChatGPT. Incluyen el vector PKCE de RFC 7636, validación y rechazo de JWT, callback loopback real con servidor de identidad ficticio, autorización nueva y recurrente, rechazo de otra identidad, permiso de plan faltante, cancelación y reinicio de autorización, rotación serializada, revocación, catálogo, texto e imágenes, contrato HTTP, límites tardíos, cancelación de streaming, redirecciones y ausencia de secretos en mensajes de error.
- **15 pruebas instrumentadas aprobadas** en emulador Android 15/API 35, x86_64: interfaz, superposición, cifrado de claves y sesión OAuth, persistencia, contexto, autorización real de MediaProjection contra análisis ficticio y dos pruebas reales de síntesis/cancelación Qwen con los modelos instalados. La ejecución final por ADB terminó `OK (15 tests)` en 62,534 segundos.
- Compilaciones Debug, APK de pruebas y Release con R8 completadas.
- Lint: **0 errores**, 18 advertencias y 2 sugerencias. Las advertencias corresponden a actualizaciones de dependencias, constructores para herramientas y sugerencias KTX; no son fallos de compilación.

Comandos de compilación: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease`. Ejecución final en Android: instalar los dos APK, preparar los modelos en el almacenamiento privado de la app de prueba y ejecutar `adb shell am instrument -w -r com.capri.companion.test/androidx.test.runner.AndroidJUnitRunner`.

Una ejecución anterior de la prueba de captura falló porque un teléfono recién instalado mostró tarde el diálogo de notificaciones. La prueba ahora espera explícitamente ese permiso antes de comprobar la sesión. La ejecución final incluye ese caso desde la instalación nueva y pasa. La ejecución directa por ADB conserva los modelos; Gradle connected puede desinstalar la app al terminar y eliminarlos.

## Límites pendientes de comprobar

No se inició sesión con una cuenta personal, no se consumió uso real de ChatGPT y no se probó un teléfono físico. Los servidores de identidad, catálogo y Responses son ficticios en las pruebas de conexión. Que las pruebas pasen no confirma que una cuenta determinada sea elegible: **Continue with ChatGPT → Probar conexión** es la comprobación real en el teléfono.

No se validó que cada modelo del catálogo admita imágenes. Qwen fue ejecutado en el emulador; su velocidad, timbre y consumo en el teléfono siguen dependiendo del equipo. La integración de pantalla conserva los permisos explícitos de 0.2.0 y no oye el audio de TikTok u otras apps.
