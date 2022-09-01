# Usar Capri contextual 0.5.0

## Preparar cerebro y voz

En Mainframe, elegir **ChatGPT · cuenta**, tocar **Continue with ChatGPT**, autorizar la cuenta en el navegador y volver a Capri. Elegir uno de los modelos que devuelve la cuenta, probar la conexión y guardar al final de la pantalla. Esta opción usa un plan ChatGPT elegible y no pide clave API. Ver [conexión y límites](CONECTAR_CHATGPT.md).

También se puede elegir **Gemini**, **Claude**, **OpenAI** o **Compatible**, indicar el ID de modelo y la clave propia, y guardar. Cambiar de proveedor limpia los campos de clave y modelo en el formulario para no reutilizarlos por accidente con otro servicio.

Elegir **Qwen** en Voz. **Descargar** obtiene 884 MB de modelos y verifica su integridad. **Probar voz** prepara el perfil y genera audio español en el teléfono. Qwen requiere Android 12 o posterior y recursos suficientes. La primera carga puede tardar; **Detener** cancela la espera/reproducción y evita que salga audio tardío. Una fase nativa ya iniciada puede tardar en liberar sus recursos.

La referencia predeterminada usa ambos WAV suministrados en `VOZ_CAPRI`, en español latino. Se puede elegir **Audio 1**, **Audio 2** o **Cargar mi voz WAV**. Qwen reutiliza el perfil calculado y prepara una frase completa antes de reproducir, para evitar silencios entre bloques. **Preparar voz** adelanta la carga del modelo; repetir la última frase reutiliza el audio en memoria. Los modelos existentes se conservan al actualizar. Ver [voz y rendimiento](VOZ_LATINA_Y_LATENCIA.md).

## Activar contexto de pantalla

En **Entorno**, habilitar visión y **Usar la conexión del cerebro** para reutilizar la cuenta ChatGPT o las credenciales configuradas. También se puede configurar otro proveedor. El modelo visual debe admitir imágenes; aparecer en el catálogo no garantiza esa capacidad. Guardar los ajustes antes de comenzar. Con ChatGPT, cada análisis visual consume uso del mismo plan.

Activar **Comentarios cada 3 minutos** para que Capri intervenga periódicamente. **Comentar pantalla ahora**, en las opciones flotantes, permite pedir una reacción concreta entre los análisis automáticos.

Activar **Recordar resúmenes de actividades** sólo si se desean recuerdos entre sesiones. Por defecto está apagado. Los resúmenes opcionales duran hasta 7 días, se revisan al abrir Entorno y pueden borrarse. No constituyen un historial completo del teléfono: sólo cubren las sesiones observadas.

Para tener a Capri a la vista mientras se usa otra aplicación, tocar **Mostrar Capri flotante** y conceder el permiso de superposición. Después, tocar **Compartir pantalla**, leer el aviso de envío al proveedor y aceptar la autorización de Android.

Elegir **Una sola aplicación** y seleccionar, por ejemplo, TikTok. Si se elige **Pantalla completa**, la captura puede abarcar otras apps y notificaciones; el aviso también informa ese alcance. El diálogo lo controla Android. Esta versión no usa AccessibilityService ni el historial de uso para vigilar cambios de app.

## Durante la sesión

- Capri analiza un cuadro al comenzar y luego con un intervalo mínimo de tres minutos, aunque no haya cambios. La siguiente imagen depende de que la app compartida esté visible y Android entregue un cuadro válido. No guarda un video ni las capturas.
- El texto e imagen enviados van al proveedor configurado. Su tratamiento y retención externos dependen de ese proveedor.
- **Comentar pantalla ahora**, al mantener pulsada la mascota, pide analizar el próximo cuadro disponible sin salir de la app compartida. Hay un intervalo mínimo de 10 segundos entre solicitudes manuales.
- Los comentarios automáticos siguen la cadencia de tres minutos, con hasta 20 por hora, y no requieren contestar para continuar. No repiten exactamente la última frase. Si no hay contenido claro o apropiado, o estás escribiendo/hablando o preparando otra voz, Capri espera. No se acumulan comentarios atrasados.
- El resumen reciente caduca a los 2 minutos. No interpreta el audio de videos ni conoce contenido que no haya recibido.
- La ventana de Capri se protege durante la observación para evitar capturar sus claves y ajustes. Otras apps también pueden impedir capturas; en ese caso Capri no puede describirlas.
- Compartiendo una sola app, el análisis se pausa cuando Android informa que ese contenido no está visible.

## Detener y borrar

**Pausar comentarios** sólo silencia la iniciativa; el análisis visual continúa. **Detener observación**, desde Entorno, el personaje o la notificación, termina la sesión, cancela análisis pendientes e invalida el contexto reciente.

Bloquear el teléfono también termina la sesión. Para volver a observar hay que autorizar una nueva. Cambiar el proveedor o modelo visual durante una sesión la detiene para solicitar de nuevo el consentimiento sobre el destino de las imágenes.

Ocultar/detener el personaje flotante también detiene la observación. Los resúmenes opcionales ya guardados permanecen hasta que se borren o caduquen. **Borrar recuerdos** elimina esos resúmenes locales; no borra datos que ya se hayan enviado a un proveedor externo.


## Mascota flotante compacta

- Al flotar se ve sólo Capri; no hay filas de botones permanentes.
- Cada respuesta nueva aparece en un globo angular junto a su cabeza. No aparecen avisos como “Ajustes guardados” ni se vuelve a mostrar el historial al iniciar la mascota.
- El globo permanece entre 6,5 y 20 segundos según su longitud. Se mantiene mientras se prepara o reproduce voz; al terminar deja al menos 2,5 segundos para leer. Los mensajes largos se recortan visualmente a cinco líneas; tocar el globo abre el chat completo.
- Un toque en Capri muestra los iconos de escribir, hablar y opciones. Se ocultan después de cinco segundos.
- Mantener pulsado abre el menú: sentarse/levantarse, detener voz si está activa, acciones de pantalla durante una sesión y ocultar Capri. El menú se oculta después de doce segundos.
- Arrastrarla cierra las opciones, conserva su ajuste al borde y mueve el globo con ella. Las ventanas de mensaje y controles son independientes: al abrirlas no desplazan al personaje.
- La pose sentada usa un encuadre ajustado a la zona visible del sprite, sin el amplio margen transparente del atlas original.

## Ritmo y efecto Qwen

En Mainframe → Voz → Qwen, **Ritmo alegre** ajusta la reproducción entre 100 y 120%; por defecto es 108%. Eleva también un poco el tono. **Filtro robótico suave** mezcla una modulación metálica: por defecto usa un 12% de mezcla (34% del recorrido del control); en cero no aplica el filtro. Probar voz permite escuchar los valores del formulario; guardar los conserva para las siguientes respuestas.

El procesamiento conserva los WAV y el PCM original del caché. No acelera la generación, no vuelve a activar el streaming lento ni entrena un nuevo acento. La personalidad y el texto se orientan al español latino; la pronunciación también depende del modelo y la referencia.
