# Backend Modernization Handoff

> Branch-local work log for `refactor/backend-modernization`.
> `AGENT.md` remains the authoritative engineering specification. This file records enough state for another agent/session to resume without chat context.

## Goal and constraints

Modernize APRSdroid's internal backend architecture incrementally while preserving user-visible behavior.

Non-negotiable constraints followed by this refactor:

- No intentional UI/layout/animation/navigation/string behavior changes.
- Preserve APRS behavior, preference keys/defaults, imported profiles and stored configuration.
- Keep `AprsService` as the Android lifecycle/foreground-service boundary while moving orchestration/data access behind narrow seams.
- Do not rewrite stable IC-705 protocol/session/PTT/Graywolf code.
- Preserve IC-705 recovery/PTT invariants in `AGENT.md`.
- Do not combine Hilt/Room/DataStore migrations with this refactor.
- Keep logical changes atomic and reviewable.
- Do not merge to `main` automatically; merge/PR remains an explicit user action.

## Baseline

- Base branch: `main`
- Base commit: `27cb0a30c4a8647a7868f0e58cb4c31aeef9f105` (`release: prepare Mod-v2.1.0`)
- Refactor branch: `refactor/backend-modernization`
- `main` was rechecked in Round 8 and remained at the base commit.

## Final architecture after Round 8

`AprsService` still owns Android lifecycle/commands, broadcasts/notifications, APRS packet formatting and compatibility entry points. The extracted boundaries are:

- `src/data/preferences/AprsServiceSettings.kt` — typed facade over existing `PrefsWrapper` keys/defaults.
- `src/service/BackendLifecycleCoordinator.kt` — backend instance ownership/start/stop/update through a narrow adapter.
- `src/service/ImmediateLocationCoordinator.kt` + `LocationSelection.kt` — immediate cached/one-shot location acquisition.
- `src/service/PacketSendCoordinator.kt` — serialized send executor, backend update and send result policy.
- `src/data/repository/PacketPostRepository.kt` — narrow adapter over existing `StorageDatabase` APIs.
- `src/service/PacketPersistenceCoordinator.kt` — post persistence, APRS parsing and packet-type routing.
- `src/service/ServicePostCoordinator.kt` — main-thread post-event follow-up policy.
- `src/service/ServiceRuntimeState.kt` — service-owned access to runtime state while `AprsService.running` / `link_error` remain the actual compatibility backing fields.

`PrefsWrapper`, concrete APRS backends, database schema, UI/map code, IC-705 protocol/session/PTT and Graywolf RX code remain unchanged by this refactor.

## Round history

### Round 1 — typed service preference boundary

- Added `AprsServiceSettings`.
- Preserved existing SharedPreferences implementation, keys and defaults.
- Removed direct raw preference-key knowledge from service code where migrated.

### Round 2 — backend lifecycle ownership

- Added `BackendLifecycleCoordinator`, `ServiceBackend` and `AprsBackendServiceAdapter`.
- Preserved stop-old -> create-new -> start-new ordering.
- A failed-start backend remains owned for teardown.
- Added JVM tests for replacement ordering, failed-start cleanup and idempotent stop.
- Repository scan found no external in-repo `AprsService.poster` dependency requiring a compatibility shim.

### Round 3 — immediate location acquisition

- Added `ImmediateLocationCoordinator` and pure `newestByTimestamp` selection seam.
- Moved GPS/network/passive cached location lookup, one-shot listener registration/removal and 15-second timeout out of `AprsService`.
- Preserved `FixedPosition.start(true)` behavior and existing exception handling.
- Added host-JVM selection tests.

### Interim debug CI #1

- Trigger commit: `a1e5701e3c8f604defe473ae925d7b55aa571efc`
- Run: `33269561545`
- Scope: `testArm64OpenglDebugUnitTest`, `lintArm64OpenglDebug`, `assembleArm64OpenglDebug`
- Result: **success**; validated through Round 3.

### Round 4 — packet send execution

- Added `PacketSendCoordinator`.
- Preserved the single-thread serialized send worker.
- Backend `update(packet)` and TX/error persistence remain on the worker thread.
- Only final completion returns through the main-thread handler.
- Preserved `"No poster"` and exception-result behavior.
- Added JVM tests for success, no backend, backend exception and persistence exception.

### Round 5 — post persistence and APRS parsing boundary

- Added `PacketPostRepository` + `StorageDatabasePacketPostRepository`.
- Added `PacketPersistenceCoordinator`.
- No database schema/storage migration.
- Moved post timestamp/status normalization, POST/INCMG/TX parse routing, third-party unwrap, own-digipeat detection, Position/Object/Message dispatch, course/speed extraction and position persistence out of `AprsService`.
- Android broadcasts/notifications/message delivery remain Service-edge callbacks.
- Existing `parsePacket()`, `getCSE()`, `addPosition()` and `addPost()` remain compatibility wrappers.
- Preserved persistence-before-parse and update-after-parse/log ordering.

