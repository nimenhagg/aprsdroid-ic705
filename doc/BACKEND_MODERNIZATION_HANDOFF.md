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
- Avoid repeated intermediate CI. One explicitly requested interim debug CI was started after Round 3; the final full CI is still required at the end.

## Baseline

- Base branch: `main`
- Base commit: `27cb0a30c4a8647a7868f0e58cb4c31aeef9f105` (`release: prepare Mod-v2.1.0`)
- Refactor branch: `refactor/backend-modernization`
- Estimated plan: about 8 focused implementation/debug rounds, with a small buffer if hidden coupling appears.

## Current architecture observations

- `AprsService` still owns Android lifecycle, packet formatting, packet parsing/storage, message coordination, broadcasts, and notifications.
- Raw preference access used directly by `AprsService` is behind `AprsServiceSettings`.
- Backend instance ownership/start/stop/update is behind `BackendLifecycleCoordinator`; Android notification/broadcast behavior remains in `AprsService`.
- Immediate GPS/network/passive location acquisition is behind `ImmediateLocationCoordinator`; `postLocation()` and packet generation remain in `AprsService`.
- Packet send worker ownership, backend-update execution and send result policy are behind `PacketSendCoordinator`; TX/error persistence callbacks still execute on the worker thread and final completion still returns through the main-thread handler.
- `PrefsWrapper` is still the compatibility/storage surface used by legacy backends and location factories. Replacing it globally in one step would create unnecessary migration risk.
- `src/data/repository/` exists, but the repository boundary is still thin compared with the amount of behavior living directly in legacy Android components.
- The IC-705 code under `src/ic705/{protocol,transport,session,backend}` is already substantially better isolated and has dedicated tests. Treat it as a stable lower-level subsystem.

## Progress

### Round 1 — typed service preference boundary

Status: implemented.

Changes:

- Added `src/data/preferences/AprsServiceSettings.kt`.
- The new class is a typed facade over the existing `PrefsWrapper`; it does **not** change storage technology, keys, defaults, or migration behavior.
- `AprsService` uses that facade for the settings it directly consumes: service-running state, frequency, callsign/SSID presentation, location/backend labels, digipeater path, APRS-IS battery flag, position privacy fields, symbol/status, and connection logging.
- `AprsService.prefs` remains available unchanged for current backend/location factories, so this is an incremental boundary rather than a flag-day migration.

### Round 2 — backend lifecycle ownership

Status: implemented.

Changes:

- Added `src/service/BackendLifecycleCoordinator.kt`.
- Added a narrow `ServiceBackend` contract plus `AprsBackendServiceAdapter`; the legacy `AprsBackend` hierarchy and all concrete backend implementations remain unchanged.
- `BackendLifecycleCoordinator` owns the current backend instance and is responsible for replacement/start, teardown, and packet update delegation.
- `AprsService.poster` was removed. `AprsService.startPoster()`, `onDestroy()`, and packet updates now delegate backend ownership to the coordinator while keeping notifications, broadcasts, parser/storage behavior and Android lifecycle decisions in the Service.
- A backend whose `start()` returns false remains owned so teardown still calls `stop()`, preserving the old cleanup behavior.
- Added `test/java/org/aprsdroid/app/service/BackendLifecycleCoordinatorTest.kt` covering replacement order, failed-start cleanup ownership, and idempotent stop without constructing an Android `Service`.

### Round 3 — immediate location acquisition ownership

Status: implemented.

Changes:

- Added `src/service/ImmediateLocationCoordinator.kt`.
- Moved immediate-location Android orchestration out of `AprsService`: fixed-position dispatch, cached GPS/network/passive lookup, newest cached location selection, one-shot GPS/network listener registration, listener removal, and the existing 15-second cleanup timeout now live in the coordinator.
- `AprsService.triggerImmediateLocation()` is reduced to delegating the current `LocationSource`; `postLocation()`, APRS packet formatting, send behavior, notification behavior, and `LocationSource` implementations remain unchanged.
- The manual `FixedPosition.start(true)` branch is intentionally preserved as-is, including its existing side effects.
- Provider order remains GPS → network → passive for cached reads; equal timestamps keep the first candidate, matching the previous `>` comparison semantics.
- Added `src/service/LocationSelection.kt` and `LocationSelectionTest.kt` for pure host-JVM verification of newest-location selection.

