#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ABI="${1:-arm64-v8a}"
OUTPUT="${2:-$ROOT/build/generated/rustJniLibs/$ABI/libaprs_graywolf.so}"
NDK_VERSION="28.2.13676358"
ANDROID_API=27
GRAYWOLF_VERSION="0.14.13"
GRAYWOLF_REV="34cd0111b7a40e7d91607699b7b4dd188574970a"

case "$ABI" in
  arm64-v8a)
    TARGET="aarch64-linux-android"
    LINKER_PREFIX="aarch64-linux-android"
    CARGO_TARGET_ENV="AARCH64_LINUX_ANDROID"
    ;;
  armeabi-v7a)
    TARGET="armv7-linux-androideabi"
    LINKER_PREFIX="armv7a-linux-androideabi"
    CARGO_TARGET_ENV="ARMV7_LINUX_ANDROIDEABI"
    ;;
  *)
    echo "Unsupported Graywolf ABI: $ABI" >&2
    exit 2
    ;;
esac

: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
command -v cargo >/dev/null || { echo "cargo is required" >&2; exit 2; }
command -v rustup >/dev/null || { echo "rustup is required" >&2; exit 2; }
command -v protoc >/dev/null || { echo "protoc is required (protobuf-compiler on Ubuntu)" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 is required for ELF alignment verification" >&2; exit 2; }

MANIFEST="$ROOT/native/graywolf-jni/Cargo.toml"
NDK="$ANDROID_HOME/ndk/$NDK_VERSION"
if [ ! -d "$NDK" ]; then
  SDKMANAGER="$(command -v sdkmanager || true)"
  if [ -z "$SDKMANAGER" ]; then
    SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
  fi
  yes | "$SDKMANAGER" "ndk;$NDK_VERSION" >/dev/null
fi

HOST_TAG="linux-x86_64"
case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
  Linux) ;;
  *) echo "Unsupported build host: $(uname -s)" >&2; exit 2 ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
LINKER="$TOOLCHAIN/bin/${LINKER_PREFIX}${ANDROID_API}-clang"
STRIP="$TOOLCHAIN/bin/llvm-strip"
READELF="$TOOLCHAIN/bin/llvm-readelf"
NM="$TOOLCHAIN/bin/llvm-nm"
if [ ! -x "$LINKER" ] || [ ! -x "$STRIP" ] || [ ! -x "$READELF" ] || [ ! -x "$NM" ]; then
  echo "Required NDK LLVM tools are missing under $TOOLCHAIN" >&2
  exit 2
fi

rustup target add "$TARGET" >/dev/null

mkdir -p "$(dirname "$OUTPUT")"
OUTPUT="$(cd "$(dirname "$OUTPUT")" && pwd)/$(basename "$OUTPUT")"

export GRAYWOLF_VERSION
export GRAYWOLF_GIT_COMMIT="${GRAYWOLF_REV:0:8}"
export CARGO_NET_GIT_FETCH_WITH_CLI=true
export "CARGO_TARGET_${CARGO_TARGET_ENV}_LINKER=$LINKER"
export "CARGO_TARGET_${CARGO_TARGET_ENV}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384"

cargo build \
  --manifest-path "$MANIFEST" \
  --target "$TARGET" \
  --release \
  --locked

SOURCE="$ROOT/native/graywolf-jni/target/$TARGET/release/libaprs_graywolf.so"
if [ ! -s "$SOURCE" ]; then
  echo "Graywolf JNI library was not produced: $SOURCE" >&2
  exit 1
fi

cp "$SOURCE" "$OUTPUT"
"$STRIP" --strip-unneeded "$OUTPUT"

for symbol in \
  Java_org_aprsdroid_app_audio_GraywolfNative_nativeCreate \
  Java_org_aprsdroid_app_audio_GraywolfNative_nativeProcess \
  Java_org_aprsdroid_app_audio_GraywolfNative_nativeDestroy; do
  if ! "$NM" -D --defined-only "$OUTPUT" | awk '{print $3}' | grep -Fxq "$symbol"; then
    echo "Missing JNI export in $OUTPUT: $symbol" >&2
    exit 1
  fi
done

echo "Verified Graywolf JNI exports for $ABI"
ELF_PROGRAM_HEADERS="$(mktemp)"
trap 'rm -f "$ELF_PROGRAM_HEADERS"' EXIT
"$READELF" -lW "$OUTPUT" > "$ELF_PROGRAM_HEADERS"
python3 - "$ELF_PROGRAM_HEADERS" "$ABI" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
abi = sys.argv[2]
loads = []
for line in path.read_text(encoding="utf-8").splitlines():
    fields = line.split()
    if fields and fields[0] == "LOAD":
        try:
            loads.append(int(fields[-1], 16))
        except ValueError as exc:
            raise SystemExit(f"Could not parse LOAD alignment for {abi}: {line}") from exc
if not loads:
    raise SystemExit(f"No LOAD program headers found for {abi}")
minimum = min(loads)
if minimum < 0x4000:
    formatted = ", ".join(hex(value) for value in loads)
    raise SystemExit(
        f"Graywolf {abi} is not 16 KiB ELF-aligned: LOAD alignments={formatted}"
    )
print(
    f"Verified Graywolf {abi} ELF LOAD alignment >= 16 KiB: "
    + ", ".join(hex(value) for value in loads)
)
PY
"$READELF" -lW "$OUTPUT" | grep -E 'LOAD|GNU_RELRO' || true
sha256sum "$OUTPUT"
ls -lh "$OUTPUT"
