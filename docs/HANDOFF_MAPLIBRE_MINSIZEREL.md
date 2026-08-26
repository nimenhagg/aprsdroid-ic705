# APRSdroid IC-705 — MapLibre MinSizeRel / APK size optimization handoff

> Snapshot branch: `perf/ci-size-optimization`
>
> **Do not merge this branch yet.** At the time this handoff was written, the latest release CI and the MinSizeRel-vs-RelWithDebInfo benchmark were still running.

## 1. Repository state

- Repository: `nimenhagg/aprsdroid-ic705`
- Main SHA at snapshot: `9dea16045cc9be4229bbec9cc428065c98a3a028`
- Working branch: `perf/ci-size-optimization`
- Branch SHA at snapshot: `b473d42781e590d28d4a16e476c8e0ba49403199`
- Branch state vs main at snapshot: 13 commits ahead, 0 behind.
- No version bump, release tag, or GitHub Release is intended as part of this optimization work.

Final diff at snapshot contains these files:

- `.github/scripts/build_maplibre_minsizerel.sh` — added
- `.github/scripts/replace_apk_maplibre.py` — added
- `.github/workflows/maplibre-size-benchmark.yml` — added, temporary benchmark workflow
- `.github/workflows/release.yml` — modified
- `build.gradle` — modified
- `gradle.properties` — modified
- `res/drawable-nodpi/allicons.png` — removed
- `res/drawable-nodpi/allicons.webp` — added

## 2. User goal / required behavior

The user explicitly wants MapLibre Native to use **CMake `MinSizeRel` with interprocedural optimization (IPO/LTO)** rather than merely relying on AGP's ordinary Android Release native configuration.

Do not satisfy this by only adding a Gradle flag: APRSdroid currently consumes MapLibre through the Maven AAR, so the `.so` inside the published AAR is already compiled. A Gradle-side `MinSizeRel` setting alone would not rebuild it.

The chosen architecture is therefore:

1. Keep the official MapLibre Android 13.5.1 AAR for Java/Kotlin API, Android resources, manifests, and transitive dependencies.
2. Build only `libmaplibre.so` ourselves from the exact `android-v13.5.1` MapLibre Native source tag.
3. Build that native library with `CMAKE_BUILD_TYPE=MinSizeRel` and `CMAKE_INTERPROCEDURAL_OPTIMIZATION_MINSIZEREL=ON`.
4. Keep OpenGL enabled and Vulkan/WebGPU disabled for the OpenGL APK flavors.
5. After Gradle creates the APK, replace only `lib/<abi>/libmaplibre.so` before signing.
6. Re-run `zipalign -P 16`, then sign, then verify both the packaged `.so` hash and APK alignment.

This deliberately avoids maintaining a full MapLibre Android SDK fork.

## 3. Important upstream MapLibre facts already verified

MapLibre Native 13.5.1 top-level `CMakeLists.txt` contains:

```cmake
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_MINSIZEREL ON)
```

So a real `MinSizeRel` configure selects upstream IPO behavior.

Android Release builds do **not** simply mean CMake `Release`: AGP normally maps the Android release native build to `RelWithDebInfo`. MapLibre's Android CMake already accounts for that and applies its size-oriented options to Release / RelWithDebInfo, including `-Oz` and `-flto` in core compiler options.

However, some Android wrapper flags were gated only on `$<CONFIG:Release>`, so the custom build script extends those Release-only generator expressions to `MinSizeRel` as well. This preserves the Android wrapper's size/link behavior when using MinSizeRel, including the relevant `-Oz`, LTO, ICF, section GC, hidden visibility, lld, and version-script options.

Do not assume “MinSizeRel” automatically inherits every MapLibre Android Release-only generator expression without this patch.

## 4. `build_maplibre_minsizerel.sh`

File: `.github/scripts/build_maplibre_minsizerel.sh`

Current behavior:

- MapLibre version parameter, default `13.5.1`.
- Supported ABIs: `arm64-v8a`, `armeabi-v7a`.
- Fixed NDK: `28.2.13676358`, matching MapLibre Android 13.5.1's declared NDK version.
- Android platform: API 25.
- `ANDROID_STL=c++_static`.
- `ANDROID_CPP_FEATURES=exceptions`.
- `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`.
- OpenGL ON.
- Vulkan OFF.
- WebGPU OFF.
- GLFW OFF.
- `CMAKE_BUILD_TYPE=MinSizeRel`.
- `CMAKE_INTERPROCEDURAL_OPTIMIZATION_MINSIZEREL=ON`.
- Builds target `maplibre` only.
- Runs NDK `llvm-strip --strip-unneeded` on the final library.
- Prints ELF LOAD/RELRO information, SHA-256, and file size.
- If the output file already exists and is non-empty, exits immediately so the Actions cache can be used.

