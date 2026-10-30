# Phase 1 Data Model: Light Data Fields

These fields are **read-only projections** of existing in-memory state. No new persistent entities are
introduced. The "entities" below describe the data each field reads and the derived display values.

## Existing sources (consumed, not created)

| Source | Type | Origin | Used by |
|--------|------|--------|---------|
| `engine.activeZone` | `StateFlow<DayTimeZone?>` | `LightControlEngine` | Mode Switch, Current Zone |
| `engine.currentZone` | `StateFlow<DayTimeZone>` | `LightControlEngine` | (reference / status) |
| `engine.state` | `StateFlow<EngineState>` | `LightControlEngine` | Mode Switch (override indication) |
| `engine.onCycleMode()` | function | `LightControlEngine` | Mode Switch (tap action) |
| `discoveredLights` | `StateFlow<List<DiscoveredLight>>` | `KarooLightControllerExtension` | Battery |
| `engine.settings.lightAssignments` | `List<LightAssignment>` | DataStore settings | Battery (filter) |

`DayTimeZone` enum values: `DAY`, `NIGHT` (plus the "Off" state represented by `activeZone == null`).
`DiscoveredLight` fields: `id`, `name`, `manufacturer?`, `protocol` (`ANT_PLUS`/`BLE`), `connected`,
`batteryPercent: Int?`, `batteryStatus: String?` (ANT+ word; null for BLE), `temperature?`.

## Derived view models

### BatteryFieldRow (per configured light)

| Field | Type | Derivation |
|-------|------|------------|
| `label` | String | Role (`F`/`R`) from matched `LightAssignment` |
| `batteryDisplay` | String | ANT+: `batteryStatus` word (GOOD/OK/LOW/CRITICAL); BLE: `"$batteryPercent%"`; disconnected/unknown: placeholder `"—"` |
| `available` | Boolean | `connected && (batteryStatus != null || batteryPercent != null)` |

- Collection is `lightAssignments` filtered/joined with `discoveredLights` by device id, preserving
  `lightAssignments` order (R4).
- Empty collection → explicit "No lights" empty state.

### ZoneDisplay (Current Zone + Mode Switch display)

| Field | Type | Derivation |
|-------|------|------------|
| `zoneLabel` | String | `activeZone == null → "Off"`, `DAY → "Day"`, `NIGHT → "Night"` |
| `isManualOverride` | Boolean | `engine.state == MANUAL_OVERRIDE` (Mode Switch indication only) |

### Zone cycle transition (Mode Switch tap) — delegates to `onCycleMode()`

| Current `activeZone` | After tap (per existing `onCycleMode()`) |
|----------------------|------------------------------------------|
| `null` (Off) | `DAY` (or `NIGHT` if only night has modes) |
| `DAY` | `NIGHT` (or `null`/Off if night has no modes) |
| `NIGHT` | `null` (Off) |

The data field MUST NOT reimplement this ordering — it calls `onCycleMode()` so behaviour stays
identical to the existing `cycle-mode` BonusAction (FR-011, FR-012).

## Validation rules

- Battery display never shows a stale value when a light is disconnected → placeholder instead
  (FR-004).
- Zone label is always one of exactly {Day, Night, Off} (FR-008).
- All view updates gated by `.distinctUntilChanged()` to avoid redundant `RemoteViews` emission.
