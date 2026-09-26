# Recomendaciones

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Elige proveedores en **Ajustes → Avanzado → Sistema de sugerencias**. Los resultados
aparecen como títulos relacionados en el detalle. Usa uno o una combinación priorizada.

| Proveedor | Comportamiento |
|---|---|
| Osusume (`OSUSUME`, predeterminado) | Resultados de Komikku más búsquedas por palabras del título. |
| Uwasa (`UWASA`) | Relaciones del parser en Kotatsu; relaciones del sitio y el grupo no vacío más pequeño por palabra en extensiones. |
| Zokuhen (`ZOKUHEN`) | Quita sufijos de volumen, busca hasta tres prefijos decrecientes por título y ordena por similitud Dice. |
| Tagu Osekkai (`TAGU_OSEKKAI`) | Usa etiquetas locales y luego géneros de la fuente, con hasta dos búsquedas y orden por afinidad. |
| AniList/MAL (`TRACKER`) | Recomendaciones externas; pulsa para búsqueda inteligente o mantén pulsado para abrir el sitio de seguimiento. |

En modo múltiple los proveedores se ejecutan concurrentemente con supervisión.
La prioridad ordena grupos y elimina duplicados. Es una combinación, no una cadena
de alternativas ante fallos. El modo único usa Osusume; Zokuhen se activa explícitamente.

## Similitud y persistencia

Zokuhen usa el título y el original como semillas. Gana la primera consulta con
resultados que superan el umbral. El rango es 50–100%, por defecto 60%; el control
aparece mientras Zokuhen participa. Excluye la semilla, ordena por similitud y limita
a 20 resultados. Los títulos largos solo prueban sus tres prefijos candidatos más largos.

`RecommendationEngine` invoca proveedores elegidos en `RecommendationPreferences`.
La configuración se serializa en `miko_recommendation_settings`; `zokuhenThreshold`
forma parte del JSON. Una caché por proveedor/fuente/URL evita repetir trabajo.
Confirmar ajustes la invalida; restaurar preferencias desde una copia no elimina
inmediatamente todos los resultados existentes en memoria.

El único llamador es `MangaScreenModel.fetchRelatedMangasFromSource`. Las preferencias
existentes de relacionados y desactivación de búsqueda siguen aplicándose. Kotatsu
carga filtros mediante `KotatsuSource.awaitFilterList()`. Proveedores y pruebas puras
de ordenación están en `eu/kanade/domain/recommendation/`, en código y pruebas de app.

## Limitaciones

Las etiquetas se normalizan, pero no se traducen. Los metadatos NSFW son por fuente,
no por resultado. Las entradas de trackers son transitorias (`source = -1`), no se
insertan directamente ni se añaden a favoritos desde la fila: primero encuentra una
entrada de fuente. Los resultados de red requieren pruebas en dispositivos y fuentes reales.
