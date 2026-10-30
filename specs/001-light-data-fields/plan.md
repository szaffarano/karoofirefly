# Implementation Plan: Light Data Fields

**Branch**: `001-light-data-fields` | **Date**: 2026-10-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-light-data-fields/spec.md`

## Summary

Add three Karoo ride-screen data fields to the existing extension: a glanceable **Light Battery
Status** field, an interactive **Mode Switch** field that cycles the active zone (Day → Night → Off),
and a read-only **Current Zone** field. All three follow the existing `DataTypeImpl` + `RemoteViews`
pattern established by `LightStatusDataType`, consume the engine's existing `StateFlow`s and the
extension's `discoveredLights`, and are registered additively so no existing field or action changes
behaviour. The Mode Switch reuses `LightControlEngine.onCycleMode()`. The one novel technical unknown
— how a data-field view receives a tap (karoo-ext <= 1.1.9 exposes no inbound tap event) — was
resolved by an on-device spike (research R3): attaching a `PendingIntent` to the `RemoteViews` root
works on Karoo 3. The Mode Switch uses this mechanism.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (JVM target 11)

**Primary Dependencies**: `io.hammerhead:karoo-ext:1.1.8` (DataTypeImpl, ViewEmitter, ViewConfig,
RemoteViews), Jetpack Compose (settings UI only, not used by data fields), kotlinx.coroutines
(StateFlow)

**Storage**: N/A — data fields are read-only views over in-memory `StateFlow`s; existing DataStore
settings (`lightAssignments`) are consumed read-only, no new persistence

**Testing**: `./gradlew testDebugUnitTest` (JVM unit tests). NOTE: no test source set or test
dependencies currently exist (see Constitution Check + research.md R5)

**Target Platform**: Android, Hammerhead Karoo 3 (compileSdk 37, minSdk 24, targetSdk 35)

**Project Type**: Single-module Android extension app (`app/`)

**Performance Goals**: Glanceable fields update within one status/refresh cycle; view updates are
`distinctUntilChanged` to avoid redundant `RemoteViews` churn; BLE battery already polled at 60s,
ANT+ battery cached — no new polling introduced

**Constraints**: Must run within Karoo's data-field host process via `RemoteViews` (no arbitrary
custom views); additive only — must not alter `light-status`, `toggle-lights`, or `cycle-mode`
behaviour

**Scale/Scope**: 3 new data types, ~3 layout XMLs, additive edits to extension registration +
`extension_info.xml` + `strings.xml`; typically 1–4 configured lights

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Public API Compatibility** — PASS. Changes are additive: new `typeId`s and `<DataType>`
  entries only. No existing data type, bonus action, DataPoint field, or `applicationId` changes.
  No breaking change, no major bump required.
- **II. Respect Service Boundaries** — PASS. New code lives in `datatypes/`, consuming `engine/`
  via its public `StateFlow`s (`activeZone`, `currentZone`, `state`) and `onCycleMode()`, and light
  info via the extension's public `discoveredLights`. No reaching into `ble/` internals
  (e.g. `batteryLevels` map stays private; battery is read via `DiscoveredLight.batteryPercent`).
- **III. Established Test Suites (NON-NEGOTIABLE)** — GATE RISK. The repo currently has **no test
  source set and no test dependencies**; `testDebugUnitTest` passes vacuously. This feature adds
  pure, testable mapping logic (zone→label, battery→display, cycle ordering). Plan establishes a
  minimal JUnit unit-test harness and tests for that logic so the principle is satisfied in spirit
  rather than vacuously. See Complexity Tracking and research.md R5.

Re-evaluation after Phase 1 design: see "Post-Design Constitution Re-Check" below.

## Project Structure

### Documentation (this feature)

```text
specs/001-light-data-fields/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (data-type + registration contracts)
│   ├── data-types.md
│   └── registration.md
└── tasks.md             # /speckit.tasks output (NOT created here)
```

### Source Code (repository root)

```text
app/src/main/kotlin/io/github/derstrassi/karoofirefly/
├── datatypes/
│   ├── LightStatusDataType.kt        # existing — also serves "current zone" (merged, see spec US3)
│   ├── LightBatteryDataType.kt       # built — battery per configured light (BLE %, ANT+ word)
│   └── ModeSwitchDataType.kt         # built — tap cycles zone via onCycleMode()
├── engine/
│   └── LightControlEngine.kt         # consumed read-only (activeZone/currentZone/state/onCycleMode)
└── KarooLightControllerExtension.kt  # MODIFIED — register 2 new types; DiscoveredLight.batteryStatus

app/src/main/res/
├── layout/
│   ├── light_battery_view.xml        # built
│   └── mode_switch_view.xml          # built
├── xml/extension_info.xml            # MODIFIED — added light-battery + mode-switch <DataType> entries
└── values/strings.xml                # MODIFIED — display names + descriptions

# No CurrentZoneDataType: the current-zone need is served by the existing light-status field (merged).
# A unit-test source set remains outstanding (Constitution III) — see research R5.
```

**Structure Decision**: Single-module Android extension. New data fields mirror the existing
`datatypes/LightStatusDataType.kt` pattern exactly (one `DataTypeImpl` subclass per field, one
`RemoteViews` layout each), registered in `KarooLightControllerExtension.types` and declared in
`extension_info.xml`. No new module or architectural layer is introduced.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Introduce a test source set + JUnit deps (new to repo) | Constitution III requires a real, non-vacuous test suite; feature adds testable mapping/cycle logic | Shipping with no tests leaves III satisfied only vacuously; the logic (battery/zone mapping, cycle order) is deterministic and cheap to cover |

## POC cleanup

DONE. The `poc-click` spike (used to validate data-field tap delivery) has been removed now that
`ModeSwitchDataType` is verified on-device.

## Implementation notes (post-build)

- **Tap delivery**: validated on Karoo 3 and same-process (an `INSTANCE_CHECK` log spike confirmed
  `startView`, `onCreate`, and `handleRideState` share one engine instance). The tap uses a
  `RemoteViews` `PendingIntent` + runtime `BroadcastReceiver`; no cross-process routing needed.
- **1Hz updateView throttle**: karoo-ext drops view updates faster than 1Hz, so a ride-start/pause
  burst of engine state changes dropped the meaningful update and left fields stale. Fixed by
  `debounce(250ms)` on each field's flow (also fixed the pre-existing `light-status` staleness).
- **ANT+ status word**: preserved via a new nullable `DiscoveredLight.batteryStatus`; BLE shows %.

## Post-Design Constitution Re-Check

- **I. Public API Compatibility** — PASS. All additions are new `typeId`s/entries; `DiscoveredLight`
  gained a nullable, defaulted `batteryStatus` field (backward-compatible). No existing contract
  changed.
- **II. Respect Service Boundaries** — PASS. Battery sourced from the public `discoveredLights`
  (incl. `batteryPercent`/`batteryStatus`); zone from engine `StateFlow`s; no `ble/` internals.
- **III. Established Test Suites** — OUTSTANDING. A unit-test source set for the mapping/zone/cycle
  logic is still to be added (see research R5); currently satisfied only vacuously. Tracked for
  `/speckit.tasks`.
