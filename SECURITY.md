# Seguridad y datos privados

No publiques tokens, contraseñas, claves API, signing.properties ni los archivos de signing/. Las claves del usuario se guardan cifradas con Android Keystore y no forman parte del código ni de los paquetes de distribución.

Antes de reportar un problema, elimina datos privados de registros y capturas. Para un problema sensible utiliza el canal privado del mantenedor del repositorio donde obtuviste esta copia; este código fuente no configura un buzón externo.

La release pública usa capturas manuales con autorización de Android por imagen y no declara Accesibilidad. Las imágenes se envían al proveedor configurado por el usuario y no se guardan en disco. El chat de voz flotante usa un servicio de micrófono que inicia el usuario, con notificación y controles para detenerlo; el reconocimiento puede usar un proveedor de Android en red. Ver docs/PRIVACIDAD.md.
