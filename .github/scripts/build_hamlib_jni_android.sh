#!/usr/bin/env bash
# Builds libaprs_hamlib.so (the thin Hamlib JNI boundary) for one Android ABI.
# The corresponding libhamlib.so is produced by build_hamlib_android.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ABI="${1:-arm64-v8a}"
HAMLIB_VERSION="4.7.2"
HAMLIB_REV="40f63488fe0bd751b147f48d62fd217bf53713a0"
NDK_VERSION="28.2.13676358"
ANDROID_API=28
OUT_DIR="$ROOT/build/generated/hamlibJniLibs/$ABI"
HAMLIB_SO="$OUT_DIR/libhamlib.so"
OUTPUT="$OUT_DIR/libaprs_hamlib.so"
INSTALL="$ROOT/build/hamlib-install/$ABI/hamlib"

case "$ABI" in
 arm64-v8a) CLANG_TRIPLE="aarch64-linux-android" ;;
 armeabi-v7a) CLANG_TRIPLE="armv7a-linux-androideabi" ;;
 *) echo "Unsupported Hamlib JNI ABI: $ABI" >&2; exit 2 ;;
esac

: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
: "${JAVA_HOME:?JAVA_HOME must point to a JDK that ships jni.h}"

if [ ! -s "$HAMLIB_SO" ]; then
  bash "$ROOT/.github/scripts/build_hamlib_android.sh" "$ABI"
fi
[ -s "$HAMLIB_SO" ] || { echo "libhamlib.so missing for $ABI" >&2; exit 1; }
[ -d "$INSTALL/include/hamlib" ] || { echo "Hamlib headers missing for $ABI (expected $INSTALL/include)" >&2; exit 1; }

TOOLCHAIN="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/${CLANG_TRIPLE}${ANDROID_API}-clang"
READELF="$TOOLCHAIN/bin/llvm-readelf"
NM="$TOOLCHAIN/bin/llvm-nm"
for tool in "$CC" "$READELF" "$NM"; do
  [ -x "$tool" ] || { echo "Missing NDK tool: $tool" >&2; exit 2; }
done

"$CC" -shared -fPIC -O2 -Wall \
  -DHAMLIB_JNI_PINNED_VERSION="\"$HAMLIB_VERSION\"" \
  -DHAMLIB_JNI_PINNED_REVISION="\"$HAMLIB_REV\"" \
  -I"$INSTALL/include" \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o "$OUTPUT" \
  "$ROOT/native/hamlib-jni/hamlib_jni.c" \
  -L"$OUT_DIR" -lhamlib -pthread \
  -Wl,-z,max-page-size=16384

for symbol in \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeVersion \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeBackendRevision \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeRigCount \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeListRigs \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeCreate \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeOpen \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeClose \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeDestroy \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeIsOpen \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeGetFreq \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeSetFreq \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeGetMode \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeSetMode \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeGetPtt \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeSetPtt \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeErrorText \
  Java_org_aprsdroid_app_hamlib_HamlibNative_nativeModeName ; do
  "$NM" -D --defined-only "$OUTPUT" | awk '{print $3}' | grep -Fxq "$symbol" \
    || { echo "Missing JNI export: $symbol" >&2; exit 1; }
done

"$READELF" -d "$OUTPUT" | grep -Eq '\(NEEDED\).*\[libhamlib\.so\]' \
  || { echo "libaprs_hamlib.so does not link against libhamlib.so" >&2; exit 1; }

tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT; "$READELF" -lW "$OUTPUT" > "$tmp"
python3 - "$tmp" "$ABI" <<'PY'
import pathlib,sys
loads=[int(x.split()[-1],16) for x in pathlib.Path(sys.argv[1]).read_text().splitlines() if x.split() and x.split()[0]=="LOAD"]
if not loads or min(loads)<0x4000: raise SystemExit(f"Hamlib JNI {sys.argv[2]} invalid LOAD alignment: {loads}")
print("Hamlib JNI",sys.argv[2],"LOAD alignments:",", ".join(hex(x) for x in loads))
PY

echo "aprs_hamlib $HAMLIB_VERSION $HAMLIB_REV API $ANDROID_API $ABI"
sha256sum "$OUTPUT"
