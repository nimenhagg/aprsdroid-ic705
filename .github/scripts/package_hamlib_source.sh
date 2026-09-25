#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${1:-$ROOT/release-apks}"
VER="4.7.2"; REV="40f63488fe0bd751b147f48d62fd217bf53713a0"; REPO="https://github.com/Hamlib/Hamlib.git"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT; src="$tmp/Hamlib"
git init -q "$src"; git -C "$src" remote add origin "$REPO"; git -C "$src" fetch -q --depth 1 origin "$REV"; git -C "$src" checkout -q --detach FETCH_HEAD
[ "$(git -C "$src" rev-parse HEAD)" = "$REV" ] || exit 1
mkdir -p "$OUT_DIR"; short="${REV:0:12}"; archive="$OUT_DIR/Hamlib-source-${VER}-${short}.tar.gz"; prefix="Hamlib-${VER}-${short}/"
git -C "$src" archive --format=tar --prefix="$prefix" "$REV" | gzip -n -9 > "$archive"
# List the archive once into a file and grep that file. Piping `tar -t` straight
# into `grep -q` makes tar fail with EPIPE as soon as grep exits on its first
# match, and `set -o pipefail` then turns a successful listing into a failed
# step. Hamlib's listing easily exceeds the pipe buffer.
listing="$tmp/archive-listing.txt"
tar -tzf "$archive" > "$listing"
for entry in COPYING.LIB README.android configure.ac; do
  grep -Fxq "${prefix}${entry}" "$listing" || { echo "Hamlib source archive is missing ${entry}" >&2; exit 1; }
done
sha256sum "$archive"
