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
- Avoid repeated intermediate CI. One explicitly requested interim debug CI was run after Round 3; the final full CI is still required at the end.

## Baseline

- Base branch: `main`
- Base commit: `27cb0a30c4a8647a7868f0e58cb4c31aeef9f105` (`release: prepare Mod-v2.1.0`)
- Refactor branch: `refactor/backend-modernization`
- Estimated plan: about 8 focused implementation/debug rounds, with a small buffer if hidden coupling appears.

## Current architecture observations

- `AprsService` still owns Android lifecycle, command dispatch, service state, message coordination, broadcasts, notifications, and APRS packet formatting.
- Raw preference access used directly by `AprsService` is behind `AprsServiceSettings`.
- Backend instance ownership/start/stop/update is behind `BackendLifecycleCoordinator`; Android notification/broadcast behavior remains in `AprsService`.
- Immediate GPS/network/passive location acquisition is behind `ImmediateLocationCoordinator`; `postLocation()` and packet generation remain in `AprsService`.
- Packet send worker ownership, backend-update execution and send result policy are behind `PacketSendCoordinator`; TX/error persistence callbacks still execute on the worker thread and final completion still returns through the main-thread handler.
- Post/position persistence and APRS parser routing are behind `PacketPersistenceCoordinator` + `PacketPostRepository`; Android broadcasts/notifications remain callbacks at the Service edge.
- `PrefsWrapper` is still the compatibility/storage surface used by legacy backends and location factories. Replacing it globally in one step would create unnecessary migration risk.
- The IC-705 code under `src/ic705/{protocol,transport,session,backend}` remains a stable lower-level subsystem and has not been rewritten by this refactor.

## Progress

### Round 1 — typed service preference boundary

Status: implemented.

- Added `src/data/preferences/AprsServiceSettings.kt`.
- The facade preserves the existing `PrefsWrapper`, keys, defaults and migration behavior.
- `AprsService` uses typed access for the settings it directly consumes.

### Round 2 — backend lifecycle ownership

Status: implemented.

- Added `src/service/BackendLifecycleCoordinator.kt`.
- Added a narrow `ServiceBackend` contract plus `AprsBackendServiceAdapter`; concrete backend implementations remain unchanged.
- Backend replacement/start/stop/update ownership moved out of `AprsService`.
- Added JVM coverage for replacement order, failed-start cleanup and idempotent stop.

### Round 3 — immediate location acquisition ownership

Status: implemented.

- Added `src/service/ImmediateLocationCoordinator.kt`.
- Moved cached GPS/network/passive lookup, newest-location selection, one-shot listener registration/removal and the existing 15-second timeout out of `AprsService`.
- Manual `FixedPosition.start(true)` behavior and side effects are preserved.
- Added pure host-JVM tests for cached-location selection.

### Interim debug CI after Round 3

User explicitly requested one CI run while Round 4 continued in parallel.

- Trigger commit: `a1e5701e3c8f604defe473ae925d7b55aa571efc`.
- Workflow run: `33269561545` (`Backend Modernization CI`).
- Scope: `testArm64OpenglDebugUnitTest`, `lintArm64OpenglDebug`, `assembleArm64OpenglDebug`.
- Result: **success**.
- This run validates the branch through Round 3 plus the CI-trigger commit; it does not validate Round 4 or later changes.
- The workflow is one-shot by path filtering, so subsequent refactor commits do not trigger more intermediate CI.
- `.github/workflows/backend-modernization-ci.yml` and `.github/ci-trigger/backend-modernization` are temporary branch-only CI plumbing and must be removed before final merge to `main`.

### Round 4 — packet send execution ownership

Status: implemented; not included in the interim CI snapshot.

- Added `src/service/PacketSendCoordinator.kt`.
- The coordinator owns the single-thread executor previously created by `AprsService`.
- Backend `update(packet)` remains serialized off the main thread.
- The legacy `"No poster"` fallback, TX/error persistence ordering, exception text result and main-thread completion callback are preserved.
- Added JVM tests covering normal send, no backend, backend exceptions and TX-persistence exceptions.

### Round 5 — post persistence and APRS parsing boundary

Status: implemented on this branch; final CI still pending.

Changes:

- Added `src/data/repository/PacketPostRepository.kt` with a narrow `PacketPostRepository` interface and `StorageDatabasePacketPostRepository` adapter.
- The adapter delegates to the existing `StorageDatabase.addPost()` / `addPosition()` methods unchanged; there is no database schema or storage migration.
- Added `src/service/PacketPersistenceCoordinator.kt`.
- Moved the following responsibilities out of `AprsService`:
  - post persistence timestamping and status normalization;
  - the exact POST/INCMG/TX parse-vs-log routing rule;
  - APRS parsing and third-party packet unwrapping;
  - own-digipeat detection;
  - Position/Object/Message type dispatch;
  - position persistence and course/speed extension extraction.
- Android-specific effects remain at the `AprsService` boundary through callbacks: own-digipeat notification, position/update broadcasts, message delivery and logging.
- Existing public/service-facing `parsePacket()`, `getCSE()`, `addPosition()` and `addPost()` methods remain as compatibility wrappers, reducing risk to legacy callers/backends.
- Parsing exception behavior remains intentionally swallowed/reported through the same Service log + stack trace path.
- Post persistence still occurs before parsing, and UPDATE broadcast still occurs after parsing/log-only handling, matching the legacy order.
- Added `PacketPersistenceCoordinatorTest.kt` covering the exact legacy post routing types, log-only ordering, persistence-before-parse and update-after-parse-failure behavior.

Validation notes:

- No UI, Compose, navigation, animation, map, database schema, IC-705 session/PTT, Graywolf, preference keys or backend implementations are intentionally changed in Round 5.
- The earlier interim CI passed, but it predates Rounds 4 and 5.
- Final full CI must validate all rounds together before merge.

## Next recommended round

Consolidate **service state / error / post-event orchestration** while preserving Android lifecycle behavior.

Suggested shape:

1. Extract the `postAddPost()` policy around main-thread dispatch, INFO logging suppression, incoming-message retry kick and ERROR-triggered `stopSelf()` into a small service coordinator/policy seam.
2. Reduce direct mutation of global `running` / `link_error` where possible behind a service-owned state holder without changing existing static compatibility fields yet.
3. Keep `ServiceNotifier`, Android broadcasts and `stopSelf()` at the Android edge through callbacks.
4. Do not migrate to coroutines, Hilt, Room or DataStore in this round.
5. Add pure policy tests for event decisions and error transitions.

After that, perform integration/regression cleanup, remove temporary CI plumbing, inspect R8/build warnings if any, and run the final full CI once.

## Resume protocol

At the start of every future session/agent handoff:

1. Read `AGENT.md`.
2. Read this file.
3. Inspect the current head of `refactor/backend-modernization` and compare it with the branch state described here.
4. If code and this document disagree, trust code/reproducible verification and update this document in the same change.
5. Continue from the first unfinished item; do not redo completed rounds unless fixing a regression.
6. Update this handoff after each logical round with what changed, validation performed/not performed, known risks, and exact next step.

## Last known branch state

- Round completed: 5
- Intermediate CI runs explicitly requested/consumed: 1
- Interim CI result: success (through Round 3)
- Final full CI: pending
- Temporary CI plumbing cleanup: pending
