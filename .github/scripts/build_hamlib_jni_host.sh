#!/usr/bin/env bash
# Builds a host (x86_64-linux) copy of Hamlib and of the JNI boundary so the
# JVM unit tests can exercise the real native lifecycle without a device.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HAMLIB_VERSION="4.7.2"
HAMLIB_REV="40f63488fe0bd751b147f48d62fd217bf53713a0"
HAMLIB_REPO="https://github.com/Hamlib/Hamlib.git"
OUT_DIR="${1:-$ROOT/build/generated/hamlibJniHost}"
SRC="$ROOT/build/hamlib-host-src/$HAMLIB_REV"
BLD="$ROOT/build/hamlib-host-build"
INST="$ROOT/build/hamlib-host-install"

: "${JAVA_HOME:?JAVA_HOME must point to a JDK that ships jni.h}"
for cmd in git make autoconf automake libtoolize pkg-config cc; do
  command -v "$cmd" >/dev/null || { echo "$cmd is required" >&2; exit 2; }
done

if [ ! -d "$SRC/.git" ]; then
  rm -rf "$SRC"; mkdir -p "$(dirname "$SRC")"
  git init -q "$SRC"
  git -C "$SRC" remote add origin "$HAMLIB_REPO"
  git -C "$SRC" fetch -q --depth 1 origin "$HAMLIB_REV"
  git -C "$SRC" checkout -q --detach FETCH_HEAD
fi
actual="$(git -C "$SRC" rev-parse HEAD)"; [ "$actual" = "$HAMLIB_REV" ] || { echo "Hamlib revision mismatch" >&2; exit 1; }
git -C "$SRC" reset -q --hard "$HAMLIB_REV"; git -C "$SRC" clean -q -fdx
(cd "$SRC" && ./bootstrap)

rm -rf "$BLD" "$INST"; mkdir -p "$BLD" "$INST" "$OUT_DIR"
(cd "$BLD" && CFLAGS="-O2 -fPIC" "$SRC/configure" --prefix="$INST" --without-libusb --disable-static --enable-shared \
  && make -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)" V=0 --no-print-directory \
  && make install --no-print-directory)

cc -shared -fPIC -O2 -Wall \
  -DHAMLIB_JNI_PINNED_VERSION="\"$HAMLIB_VERSION\"" \
  -DHAMLIB_JNI_PINNED_REVISION="\"$HAMLIB_REV\"" \
  -I"$INST/include" \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o "$OUT_DIR/libaprs_hamlib.so" \
  "$ROOT/native/hamlib-jni/hamlib_jni.c" \
  -L"$INST/lib" -lhamlib -pthread \
  -Wl,-rpath,'$ORIGIN'

# The JNI library resolves libhamlib.so through $ORIGIN, so both live together.
cp -f "$INST"/lib/libhamlib.so* "$OUT_DIR"/
ldd "$OUT_DIR/libaprs_hamlib.so" | grep -q "$OUT_DIR/libhamlib.so" \
  || echo "note: $OUT_DIR/libaprs_hamlib.so did not report $OUT_DIR/libhamlib.so in ldd" >&2

echo "host aprs_hamlib $HAMLIB_VERSION $HAMLIB_REV"
echo "APRSDROID_HAMLIB_NATIVE=$OUT_DIR/libaprs_hamlib.so"
sha256sum "$OUT_DIR/libaprs_hamlib.so"
