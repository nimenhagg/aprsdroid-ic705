# Backend Modernization Handoff

> Branch-local work log for `refactor/backend-modernization`.
> `AGENT.md` remains the authoritative engineering specification. This file records refactor state so another agent/session can resume without relying on chat context.

## Goal

Modernize APRSdroid's internal architecture incrementally while preserving user-visible behavior.

Non-negotiable constraints:

- Do not intentionally change UI, layout, animation, navigation, strings, or visual behavior in this phase.
- Preserve existing APRS behavior, preference keys, defaults, imported profiles, and stored user configuration.
- Keep `AprsService` as the Android lifecycle/foreground-service boundary; move orchestration/data access out incrementally rather than deleting it.
- Do not rewrite stable IC-705 protocol/session/PTT/Graywolf pipelines except for a narrow adapter when strictly required.
- Preserve all IC-705 recovery/PTT invariants documented in `AGENT.md`.
- Do not migrate to Hilt/Room/DataStore as one large change. First introduce stable interfaces/facades around legacy implementations.
- Keep implementation rounds atomic and reviewable.
- Do not merge into `main` until planned final validation passes.
- Two interim debug CI runs were explicitly requested by the user; the final full CI is still required before merge.

## Baseline

- Base branch: `main`
- Base commit: `27cb0a30c4a8647a7868f0e58cb4c31aeef9f105` (`release: prepare Mod-v2.1.0`)
- Refactor branch: `refactor/backend-modernization`
- Estimated plan: about 8 focused implementation/debug rounds, with a small buffer if hidden coupling appears.

## Current architecture observations

- `AprsService` still owns Android lifecycle/command dispatch, broadcasts/notifications, APRS packet formatting, and compatibility entry points.
- Direct service preference reads/writes are behind `AprsServiceSettings`.
- Backend instance ownership/start/stop/update is behind `BackendLifecycleCoordinator`.
- Immediate GPS/network/passive acquisition is behind `ImmediateLocationCoordinator`.
- Packet send worker ownership/update execution/result policy is behind `PacketSendCoordinator`.
- Post/position persistence and APRS parser routing are behind `PacketPersistenceCoordinator` + `PacketPostRepository`.
- Main-thread post-event follow-up policy is behind `ServicePostCoordinator`.
- Runtime `running` / `link_error` mutation is behind `ServiceRuntimeState`, but the legacy static fields remain the actual compatibility backing storage and are intentionally not removed yet.
- `PrefsWrapper` remains the compatibility/storage surface for legacy backend/location factories.
- The IC-705 code under `src/ic705/{protocol,transport,session,backend}` remains a stable lower-level subsystem and has not been rewritten by this refactor.

## Progress

### Round 1 — typed service preference boundary

Status: implemented.

- Added `src/data/preferences/AprsServiceSettings.kt`.
- Preserved `PrefsWrapper`, keys, defaults and migration behavior.
- `AprsService` uses typed access for settings it directly consumes.

### Round 2 — backend lifecycle ownership

Status: implemented.

- Added `src/service/BackendLifecycleCoordinator.kt` and a narrow `ServiceBackend` adapter contract.
- Backend replacement/start/stop/update ownership moved out of `AprsService`.
- Concrete backend implementations remain unchanged.
- Added JVM coverage for replacement order, failed-start cleanup and idempotent stop.

### Round 3 — immediate location acquisition ownership

Status: implemented.

- Added `src/service/ImmediateLocationCoordinator.kt`.
- Moved cached GPS/network/passive lookup, newest-location selection, one-shot listener registration/removal and the existing 15-second timeout out of `AprsService`.
- Manual `FixedPosition.start(true)` behavior and side effects are preserved.
- Added pure host-JVM tests for cached-location selection.

### Interim debug CI #1 after Round 3

- Trigger commit: `a1e5701e3c8f604defe473ae925d7b55aa571efc`.
- Workflow run: `33269561545` (`Backend Modernization CI`).
- Scope: `testArm64OpenglDebugUnitTest`, `lintArm64OpenglDebug`, `assembleArm64OpenglDebug`.
- Result: **success**.
- This validated the branch through Round 3 only.

### Round 4 — packet send execution ownership

Status: implemented.

- Added `src/service/PacketSendCoordinator.kt`.
- Single-thread send executor moved out of `AprsService`.
- Preserved serialized backend `update(packet)`, `"No poster"`, TX/error persistence ordering, exception-result behavior and main-thread completion callback.
- Added JVM tests for normal send, no backend, backend exception and TX persistence exception.

### Round 5 — post persistence and APRS parsing boundary

Status: implemented.

