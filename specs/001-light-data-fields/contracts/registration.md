# Contract: Registration & Resource Declarations

## 1. Extension type registration

`KarooLightControllerExtension.types` was extended additively (as built):

```text
override val types by lazy {
    listOf(
        LightStatusDataType(engine),            // existing; also serves "current zone" (merged)
        ModeSwitchDataType(engine),
        LightBatteryDataType(engine, discoveredLights),
    )
}
```

Constraint: existing entries are preserved and unchanged (Constitution I). No `CurrentZoneDataType`
— the current-zone need is served by `light-status` (merged).

## 2. `extension_info.xml` — two new `<DataType>` entries

Alongside the existing `light-status` entry, added (additive only):

```xml
<DataType typeId="light-battery"  displayName="@string/light_battery"  description="@string/light_battery_desc"  graphical="true" icon="@drawable/ic_light" />
<DataType typeId="mode-switch"    displayName="@string/mode_switch"    description="@string/mode_switch_desc"    graphical="true" icon="@drawable/ic_light" />
```

Existing `<BonusAction>` entries (`toggle-lights`, `cycle-mode`) remain unchanged — Mode Switch
coexists with `cycle-mode`. No `current-zone` entry (merged into `light-status`).

## 3. `strings.xml` — display names + descriptions

Four new string resources: `light_battery`, `light_battery_desc`, `mode_switch`, `mode_switch_desc`.
No existing strings modified. (No `current_zone` strings — merged.)

## 4. Layouts

Two new `RemoteViews`-compatible layouts under `res/layout/`: `light_battery_view.xml`,
`mode_switch_view.xml` (model existing `light_status_view.xml`).

## 5. Data model change

`DiscoveredLight` gained a nullable, defaulted `batteryStatus: String?` (ANT+ status word; null for
BLE), populated from `SavedDevices` `lastBattery?.name`. Backward-compatible.

## 6. Tap trigger wiring (ModeSwitchDataType only) — as built

The tap uses a runtime-registered `BroadcastReceiver` (same process, verified) with an internal
action string and a `PendingIntent.getBroadcast` using `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`. It
calls `engine.onCycleMode()` and does not alter the existing `cycle-mode` BonusAction path.
