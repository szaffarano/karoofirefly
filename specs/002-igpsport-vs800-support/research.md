# Phase 0 Research: iGPSport VS800 Light Support

All open questions from the spec were resolved by an on-hardware discovery spike (a throwaway
instrumented debug build driven against a real VS800 on a Karoo 3). This document records the findings
and the design decisions that follow from them. No `NEEDS CLARIFICATION` items remain.

## R1. VS800 BLE transport

**Decision**: Treat the VS800 as a Nordic UART device, identical transport to other iGPSport lights.

**Findings**: The VS800 exposes the Nordic UART service (`6e400001-...`) with write (`...0002`) and
notify (`...0003`) characteristics and the standard CCCD. Frames are a 20-byte header + protobuf
payload, chunked at 20 bytes, with a Maxim/Dallas CRC8 over the header and over the payload. All of
this matched existing iGPSport framing — frames passed header/CRC validation on the first connect.

**Rationale**: Confirmed by logcat: connect → service discovery → notifications enabled → frames
exchanged. No transport work is needed beyond porting the framing helpers.

## R2. Why the existing iGPSport code could not drive the VS800

**Finding**: Other iGPSport lights use **service 106** for light control with top-level protobuf
fields. The VS800 uses **service 105 (0x69)** and **wraps every sub-payload in protobuf field 5**.
Writes to service 106 were silently ignored (no reply, no beam change); the light's own reports arrived
on services it does not recognize. This is a service/sub/field-layout divergence, not a transport
problem.

## R3. VS800 control protocol (confirmed)

Service **105 (0x69)**:

| Operation          | sub | op    | body / parse                                                |
|--------------------|-----|-------|-------------------------------------------------------------|
| Set mode / OFF     | 7   | WRITE | `field5 { field2 = mode }`; mode 0 / field omitted = OFF    |
| Read current mode  | 7   | READ  | reply `field5 { field2 = mode }`                            |
| Read supported     | 6   | READ  | reply `field5 { repeated field1 = [1,2,7,4,5,20] }`         |
| Live state frame   | 7   | STATE | type 0x03: byte7 = mode, bytes 11-13 = minutes, byte15 = battery% |

**Mode map** (confirmed; brightness inferred from reported runtime, exact HIGH/MID/LOW labels to be
confirmed visually during implementation):

| mode | runtime  | label (provisional) |
|------|----------|---------------------|
| 1    | 110 min  | HIGH (steady)       |
| 2    | 225 min  | MID (steady)        |
| 7    | 290 min  | LOW (steady)        |
| 4    | 2160 min | FLASH A             |
| 5    | 4290 min | FLASH B             |
| 20   | ~82000   | ECO / near-off      |

**OFF semantics** (confirmed by probe): a write to 105/7 with `field5` present and the mode field
absent/zero turns the beam off. A write with no `field5` wrapper is a no-op; a write to the wrong sub
does nothing. So OFF is simply `setMode(0)` — symmetric with every other mode. The beam goes off but
the light stays connected/reachable (standby), which matches KarooFireFly's expectation that it can
re-drive the light for the next ride.

**Battery / runtime**: byte 15 of the 105/7 state frame is battery percent (observed 0x5A = 90%); bytes
11-13 are remaining minutes. Battery rides along with mode state, so no separate battery poll is
required for the battery field.

**Model identity**: service 101 sub 2 returns model/brand strings ("VS800" / "IGPSPORT"), and the
device advertises as `VS800`. Scan matching can key on the advertised name plus the iGPSport advert
marker.

## R4. BLE stack: raw GATT vs Nordic kotlin library

**Decision**: Use a ported raw `android.bluetooth` GATT link for the iGPSport controller, not
KarooFireFly's Nordic kotlin BLE library.

**Rationale**: The raw link proven in the spike encodes Karoo-specific reliability that is hard-won:
scan-then-connect (a blind `connectGatt` on a busy Karoo radio often hangs), setup/scan watchdogs,
1/2/4/8 s reconnect backoff, and connection-priority management. It uses the Android BT adapter
directly and coexists with the extension's Bluetooth hold, so it can run alongside the Nordic-lib
Magicshine controller.

**Alternatives considered**: Re-implement on the Nordic kotlin library (rejected — would re-introduce
the connection-reliability risks the raw stack already solves, for no benefit). A shared BLE "vendor"
abstraction unifying Magicshine + iGPSport (rejected for now — larger blast radius on working code;
revisit if a third BLE vendor appears).

**Open item for implementation**: confirm at runtime that the raw link and the Nordic-lib Magicshine
controller coexist on the Karoo under one Bluetooth hold without starving each other.

## R5. Protocol scope: slim VS800 object vs full iGPSport protocol

**Decision**: Write a compact VS800-only `IgpsportProtocol`, porting only the pure-Kotlin helpers
(`Crc8`, `ProtoWire`, `FrameAssembler`).

**Rationale**: KarooFireFly's light model is opaque mode strings, so it needs only: build setMode/OFF,
read current mode, read supported modes, and parse the state frame (mode + battery). Porting a full
multi-model iGPSport protocol (custom-mode editor, smart configs, mode classification tables) would
drag in features that are out of scope and shaped around a different model.

**Alternatives considered**: Port the whole upstream `protocol/` module (rejected — unused complexity,
different model's layout).

## R6. Out-of-scope confirmations

Custom-mode editor, in-light smart-feature toggles, and remaining-runtime display are out of scope
(per spec Assumptions). Their VS800 sub-protocols were **not** reverse-engineered; only core mode
control + battery were mapped. Adding them later would require a further discovery pass.
