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
tar -tzf "$archive" | grep -q "^${prefix}COPYING.LIB$"; tar -tzf "$archive" | grep -q "^${prefix}README.android$"; tar -tzf "$archive" | grep -q "^${prefix}configure.ac$"
sha256sum "$archive"
