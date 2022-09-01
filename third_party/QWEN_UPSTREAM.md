# Procedencia del motor de voz local

- App de referencia: https://github.com/Danmoreng/qwen3-tts-android
- Commit: `35738304b31c425cc08fb5405372fedfd6531b3b`.
- Motor C++: https://github.com/Danmoreng/qwen3-tts.cpp
- Commit del motor: `4562731dc612cb87b4cd1eedac275a17d1078773`.
- GGML: https://github.com/ggml-org/ggml
- Commit GGML: `eced84c86f8b012c752c016f7fe789adea168e1e`.

Se incorporan los fuentes del motor y GGML, el wrapper Kotlin `com.qwen.tts.studio.engine.QwenEngine` y la configuración Android CMake. Se preservan los nombres JNI originales. Se adaptó la ruta CMake al árbol de Capri y se añadieron cancelación atómica y reinicio de cancelación en la frontera JNI/C/C++ y en el callback de generación. El audio cancelado no se reproduce; una operación de carga o decodificación que ya comenzó puede tardar en terminar.

La compilación usa CPU para ARM64 y x86_64, sin servicios remotos, CUDA ni dependencia de una app externa. No se habilitan Vulkan u OpenCL en esta versión. El APK de depuración conserva optimización de C++.

Modelo: https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF

Revisión de descarga: `b7ee2e8c7459c3bea99da23e3d178125a7d1713c`.

| Archivo | Bytes | SHA-256 |
| --- | --- | --- |
| qwen-talker-0.6b-base-Q4_K_M.gguf | 628905056 | 4b468ec7b1f62b90ef4ca316c0aa57deadfd54b2cf9651703ea753cedaf04226 |
| qwen-tokenizer-12hz-Q4_K_M.gguf | 254974752 | cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039 |

Los pesos se descargan bajo control del usuario; no se incorporan al repositorio ni al APK. La app verifica tamaño y SHA-256 antes de publicar cada archivo descargado.

Los modelos Qwen conservan Apache 2.0. El código de las implementaciones nativas y GGML conserva sus licencias MIT. Los textos se incluyen en `app/src/main/assets/licenses/` y en el visor de licencias de Capri.

Hasta 0.3.x, el perfil inicial se extraía de `hello-morning.wav`, `hello-late.wav` y `hello-miss.wav`, copiados de Capri Desktop. Desde 0.4.0 se usan las referencias suministradas por el usuario en `VOZ_CAPRI`: `cut-CAPRI_VOZ2.wav` (SHA-256 `32595002616bcb583a095e5df7395ae4d59bca1fbb6414c58bab857c34738384`) y `cut-CAPRI_VOZ_2.wav` (SHA-256 `0016c7d714800a46dda010dadba5cdf7bc66372e3e86da3c1261cdcb5ced21aa`). Se copian sin cambios como `voice/capri-latino-1.wav` y `voice/capri-latino-2.wav`. Su origen externo no se ha verificado y no se presentan como material del repositorio original.

La normalización de referencias produce mono PCM16 a 24 kHz. El perfil combinado precalculado `voice/profiles/8e3135c85f730c031690691f.json` fue extraído con el motor y modelos de esta tabla. Se reutiliza únicamente para la referencia cuyo hash coincide. Los WAV importados generan su propio perfil local.

En 0.4.0 se añadió el puente JNI `nativeSynthesizeCapriStreaming`, usando la API C de streaming ya incluida en upstream. Envía PCM incremental con callbacks síncronos cancelables; `collect_audio=false` evita acumular todo el audio nativo. Se conserva sin cambios la API JNI de streaming con metadatos de upstream. No se afirma equivalencia con una interpretación oficial del personaje ni tiempo real continuo en cualquier teléfono.

La estructura de parámetros de streaming C/C++ añade `continuation_chunk_sec`, con cero como comportamiento previo. El puente Capri pide un primer bloque de 0,32 s y bloques posteriores de 1,28 s. En Android con CPU se conserva el caché del grafo del decodificador entre bloques; las rutas de otros sistemas o de Vulkan/OpenCL conservan su limpieza previa. Esta extensión del ABI C se compila junto con sus consumidores dentro del APK.

Desde 0.4.1, el reproductor normal usa la síntesis de onda completa y AudioTrack MODE_STATIC precargado. La API de streaming permanece para pruebas. Esto evita que una CPU lenta vacíe el audio mientras se espera otro bloque y evita las uniones por ventanas de esa ruta. El último resultado completo puede reutilizarse en memoria para repetir la misma frase y perfil.


## Distribución 1.0.0

La edición completa incorpora los pesos fijados arriba en assets/qwen-bundle mediante -PbundleQwen=true. La preparación inicial copia cada archivo a almacenamiento privado y verifica tamaño y SHA-256; no requiere una descarga en el teléfono. Los GGUF siguen excluidos del repositorio y del archivo de código fuente. La edición de actualización sin pesos reutiliza los modelos locales existentes.

El perfil de referencia queda fijo en la combinación latina precalculada. La interfaz pública sólo ofrece Android y Qwen; no expone importación de WAV ni selección de referencias. Los valores iniciales se fijan en 1,09x de reproducción, mezcla metálica 0,182 (52% del recorrido) y volumen 0,73.

La compilación agrega una biblioteca arm64-v8a alternativa, qwen3_tts_jni_fast, con -march=armv8.2-a+dotprod+fp16. El detector JNI capri_cpu_features usa getauxval(AT_HWCAP) y exige ASIMDDP y ASIMDHP antes de cargarla. Si faltan capacidades o falla su carga se carga la biblioteca genérica. Sólo se carga una implementación Qwen en cada proceso. Todos los targets propios se enlazan con alineación ELF de 16 KB. Se conserva O3 también en RelWithDebInfo, evitando que un O2 posterior lo sustituya. El procesamiento de voz no baja la frecuencia de salida del códec ni modifica sus pesos.

El soporte optimizado ARM está compilado y protegido por detección de hardware; la ejecución de esta variante y la mejora exacta necesitan medición en un teléfono ARM compatible. Las pruebas de emulador ejercitan la biblioteca x86_64 compatible. No se afirma tiempo real universal.
