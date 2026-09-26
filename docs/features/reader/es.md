# Lectura y mejora de imágenes

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Miko ofrece visores configurables, direcciones de lectura, páginas dobles y desplazamiento
automático. Selecciona la mejora local o mediante un servidor auxiliar en los ajustes
del lector. El centro de escalado administra modelos, progreso y capítulos mejorados.
Los indicadores de la barra muestran descargas y mejoras. Las prioridades de grupos
de traducción eligen una versión de cada capítulo conservando el progreso de duplicados ocultos.

## Mejorar una portada

1. Añade el manga a la biblioteca y abre su portada a pantalla completa.
2. Elige **Mejorar** y selecciona el motor local o remoto.
3. Espera o cancela explícitamente. El resultado se guarda como portada personalizada.

La opción remota permanece desactivada hasta que un servidor configurado confirme
que está listo. Los modelos locales vienen en el APK y usan los ajustes del lector:
modelo, escala, ruido y rendimiento. Se muestra progreso nativo cuando existe; en
caso contrario, un indicador de actividad. No hay selector de modelo por portada,
vista previa ni deshacer. La acción de editar/borrar portada recupera la original.

## Administrar el almacenamiento de portadas

El visor muestra el tamaño cuando puede medirlo. **Volver a descargar** confirma
la eliminación de la portada personalizada/mejorada y del archivo de red en caché
antes de solicitar el original. **Recomprimir** genera WebP con pérdida a calidad 85
y lo conserva solo si es menor. Estas acciones se aplican a entradas de biblioteca
con fuentes de red, no a archivos locales. En pantallas estrechas se agrupan en el menú.

## Referencia técnica y limitaciones

`MangaCoverScreenModel` gestiona la mejora fuera del diálogo del manga para que el
progreso no reemplace al visor. Solicita bitmaps de software sin asignación por hardware
ni inserción en caché de memoria. No recicla bitmaps propiedad de Coil. Limita y codifica
los resultados, los guarda mediante `Manga.editCover` y actualiza `coverLastModified`.

Cancelar una operación remota detiene la corrutina. Una llamada nativa bloqueante
termina antes de reciclar el resultado cancelado. El motor nativo tiene un único
modelo/mutex; cambiarlo durante una mejora del lector puede interrumpir la mejora de
esa página. La recompresión usa un temporal y renombrado, comprobando el tamaño antes
del reemplazo. Las imágenes grandes pueden muestrearse para limitar memoria. Faltan
comprobaciones de estos recorridos en dispositivos.
