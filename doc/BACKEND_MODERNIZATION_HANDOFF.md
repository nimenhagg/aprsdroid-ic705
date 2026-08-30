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
- Estimated plan: about 8 focused implementation/debug rounds.

## Current architecture observations

- `AprsService` still owns Android lifecycle/command dispatch, broadcasts/notifications, APRS packet formatting, and compatibility entry points.
- Direct service preference reads/writes are behind `AprsServiceSettings`.
- Backend instance ownership/start/stop/update is behind `BackendLifecycleCoordinator`.
- Immediate GPS/network/passive acquisition is behind `ImmediateLocationCoordinator`.
- Packet send worker ownership/update execution/result policy is behind `PacketSendCoordinator`.
- Post/position persistence and APRS parser routing are behind `PacketPersistenceCoordinator` + `PacketPostRepository`.
- Main-thread post-event follow-up policy is behind `ServicePostCoordinator`.
- Runtime `running` / `link_error` mutation is behind `ServiceRuntimeState`, while the legacy static fields remain the actual compatibility backing storage.
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

- Trigger commit: `9194a3244191a0826b17d421ce5e4447746e1963` (`ci: validate backend modernization through round 5`).
- Workflow run: `33270075359` (`Backend Modernization CI`).
- Scope: `testArm64OpenglDebugUnitTest`, `lintArm64OpenglDebug`, `assembleArm64OpenglDebug`.
- Result: **success**.
- This validated Rounds 1–5 plus the marker commit; it does not validate Round 6 or Round 7.
- Build log warnings were unrelated to this refactor: one existing IC-705 safe-call warning, one navigation annotation-target warning, and three deprecated Material icon warnings. No new Round 1–5 service/repository file emitted a compiler warning.
- `.github/workflows/backend-modernization-ci.yml` and `.github/ci-trigger/backend-modernization` remain temporary branch-only plumbing and must be removed before final merge.

### Round 6 — service state and post-event orchestration

Status: implemented.

- Added `src/service/ServiceRuntimeState.kt`.
- `AprsService.running` and `AprsService.link_error` remain public/static compatibility fields and are still the backing storage.
- Internal `AprsService` running checks/start/stop and link-on/link-off mutations now go through the state facade.
- `markStopped()` preserves the previous `running=false` then `link_error=0` teardown sequence before backend shutdown.
- Added `src/service/ServicePostCoordinator.kt`.
- Moved `postAddPost()` policy out of the Service while preserving INFO suppression before main-thread queueing, `addPost()` before follow-up, INCMG pending-message kick, and ERROR-triggered `stopSelf()`.
- Android `Handler`, `MessageService`, and `stopSelf()` remain Service-edge callbacks.
- Added JVM unit coverage for compatibility-backed state and post/follow-up ordering.

Validation notes:

- Round 6 itself has not yet been compiled by GitHub CI; interim CI #2 is pinned to the Round 5 snapshot.

### Round 7 — integration/regression cleanup

Status: implemented as targeted regression coverage; no production behavior change was made in this round.

Audit findings:

- Full branch comparison from the Mod-v2.1.0 baseline shows the refactor is limited to `AprsService`, new service/data boundaries, tests, handoff documentation, and temporary CI plumbing. UI/map/IC-705/Graywolf/database schema files are untouched by the refactor.
- Repository code search found no external `AprsService.poster` usage, so removal of that field in Round 2 does not require a compatibility shim inside this repository.
- Existing public/static `running` and `link_error` compatibility fields remain in place.
- Interim CI #2 completed successfully, so Rounds 4–5 are now compile/unit/lint/debug-build validated as well as Rounds 1–3.
- CI warnings are pre-existing/unrelated and are intentionally not folded into this backend refactor.

Regression coverage added/refined:

- Added `ServiceOrchestrationIntegrationTest.kt` to model the cross-seam ERROR and INCMG lifecycle contracts.
- ERROR path is locked as: main-thread queue -> persist/addPost -> stop request; runtime compatibility state remains live until the later teardown phase, where `markStopped()` performs `running=false` then `link_error=0`.
- INCMG path is locked as: main-thread queue -> persist/addPost -> pending-message kick, without changing runtime state.
- `ServicePostCoordinatorTest` now verifies that INFO logging eligibility is sampled before main-thread execution, matching the legacy `postAddPost()` behavior.
- `ServiceRuntimeStateTest` now explicitly locks teardown write order (`running=false` before `link_error=0`).

Validation notes:

- Round 7 intentionally does not trigger another intermediate CI. Final Round 8 validation must compile/run the Round 6–7 additions together.
- Local Gradle execution remains unavailable in the agent container because `github.com` DNS resolution is unavailable there.

## Next recommended round

Round 8 is the final cleanup/validation round.

Required scope:

1. Re-read `AGENT.md` and this handoff, verify branch HEAD and compare against `main`.
2. Remove temporary `.github/workflows/backend-modernization-ci.yml` and `.github/ci-trigger/backend-modernization` from the branch before merge.
3. Perform a final static integration review of `AprsService` and all new coordinators; only fix concrete regressions/compile issues, not unrelated warnings or style debt.
4. Run final GitHub validation against the complete Round 1–8 snapshot. At minimum cover ARM64 Debug unit tests + Lint + Debug build; for merge readiness also run the repository's relevant release/ABI validation required by `AGENT.md` without publishing a release/tag.
5. If final validation fails, fix only the failing refactor issue and rerun the failed validation as needed; record exact results here.
6. Do not merge to `main` automatically unless the user explicitly asks for merge/PR action.

## Resume protocol

At the start of every future session/agent handoff:

1. Read `AGENT.md`.
2. Read this file.
3. Inspect current HEAD of `refactor/backend-modernization` and compare it with this state.
4. If code and this document disagree, trust code/reproducible verification and update this document in the same change.
5. Continue from the first unfinished item; do not redo completed rounds unless fixing a regression.
6. Update this handoff after each logical round with changes, validation, known risks and the exact next step.

## Last known branch state

- Round completed/prepared: 7
- Intermediate CI runs explicitly requested/consumed: 2
- Interim CI #1: success (through Round 3)
- Interim CI #2: success (through Round 5)
- Round 6–7 final compile/unit/lint validation: pending Round 8
- Final full CI: pending
- Temporary CI plumbing cleanup: pending
