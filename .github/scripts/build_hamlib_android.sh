#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ABI="${1:-arm64-v8a}"
OUTPUT="${2:-$ROOT/build/generated/hamlibJniLibs/$ABI/libhamlib.so}"
HAMLIB_VERSION="4.7.2"
HAMLIB_REV="40f63488fe0bd751b147f48d62fd217bf53713a0"
HAMLIB_REPO="https://github.com/Hamlib/Hamlib.git"
NDK_VERSION="28.2.13676358"
ANDROID_API=28
case "$ABI" in
 arm64-v8a) HOST="aarch64-linux-android"; CLANG_TRIPLE="aarch64-linux-android" ;;
 armeabi-v7a) HOST="armv7a-linux-androideabi"; CLANG_TRIPLE="armv7a-linux-androideabi" ;;
 *) echo "Unsupported Hamlib ABI: $ABI" >&2; exit 2 ;;
esac
: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
for cmd in git make autoconf automake libtoolize pkg-config patchelf python3; do command -v "$cmd" >/dev/null || { echo "$cmd is required" >&2; exit 2; }; done
NDK="$ANDROID_HOME/ndk/$NDK_VERSION"
if [ ! -d "$NDK" ]; then SDKMANAGER="$(command -v sdkmanager || true)"; [ -n "$SDKMANAGER" ] || SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"; yes | "$SDKMANAGER" "ndk;$NDK_VERSION" >/dev/null; fi
HOST_TAG="linux-x86_64"; [ "$(uname -s)" != "Darwin" ] || HOST_TAG="darwin-x86_64"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
AR="$TOOLCHAIN/bin/llvm-ar"; AS="$TOOLCHAIN/bin/llvm-as"; CC="$TOOLCHAIN/bin/${CLANG_TRIPLE}${ANDROID_API}-clang"; CXX="$TOOLCHAIN/bin/${CLANG_TRIPLE}${ANDROID_API}-clang++"; LD="$TOOLCHAIN/bin/ld"; RANLIB="$TOOLCHAIN/bin/llvm-ranlib"; STRIP="$TOOLCHAIN/bin/llvm-strip"; READELF="$TOOLCHAIN/bin/llvm-readelf"; NM="$TOOLCHAIN/bin/llvm-nm"
for tool in "$AR" "$AS" "$CC" "$CXX" "$LD" "$RANLIB" "$STRIP" "$READELF" "$NM"; do [ -x "$tool" ] || { echo "Missing NDK tool: $tool" >&2; exit 2; }; done
SRC="$ROOT/build/hamlib-src/$HAMLIB_REV"; BLD="$ROOT/build/hamlib-build/$ABI"; INST="$ROOT/build/hamlib-install/$ABI"
if [ ! -d "$SRC/.git" ]; then rm -rf "$SRC"; mkdir -p "$(dirname "$SRC")"; git init -q "$SRC"; git -C "$SRC" remote add origin "$HAMLIB_REPO"; git -C "$SRC" fetch -q --depth 1 origin "$HAMLIB_REV"; git -C "$SRC" checkout -q --detach FETCH_HEAD; fi
actual="$(git -C "$SRC" rev-parse HEAD)"; [ "$actual" = "$HAMLIB_REV" ] || { echo "Hamlib revision mismatch" >&2; exit 1; }
git -C "$SRC" reset -q --hard "$HAMLIB_REV"; git -C "$SRC" clean -q -fdx
(cd "$SRC" && ./bootstrap)
rm -rf "$BLD" "$INST"; mkdir -p "$BLD" "$INST" "$(dirname "$OUTPUT")"
(cd "$BLD" && AR="$AR" AS="$AS" CC="$CC" CXX="$CXX" LD="$LD" RANLIB="$RANLIB" STRIP="$STRIP" CFLAGS="-O2 -fPIC" CXXFLAGS="-O2 -fPIC" LDFLAGS="-Wl,-z,max-page-size=16384" "$SRC/configure" --host="$HOST" --prefix=/hamlib --without-libusb --disable-static --enable-shared && make -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)" V=0 --no-print-directory && make install DESTDIR="$INST" --no-print-directory)
SOURCE_SO="$(find "$INST/hamlib" -type f -name 'libhamlib.so.*' -print | sort -V | tail -n 1)"
[ -s "$SOURCE_SO" ] || { echo "Hamlib shared library missing" >&2; exit 1; }
cp "$SOURCE_SO" "$OUTPUT"; patchelf --set-soname libhamlib.so "$OUTPUT"; "$STRIP" --strip-unneeded "$OUTPUT"
"$READELF" -d "$OUTPUT" | grep -Eq '\(SONAME\).*\[libhamlib\.so\]' || exit 1
! "$READELF" -d "$OUTPUT" | grep -qi libusb || { echo "Unexpected libusb dependency" >&2; exit 1; }
for symbol in hamlib_version rig_load_all_backends rig_list_foreach rig_init rig_open rig_close rig_cleanup rig_get_freq rig_set_freq rig_get_mode rig_set_mode rig_get_ptt rig_set_ptt rigerror; do "$NM" -D --defined-only "$OUTPUT" | awk '{print $3}' | grep -Fxq "$symbol" || { echo "Missing export: $symbol" >&2; exit 1; }; done
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT; "$READELF" -lW "$OUTPUT" > "$tmp"
python3 - "$tmp" "$ABI" <<'PY'
import pathlib,sys
loads=[int(x.split()[-1],16) for x in pathlib.Path(sys.argv[1]).read_text().splitlines() if x.split() and x.split()[0]=="LOAD"]
if not loads or min(loads)<0x4000: raise SystemExit(f"Hamlib {sys.argv[2]} invalid LOAD alignment: {loads}")
print("Hamlib",sys.argv[2],"LOAD alignments:",", ".join(hex(x) for x in loads))
PY
echo "Hamlib $HAMLIB_VERSION $HAMLIB_REV API $ANDROID_API $ABI"
"$READELF" -d "$OUTPUT" | grep -E 'SONAME|NEEDED' || true
sha256sum "$OUTPUT"
