# Fuentes

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Miko lee desde extensiones instaladas y parsers Kotatsu integrados. La disponibilidad
y las funciones dependen de cada sitio; que una fuente aparezca no garantiza que funcione.

## Elegir y organizar fuentes

1. En **Ajustes → Explorar**, elige extensiones, parsers integrados o ambos.
2. Abre Fuentes. Usa sus ajustes de visualización para agrupar, ordenar o alternar entre lista y cuadrícula.
3. Mantén pulsada una fuente para configurarla, asignarle categorías o consultar su información.
4. Activa una categoría de fuentes desde el menú de presets. Desactívala para recuperar todas las fuentes.

Un preset filtra Fuentes y la búsqueda global, no la biblioteca ni las descargas.
En la búsqueda global, **Ancladas** y **Todas** respetan el preset activo. Elegir
el chip de otro preset reemplaza esa restricción. Renombrar o borrar un preset
actualiza la selección guardada; un preset de búsqueda inexistente vuelve a Todas.

## Tipos y capacidades

| Tipo | Significado |
|---|---|
| `LOCAL` | Archivos de la fuente local. |
| `BUILT_IN` | Un parser integrado. |
| `BUILT_IN_DEDICATED` | Una fuente propia de la app o un parser integrado con una mejora asociada. |
| `EXTENSION` | Una fuente de extensión. |
| `EXTENSION_ENHANCED` | Una extensión con un delegado o una mejora asociada. |
| `NOT_INSTALLED` | Una fuente ausente o provisional. |

Las insignias aparecen al explorar, en portadas, búsquedas, detalles y migraciones.
Las capacidades describen operaciones como búsqueda, etiquetas, inicio de sesión,
dominios alternativos y comentarios. Se detectan mediante interfaces, metadatos y
filtros, sin probar el sitio. La clasificación prioriza los identificadores locales
y propios, las fuentes ausentes y los parsers integrados antes que las extensiones.

## Mejoras por sitio y confianza de extensiones

**Ajustes → Keiyoushi** y **Proveedores embebidos** ofrecen interruptores por sitio,
activados por defecto. Asura Scans aporta puntuaciones y comentarios de series y
capítulos. Comix aporta comentarios con paginación por cursor, respuestas aplanadas
y conversión de HTML. Usa Comentarios en el detalle o el botón de chat del lector
cuando esté disponible. Desactivar un grupo elimina sus capacidades adicionales,
puntuación y acciones de comentarios e invalida la caché de capacidades.

**Confiar en todas**, en Extensiones, procesa las extensiones pendientes mediante
la operación de confianza individual existente. Es una acción puntual, no una
preferencia que confíe automáticamente en instalaciones futuras.

## Referencia técnica

- `source-kotatsu/` adapta `kotatsu-parsers-redo` mediante `KotatsuParserSource : HttpSource`.
  `AndroidSourceManager` registra fuentes según `SourcePreferences.sourceMode()`.
  `KotatsuLoaderContext` proporciona red, cookies, configuración y utilidades WebView.
- Conserva los identificadores de parsers: la biblioteca y las copias de seguridad
  los utilizan. Conserva los paquetes de la dependencia y `libs.kotatsuParsers`.
- El mapeo conserva URLs originales. La conversión base a `SManga` no conserva títulos
  alternativos, clasificación de contenido por manga ni puntuaciones; las ramas de
  capítulos pasan a grupos de traducción y los volúmenes al nombre del capítulo.
  Completado y publicación finalizada comparten estado. Las puntuaciones se consultan aparte.
- `KotatsuDedicatedParsers` selecciona las fábricas de adaptadores dedicados. Registra
  un `SourceEnhancement` asociado cuando corresponda mostrar una insignia dedicada.
- `classifySourceKind` es pura. Resuelve `SourceCapabilities` mediante
  `SourceCapabilitiesCache` fuera de la composición; no detectes capacidades desde un composable.
- `SourceEnhancementRegistry` expone `RemoteRatingProvider` y `SourceCommentsProvider`.
  `EnhancementPreferences.groupEnabled()` controla las consultas aplicables del registro.
- Los presets reutilizan `sources_tab_categories` y `sources_tab_source_categories`.
  `active_source_preset` guarda la selección; `global_search_pinned_toggle_state`
  guarda `SourceFilter`, incluido `Preset(name)`.
- Las pruebas cubren clasificación, capacidades, agrupación, presets, interruptores
  y respuestas de sitios en `app/src/test/` y `source-kotatsu/src/test/`.

## Limitaciones

Algunos parsers requieren interceptores WebView no compatibles. La integración no
incluye el solucionador automático de Cloudflare de Kotatsu. Los iconos son compartidos
y falta medir en dispositivos el rendimiento de listas grandes. La pertenencia a un
preset se actualiza al buscar, no continuamente en una búsqueda abierta. Los cambios
de API de un sitio pueden romper sus mejoras independientemente de la extensión.
