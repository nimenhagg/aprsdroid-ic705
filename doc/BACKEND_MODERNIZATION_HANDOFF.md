# Backend Modernization Handoff

> Branch-local work log for `refactor/backend-modernization`.
> `AGENT.md` remains the authoritative engineering specification. This file only records the state of this refactor so another agent/session can resume without relying on chat context.

## Goal

Modernize APRSdroid's internal architecture incrementally while preserving user-visible behavior.

Non-negotiable constraints:

- Do not intentionally change UI, layout, animation, navigation, strings, or visual behavior in this phase.
- Preserve existing APRS behavior, preference keys, defaults, imported profiles, and stored user configuration.
- Keep `AprsService` as the Android lifecycle/foreground-service boundary when background APRS operation requires it; move orchestration and data access out incrementally rather than deleting the Service.
- Do not rewrite the stable IC-705 protocol/session/PTT/Graywolf pipelines unless a later refactor requires a narrow adapter change.
- Preserve all IC-705 recovery/PTT invariants documented in `AGENT.md`.
- Do not migrate to Hilt/Room/DataStore as a single large change. First introduce stable interfaces/facades around the legacy implementations.
- Keep commits atomic and independently understandable.
- Do not merge into `main` until the refactor has passed the planned final validation.
- CI budget for this refactor: run the full GitHub CI once at the end. The current workflows do not run on pushes to this branch, so intermediate branch commits are allowed without consuming that CI run.

## Baseline

- Base branch: `main`
- Base commit: `27cb0a30c4a8647a7868f0e58cb4c31aeef9f105` (`release: prepare Mod-v2.1.0`)
- Refactor branch: `refactor/backend-modernization`
- Estimated plan: about 8 focused implementation/debug rounds, with a small buffer if hidden coupling appears.

## Current architecture observations

- `AprsService` still owns Android lifecycle, raw preference reads/writes, backend lifecycle, location triggering, packet formatting, packet parsing/storage, message coordination, broadcasts, notifications, and its send executor.
- `PrefsWrapper` is the compatibility/storage surface used throughout legacy code. Replacing it globally in one step would create unnecessary migration risk.
- `src/data/repository/` exists, but the repository boundary is still thin compared with the amount of behavior living directly in legacy Android components.
- The IC-705 code under `src/ic705/{protocol,transport,session,backend}` is already substantially better isolated and has dedicated tests. Treat it as a stable lower-level subsystem.

## Progress

### Round 1 — typed service preference boundary

Status: implemented on this branch; CI intentionally not run.

Changes:

- Added `src/data/preferences/AprsServiceSettings.kt`.
- The new class is a typed facade over the existing `PrefsWrapper`; it does **not** change storage technology, keys, defaults, or migration behavior.
- `AprsService` now uses that facade for the settings it directly consumes: service-running state, frequency, callsign/SSID presentation, location/backend labels, digipeater path, APRS-IS battery flag, position privacy fields, symbol/status, and connection logging.
- `AprsService.prefs` remains available unchanged for current backend/location factories, so this is an incremental boundary rather than a flag-day migration.

Validation notes:

- GitHub CI has not been run by design.
- The execution environment used by this agent cannot resolve `github.com` from the local container, so a local Gradle checkout/build was not available in this session.
- Source was read from the GitHub connector and the change is intentionally delegation-only; no preference key/default or control-flow semantics are meant to change.
- Before the final CI, do a full debug/unit/lint pass in an environment with the Android SDK/Gradle dependencies available.

## Next recommended round

Extract **backend lifecycle orchestration** from `AprsService` without changing backend implementations.

Suggested shape:

1. Introduce a small service-owned coordinator/controller responsible for:
   - stopping the old poster;
   - creating the current backend through the existing `AprsBackend` factory;
   - starting/stopping the poster;
   - reporting start success back to `AprsService`.
2. Keep `AprsBackend` and IC-705 session internals unchanged in that round.
3. Do not move Android notifications/broadcasts at the same time; keep the commit narrowly about backend lifecycle ownership.
4. Add a seam that can later be unit-tested without constructing the full Android `Service`.

After that, progressively extract location triggering, packet send execution, and packet persistence/parsing boundaries.

## Resume protocol

At the start of every future session/agent handoff:

1. Read `AGENT.md`.
2. Read this file.
3. Inspect the current head of `refactor/backend-modernization` and compare it with the commit recorded below.
4. If code and this document disagree, trust code/reproducible verification and update this document in the same change.
5. Continue from the first unfinished item; do not redo completed rounds unless fixing a regression.
6. Update this handoff after each logical round with:
   - what changed;
   - important design decisions;
   - validation performed/not performed;
   - known risks;
   - exact next step.

## Last known branch state

The exact commit SHA is intentionally updated after each round. If this line is stale because the document is part of the commit it describes, use the branch HEAD as the authoritative SHA and update this section in the next round.

- Round completed: 1
- CI runs consumed for this refactor: 0
- Final full CI: pending
