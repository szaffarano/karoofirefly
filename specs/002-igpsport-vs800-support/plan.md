# Implementation Plan: iGPSport VS800 Light Support

**Branch**: `szaffarano/002-igpsport-vs800-support` | **Date**: 2026-10-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-igpsport-vs800-support/spec.md`

## Summary

Add the iGPSport VS800 as a new BLE light KarooFireFly can discover, configure, and drive. The VS800
speaks the Nordic UART transport (same framing + Maxim CRC8 as other iGPSport lights) but with its own
service/sub map and a protobuf layout that wraps every sub-payload in field 5. Light control lives on
service 105: set/read current mode on sub 7, read supported modes on sub 6, and the live state frame
(sub 7, type 0x03) carries mode, remaining minutes, and battery. These facts were confirmed on real
hardware during a discovery spike.

Technical approach: a dedicated `IgpsportBleController` implementing the existing thin `LightController`
contract, backed by a slim VS800-specific `IgpsportProtocol` and a ported raw-GATT link. The controller
is registered under a new `LightProtocol.IGPSPORT`, so KarooFireFly's existing engine (zones, auto
on/off, manual override), settings UI, mode provider, and battery field drive it unchanged. The
Magicshine (Nordic-lib) and ANT+ paths are untouched.

## Technical Context

**Language/Version**: Kotlin (project toolchain, JVM target 17)

**Primary Dependencies**: `karoo-ext` (Hammerhead extension/SensorService + Bluetooth lifecycle);
`no.nordicsemi.kotlin.ble` (existing, Magicshine path only); `android.bluetooth` raw GATT (new, for the
iGPSport controller); Jetpack Compose (settings UI); DataStore (settings); kotlinx.serialization
(settings model); Timber (logging).

**Storage**: Existing DataStore-backed `LightControllerSettings` / `LightAssignment`. No new store; the
VS800 reuses the per-light assignment model. The `LightProtocol` enum gains an additive `IGPSPORT`
value.

**Testing**: JUnit (`testDebugUnitTest`) for pure protocol logic; ktlint via Spotless
(`spotlessCheck`); Detekt (`detekt`, strict on new code, no baseline additions); Android Lint
(`lintDebug`); `assembleDebug` for build.

**Target Platform**: Hammerhead Karoo 3 (Android 12). `minSdk 24`, `compileSdk 37`.

**Project Type**: Single-module Android extension app (`app/`).

**Performance Goals**: Mode change reflected on the light within ~5 s of a zone change (SC-002). Low BLE
power draw during rides (long connection interval / low-power priority once connected).

**Constraints**: The extension holds the Bluetooth radio via `RequestBluetooth`; the VS800 accepts one
connection at a time; VS800 support must not regress ANT+ or Magicshine behavior; Detekt is strict on
new code.

**Scale/Scope**: One additional light protocol — roughly a new `ble/igpsport/` package (protocol +
helpers + GATT link + controller), a `LightProtocol` enum value, a mode provider, and wiring in the
extension, settings UI, and battery field. No new external dependency.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Public API Compatibility** — PASS. The only public-surface change is an additive
  `LightProtocol.IGPSPORT` enum value and a new controller behind the existing `LightController`
  interface. Existing serialized `LightAssignment`s (protocol `ANT_PLUS`/`BLE`) continue to
  deserialize unchanged; the new value only appears on assignments the user creates. No breaking change
  to any consumed contract; no major bump required.
- **II. Respect Service Boundaries** — PASS. The feature uses `karoo-ext` for the extension lifecycle,
  data types, and `RequestBluetooth`, and drives the light over the Android BT adapter within that
  Bluetooth hold. It does not bypass or reimplement Karoo service contracts.
- **III. Established Test Suites (NON-NEGOTIABLE)** — PASS (committed). Pure protocol logic
  (frame build/parse, OFF, CRC, field-5 wrapping, supported-mode list) gets unit tests. The full CI
  gate (`spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug`) must pass before the work is
  considered complete.

No violations — Complexity Tracking left empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-igpsport-vs800-support/
├── plan.md              # This file
├── research.md          # Phase 0: VS800 protocol + design decisions
├── data-model.md        # Phase 1: entities (mode map, state, assignment)
├── quickstart.md        # Phase 1: build + on-device validation procedure
├── contracts/
│   ├── igpsport-protocol.md   # VS800 frame contracts (service 105, field-5)
│   └── light-controller.md    # Controller surface KarooFireFly depends on
└── checklists/
    └── requirements.md  # Spec quality checklist (from /speckit.specify)
```

### Source Code (repository root)

```text
app/src/main/kotlin/io/github/derstrassi/karoofirefly/
├── ble/
│   ├── MagicshineBleController.kt     # unchanged
│   ├── MagicshineProtocol.kt          # unchanged
│   └── igpsport/                      # NEW package
│       ├── IgpsportProtocol.kt        # VS800 frames: setMode/read/parseState (service 105, field 5)
│       ├── Crc8.kt                    # ported pure-Kotlin Maxim CRC8
│       ├── ProtoWire.kt               # ported minimal protobuf varint/message encode+decode
│       ├── FrameAssembler.kt          # ported notification reassembly
│       ├── GattLightLink.kt           # ported raw android.bluetooth link (scan-then-connect, reconnect)
│       └── IgpsportBleController.kt    # LightController impl + discovery/connect/battery surface
├── data/
│   └── LightProfile.kt                # add LightProtocol.IGPSPORT + modeProviderFor() branch
├── light/
│   └── LightController.kt             # unchanged (interface already fits)
├── datatypes/
│   └── LightBatteryDataType.kt        # unchanged (renders DiscoveredLight.batteryPercent)
├── ui/screens/                        # audit LightDetailDialog / SettingsScreen for protocol branches
└── KarooLightControllerExtension.kt   # register igpsportController; merge discovery; BLE lifecycle

app/src/test/kotlin/io/github/derstrassi/karoofirefly/
└── ble/igpsport/
    ├── IgpsportProtocolTest.kt        # frame build/parse, OFF, field-5, supported modes
    └── Crc8Test.kt                    # ported CRC vectors
```

**Structure Decision**: Single Android module (`app/`). The VS800 lives in a new self-contained
`ble/igpsport/` package so its raw-GATT stack and protocol do not entangle the Nordic-lib Magicshine
code. Integration with the rest of the app is limited to a handful of well-defined seams
(`LightProtocol` enum, `modeProviderFor`, controller registration, discovery merge), mirroring how
`MagicshineBleController` is wired today.

## Complexity Tracking

No constitution violations; no complexity deviations to justify.
