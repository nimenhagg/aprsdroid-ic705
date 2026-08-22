# Vendored Scalroid build plugin

This directory contains the production sources from the published
`cash.bdo.scalroid:1.6-gradle8` Gradle plugin. They are vendored because that
release assumes POSIX path separators when locating Android's generated
`R.jar`, which prevents APRSdroid from configuring on Windows.

- Upstream: https://github.com/chenakam/scalroid
- Published source artifact: `cash.bdo.dev:buildSrc:1.6-gradle8:sources`
- Source artifact SHA-256:
  `d89dc2c7b1f985ecb764a0e5b577d46f212d511641f6ea342eb72bc8a108bf13`
- License: Apache License 2.0; see `LICENSE`.

The APRSdroid changes are intentionally limited to platform-neutral `R.jar`
discovery, separator-neutral source-directory matching, a diagnostic error
when AGP does not expose the expected file, skipping the de-duplication task
when a source set has no Scala output, and package-aware Java class
de-duplication for APRSdroid's legacy flat source directory.
