# Phase 1 Data Model: iGPSport VS800 Light Support

Entities are described at the domain level. Most reuse existing KarooFireFly types; the VS800 adds a
mode map and a transient per-device state. No new persistent store is introduced.

## LightProtocol (existing enum — additive change)

`enum class LightProtocol { ANT_PLUS, BLE, IGPSPORT }`

- Adds `IGPSPORT`. `@Serializable`; adding a value is backward-compatible for existing saved
  assignments (which only ever hold `ANT_PLUS`/`BLE`).
- Selects the controller in the extension's `lightControllers` map and the branch in
  `modeProviderFor(...)`.

## LightAssignment (existing — reused unchanged)

Per-light configuration already supports the VS800 with no schema change:

- `deviceId: String` — the VS800's BLE address.
- `deviceName: String` — e.g. "VS800".
- `role: LightRole` — FRONT / REAR (the VS800 is a front light).
- `protocol: LightProtocol` — `IGPSPORT` for a VS800.
- `dayMode: String`, `nightMode: String` — opaque mode ids (see VS800 Mode below).
- `radarWarnFlash: Boolean` — not used for the VS800 (front light); left at default.

## VS800 Mode (new value set, surfaced via LightModeOption)

The VS800's selectable modes, exposed through the existing `LightModeProvider` / `LightModeOption`
mechanism. Mode ids are opaque strings persisted in `LightAssignment`; the controller maps them to the
protocol mode number.

| mode id (string) | protocol mode | display label (provisional) | kind   |
|------------------|---------------|-----------------------------|--------|
| `OFF`            | 0             | Off                         | off    |
| `MODE_1`         | 1             | High                        | steady |
| `MODE_2`         | 2             | Mid                         | steady |
| `MODE_7`         | 7             | Low                         | steady |
| `MODE_4`         | 4             | Flash A                     | flash  |
| `MODE_5`         | 5             | Flash B                     | flash  |
| `MODE_20`        | 20            | Eco                         | steady |

- `OFF` → protocol mode 0 (beam off, light stays reachable).
- The supported list is read from the light (service 105 / sub 6) and should be intersected with the
  known set above; unknown modes fall back to a generic `MODE_<n>` label.
- Label strings are provisional and must be confirmed visually on the light during implementation.
- The mode-id string format is a stable contract (persisted in settings) — do not change it later
  without a migration.

## VS800 live state (transient, not persisted)

Parsed from the service-105 / sub-7 state frame and from read-backs; held in-memory by the controller
and surfaced via the existing `DiscoveredLight`:

- `currentMode: Int` — the mode the light reports (drives optimistic UI / "follows the light").
- `batteryPercent: Int` — from state-frame byte 15; maps to `DiscoveredLight.batteryPercent`.
- `remainingMinutes: Int` — parsed but not displayed (no runtime field in scope).
- `connected: Boolean` — link state; maps to `DiscoveredLight.connected`.

## DiscoveredLight (existing — reused unchanged)

The controller emits `DiscoveredLight` entries with `protocol = IGPSPORT`, `name`, `id`, `connected`,
and `batteryPercent`. No new fields are required; the battery data field already renders
`batteryPercent` for BLE-style lights.

## Relationships

- A `LightAssignment` references one VS800 by `deviceId` and holds its `dayMode`/`nightMode` as VS800
  Mode ids.
- `IgpsportBleController` owns the live link and state per `deviceId`, emits `DiscoveredLight`s, and
  resolves mode-id strings to protocol mode numbers when `setMode` is called by the engine.
