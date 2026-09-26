# Enlaces a títulos

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Comparte un manga desde su menú para incluir un enlace de Miko y la URL pública
del sitio. Esta última sirve a destinatarios sin Miko. Compartir desde el lector
o WebView sigue enviando URLs ordinarias.

## Formato del enlace

```text
miko://entry?s=<sourceId>&t=<title>&u=<base64url(SManga.url)>
```

Son obligatorios `s` (ID de fuente, Long decimal), `t` (título, UTF-8 codificado para
URL) y `u` (identificador relativo, Base64 seguro para URL sin relleno). Opcionales:
`n` nombre de fuente, `l` idioma, `v` versión, `cu` identificador de capítulo (misma
codificación Base64), `a` autor (hasta 80 caracteres), `g` primeros cinco géneros
separados por comas y `st` estado. Se ignoran campos desconocidos. La URL pública
no sustituye a los identificadores originales `SManga.url`/`SChapter.url`.

## Abrir un enlace

Miko resuelve el ID de fuente, crea la entrada con los datos del enlace y combina
los detalles obtenidos sin perder identificadores. Un ID de capítulo lo abre tras
buscarlo o actualizar capítulos. Las fuentes ausentes ofrecen explorar extensiones
o buscar por título. Los campos obligatorios inválidos muestran un error de enlace.

Otras URLs usan `ResolvableSource`, después coincidencia de host con fuentes
instaladas y finalmente búsqueda si no hay coincidencia. Se ignoran mayúsculas y
el prefijo `www.`. El manifiesto registra `miko://entry`; no registra App Links
HTTP verificados ni una página web de destino.

## Referencia técnica y límites

`MikoEntryLink` tiene funciones puras `toQueryParams`/`parseQuery` probadas en JVM;
las envolturas URI usan Android. `DeepLinkScreenModel` resuelve entradas y capítulos.
El esquema requiere que la app receptora lo reconozca y Miko esté instalado.
Los enlaces no tienen firma ni versión; el análisis debe conservar compatibilidad.
Los autores se recortan y los géneros con comas pierden sus límites exactos. Los hosts
alternativos o configurables pueden no coincidir con la alternativa HTTP.
