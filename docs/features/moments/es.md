# Momentos

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Momentos reúne páginas guardadas y capítulos marcados. Algunos nombres de clases y
claves internas usan `Favorites`; identifican esta misma función.

## Guardar y volver a páginas

1. Mantén pulsada una página del lector y elige marcarla, o usa el botón de marcador
   de página de la barra. En modo doble página, la hoja de acciones usa la primera.
2. Abre Momentos → Páginas. Pulsa una entrada para volver a esa página en el lector.
3. Mantén pulsada una entrada para abrir su vista previa completa. Amplía, edita la
   nota, guarda, comparte o elimina. Borrar todos los marcadores requiere confirmación.

Las notas aparecen debajo de cada página. El diálogo de visibilidad filtra fuentes
mediante una selección propia de la pantalla y presets compartidos. La mascota Miko
opcional reacciona a carga, errores y acciones; puedes desactivarla en Apariencia.
La nota sigue disponible con la mascota desactivada.

## Tipos de marcador de capítulo

Los tipos son genérico, giro argumental, evolución de personaje y arte precioso.
Cambia el tipo desde listas de capítulos, selección múltiple o el menú del lector.
El icono incluye el símbolo del tipo y una etiqueta al mantenerlo pulsado. Quitar el
marcador elimina su tipo guardado. La pestaña Capítulos filtra por tipo; el filtro
no persiste y no hay fecha de marcado para ordenar cronológicamente.

## Posiciones, capturas y copias de seguridad

| Dato | Significado |
|---|---|
| `pageIndex` | Índice desde cero dentro del capítulo; la interfaz numera desde uno. |
| `scroll_fraction` | Posición de restauración del lector, de 0 a 1 de la altura (`52.sqm`). |
| `focus_fraction` | Foco de miniatura/vista previa (`53.sqm`); no cambia la restauración. |
| `previewWebp` | Captura opcional del área visible, de hasta 1.5 MB (`54.sqm`). |

La pulsación larga en webtoon registra el punto tocado; la barra usa el centro del
área visible. Los visores paginados guardan fracciones desconocidas. La vista previa
usa el foco, después la posición de desplazamiento y finalmente el centro. El lector
espera al tamaño real de la imagen para restaurar la posición.

`PageCapture` usa PixelCopy sobre el área visible, excluyendo franjas cubiertas por
menús. Los filtros de brillo/color quedan en la captura; un escalado en curso guarda
lo visible en ese instante. Captura y marcador se insertan atómicamente. El cargador
Coil lee el blob guardado; sin captura, busca en caché, imagen descargada y fuente.
La alternativa de imagen descargada no admite archivos CBZ.

La tarjeta de almacenamiento permite recomprimir y generar capturas faltantes.
Sus contadores y acciones abarcan todas las páginas guardadas, no solo la lista
filtrada por visibilidad. Solo aparece cuando la lista de Páginas no está vacía.

`page_bookmarks` (`49.sqm`) es única por capítulo/página y se elimina en cascada.
`chapter_bookmark_types` (`50.sqm`) separa los tipos de los indicadores heredados.
`page_bookmark_previews` (`54.sqm`) se elimina con su marcador. El reemplazo durante
la restauración conserva los ID para mantener las relaciones con capturas.

Los campos `1002` y `1003` de `BackupManga` contienen páginas y tipos de capítulo.
Los campos `6`, `7` y `8` de `BackupPageBookmark` contienen desplazamiento, foco y
captura. Las capturas se incluyen solo al elegir la opción de copia `momentCaptures`.

## Verificación y limitaciones

Las comprobaciones SQL de `scripts/checks/` cubren tipos, posiciones y capturas
contra las migraciones reales. Sus encabezados indican los archivos de entrada.
Faltan pruebas Android de PixelCopy, zoom, páginas dobles o cargando, renderizado
Coil, compresión masiva y restauración con capturas. Abrir una página guardada usa
el comportamiento normal de lectura/historial, sin activar incógnito automáticamente.
