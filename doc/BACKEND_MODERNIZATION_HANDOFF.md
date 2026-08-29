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

- `AprsService` still owns Android lifecycle, location triggering, packet formatting, packet parsing/storage, message coordination, broadcasts, notifications, and its send executor.
- Raw preference access used directly by `AprsService` is now behind `AprsServiceSettings`.
- Backend instance ownership/start/stop/update is now behind `BackendLifecycleCoordinator`; Android notification/broadcast behavior remains in `AprsService`.
- `PrefsWrapper` is still the compatibility/storage surface used by legacy backends and location factories. Replacing it globally in one step would create unnecessary migration risk.
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

### Round 2 — backend lifecycle ownership

Status: implemented on this branch; CI intentionally not run.

Changes:

- Added `src/service/BackendLifecycleCoordinator.kt`.
- Added a narrow `ServiceBackend` contract plus `AprsBackendServiceAdapter`; the legacy `AprsBackend` hierarchy and all concrete backend implementations remain unchanged.
- `BackendLifecycleCoordinator` now owns the current backend instance and is responsible for replacement/start, teardown, and packet update delegation.
- `AprsService.poster` was removed. `AprsService.startPoster()`, `onDestroy()`, and `sendPacket()` now delegate backend ownership to the coordinator while keeping notifications, broadcasts, parser/storage behavior and Android lifecycle decisions in the Service.
- A backend whose `start()` returns false remains owned so teardown still calls `stop()`, preserving the old cleanup behavior.
- Teardown clears coordinator ownership before calling `stop()`, making repeated teardown idempotent and preventing post-stop packet updates from reaching a stopped backend.
- Added `test/java/org/aprsdroid/app/service/BackendLifecycleCoordinatorTest.kt` covering replacement order, failed-start cleanup ownership, and idempotent stop without constructing an Android `Service`.

Validation notes:

- GitHub CI has not been run by design; CI runs consumed remain 0.
- No UI, Compose, navigation, animation, map, IC-705 session/PTT, Graywolf, preference key, or backend implementation files are intentionally changed in this round.
- Local Gradle execution is still unavailable in the current agent environment, so the new JVM tests are source-reviewed but not executed here.
- Final validation must execute the normal debug/unit/lint suite and release build once the planned refactor rounds are complete.

## Next recommended round

Extract **immediate location acquisition/orchestration** from `AprsService` while preserving `LocationSource` behavior.

Suggested shape:

1. Introduce a service-facing location coordinator responsible for:
   - using fixed-position immediately when configured;
   - selecting the newest cached GPS/network/passive location;
   - requesting one-shot GPS/network updates when no cached location exists;
   - cancelling the temporary listener after the existing 15-second timeout.
2. Keep APRS packet formatting and `postLocation()` in `AprsService` for that round; the coordinator should report a `Location` back through a callback rather than format/transmit packets.
3. Preserve the current permission/error swallowing behavior unless a dedicated behavior change is explicitly planned.
4. Add pure decision tests where possible (for example newest cached location selection) without requiring Android framework objects.

After that, extract packet send execution, then packet persistence/parsing boundaries.

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

- Round completed: 2
- CI runs consumed for this refactor: 0
- Final full CI: pending