### Interim debug CI after Round 3

User explicitly requested one CI run while Round 4 continued in parallel.

- Trigger commit: `a1e5701e3c8f604defe473ae925d7b55aa571efc`.
- Workflow run: `33269561545` (`Backend Modernization CI`).
- Scope: `testArm64OpenglDebugUnitTest`, `lintArm64OpenglDebug`, `assembleArm64OpenglDebug`.
- The workflow is one-shot by path filtering: only `.github/ci-trigger/backend-modernization` triggers it, so later refactor commits do not automatically consume more CI runs.
- At the Round 4 handoff snapshot, checkout/JDK/setup steps had passed and the Gradle validation step was in progress.
- This run validates the branch state through Round 3 plus the CI-trigger commit; it does **not** validate Round 4 changes made afterward.
- `.github/workflows/backend-modernization-ci.yml` and `.github/ci-trigger/backend-modernization` are temporary branch-only CI plumbing and must be removed before final merge to `main`.

### Round 4 — packet send execution ownership

Status: implemented on this branch; Round 4 itself has not yet been CI-validated.

Changes:

- Added `src/service/PacketSendCoordinator.kt`.
- The coordinator owns the single-thread executor previously created directly by `AprsService`.
- `AprsService.sendPacket()` now only delegates the packet and optional status postfix to the coordinator.
- Backend `update(packet)` still runs on one serialized worker thread.
- The legacy `"No poster"` fallback is preserved.
- Successful TX persistence still runs on the worker thread via the existing `addPost(TYPE_TX, status, packetText)` path before the final completion callback.
- Backend/update/packet-string/TX-persistence exceptions still enter the legacy error path: `addPost(TYPE_ERROR, "Error", exception.toString())`, stack trace reporting, and the exception text as the final status.
- Only the final `sendPacketFinished(status)` callback is marshalled back through the existing main-thread `Handler`.
- `AprsService.onDestroy()` now shuts down the coordinator-owned executor instead of an executor field on the Service.
- Added `PacketSendCoordinatorTest.kt` covering postfix behavior, `No poster`, backend exceptions, and TX-persistence exceptions using the pure `executePacketSend()` policy seam.

Validation notes:

- No UI, Compose, navigation, animation, map, packet formatting, database schema, IC-705 session/PTT, Graywolf, preference keys, or backend implementations are intentionally changed in Round 4.
- The current agent environment still cannot resolve `github.com`, so local Gradle execution is unavailable; the interim GitHub CI is running against the Round 3 snapshot instead.
- Final full CI must still validate all rounds together before merge.

## Next recommended round

Extract **packet persistence/parsing boundaries** from `AprsService` without changing database schema or APRS parser behavior.

Suggested shape:

1. Introduce a service-facing packet/post repository/coordinator around the existing `StorageDatabase` rather than migrating storage technology.
2. Move `addPost()` persistence/routing responsibilities and position persistence behind that boundary while keeping Android broadcasts/notifications at the Service edge where practical.
3. Preserve the exact rule that POST/INCMG/TX entries are parsed while INFO/ERROR entries are only logged/broadcast.
4. Preserve third-party packet unwrapping, own-digipeat detection, message dispatch, position/object handling, and current error swallowing semantics.
5. Add pure parser/routing decision seams where possible; do not introduce Room, coroutines, Hilt, or a schema migration in the same round.

After that, consolidate service state/error ownership and perform the regression/debug cleanup rounds, remove the temporary CI plumbing, then run the final full CI once.

## Resume protocol

At the start of every future session/agent handoff:

1. Read `AGENT.md`.
2. Read this file.
3. Inspect the current head of `refactor/backend-modernization` and compare it with the branch state described here.
4. Check workflow run `33269561545` if its result was not yet recorded in a later update.
5. If code and this document disagree, trust code/reproducible verification and update this document in the same change.
6. Continue from the first unfinished item; do not redo completed rounds unless fixing a regression.
7. Update this handoff after each logical round with what changed, validation performed/not performed, known risks, and exact next step.

## Last known branch state

- Round completed: 4
- Intermediate CI runs explicitly requested/consumed: 1
- Final full CI: pending
- Temporary CI plumbing cleanup: pending
