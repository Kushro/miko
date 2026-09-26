# Estado del proyecto

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Miko implementa parsers integrados, presets y mejoras por fuente, categorías con
ajustes independientes, etiquetas/puntuaciones locales, controles de descarga,
mejora de imágenes, Momentos, navegación configurable, recomendaciones y mapeo de
favoritos de E-Hentai.

## Verificación

El repositorio incluye pruebas JVM, comprobaciones SQL y controles de formato y
compilación Gradle. Consulta comandos en [Contribuciones](../../../CONTRIBUTING.md)
(en inglés). Compilar correctamente no demuestra que funcionen todos los sitios o dispositivos.

Faltan comprobaciones en dispositivos de capturas, zoom/páginas dobles, restauración
con capturas, restricciones de descarga y rendimiento de fuentes. Cada guía indica
sus limitaciones. Un APK local anterior no demuestra el estado de una compilación nueva.

## Trabajo pendiente

- Probar parsers de red, requisitos WebView y mejoras por sitio.
- Revisar ajustes globales heredados dentro de categorías con configuración propia.
- Probar captura/renderizado/compresión de Momentos en Android y la alternativa de vista previa CBZ.
- Evaluar recomendaciones con títulos largos, idiomas distintos y trackers externos.
- Verificar secretos, integraciones y licencias nativas antes de distribuir.

Para publicar, consulta [Compilación y firma](../release/es.md).
