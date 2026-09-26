# Organización de la biblioteca

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Usa categorías, etiquetas y puntuaciones locales para organizar mangas sin depender
de los metadatos de las fuentes.

## Categorías y ajustes independientes

Pulsa el título de la categoría para elegir otra visible. El selector respeta la
visibilidad y la agrupación actual; no expone categorías ocultas. Usa la pestaña
**Especial** de los ajustes de biblioteca para asignar filtros, orden, visualización,
columnas y agrupación propios. Las categorías sin ajustes guardados usan los globales.

`CategoryLibrarySettingsResolver` resuelve y captura estos ajustes. Se guardan como
JSON `CategoryLibrarySettings` en `category_library_settings` (migración `51.sqm`).
Las claves JSON desconocidas se ignoran y los valores predeterminados permiten leer
copias anteriores. El campo `1000` de `BackupCategory` transporta estos ajustes.
Borrar una categoría elimina su configuración. Algunos controles heredados, como
las insignias y los filtros de categorías incluidas/excluidas, siguen siendo globales.
Los contadores del título de biblioteca también pueden reflejar el filtro global.

## Etiquetas y puntuaciones

- Añade etiquetas locales desde el detalle o copia un género de la fuente. Borrar
  una etiqueta local no modifica la fuente. Las sugerencias excluyen etiquetas ya asignadas.
- Busca por texto o `namespace:tag`. Usa **Mis tags** para incluir o excluir etiquetas
  y agrupar entradas por ellas. Las entradas sin etiquetas tienen su propio grupo.
- Asigna una puntuación local de 1 a 5 o bórrala. Filtra entradas puntuadas/sin puntuar,
  ordénalas por puntuación local o agrúpalas por valor. Las puntuaciones del sitio y
  de los servicios de seguimiento son valores distintos.

`manga_tags` y `manga_ratings` se crean en `49.sqm`. Las etiquetas son únicas por manga
sin distinguir mayúsculas; `rename` utiliza `UPDATE OR REPLACE` ante colisiones.
Se muestran como `namespace:name` cuando tienen espacio de nombres. Un manga sin
puntuación no tiene fila, en lugar de guardar cero. `BackupManga` usa el campo `1000`
para etiquetas y `1001` para puntuación; la copia representa la ausencia de puntuación
con cero. Repositorios e interactores están en `domain/.../manga/` y `data/.../manga/`.

## Agrupar títulos parecidos

Selecciona **Títulos parecidos** como capa y ajusta el umbral entre 50 y 100%
(60% por defecto). No se combina con género ni títulos duplicados exactos.
La preferencia global es `library_title_similarity_threshold`; por categoría se usa
`CategoryLibrarySettings.titleSimilarityThreshold`.

`RuijiTitleClusterer` convierte a minúsculas con `Locale.ROOT`, conserva letras/dígitos
Unicode y calcula Sørensen–Dice sobre multiconjuntos de bigramas. Los títulos vacíos
nunca coinciden; los idénticos no vacíos puntúan 1. La unión por enlace simple conecta
pares coincidentes: dos títulos pueden compartir grupo mediante títulos intermedios.

`LibraryGroupingEngine.ruijiTitleBuckets` agrupa títulos normalizados distintos antes
de procesar cada entrada y guarda asignaciones en una caché LRU de 64 entradas.
El título más corto representa al grupo, con desempate determinista. El orden natural
coloca primero los grupos grandes y los títulos aislados en «Sin parecidos». Cambiar
el representante puede reiniciar el estado plegado. La precisión de coma flotante
puede afectar a coincidencias exactamente en el umbral.

## Verificación

Las pruebas del algoritmo cubren Unicode, similitud, vacíos, transitividad y
determinismo. Las de categorías cubren serialización y resolución de ajustes.
Ejecuta la comprobación SQL desde la raíz del repositorio:

```bash
python3 scripts/checks/category-settings.py data/src/main/sqldelight/tachiyomi/migrations/51.sqm data/src/main/sqldelight/tachiyomi/data/category_library_settings.sq
```
