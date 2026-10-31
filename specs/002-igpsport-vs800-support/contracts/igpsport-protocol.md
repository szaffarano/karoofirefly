# Contract: VS800 Protocol (`IgpsportProtocol`)

Frame-level contract for the VS800, confirmed on hardware. All frames use the Nordic UART transport, a
20-byte header + protobuf payload, 20-byte chunking, and Maxim/Dallas CRC8 over header and payload.
Light control is on **service 105 (0x69)**; every sub-payload is wrapped in **protobuf field 5**.

Constants:

- UART service `6e400001-b5a3-f393-e0a9-e50e24dcca9e`, write `...0002`, notify `...0003`, CCCD
  `00002902-...`.
- `SERVICE_LIGHT = 105`, `OP_READ = 2`, `OP_WRITE = 1`.
- Subs: `SUB_SUPPORTED = 6`, `SUB_CURRENT_MODE = 7`.
- Payload envelope: `field1 = service`, `field2 = op`, `field3 = sub`, `field5 = sub-payload`.

## Outbound (host → VS800)

### `setMode(mode: Int): ByteArray`

- Builds service 105, `OP_WRITE`, sub 7, body `field5 { field2 = mode }`.
- `mode == 0` (OFF): `field2` omitted (proto3 zero) — `field5` present but empty inner. Turns the beam
  off; the light stays connected.
- Must emit the `field5` wrapper even for OFF (a bodyless write is a no-op on the VS800).

### `readCurrentMode(): ByteArray`

- Service 105, `OP_READ`, sub 7.

### `readSupportedModes(): ByteArray`

- Service 105, `OP_READ`, sub 6.

### `chunks(frame): List<ByteArray>`

- Splits a frame into ≤20-byte write chunks.

## Inbound (VS800 → host)

### `parseState(frame): Vs800Update?`

- Accepts the service-105 / sub-7 **state frame** (header byte0 = 0x03):
  - `mode = byte[7]`
  - `remainingMinutes = LE(bytes[11..13])`
  - `batteryPercent = byte[15]`
- Validates header CRC before parsing; returns null for frames that are not understood.

### `parseReply(frame): Vs800Update?`

- Accepts data replies (header byte0 = 0x01) on service 105:
  - sub 7 → `field5 { field2 = mode }` ⇒ `currentMode`
  - sub 6 → `field5 { repeated field1 = modes }` ⇒ `supportedModes`

### `Vs800Update` (parse result)

- Nullable fields: `currentMode: Int?`, `supportedModes: List<Int>?`, `batteryPercent: Int?`,
  `remainingMinutes: Int?`. Callers merge non-null fields into live state.

## Reassembly

- Notification fragments (≤20 bytes) are reassembled by `FrameAssembler` using header validity +
  expected length (DATA frames carry a payload length in the header; STATE/ACK frames are header-only).

## Invariants / test obligations

- Header CRC and payload CRC must validate (Maxim CRC8). Unit tests must cover a known-good vector.
- `setMode(0)` must produce a frame with a present-but-empty `field5` (OFF), distinct from a bodyless
  write.
- Round-trip: `parseReply(readSupportedModes reply)` yields `[1,2,7,4,5,20]` for the captured sample.
- `parseState` extracts mode/battery/minutes from the captured sample frame
  (`03 69 07 … <mode> … <min> … <batt>`).
- Building a frame then re-parsing the supported-mode / current-mode reply is consistent.
