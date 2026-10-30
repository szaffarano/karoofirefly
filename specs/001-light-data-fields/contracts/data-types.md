# Contract: Data Type Interfaces

Each field is a `DataTypeImpl(extension = "karoo-light-controller", typeId = <id>)` exposing a
graphical view via `startView()`. `startStream()` is optional (numeric streaming) but these are
primarily graphical glance fields, mirroring `LightStatusDataType`.

## LightBatteryDataType

- **typeId**: `light-battery`
- **extension**: `karoo-light-controller`
- **View contract**: emits `UpdateGraphicConfig(showHeader = ...)` then `RemoteViews`
  (`R.layout.light_battery_view`) showing one row per configured light (`label` + `batteryDisplay`),
  or an empty state when none configured.
- **Inputs**: `discoveredLights` filtered by `engine.settings.lightAssignments`.
- **Display rule**: BLE lights show an exact `percent%`; ANT+ lights show their coarse status word
  (GOOD/OK/LOW/CRITICAL, preserved via `DiscoveredLight.batteryStatus`); disconnected/unknown show
  a placeholder; no assignments shows "No lights".
- **Update trigger**: changes to the filtered battery set (`debounce` + `distinctUntilChanged`).
- **No inbound events** (display only).

## ModeSwitchDataType

- **typeId**: `mode-switch`
- **extension**: `karoo-light-controller`
- **View contract**: emits `RemoteViews` (`R.layout.mode_switch_view`) showing the active zone label
  and (optionally) a manual-override indicator; attaches a `PendingIntent` via
  `setOnClickPendingIntent` on the view root.
- **Tap action**: the `PendingIntent` triggers `LightControlEngine.onCycleMode()` (Day → Night → Off),
  then the view re-renders from `activeZone`.
- **Inputs**: `engine.activeZone`, `engine.state` (`debounce` + `distinctUntilChanged`).
- **Status**: VALIDATED on-device (Karoo 3). The host delivers `RemoteViews` click intents and the
  view re-renders; the display-only fallback is not needed.

## Current Zone — merged into `light-status` (no separate type)

The planned `CurrentZoneDataType` was **not** created. The existing `light-status` field already
displays the active zone (Day / Night / Off), so adding a separate current-zone field would duplicate
it. Decision: merge — `light-status` is the single zone/status display. (It also received the
`debounce` fix so it refreshes on ride transitions.)

## Compatibility guarantee (Constitution I)

No existing data type (`light-status`) or bonus action (`toggle-lights`, `cycle-mode`) `typeId` or
`DataPoint` field is modified. The two new ids (`light-battery`, `mode-switch`) are additive, and
`DiscoveredLight` only gained a nullable, defaulted `batteryStatus`. Backward-compatible — no major
version bump required.
