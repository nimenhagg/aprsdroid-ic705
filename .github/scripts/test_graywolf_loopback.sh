#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$ROOT/native/graywolf-jni/Cargo.toml"

export GRAYWOLF_VERSION="0.14.13"
export GRAYWOLF_GIT_COMMIT="34cd0111"
export CARGO_NET_GIT_FETCH_WITH_CLI=true

cargo test \
  --manifest-path "$MANIFEST" \
  --lib \
  synthetic_12khz_ax25_round_trip \
  -- --nocapture
