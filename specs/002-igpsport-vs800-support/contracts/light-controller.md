# Contract: `IgpsportBleController`

The controller plugs the VS800 into KarooFireFly through the existing `LightController` interface and
mirrors the surface the extension already uses on `MagicshineBleController`. No existing interface
changes shape; the controller must satisfy these expectations.

## Implements `LightController`

```kotlin
fun setMode(deviceId: String, modeName: String)
```

- `modeName` is a VS800 mode id string (`OFF`, `MODE_1`, `MODE_2`, `MODE_7`, `MODE_4`, `MODE_5`,
  `MODE_20`). The controller maps it to the protocol mode number and sends the frame.
- `OFF` → protocol mode 0 (beam off, light stays reachable).
- Unknown/unsupported `modeName` → no-op (logged), never a crash.
- Idempotent and safe to call when disconnected (queues or drops cleanly; never throws).

## Discovery / lifecycle surface (as used by `KarooLightControllerExtension`)

```kotlin
val discoveredLights: StateFlow<List<DiscoveredLight>>   // protocol = IGPSPORT, battery populated
var assignedDeviceIds: Set<String>
var onDeviceConnected: (() -> Unit)?
fun startDiscovery()
fun stopDiscovery()
fun connect(deviceId: String)
fun allConnected(ids: Set<String>): Boolean
```

Behavioral expectations (mirroring the Magicshine controller):

- `startDiscovery()` runs only while settings are open; discovery scans for VS800 devices and publishes
  them as `DiscoveredLight(protocol = IGPSPORT, connected, name, batteryPercent)`.
- Assigned VS800s are (re)connected by address for a ride via `connect(id)`, independent of discovery.
- Reconnect automatically after the light sleeps / leaves range; resume control on reconnect.
- `allConnected(ids)` reports readiness so the extension's BLE-hold logic can release the radio when
  everything is connected.
- Battery and current mode are updated from state frames and surfaced through `discoveredLights`.

## Integration seams (existing files)

- `data/LightProfile.kt`: add `LightProtocol.IGPSPORT`; extend `modeProviderFor(protocol, deviceId)`
  with an `IGPSPORT` branch returning a provider whose options are the VS800 Mode set (reading the
  light's supported list when available, else the known default).
- `KarooLightControllerExtension`:
  - instantiate `igpsportController`; register `lightControllers[LightProtocol.IGPSPORT]`.
  - add `igpsportController.discoveredLights` to the `combine(...)` discovery merge.
  - fold iGPSport into the BLE lifecycle helpers (`hasBleAssignments`, assigned-id wiring, discovery
    start/stop, connect-on-assign, `allConnected`).
- Mode dispatch is unchanged: the engine already calls
  `lightControllers[assignment.protocol]?.setMode(deviceId, modeName)`.
- `LightBatteryDataType`: unchanged — renders `DiscoveredLight.batteryPercent`.
- `ui/screens`: audit any exhaustive `when (protocol)` for an `IGPSPORT` branch (labels); mode dropdown
  falls out of `modeProviderFor`.

## Non-regression contract

- ANT+ (`lightControl`) and Magicshine (`magicshineController`) behavior must be unchanged. The
  iGPSport raw-GATT link must coexist with the Nordic-lib Magicshine controller under the extension's
  single Bluetooth hold.
