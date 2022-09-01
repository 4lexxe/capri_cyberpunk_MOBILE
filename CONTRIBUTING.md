# Contribuir

Lee README.md, docs/COMPILAR.md y los AGENTS.md aplicables antes de cambiar el motor nativo. Usa una rama por cambio y mantén los commits centrados en un problema.

Antes de proponer un cambio ejecuta las pruebas unitarias, lint y una compilación. Los cambios de audio necesitan comprobar cancelación, continuidad y duración; los de capturas, consentimiento, frecuencia y cierre de sesión. Incluye el teléfono/emulador y ABI utilizados y distingue mediciones físicas de resultados del emulador.

No incluyas sesiones, claves API, almacenes de firma, capturas privadas, modelos GGUF ni rutas de tu equipo. No cambies las licencias del arte, las fuentes ni las dependencias. Las distribuciones usan el modelo fijado en models.lock.json.

Describe el problema, el comportamiento resultante y la validación. No afirmes tiempo real universal ni mejoras de rendimiento sin mediciones reproducibles.
