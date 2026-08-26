#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-13.5.1}"
ABI="${2:-arm64-v8a}"
OUTPUT="${3:-$PWD/libmaplibre.so}"
NDK_VERSION="28.2.13676358"

case "$ABI" in
  arm64-v8a|armeabi-v7a) ;;
  *) echo "Unsupported ABI: $ABI" >&2; exit 2 ;;
esac

: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
NDK="$ANDROID_HOME/ndk/$NDK_VERSION"
if [ ! -d "$NDK" ]; then
  SDKMANAGER="$(command -v sdkmanager || true)"
  if [ -z "$SDKMANAGER" ]; then
    SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
  fi
  yes | "$SDKMANAGER" "ndk;$NDK_VERSION" >/dev/null
fi

CMAKE_BIN="$(command -v cmake)"
if [ -z "$CMAKE_BIN" ]; then
  echo "cmake is required" >&2
  exit 2
fi

mkdir -p "$(dirname "$OUTPUT")"
OUTPUT="$(cd "$(dirname "$OUTPUT")" && pwd)/$(basename "$OUTPUT")"
if [ -s "$OUTPUT" ]; then
  echo "Using cached MapLibre MinSizeRel library: $OUTPUT"
  exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone --quiet --depth 1 --branch "android-v$VERSION" --recurse-submodules --shallow-submodules \
  https://github.com/maplibre/maplibre-native.git "$WORK/maplibre-native"
cd "$WORK/maplibre-native"

# AGP's Android Release variant normally maps native code to RelWithDebInfo.
# Build MinSizeRel directly so the upstream
# CMAKE_INTERPROCEDURAL_OPTIMIZATION_MINSIZEREL=ON setting is selected.
# Extend Android's existing Release/RelWithDebInfo size flags to MinSizeRel too,
# preserving -Oz, LTO, ICF, section GC and the version script.
python3 - <<'PY'
from pathlib import Path

android_cmake = Path('platform/android/android.cmake')
s = android_cmake.read_text()
old = 'set(_OPT_CONFIGS "$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>")'
new = 'set(_OPT_CONFIGS "$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>,$<CONFIG:MinSizeRel>>")'
if old not in s:
    raise SystemExit('android.cmake optimization config anchor not found')
android_cmake.write_text(s.replace(old, new, 1))

wrapper = Path('platform/android/MapLibreAndroid/src/cpp/CMakeLists.txt')
s = wrapper.read_text()
old = '$<$<CONFIG:Release>:'
new = '$<$<OR:$<CONFIG:Release>,$<CONFIG:MinSizeRel>>:'
count = s.count(old)
if count < 1:
    raise SystemExit('Android wrapper Release flag anchors not found')
wrapper.write_text(s.replace(old, new))
print(f'Extended {count} Android wrapper Release flags to MinSizeRel')
PY

BUILD="$WORK/build-$ABI"
"$CMAKE_BIN" -S platform/android/MapLibreAndroid/src/cpp -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM=android-25 \
  -DANDROID_STL=c++_static \
  -DANDROID_CPP_FEATURES=exceptions \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DCMAKE_INTERPROCEDURAL_OPTIMIZATION_MINSIZEREL=ON \
  -DMLN_WITH_OPENGL=ON \
  -DMLN_WITH_VULKAN=OFF \
  -DMLN_WITH_WEBGPU=OFF \
  -DMLN_WITH_GLFW=OFF

"$CMAKE_BIN" --build "$BUILD" --target maplibre --parallel "$(nproc)"
SO="$(find "$BUILD" -type f -name libmaplibre.so -print -quit)"
if [ -z "$SO" ]; then
  echo "MinSizeRel libmaplibre.so was not produced" >&2
  exit 1
fi

STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
if [ ! -x "$STRIP" ]; then
  echo "llvm-strip not found in NDK $NDK_VERSION" >&2
  exit 1
fi
cp "$SO" "$OUTPUT"
"$STRIP" --strip-unneeded "$OUTPUT"

READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
if [ -x "$READELF" ]; then
  "$READELF" -l "$OUTPUT" | grep -E 'LOAD|GNU_RELRO' || true
fi
sha256sum "$OUTPUT"
ls -lh "$OUTPUT"
