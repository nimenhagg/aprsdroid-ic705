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
# Android libtool uses version_type=none, so the installed shared library keeps the
# unversioned name (libhamlib.so) instead of libhamlib.so.<major>.<minor>.<patch>.
SOURCE_SO="$(find "$INST/hamlib" -type f \( -name 'libhamlib.so' -o -name 'libhamlib.so.*' \) -print | sort -V | tail -n 1)"
[ -s "$SOURCE_SO" ] || { echo "Hamlib shared library missing" >&2; exit 1; }
# The JNI boundary links against these symbols, so they are read from the
# dynamic symbol table and verified before and after stripping: an export that
# disappears while stripping would silently make the packaged library unusable.
#
# Every check below writes readelf output to a file first. Piping readelf or
# printf straight into `grep -q` looks equivalent but is not: grep exits on the
# first match, the writer fails with EPIPE, and `set -o pipefail` then reports a
# mismatch even when the symbol was found.
HAMLIB_REQUIRED_EXPORTS="hamlib_version hamlib_version2 rig_load_all_backends rig_list_foreach rig_init rig_open rig_close rig_cleanup rig_get_freq rig_set_freq rig_get_mode rig_set_mode rig_get_ptt rig_set_ptt rigerror"

tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT

verify_exports() {
  stage="$1"
  "$READELF" -W --dyn-syms "$OUTPUT" | awk '{print $NF}' > "$tmp"
  export_missing=""
  for symbol in $HAMLIB_REQUIRED_EXPORTS; do
    grep -Fxq "$symbol" "$tmp" || export_missing="$export_missing $symbol"
  done
  if [ -n "$export_missing" ]; then
    echo "Missing exports ${stage}:$export_missing" >&2
    echo "--- libhamlib.so dynamic symbols (last 60, ${stage}) ---" >&2
    tail -n 60 "$tmp" >&2
    return 1
  fi
  return 0
}

cp "$SOURCE_SO" "$OUTPUT"
patchelf --set-soname libhamlib.so "$OUTPUT"
verify_exports "before strip" || exit 1
"$STRIP" --strip-unneeded "$OUTPUT"
verify_exports "after strip" || exit 1
"$READELF" -d "$OUTPUT" > "$tmp"
grep -Eq '\(SONAME\).*\[libhamlib\.so\]' "$tmp" || { echo "Unexpected SONAME for $ABI" >&2; exit 1; }
if grep -qi libusb "$tmp"; then echo "Unexpected libusb dependency" >&2; exit 1; fi
"$READELF" -lW "$OUTPUT" > "$tmp"
python3 - "$tmp" "$ABI" <<'PY'
import pathlib,sys
loads=[int(x.split()[-1],16) for x in pathlib.Path(sys.argv[1]).read_text().splitlines() if x.split() and x.split()[0]=="LOAD"]
if not loads or min(loads)<0x4000: raise SystemExit(f"Hamlib {sys.argv[2]} invalid LOAD alignment: {loads}")
print("Hamlib",sys.argv[2],"LOAD alignments:",", ".join(hex(x) for x in loads))
PY
echo "Hamlib $HAMLIB_VERSION $HAMLIB_REV API $ANDROID_API $ABI"
"$READELF" -d "$OUTPUT" | grep -E 'SONAME|NEEDED' || true
sha256sum "$OUTPUT"

# Locate and package NDK libc++_shared.so for this ABI
LIBCXX_SO=""
for candidate in \
  "$TOOLCHAIN/sysroot/usr/lib/$HOST/libc++_shared.so" \
  "$TOOLCHAIN/sysroot/usr/lib/$CLANG_TRIPLE/libc++_shared.so" \
  "$TOOLCHAIN/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so" \
  "$NDK/sources/cxx-stl/llvm-libc++/libs/$ABI/libc++_shared.so"; do
  if [ -s "$candidate" ]; then
    LIBCXX_SO="$candidate"
    break
  fi
done
if [ -z "$LIBCXX_SO" ]; then
  LIBCXX_SO="$(find "$NDK" -name "libc++_shared.so" 2>/dev/null | grep -E "/$ABI/|/$HOST/|arm-linux-androideabi" | head -n 1 || true)"
fi
[ -s "$LIBCXX_SO" ] || { echo "libc++_shared.so not found in NDK for $ABI" >&2; exit 1; }
LIBCXX_DEST="$(dirname "$OUTPUT")/libc++_shared.so"
cp "$LIBCXX_SO" "$LIBCXX_DEST"
"$STRIP" --strip-unneeded "$LIBCXX_DEST"
"$READELF" -lW "$LIBCXX_DEST" > "$tmp"
python3 - "$tmp" "$ABI" <<'PY'
import pathlib,sys
loads=[int(x.split()[-1],16) for x in pathlib.Path(sys.argv[1]).read_text().splitlines() if x.split() and x.split()[0]=="LOAD"]
if not loads or min(loads)<0x4000: raise SystemExit(f"libc++_shared {sys.argv[2]} invalid LOAD alignment: {loads}")
print("libc++_shared",sys.argv[2],"LOAD alignments:",", ".join(hex(x) for x in loads))
PY
echo "Packaged libc++_shared.so for $ABI: $(sha256sum "$LIBCXX_DEST" | awk '{print $1}')"

