# Historial de lectura

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Usa los filtros del Historial para seguir la categoría activa, ocultar entradas leídas
y ordenar por última lectura, título, fuente, capítulo o tiempo de lectura.
El diálogo de visibilidad oculta fuentes seleccionadas y admite presets con nombre.

## Seguir una categoría de biblioteca

Activa **Seguir la categoría de biblioteca** en los filtros. Un chip muestra la
restricción; púlsalo para desactivarla. El filtro excluye entradas fuera de la
biblioteca. Predeterminada corresponde a entradas sin categoría asignada.

El modo sin agrupación, la ausencia de categoría o una categoría borrada desactivan
la restricción en lugar de mostrar una lista vacía sin explicación. Se combina con
búsqueda y otros filtros. `scopeToLibraryCategory()` se incluye en copias;
`active_category_id` es estado local del dispositivo y usa `-1` para ninguna categoría.

## Borrar historial

El diálogo del menú ofrece **Todo** o **Solo las entradas mostradas**. Todo elimina
todas las filas del historial. La segunda opción realiza un borrado lógico para
los ID de manga visibles respetando filtros y búsqueda. Afecta a esos mangas, no
solo a un capítulo mostrado. Con una categoría activa se preselecciona esta opción.
La acción existente de la barra sigue entrando en modo selección.

## Referencia técnica y límites

`LibraryScreenModel.updateActiveCategoryIndex` publica el ID de categoría.
`HistoryScreenModel` lo valida mediante la suscripción a categorías y pasa una
categoría opcional a `GetHistory` y a la consulta de `historyView.sq`. Es un filtro
de consulta, no otra tabla de historial. `HistorySorting.kt` contiene el orden;
`HistoryPreferences`, los ajustes.

La visibilidad usa `pref_history_hidden_sources`; Momentos tiene su propia selección.
Los presets con nombre se comparten mediante `miko_source_hide_presets`. No hay un
paginador de pestañas por fuente/estado de lectura. Comprueba cambios de categoría,
ID obsoletos, filtros combinados y alcance del borrado en un dispositivo al modificar estos flujos.
