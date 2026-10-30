# Phase 0 Research: Light Data Fields

All NEEDS CLARIFICATION items from the spec were resolved during specification (FR-011 → zone
cycling). The research below resolves the technical unknowns surfaced while planning.

## R1 — Data-field implementation pattern

**Decision**: One `DataTypeImpl` subclass per field, each overriding `startView()` to push
`RemoteViews` through the `ViewEmitter`, driven by `engine` `StateFlow`s with
`.distinctUntilChanged()`. Register instances in `KarooLightControllerExtension.types` and declare
each with a `<DataType graphical="true">` entry in `extension_info.xml`.

**Rationale**: This is exactly how the existing `LightStatusDataType` works (verified in
`app/src/main/kotlin/.../datatypes/LightStatusDataType.kt`). Reusing the established pattern honors
Constitution II (service boundaries) and minimizes risk.

**Alternatives considered**: A single combined multi-value data field — rejected because FR-010
requires each field to be independently selectable into a ride profile.

## R2 — Battery data source for configured lights

**Decision**: Read battery per configured light from `DiscoveredLight.batteryPercent` (nullable Int)
on the extension's public `discoveredLights: StateFlow<List<DiscoveredLight>>`, filtered to
`engine.settings.lightAssignments` device IDs. Display `—` (or similar) when `batteryPercent == null`
or the light is not `connected`.

**Rationale**: `DiscoveredLight` already merges ANT+ and BLE lights and carries `batteryPercent`,
`connected`, `protocol`, and `name` (verified in `KarooLightControllerExtension.kt`). This keeps the
BLE `batteryLevels` map and ANT+ internals private (Constitution II).

**Alternatives considered**: Reaching into `MagicshineBleController.batteryLevels` or ANT+
`SavedDevices.lastBattery` directly — rejected as a service-boundary violation; the merged
`discoveredLights` is the public surface.

**Note on granularity**: ANT+ battery is coarse (a status word GOOD/OK/LOW/CRITICAL), BLE is an exact
% polled at 60s. Rather than map the ANT+ word to a fake %, the word is preserved via a new nullable
`DiscoveredLight.batteryStatus` (null for BLE); the field shows BLE as `%` and ANT+ as the word. No
new polling is added (FR-003 assumption).

**Implemented**: `LightBatteryDataType` observes `discoveredLights`, filters by
`engine.settings.lightAssignments`, renders one row per light, and is `debounce`d like the others.

## R3 — Mode Switch tap interaction (primary technical risk)

**Decision**: karoo-ext 1.1.8 exposes **no inbound tap/click `ViewEvent`** for data fields —
inspection of the AAR shows `ViewEvent` has only outbound config subtypes and `ViewEmitter` only
emits (`updateView`, `UpdateGraphicConfig`). Tap handling will therefore be implemented by attaching
a `PendingIntent` to the `RemoteViews` root (`setOnClickPendingIntent`) that triggers
`LightControlEngine.onCycleMode()` (via a broadcast the extension already can service, or the
`getInstance()` singleton). The view then re-renders from the updated `activeZone` flow.

**Rationale**: `RemoteViews` support `setOnClickPendingIntent`, and `onCycleMode()` already exists and
performs Day → Night → Off cycling with `MANUAL_OVERRIDE` (verified in
`LightControlEngine.onCycleMode()`), satisfying FR-011/FR-012.

**VALIDATED on-device (Karoo 3, 2026-10-30)**: A proof-of-concept field (`poc-click`) attached a
`PendingIntent` to the `RemoteViews` root and cycled A → B → C on tap. On real hardware the host
**does** deliver the click: the broadcast fired (`PocClickDataType: tap received` in logcat) and the
view re-rendered the new label. Approach A is therefore confirmed viable for the Mode Switch; the
display-only fallback is no longer required.

**Correction**: the POC also disproved the earlier cross-process hypothesis — an `INSTANCE_CHECK`
log confirmed `startView`, `onCreate`, and `handleRideState` share one engine instance (same pid).
The staleness on ride transitions was instead the 1Hz `updateView` throttle dropping burst updates,
fixed with `debounce(250ms)`.

**Follow-up (DONE)**: the `poc-click` spike has been removed now that `ModeSwitchDataType` is verified
on-device.

**Alternatives considered**: (a) `PerformHardwareAction` — rejected, it models fixed hardware buttons,
not per-field taps. (b) Keeping only the existing `cycle-mode` BonusAction — rejected because the
ticket explicitly asks for an interactive data field, though the BonusAction remains as coexisting
fallback.

## R4 — Multi-light presentation in a glance view

**Decision**: Render one compact row per configured light (role/name + battery %), ordered by the
existing `lightAssignments` order, within the single `RemoteViews` layout. If no lights are
configured, show an explicit empty state ("No lights").

**Rationale**: FR-001/FR-002 require a glanceable per-light view; the existing `buildModeDetail()`
already joins per-assignment lines, confirming the data is available and ordered.

**Alternatives considered**: Aggregate "lowest battery only" — rejected as it hides per-light state
the rider needs; may be offered later but the ticket asks for all configured lights.

## R5 — Test harness (Constitution III)

**Decision**: Add a `app/src/test` source set with JUnit (and kotlinx-coroutines-test if flow logic
is tested) via the version catalog, and unit-test the deterministic mapping/cycle logic: zone→label,
battery→display string (null/disconnected → placeholder), and zone cycle ordering.

**Rationale**: The repo currently has no test source set or test deps, so Constitution III is only
vacuously satisfied. Extracting pure mapping functions and testing them makes the suite meaningful
without requiring an instrumented Karoo device.

**Alternatives considered**: Instrumented/Robolectric tests for `RemoteViews` — deferred; the
view-host and tap behaviour are validated manually via quickstart because they depend on the Karoo
runtime. Pure-logic unit tests give the best value/cost here.
