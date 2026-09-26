#!/usr/bin/env bash
# MIKO --> Optional local signing helper; GitHub Actions uses repository secrets.
set -euo pipefail

if [[ "${1:-}" == --help ]]; then
  echo 'Usage: bash scripts/sign-apk.sh [input.apk] [output.apk]'
  echo 'Prompts for missing inputs. Optional environment: ANDROID_HOME, ANDROID_SDK_ROOT, KEYSTORE, KEY_ALIAS.'
  exit 0
fi
[[ $# -le 2 ]] || { echo 'Use --help for usage.' >&2; exit 2; }

prompt() {
  local name="$1" label="$2"
  if [[ -z "${!name:-}" ]]; then
    [[ -t 0 ]] || { echo "Missing $name; run interactively or supply it explicitly." >&2; exit 2; }
    read -r -p "$label: " "$name"
  fi
  [[ -n "${!name:-}" ]] || { echo "$name must not be empty." >&2; exit 2; }
}

IN_APK="${1:-}"
prompt IN_APK 'Input APK path'
[[ -f "$IN_APK" ]] || { echo 'Input APK not found.' >&2; exit 1; }
OUT_APK="${2:-${IN_APK%.apk}-signed.apk}"
[[ ! -e "$OUT_APK" ]] || { echo 'Output already exists; choose another path.' >&2; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
prompt SDK 'Android SDK directory'
[[ -d "$SDK/build-tools" ]] || { echo 'Android SDK build-tools directory not found.' >&2; exit 1; }
prompt KEYSTORE 'Keystore path (outside the repository)'
prompt KEY_ALIAS 'Signing key alias'
[[ -f "$KEYSTORE" ]] || { echo 'Keystore not found.' >&2; exit 1; }

BUILD_TOOLS=''
while IFS= read -r version; do
  candidate="$SDK/build-tools/$version"
  if [[ -x "$candidate/apksigner" && -x "$candidate/zipalign" ]]; then
    BUILD_TOOLS="$candidate"
    break
  fi
done < <(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -Vr)
[[ -n "$BUILD_TOOLS" ]] || { echo 'Install Android SDK build-tools (apksigner and zipalign).' >&2; exit 1; }

TMP_ALIGNED="$(mktemp "${TMPDIR:-/tmp}/miko-sign-XXXXXXXX.apk")"
trap 'rm -f "$TMP_ALIGNED"' EXIT
"$BUILD_TOOLS/zipalign" -f -p 4 "$IN_APK" "$TMP_ALIGNED"
# apksigner prompts for passwords; none are stored or passed on the command line.
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --out "$OUT_APK" "$TMP_ALIGNED"
"$BUILD_TOOLS/apksigner" verify "$OUT_APK"
echo "Signed and verified: $OUT_APK"
# MIKO <--
