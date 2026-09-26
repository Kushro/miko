# Navegación

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Elige un diseño en **Ajustes → Apariencia → Barra de navegación**. El mismo preset
controla la barra inferior del teléfono y la barra lateral de tablets.

| Preset | Orden |
|---|---|
| `MIHON` | Biblioteca, Actualizaciones, Historial, Explorar, Momentos, Más |
| `KOTATSU` | Historial, Biblioteca, Explorar, Actualizaciones, Momentos, Más |
| `COMPACT` | Biblioteca, Explorar, Momentos, Más |
| `CUSTOM` (predeterminado) | Orden MIHON, con Actualizaciones e Historial opcionales |

Solo CUSTOM usa los interruptores heredados de Actualizaciones/Historial. La primera
pestaña es el inicio y el destino de Atrás. Buscar en la biblioteca sigue abriendo
Biblioteca. Si un cambio oculta la pestaña seleccionada, Miko vuelve a la inicial.

Actualizaciones/Historial siguen disponibles desde Más, notificaciones y accesos
directos. Si no están en la barra, se abren como pantallas independientes. Momentos
aparece antes de Más en todos los presets. Su ID interno sigue siendo `NavTabId.FAVORITES`.

## Accesos directos y apariencia

El launcher tiene un acceso a Momentos después de Explorar. Selecciona `FavoritesTab`
directamente. Los launchers limitados a cuatro accesos pueden ocultar este quinto.
Los diseños de seis pestañas pueden recortar etiquetas en pantallas estrechas.
Momentos usa un icono estático. Desactivar la mascota en Apariencia no elimina notas.

## Referencia técnica

`NavPreset.kt` define los ID, presets y la función pura `resolveNavTabs()`.
`UiPreferences.bottomNavPreset()` guarda `pref_bottom_nav_preset` en las copias de
preferencias. `HomeScreen`, las filas alternativas de Más y `UpdatesTab`/`HistoryTab.isEnabled()`
deben usar el resolver, no leer interruptores por separado.
`NavPresetTest` comprueba pestañas obligatorias, unicidad y orden. Los accesos están
en `app/shortcuts.xml`; `MainActivity` procesa `SHORTCUT_FAVORITES`.
