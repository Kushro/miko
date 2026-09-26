# Sincronización de favoritos de E-Hentai

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Vincula las diez posiciones de favoritos remotos con categorías locales antes de
sincronizar. Miko usa nombres de categorías, no posiciones, y nunca las renombra ni
reordena para imitar al sitio.

## Configurar el mapeo

1. Inicia sesión en ExHentai y abre **Ajustes → E-Hentai → Sincronización de favoritos → Mapeo de categorías**.
2. Actualiza nombres y cantidades de las posiciones remotas.
3. Asigna una categoría a cada posición deseada y actívala. **Mapear por orden** crea
   un mapeo inicial editable, no una regla posicional permanente.
4. Resuelve categorías duplicadas y guarda. Se necesita al menos un mapeo activo.

Una categoría asignada ausente detiene la sincronización. Renombrar/borrar categorías
actualiza el mapeo. Se rechaza una galería en varias categorías activas mapeadas;
las categorías inactivas o sin mapear no participan.

## Posiciones vacías e instantáneas

Las posiciones sin galerías remotas descargadas se omiten **en ambas direcciones**:
no se descargan, no se suben ni se incluyen en la instantánea. Los contadores de
visualización no anulan esta regla. Si todas están vacías, se vuelve sin adquirir bloqueos.

Las instantáneas `favorite_entry` usan `(gid, token, category)`, donde categoría es
la posición remota. Las diferencias se limitan a posiciones activas. Reactivar una
posición o volver a llenarla produce una combinación inicial de ambos lados, que
puede subir entradas locales. El modo de solo lectura evita cambios locales hacia el servidor.

## Referencia técnica

`EhFavoritesSyncConfig` y `EhFavoritesSyncPlan` están en `domain/.../exh/favorites/`.
`exhFavoritesSyncConfig()` viaja en copias; `exhFavoritesUpstreamSlots()` es estado
de visualización. `FavoritesSyncHelper` valida sesión/configuración, obtiene galerías,
construye el plan y aplica cambios. `LocalFavoritesStorage` gestiona instantáneas;
`FavoritesSyncPlanning` contiene reglas puras de selección.

`EhFavoritesSyncConfigTest` y `FavoritesSyncPlanTest` cubren mapeos, posiciones vacías
y categorías activas. Valida errores de red, renombres y solo lectura con una cuenta
antes de modificar la lógica de sincronización.
