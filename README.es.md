<div align="center">

<img width="200" height="200" src="./.github/readme-images/app-icon.png" alt="Icono de Miko"/>

# Miko

[English](./README.md) · **Español**

[![Licencia: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-0877d2?labelColor=27303D)](./LICENSE)

**Un lector de manga gratuito y de código abierto para Android.**

*Requiere Android 8.0 (API 26) o superior.*

</div>

## ¿Qué es Miko?

Miko arrancó con ganas de juntar en una sola app varias cosas que me gustaban de otros
lectores de manga y agregar algunas de cosecha propia. Partí de Komikku, que viene de
la familia de Mihon, con un primer objetivo bastante concreto: mejorar con escalado
esas imágenes que parecían haber pasado por demasiadas manos antes de llegar al lector.

En el camino también me pasó que, incluso usando las opciones de escalado más livianas,
el teléfono terminaba calentándose o me hacía esperar demasiado. Y teniendo una PC
más potente a mano, tenía poco sentido seguir haciéndolo trabajar de más. De ahí salió
[Miko Kagura](https://github.com/Kushro/miko-kagura): llevé el escalado a la PC y lo
puse a trabajar como proxy, recibiendo las imágenes, procesándolas y devolviéndolas al
lector. Más velocidad, más opciones de escalado y menos trabajo para el teléfono. Hoy
esa combinación es la que uso a diario para leer.

Después, el uso diario fue trayendo nuevas ideas. Fui sumando lo que me hacía falta o
simplemente tenía ganas de probar, como combinar fuentes integradas al estilo de
Kotatsu con extensiones instalables, y aplicar mejoras específicas cuando la app
reconoce ciertas extensiones.

Así, entre necesidades, experimentos y algún «¿y si le agrego esto?», Miko fue tomando
su propio camino. Lo voy a seguir desarrollando con esa misma idea: sumar cosas que me
interesen y me sirvan. Y si de paso también te sirven a vos, mejor todavía.

Consulta el [estado del proyecto](./docs/project/status/es.md) y la
[documentación por funciones](./docs/README.es.md) para conocer sus posibilidades,
uso y limitaciones.

## Funciones

### Heredadas y disponibles

- Lectura en línea mediante extensiones de fuentes.
- Lectura de descargas y fuente local para tus archivos.
- Visores, direcciones, diseños de página y desplazamiento automático configurables.
- Seguimiento con MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori, Bangumi, Kavita, Komga y Suwayomi.
- Categorías, filtros, categorías dinámicas y orden mediante arrastre.
- Temas claros/oscuros, unos 20 esquemas de color y temas derivados de portadas.
- Actualizaciones programadas de biblioteca y notificaciones agrupadas.
- Copias locales `.tachibk` y sincronización por Google Drive/WebDAV.
- Feed, búsquedas guardadas, entradas combinadas y migración de fuentes.
- Discord Rich Presence, incógnito y bloqueo de la aplicación.

### Funciones de Miko

- **Escalado local:** waifu2x y Anime4K con NCNN/Vulkan en el lector o incorporado a descargas.
- **Escalado remoto:** procesamiento mediante un servidor auxiliar para reducir el trabajo del teléfono.
- **Centro de escalado:** selección de modelos, progreso y administración de capítulos mejorados.
- **Agrupación por capas:** hasta dos criterios por categoría, con secciones plegables y orden propio.
  Incluye fuente, estado, seguimiento, género, puntuación, duplicados, idioma, autor, artista,
  progreso, descargas, fechas, último capítulo y categoría.
- **Prioridades de traducción:** una versión por capítulo según el grupo preferido, conservando el
  progreso de duplicados ocultos y respetando la prioridad al continuar leyendo.
- **Indicadores del lector:** progreso de descarga y escalado en la barra de páginas.
- **Fuentes Kotatsu integradas:** extensiones, parsers o ambos; presets para Explorar y búsqueda
  global, con tipos y capacidades visibles.
- **Controles de descarga:** carga, horario, velocidad, cuota con limpieza por antigüedad y descarga
  de capítulos no leídos. No incluye todo el planificador de Futon.
- **Biblioteca y navegación:** presets, ajustes por categoría, etiquetas/puntuaciones locales y
  agrupación de títulos parecidos con umbral ajustable.
- **Momentos:** páginas con notas y capturas opcionales, capítulos marcados por tipo, visibilidad
  de fuentes y una mascota opcional.
- **Descubrimiento:** recomendaciones por fuente, etiquetas, similitud, secuelas y trackers;
  mejoras por sitio y mapeo de favoritos de E-Hentai.
- **Tema Miko:** rojo por defecto, con Classic Blue, Amber, Onyx Gold y otros temas opcionales.
- **Telemetría desactivada por defecto:** el repositorio no distribuye configuración Firebase.

Algunas funciones requieren fuentes, trackers o un servidor configurado. La biblioteca
Kotatsu no garantiza que funcionen todos los sitios. Consulta las
[limitaciones conocidas](./docs/project/status/es.md), incluidas las pruebas pendientes en dispositivos.

## Descarga

Las versiones firmadas, cuando estén disponibles, se publican en:

**https://github.com/Kushro/miko/releases**

Publicar requiere configurar los secretos de firma del repositorio. Consulta la
[guía de publicación y firma local](./docs/project/release/es.md).

El workflow genera APK universal y por arquitectura: `arm64-v8a`, `armeabi-v7a`,
`x86` y `x86_64`. Si no sabes cuál elegir, instala el universal.
Miko se instala junto a Komikku/Mihon, con `applicationId` `app.miko`.

## Compilación

Miko usa Gradle para Android y un módulo nativo de escalado.

| Herramienta | Versión |
|---|---|
| JDK | 17 como destino de bytecode; CI compila con JDK 21 |
| Android SDK | compileSdk/targetSdk **36**, minSdk 26, build-tools 35.0.1 |
| Android NDK | **28.2.13676358** |
| CMake | **3.22.1** |
| NCNN | Incluido en `third_party/ncnn-20260526-android-vulkan/` |

La ruta NCNN se resuelve en este orden: `-PncnnSdkDir=…`, `ncnn.sdk.dir` en
`local.properties`, variable `NCNN_SDK_DIR` y SDK incluido en `third_party/`.

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug
./gradlew assemblePreview
./gradlew testReleaseUnitTest
```

Variantes y sufijos: `debug` → `.dev`, `releaseTest` → `.rt`, `foss` → `.foss`,
`preview` → `.beta`, `benchmark` → `.benchmark`, `release` → sin sufijo.

Se recomienda **Linux o WSL** por los límites de rutas de Windows y las herramientas
NDK. Consulta [Contribuciones](./CONTRIBUTING.md) (en inglés) para preparar el entorno.

## Antes de publicar un fork o una versión

- Actualiza propietario/repositorio en README, actualizador y workflows al publicar un fork.
- Mantén ajustes locales, `local.properties`, JSON OAuth/Firebase, keystores y contraseñas fuera
  de Git. La firma usa secretos `SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD` y `KEY_PASSWORD`.
  Ignorar un archivo no lo elimina de commits anteriores.
- Conserva módulos, `gradle/`, `third_party/` y atribuciones. El SDK NCNN necesita su
  `LICENSE.txt` BSD-3-Clause junto a los binarios antes de distribuir APK. Consulta [NOTICE](./NOTICE).

## Problemas, solicitudes y contribuciones

Las contribuciones son bienvenidas. Para cambios grandes, abre primero un issue.

- Incluye versión de Miko (**Más → Acerca de**), Android, dispositivo y pasos de reproducción.
  Obtén registros desde **Más → Ajustes → Avanzado → Volcar registros de errores**.
- Miko no aloja APK de extensiones ni contenido manga. Los problemas de extensiones suelen
  corresponder a sus mantenedores; los de parsers integrados, a Miko o a su biblioteca.
- Lee [CONTRIBUTING.md](./CONTRIBUTING.md) (en inglés).
- Los colaboradores con agentes deben leer [AGENTS.md](./AGENTS.md) (en inglés).

## Aviso

Los desarrolladores no están afiliados a los proveedores de contenido disponibles
en la aplicación. Miko no aloja contenido.

## Licencia

Miko se distribuye bajo **GPL-3.0-only**. Consulta [LICENSE](./LICENSE).
El código heredado Apache-2.0 conserva su licencia y atribución en
[LICENSE-APACHE](./LICENSE-APACHE) y [NOTICE](./NOTICE).

## Origen y agradecimientos

Distintos proyectos aportaron la base o inspiraron funciones concretas de Miko:

| Proyecto | Aporte a Miko |
|---|---|
| **Mihon / Tachiyomi** | Lector, biblioteca, seguimiento y arquitectura de extensiones. |
| **TachiyomiSY** | Feed, entradas combinadas, migración, fuentes delegadas/mejoradas y búsqueda avanzada. |
| **Komikku** | Base inicial; relacionados, categorías ocultas, temas por portada, favoritos masivos, repositorios y errores de actualización. |
| **Kotatsu-Redo / Futon** | Inspiración para presets y descargas; se integra `kotatsu-parsers-redo` mediante un adaptador propio, no su app ni su solucionador automático de Cloudflare. |
| **Taison** | Inspiración para selección de categorías, alcance del historial, confianza masiva y enlaces a títulos. |

Agradecimientos:

- [Komikku](https://github.com/komikku-app/komikku): cuong-tran y colaboradores, Apache-2.0.
- [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY): jobobby04, Apache-2.0.
- [Mihon](https://github.com/mihonapp/mihon): colaboradores de Mihon, Apache-2.0.
- [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi): Copyright 2015 Javier Tomás, Apache-2.0.
- [NCNN](https://github.com/Tencent/ncnn): Tencent, BSD-3-Clause.
- [kotatsu-parsers-redo](https://github.com/clquwu/kotatsu-parsers-redo): GPL-3.0, usado en las fuentes integradas.
