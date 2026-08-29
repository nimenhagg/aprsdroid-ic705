#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${1:-$ROOT/release-apks}"
MANIFEST="$ROOT/native/graywolf-jni/Cargo.toml"
GRAYWOLF_REV="$(sed -nE 's/^graywolfmodem = \{.*rev = "([0-9a-f]{40})".*$/\1/p' "$MANIFEST")"

if [[ ! "$GRAYWOLF_REV" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Could not read pinned Graywolf revision from $MANIFEST" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

SOURCE_DIR="$TMP_DIR/graywolf"
git clone --filter=blob:none --no-checkout https://github.com/chrissnell/graywolf.git "$SOURCE_DIR"
git -C "$SOURCE_DIR" checkout --detach "$GRAYWOLF_REV"

ARCHIVE="$OUTPUT_DIR/Graywolf-source-$GRAYWOLF_REV.tar.gz"
git -C "$SOURCE_DIR" archive \
  --format=tar.gz \
  --prefix="graywolf-$GRAYWOLF_REV/" \
  "$GRAYWOLF_REV" > "$ARCHIVE"

test -s "$ARCHIVE"
sha256sum "$ARCHIVE"
ls -lh "$ARCHIVE"