It patches a temporary MapLibre checkout only; no MapLibre source is vendored into this repository.

### Reproducibility caveat

The script currently clones `android-v13.5.1` by tag. For even stronger reproducibility, a later cleanup could resolve/pin the exact upstream commit SHA rather than trusting the tag to remain immutable. Do not change this during debugging unless needed.

## 5. APK replacement strategy

File: `.github/scripts/replace_apk_maplibre.py`

The release workflow leaves the Maven dependency unchanged and builds APRSdroid normally. In the packaging step it then:

1. Finds the flavor's generated release APK.
2. Locates the cached/custom MinSizeRel `libmaplibre.so` for the target ABI.
3. Replaces `lib/<abi>/libmaplibre.so` in a temporary APK.
4. Runs `zipalign -P 16 -f 4`.
5. Verifies alignment with `zipalign -P 16 -c 4`.
6. Signs with repository keystore secrets when available.
7. Runs `apksigner verify --verbose` for signed output.
8. Re-checks `zipalign -P 16 -c 4` on final output.
9. Extracts packaged `libmaplibre.so` and verifies its SHA-256 equals the custom build's SHA-256.
10. Verifies the APK contains exactly the intended ABI and expected single MapLibre backend library.

This hash verification is important: it proves the published APK is actually carrying the MinSizeRel library, rather than merely having a CI step that built one and then accidentally packaged the Maven AAR's original `.so`.

## 6. Actions cache behavior

Release workflow caches native outputs separately per version and ABI:

- `.cache/maplibre-minsizerel/13.5.1/arm64-v8a`
- `.cache/maplibre-minsizerel/13.5.1/armeabi-v7a`

Cache key includes:

- MapLibre version
- ABI
- `hashFiles('.github/scripts/build_maplibre_minsizerel.sh')`

First cache miss takes roughly the cost of a full MapLibre native build; later runs should skip compilation when the cached `.so` is restored.

At snapshot, the latest ARM64 native build step itself completed successfully and took about 12 minutes on the runner.

## 7. Other size/build optimizations already in this branch

### Gradle configuration cache

`gradle.properties` now has:

```properties
org.gradle.configuration-cache=true
```

A prior issue was identified with obtaining Git source revision at Gradle configuration time. `build.gradle` was changed to use Gradle Provider-based process execution for source revision so configuration-cache reuse does not incorrectly freeze an old Git SHA.

Keep an eye on CI for any configuration-cache serialization/invalidation issue.

### ARMv7 release build frequency

Normal branch pushes build/test only the recommended ARM64 OpenGL release APK. `armeabi-v7a` release assembly is restricted to tag builds.

This is intentional CI-time reduction. Tagged releases must still produce ARMv7.

### APRS symbol sheet asset

`res/drawable-nodpi/allicons.png` was converted to WebP:

- old PNG: 701,436 bytes
- new WebP: 417,068 bytes
- raw resource saving: 284,368 bytes (~40.5%)

The one-shot asset optimizer workflow used during experimentation was removed; only the resulting WebP asset remains.

## 8. PMTiles experiment — do not repeat blindly

An earlier minimal MapLibre experiment attempted `MLN_WITH_PMTILES=OFF`.

MapLibre 13.5.1 then hit a `-Werror=unused-parameter` failure in the PMTiles stub implementation. This was an upstream warning/error interaction, not evidence that MinSizeRel or the NDK toolchain was broken.

For the current comparison and production custom build, **leave PMTiles at its normal/default setting**. The purpose of the active experiment is build-mode/IPO comparison, not feature surgery.

If PMTiles removal is revisited later, handle the upstream stub warning explicitly and measure the result as a separate experiment.

## 9. Temporary benchmark workflow

File: `.github/workflows/maplibre-size-benchmark.yml`

Purpose: compare the exact same MapLibre 13.5.1 ARM64 OpenGL source/configuration under:

- `RelWithDebInfo`
- `MinSizeRel` + IPO

It builds both directly with CMake, strips both `.so` files with the same NDK `llvm-strip`, and reports byte sizes plus optimization/LTO flags.

