# Compilación, publicación y firma

[English](./en.md) · **Español** · [Documentación](../../README.es.md)

Compila Miko en Linux o WSL con `JAVA_HOME` y `ANDROID_HOME` configurados para tu
instalación. Los requisitos están en el [README](../../../README.es.md#compilación).

## Compilar un APK

Desde la raíz del repositorio, ejecuta en orden:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug
```

Usa `assembleRelease` para un APK de publicación o `assemblePreview` para una versión
preliminar. El escalado nativo requiere NDK, CMake y NCNN. Conserva el wrapper,
los módulos y `third_party/` en el repositorio.

## Configurar la firma de GitHub Actions

Los workflows usan `r0adkll/sign-android-release`. Configura estos secretos:

| Secreto | Valor |
|---|---|
| `SIGNING_KEY` | Keystore de publicación codificado en Base64 |
| `ALIAS` | Alias de la clave de firma |
| `KEY_STORE_PASSWORD` | Contraseña del keystore |
| `KEY_PASSWORD` | Contraseña de la clave |

Valida los secretos en los ajustes del repositorio. El código no demuestra que
existan. Respalda la clave fuera del repositorio para poder distribuir actualizaciones.

## Firmar localmente

```bash
bash scripts/sign-apk.sh
```

La utilidad solicita APK, SDK, keystore y alias. `apksigner` pide las contraseñas.
Para indicar entrada y salida, ejecuta desde la raíz:

```bash
mkdir -p dist
bash scripts/sign-apk.sh app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk dist/Miko-signed.apk
```

Variables opcionales: `ANDROID_HOME` (o `ANDROID_SDK_ROOT`), `KEYSTORE` y
`KEY_ALIAS`. Requiere Bash y utilidades GNU. Rechaza sobrescribir la salida,
alinea el APK y verifica su firma. No lee archivos de contraseña cercanos.
GitHub Actions no depende de esta utilidad local.

## Requisitos de distribución

- Mantén keystores, contraseñas y configuración OAuth/Firebase fuera de Git.
- Miko usa `app.miko` con sufijos por variante. Las constantes están en
  `core/common/src/main/kotlin/tachiyomi/core/common/Constants.kt`.
- Los esquemas OAuth existentes son contratos de compatibilidad. Configura las
  integraciones en las consolas de proveedores antes de activarlas. Firebase es opcional.
- Conserva `LICENSE`, `LICENSE-APACHE`, `NOTICE` y avisos de dependencias. El SDK NCNN
  incluido aún necesita el texto BSD-3-Clause antes de distribuir binarios.
- El arte fuente está en `docs/miko/brand/`; regenera los iconos desde esos archivos.
