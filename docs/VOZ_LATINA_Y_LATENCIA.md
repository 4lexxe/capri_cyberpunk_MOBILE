# Capri 0.4.1 · Referencias de voz latina y reproducción continua

## Usar tus audios

En **Mainframe → Voz → Qwen** hay tres opciones:

- **Ambos**, predeterminada: fragmentos de los dos WAV entregados en `VOZ_CAPRI`.
- **Audio 1**: `cut-CAPRI_VOZ2.wav`, 18,37 segundos en origen.
- **Audio 2**: `cut-CAPRI_VOZ_2.wav`, 11,43 segundos en origen.

Las referencias originales son estéreo PCM16 a 44,1 kHz. Capri las convierte a mono PCM16 a 24 kHz, recorta silencio de los extremos, limita la duración y ajusta el nivel. Usa hasta 12 segundos por audio individual; el perfil combinado utiliza aproximadamente seis segundos de cada uno. El perfil combinado viene calculado con los mismos modelos Qwen 0.6B que descarga Capri. Así evita esa extracción en el primer uso.

**Cargar mi voz WAV** abre el selector de Android. Acepta PCM de 16, 24 o 32 bits o float32, uno o dos canales, entre 8 y 96 kHz, hasta 30 MB. Se prepara un fragmento de hasta 12 segundos. La referencia debe contener al menos dos segundos de voz audible; conviene voz limpia, sin música ni otras personas. MP3 y M4A deben convertirse a WAV antes de importarlos. **Mi voz** permite volver al último WAV importado.

La importación se procesa localmente. Los audios no se envían a ChatGPT. El WAV convertido y sus perfiles quedan en almacenamiento privado, excluido de backups. Cada contenido tiene su propio perfil: los perfiles ingleses de 0.3.x no se reutilizan por accidente.

Qwen usa la referencia para aproximar el timbre. El idioma de generación se fija en español y el texto de Capri se pide en español latino. El modelo no ofrece un selector garantizado de acento latino; el parecido y el acento deben evaluarse escuchando el resultado.

## Reducir la espera

1. Instalar 0.4.1 sobre la versión anterior; los modelos descargados se conservan.
2. Elegir **Qwen** y la referencia deseada.
3. Tocar **Preparar voz** y esperar a que quede disponible. Al guardar Qwen o abrir Capri con Qwen seleccionado también se adelanta la carga.
4. Tocar **Probar voz**. Capri prepara la frase completa y carga todo el PCM antes de empezar. Durante esa preparación aparece **PREPARANDO VOZ**; al hablar ya no depende de que el generador alcance al reproductor.

El motor y los perfiles se reutilizan entre frases. Se conserva un único audio en memoria para repetir la última frase sin recalcularla, siempre que coincidan texto, perfil y presupuesto de generación. El caché no sobrevive al cierre del proceso ni se exporta. Se emplean hasta cuatro núcleos disponibles. **Detener** corta la reproducción o la preparación pendiente y evita audio tardío. Cambiar de referencia corta la voz anterior.

El panel muestra cuánto tardó la preparación completa o indica que el último audio está listo para repetirse. La precarga adelanta la carga del modelo y del perfil; una frase nueva todavía debe generarse.

En 0.4.0 se empezaba con sólo 320 ms de audio. Si Qwen tardaba más en producir el siguiente bloque, el reproductor se quedaba sin muestras y aparecían silencios. El vaciado entre bloques fue una consecuencia del intento de empezar antes. 0.4.1 prioriza continuidad y usa el vocoder de frase completa, evitando también sus uniones por ventanas. La API nativa de streaming permanece disponible para pruebas, pero ya no es la ruta normal de voz.

No se promete tiempo real continuo en todos los teléfonos. Ocho GB de RAM ayudan a mantener el modelo cargado, pero la velocidad depende del procesador y sus núcleos. La medición en emulador no sustituye la del teléfono. La voz Android sigue disponible como alternativa rápida; no usa el clon Qwen.

## Pantalla cada tres minutos

En **Entorno**, habilitar visión, mantener **Usar la conexión del cerebro** si se quiere usar ChatGPT, activar **Comentarios cada 3 minutos**, guardar y compartir una app mediante el diálogo de Android. Una captura inicial establece el contexto y las siguientes se envían cada tres minutos mientras la sesión siga activa y visible. No se requiere contestar cada dos comentarios para mantener la sesión.

Si el modelo detecta contenido privado o no puede identificar lo visible con suficiente confianza, no comenta. Tampoco interrumpe el micrófono o una respuesta. **Detener observación** corta las capturas; al bloquear el teléfono se termina la sesión. Cada análisis remoto consume uso del proveedor o plan elegido.
