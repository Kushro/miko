# Controles de descarga

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Configura las restricciones en Ajustes → Descargas. Miko conserva las descargas solo
por Wi-Fi, la descarga anticipada, el borrado tras leer y la cola persistente ordenable.

## Carga y horario

Activa las descargas solo durante la carga o una franja horaria. Estas condiciones
restringen los inicios y las reanudaciones automáticas. **Iniciar ahora** omite ambas
para ese inicio; nunca omite la condición de Wi-Fi. Una pausa manual no programa reanudaciones.

La franja usa minutos desde la medianoche local: inicio inclusivo y fin exclusivo.
Si el fin es anterior al inicio, cruza medianoche; si coinciden, permite todo el día.
Un capítulo iniciado dentro de la franja puede terminar después. `DownloadJob`
programa la reanudación con una restricción de carga o un retraso.
`DownloadRestrictions` contiene las reglas puras de horario.

## Velocidad y almacenamiento

- La velocidad es un límite global en KiB/s; cero significa sin límite.
  `DownloadThrottle` usa un único depósito de tokens con ráfaga de un segundo,
  compartido entre descargas. Los cambios se aplican al momento. Limita la copia
  de imágenes en bloques de 8 KiB, no el cliente OkHttp compartido, sin afectar al lector.
- La cuota se mide en MiB; cero la desactiva. Incluye directorios de capítulos y
  archivos CBZ. `DownloadCache` memoriza tamaños y los invalida al cambiar el índice.
- `DownloadQuotaEnforcer` selecciona las descargas más antiguas por modificación del
  archivo o fecha de obtención, con el ID de capítulo como desempate. Borra solo lo
  necesario mediante `DownloadManager.deleteChapters()`.
- Protege capítulos marcados, categorías excluidas, fuentes locales y capítulos en
  cola o descargándose. Si no puede cumplir la cuota, detiene las descargas.

La cuota se comprueba antes de iniciar un capítulo y tras completarlo, con un retardo
agrupado de cinco segundos. El borrado es asíncrono y la espera por el índice es
limitada. Cambiar la cuota no borra archivos por sí solo. La tarjeta de uso no sigue
el progreso en vivo; el diálogo rechaza cuotas inferiores al uso actual.

## Descargar capítulos no leídos de la biblioteca

Abre el menú de la cola, elige descargar los capítulos no leídos de la biblioteca y
confirma la cantidad de series. Miko añade los capítulos sin leer y sin descargar,
incluyendo el tratamiento existente de fuentes combinadas.

## Referencia técnica

Las preferencias están en `DownloadPreferences` y viajan en sus copias de seguridad.
La implementación está en `app/.../data/download/`; la selección pura, en
`PurgeCandidate.kt`. `DownloadRestrictionsTest` y `DownloadQuotaPolicyTest` comprueban
límites de horario y protecciones. El orden de la cola determina la prioridad;
no hay un segundo campo de prioridad ni un planificador independiente.