### Interim debug CI #2

- Trigger commit: `9194a3244191a0826b17d421ce5e4447746e1963`
- Run: `33270075359`
- Same Debug unit/Lint/ARM64 Debug scope.
- Result: **success**; validated Rounds 1–5.
- Warnings were pre-existing/unrelated: one IC-705 unnecessary safe call, one navigation annotation-target warning and three deprecated Material `List` icon warnings.

### Round 6 — service state and post-event orchestration

- Added `ServiceRuntimeState` while retaining public/static `AprsService.running` and `link_error` as backing compatibility fields.
- Preserved teardown sequence `running=false` then `link_error=0` before backend shutdown.
- Added `ServicePostCoordinator`.
- Preserved INFO suppression before queueing, `addPost()` before follow-up, INCMG pending-message kick and ERROR-triggered `stopSelf()`.
- Added JVM tests for state transitions and event ordering.

### Round 7 — integration/regression cleanup

- No production behavior change.
- Added `ServiceOrchestrationIntegrationTest` covering cross-seam ERROR/INCMG lifecycle contracts.
- Locked ERROR order as queue -> persist/addPost -> stop request; runtime state clears only in later teardown.
- Locked INCMG order as queue -> persist/addPost -> pending-message kick with no runtime state change.
- Added explicit tests that INFO logging eligibility is sampled before main-thread execution and teardown writes `running=false` before `link_error=0`.
- Full diff audit confirmed no UI/map/IC-705/Graywolf/database-schema changes.

### Round 8 — final integration and validation

Static review found no concrete production regression requiring another code change. The branch was frozen and final validation ran against snapshot:

- Validation snapshot: `055b79281c20ca623c02fa222ff69c5999ac5454`
- GitHub Actions run: `33288309883` (`Backend Modernization Final Validation`)
- Result: **success**

Executed commands/tasks:

```bash
./gradlew \
  verifyReleaseVersion \
  testArm64OpenglDebugUnitTest \
  lintArm64OpenglDebug \
  assembleArm64OpenglDebug \
  assembleArm64OpenglRelease \
  assembleArm32OpenglRelease \
  --no-daemon --stacktrace
```

CI completed with `BUILD SUCCESSFUL`; both ARM64 and ARMv7 Release paths ran through R8/resource shrinking and packaged successfully. The same five unrelated compiler warnings remained; none were emitted by the new service/data refactor files.

Graywolf-specific synthetic/native scripts were not rerun because this refactor did not modify Graywolf/AFSK RX production code or native dependencies. IC-705 hardware/RF/PTT validation was also not rerun because those subsystems were not modified; CI still cannot substitute for real hardware testing if future changes touch them.

Temporary `.github/workflows/backend-modernization-ci.yml` and `.github/ci-trigger/backend-modernization` are removed in the final cleanup commit containing this handoff. Therefore the final cleaned application source/test tree is the validated snapshot with only CI-plumbing deletion and this documentation update after validation.

## Validation status

- Interim CI #1: **success** (through Round 3)
- Interim CI #2: **success** (through Round 5)
- Final full refactor validation: **success** (Rounds 1–8 application source/tests)
- ARM64 Debug unit tests: **success**
- ARM64 Debug Lint: **success**
- ARM64 Debug APK assembly: **success**
- ARM64 Release/R8 assembly: **success**
- ARMv7 Release/R8 assembly: **success**
- `verifyReleaseVersion`: **success**
- Temporary CI plumbing cleanup: **complete**
- Merge to `main`: **not performed**

## Resume / next action

The planned backend modernization is complete and validated. Do not start another architecture expansion by default.

For a future session:

1. Read `AGENT.md` and this file.
2. Inspect current `refactor/backend-modernization` HEAD and confirm `main` has not moved unexpectedly.
3. If the user asks to merge, prefer reviewing the final `main...refactor/backend-modernization` diff and then create/merge a PR or fast-forward only as explicitly requested.
4. If new functional work is requested, treat it as a new scope rather than silently extending this refactor.
5. Real-device APRS/IC-705 smoke testing remains useful before a release even though this refactor did not modify the RF/session/PTT pipeline.

## Final state

- Planned rounds completed: **8 / 8**
- Final validation: **passed**
- Branch: `refactor/backend-modernization`
- `main`: unchanged from `27cb0a30c4a8647a7868f0e58cb4c31aeef9f105` at final review
- Ready for explicit review/PR/merge action; not merged automatically.