The stripped comparison is intentional. Comparing one unstripped binary with one stripped binary would give a misleading result because `RelWithDebInfo` can contain debug information.

### Cleanup requirement

This workflow is experimental and branch-specific. **Delete `.github/workflows/maplibre-size-benchmark.yml` before final merge to main** unless the user explicitly wants to keep a permanent benchmark workflow.

## 10. Live CI state at handoff snapshot

### Release workflow

Run ID: `32945190947`

Head: `b473d42781e590d28d4a16e476c8e0ba49403199`

At snapshot:

- checkout: success
- JDK setup: success
- ARM64 MinSizeRel cache restore step: success (step success does not by itself prove cache hit)
- ARM64 MinSizeRel MapLibre build step: **success**
- Gradle tests/lint/ARM64 release build: **in progress**
- APK MinSizeRel replacement / zipalign / signing verification: pending
- artifact upload: pending

The most important remaining proof is that the packaging step passes the SHA-256 equality check between the custom `.so` and the final APK's `libmaplibre.so`.

### Size benchmark

Run ID: `32945190881`

Head: `b473d42781e590d28d4a16e476c8e0ba49403199`

At snapshot:

- clone 13.5.1: success
- fixed Android toolchain preparation: success
- MinSizeRel flag extension: success
- building RelWithDebInfo + MinSizeRel ARM64 OpenGL: **in progress**
- stripped byte comparison: pending

## 11. Exact next steps for the next agent

1. Check run `32945190947` first.
   - If it fails, inspect the failing job logs before changing code.
   - If failure happens in Gradle, keep it separate from native packaging diagnosis.
   - If failure happens in `Prepare, Verify, and Sign Release APKs`, focus on APK replacement, `zipalign -P 16`, hash verification, or signing paths.

2. Check benchmark run `32945190881`.
   - Record stripped RelWithDebInfo bytes.
   - Record stripped MinSizeRel bytes.
   - Record absolute and percentage delta.
   - Confirm both command lines show the expected optimization/LTO flags.

3. If release CI is green, inspect/download the generated ARM64 artifact and compare total APK size against a recent main ARM64 artifact. Native `.so` delta and whole-APK delta should both be reported.

4. Do not silently discard MinSizeRel merely because the size saving is small: the user explicitly wants the MinSizeRel/IPO path. If MinSizeRel is unexpectedly larger or causes a compatibility issue, report the measured result and reason before changing strategy.

5. Before creating the final PR/merge:
   - remove `.github/workflows/maplibre-size-benchmark.yml` unless explicitly retained;
   - remove `perf/ci-size-optimization` from the `release.yml` push branch list, restoring normal main-only branch CI trigger plus tags;
   - keep the reusable MinSizeRel build/replacement scripts;
   - confirm `main` has not advanced or rebase/merge main if needed;
   - re-run the production workflow after cleanup.

6. Create a PR from `perf/ci-size-optimization` to `main` only after final CI is green.

7. Prefer squash merge because this branch contains experimental intermediate commits (including one-shot optimizer experiments and benchmark iterations).

8. After merge, verify the main branch release workflow again.

9. Do **not** create a tag, bump app version, or publish a GitHub Release unless the user separately requests it.

## 12. Likely final files to merge

Expected persistent changes after cleanup:

- `.github/scripts/build_maplibre_minsizerel.sh`
- `.github/scripts/replace_apk_maplibre.py`
- `.github/workflows/release.yml`
- `build.gradle`
- `gradle.properties`
- `res/drawable-nodpi/allicons.webp` replacing `allicons.png`

Expected temporary file to remove:

- `.github/workflows/maplibre-size-benchmark.yml`

This handoff file itself may be kept or removed at the user's preference.

## 13. Design constraints / things not to “simplify” accidentally

- Do not replace the official MapLibre Maven dependency with a full local SDK fork unless there is a concrete need.
- Do not claim the app uses MinSizeRel merely because a MinSizeRel binary was built; verify the final APK contains that exact `.so` by hash.
- Do not compare unstripped RelWithDebInfo size to stripped MinSizeRel size.
- Do not disable PMTiles as part of the MinSizeRel measurement.
- Keep 16 KiB page-size-aware APK alignment (`zipalign -P 16`).
- Keep MapLibre version synchronized between Maven dependency and custom native source build (`13.5.1` at snapshot).
- Keep ABI-specific caches separate.
- Keep ARMv7 generation on tagged releases even though ordinary branch CI skips it.
- Do not merge while the current live CI results are unknown.