- Added `src/data/repository/PacketPostRepository.kt` and `StorageDatabasePacketPostRepository`.
- Added `src/service/PacketPersistenceCoordinator.kt`.
- No database schema/storage migration was performed.
- Moved post timestamp/status normalization, POST/INCMG/TX parse routing, third-party unwrapping, own-digipeat detection, Position/Object/Message dispatch, course/speed extraction and position persistence out of `AprsService`.
- Android broadcasts/notifications/message handling remain Service-edge callbacks.
- `parsePacket()`, `getCSE()`, `addPosition()` and `addPost()` remain compatibility wrappers.
- Preserved persistence-before-parse and update-broadcast-after-parse/log ordering.
- Added JVM routing/ordering tests.

### Interim debug CI #2 after Round 5

User explicitly requested another CI while Round 6 continued in parallel.

- Trigger commit: `9194a3244191a0826b17d421ce5e4447746e1963` (`ci: validate backend modernization through round 5`).
- Workflow run: `33270075359` (`Backend Modernization CI`).
- Scope: `testArm64OpenglDebugUnitTest`, `lintArm64OpenglDebug`, `assembleArm64OpenglDebug`.
- At the Round 6 handoff snapshot, checkout/JDK/setup had passed and `Run Debug Validation` was **in progress**.
- This run validates Rounds 1–5 plus the CI marker commit; it does **not** validate Round 6.
- `.github/workflows/backend-modernization-ci.yml` and `.github/ci-trigger/backend-modernization` remain temporary branch-only plumbing and must be removed before final merge.

### Round 6 — service state and post-event orchestration

Status: implemented in the Round 6 code prepared from CI trigger commit `9194a324...`; final branch commit should contain this document and the code changes atomically.

Changes:

- Added `src/service/ServiceRuntimeState.kt`.
- `AprsService.running` and `AprsService.link_error` are intentionally retained as public/static compatibility fields.
- `ServiceRuntimeState` delegates both reads and writes directly to those fields rather than copying state, so legacy external observations/mutations remain visible during this incremental phase.
- Internal `AprsService` running checks/start/stop and link-on/link-off mutations now go through the state facade.
- `markStopped()` preserves the previous `running=false` then `link_error=0` teardown semantics before backend shutdown.
- Added `src/service/ServicePostCoordinator.kt`.
- Moved `postAddPost()` policy out of the Service while preserving:
  - INFO suppression when connection logging is disabled, evaluated before posting to the main-thread queue;
  - `addPost()` running first on the main-thread callback;
  - INCMG triggering `msgService.sendPendingMessages()` only after `addPost()`;
  - ERROR triggering `stopSelf()` only after `addPost()`;
  - all other post types having no follow-up.
- Android `Handler`, `MessageService`, and `stopSelf()` remain Service-edge callbacks.
- Added `ServiceRuntimeStateTest.kt` and `ServicePostCoordinatorTest.kt` covering compatibility-backed live reads, start/stop/link transitions, INFO suppression and post/follow-up ordering.

Validation notes:

- Detached diff review showed only the intended Service wiring/state/post-policy changes plus the new tests; no unrelated formatting/UI/backend/IC-705 changes.
- Round 6 is not included in interim CI #2 because that CI is pinned to the Round 5 snapshot.
- Final full CI must validate all rounds together before merge.

## Next recommended round

Round 7 should be **integration/regression cleanup**, not another architecture expansion.

Suggested scope:

1. Inspect the full `AprsService` after Rounds 1–6 for duplicate seams, lifecycle ordering regressions, unnecessary public exposure, and obvious Kotlin/Android lint hazards.
2. Check the result of workflow run `33270075359`; if it failed, fix Round 4/5 issues before expanding work.
3. Review tests and add only targeted regression coverage for seams introduced in these rounds.
4. Keep UI, map, IC-705 protocol/session/PTT, Graywolf, database schema, preference storage and backend implementations unchanged.
5. Do not remove temporary CI plumbing until the final cleanup round is ready.

Round 8 should clean temporary CI plumbing, perform final integration/debug cleanup, run the final full CI/release validation once, and only then prepare merge/PR to `main`.

## Resume protocol

At the start of every future session/agent handoff:

1. Read `AGENT.md`.
2. Read this file.
3. Inspect current HEAD of `refactor/backend-modernization` and compare it with this state.
4. Check workflow run `33270075359` if its final result is not recorded in a later update.
5. If code and this document disagree, trust code/reproducible verification and update this document in the same change.
6. Continue from the first unfinished item; do not redo completed rounds unless fixing a regression.
7. Update this handoff after each logical round with changes, validation, known risks and the exact next step.

## Last known branch state

- Round completed/prepared: 6
- Intermediate CI runs explicitly requested/consumed: 2
- Interim CI #1: success (through Round 3)
- Interim CI #2: in progress at snapshot (through Round 5)
- Final full CI: pending
- Temporary CI plumbing cleanup: pending
