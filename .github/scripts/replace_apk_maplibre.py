#!/usr/bin/env python3
import copy
import hashlib
import sys
import zipfile
from pathlib import Path


def die(message: str) -> None:
    raise SystemExit(message)


if len(sys.argv) != 5:
    die("usage: replace_apk_maplibre.py INPUT.apk ABI libmaplibre.so OUTPUT.apk")

src = Path(sys.argv[1])
abi = sys.argv[2]
so = Path(sys.argv[3])
out = Path(sys.argv[4])
target = f"lib/{abi}/libmaplibre.so"

if not src.is_file():
    die(f"APK not found: {src}")
if not so.is_file():
    die(f"custom MapLibre library not found: {so}")
if src.resolve() == out.resolve():
    die("input and output APK paths must differ")

replacement = so.read_bytes()
found = 0
out.parent.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(out, "w", allowZip64=True) as zout:
    for info in zin.infolist():
        # The workflow patches the unsigned Gradle output, but dropping stale JAR
        # signature entries makes this helper safe if that ever changes.
        upper = info.filename.upper()
        if upper.startswith("META-INF/") and upper.endswith((".RSA", ".DSA", ".EC", ".SF")):
            continue

        new_info = copy.copy(info)
        if info.filename == target:
            found += 1
            zout.writestr(new_info, replacement, compress_type=info.compress_type)
        else:
            zout.writestr(new_info, zin.read(info), compress_type=info.compress_type)

if found != 1:
    out.unlink(missing_ok=True)
    die(f"expected exactly one {target} entry, found {found}")

print(f"Replaced {target}")
print(f"libmaplibre.so sha256={hashlib.sha256(replacement).hexdigest()}")
print(f"output={out}")
