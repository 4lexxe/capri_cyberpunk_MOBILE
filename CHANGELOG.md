# Cambios

## 1.0.0 — publicación inicial (código interno 10)

- Nombre público 1.0.0 y código interno superior a las pruebas anteriores, para poder actualizar sin desinstalar.
- APK completo con pesos Qwen verificados y voz latina preconfigurada.
- Chat de voz por turnos desde el botón central flotante, con servicio de micrófono, notificación y detención explícita. No abre el chat; pausa la escucha durante la respuesta.
- Mascota de 100 dp por defecto; conserva los tamaños guardados por el usuario al actualizar.
- Capturas manuales por defecto y manifest sin Accesibilidad en la distribución pública.
- README visual con descarga directa, imágenes, instalación, configuración y apoyo con estrellas.

Las secciones siguientes documentan compilaciones de prueba previas a la publicación.

## 1.0.1-manual

- Variante optativa con una captura por acción del usuario, autorización nueva de Android en cada ocasión y cierre de MediaProjection antes del envío.
- Botón Tomar captura en Entorno y en el menú flotante. Olvidar captura cancela el análisis y descarta el contexto visual.
- Manifest sin servicio de Accesibilidad. Sin temporizador ni capturas automáticas en esta variante.
- Se conserva la edición automática 1.0.0 y el historial de firmas. Compilar la manual con `-PmanualCapture=true`; código de versión 9.

## 1.0.0

Corrección de distribución: APK Signature Scheme v3 y prueba de rotación de la firma de pruebas a la de producción. Probada actualización desde ambas firmas en Android 15, sin desinstalar. El código de la app y los modelos no cambian.

- Capturas puntuales por Accesibilidad, sin MediaProjection, flujo de video ni servicio de grabación. Sesiones optativas, una imagen al salir de Capri y después cada tres minutos; detención al bloquear.
- Edición completa con los modelos Qwen y el perfil latino incluidos, instalados localmente con SHA-256. Voz preconfigurada: ritmo 109%, filtro 52% del control y volumen 73%.
- Variante nativa ARMv8.2 DOTPROD/FP16 seleccionada mediante detección de capacidades, con biblioteca compatible de reserva. Optimización C/C++ O3 efectiva.
- Prompt base de comportamiento para conversación y reacciones. Español latino, respuestas breves, sin apodos inventados.
- Voz simplificada a Android y Qwen, sin importación de archivos. Botón × para cerrar cada globo.
- Archivo visual sin los paneles Origen y Esta versión; licencias conservadas mediante un enlace discreto.
- Firma de distribución, modelos fijados, documentación, CI, flujo de release, sumas y empaquetado del código fuente con exclusiones explícitas de datos privados.

## 0.5.0

Globos temporales, controles flotantes compactos, encuadre sentado, ritmo y efecto robótico ajustables. Informes históricos en docs/VALIDACION_0.5.0.md.
