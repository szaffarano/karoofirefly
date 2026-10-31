---

description: "Task list for iGPSport VS800 light support"
---

# Tasks: iGPSport VS800 Light Support

**Input**: Design documents from `/specs/002-igpsport-vs800-support/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. KarooFireFly's constitution (Principle III) makes the established test suites
non-negotiable, so protocol logic is covered by unit tests, written test-first.

**Organization**: Grouped by user story (US1 P1 MVP, US2 P2, US3 P3) so each can be built and verified
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency)
- **[Story]**: US1 / US2 / US3, or none for shared/foundational work
- Paths are repo-relative.

## Path Conventions

Single Android module. Source:
`app/src/main/kotlin/io/github/derstrassi/karoofirefly/`. Tests:
`app/src/test/kotlin/io/github/derstrassi/karoofirefly/`. Abbreviated below as `…/` and `…test/`.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 [P] Create the source package `…/ble/igpsport/` and the test package `…test/ble/igpsport/`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: no user story work can begin until this phase is complete. These are the transport,
protocol, and the shared enum every story depends on.

### Tests (write first, must FAIL before implementation)

- [ ] T002 [P] `…test/ble/igpsport/Crc8Test.kt` — Maxim/Dallas CRC8 known vectors.
- [ ] T003 [P] `…test/ble/igpsport/IgpsportProtocolTest.kt` — from
  `contracts/igpsport-protocol.md`: `setMode(1)` and `setMode(0)` frame bytes (service 105, sub 7,
  field-5 wrapper; OFF = present-but-empty field5, distinct from a bodyless write); `readSupportedModes`
  / `readCurrentMode` frames; `parseReply` of the captured supported-modes reply → `[1,2,7,4,5,20]`;
  `parseState` of the captured state frame → mode, battery, minutes; header/payload CRC validation.

### Implementation

- [ ] T004 [P] `…/ble/igpsport/Crc8.kt` — port pure-Kotlin Maxim CRC8 (makes T002 pass).
- [ ] T005 [P] `…/ble/igpsport/ProtoWire.kt` — port minimal protobuf varint/message encode + decode.
- [ ] T006 `…/ble/igpsport/IgpsportProtocol.kt` — VS800 frames: constants (service 105, subs 6/7,
  field-5 envelope), `setMode`/`readCurrentMode`/`readSupportedModes`, `parseState`, `parseReply`,
  `chunks`, `headerValid`, `expectedLength` (makes T003 pass). Depends on T004, T005.
- [ ] T007 `…/ble/igpsport/FrameAssembler.kt` — port notification reassembly using
  `IgpsportProtocol.headerValid`/`expectedLength`. Depends on T006.
- [ ] T008 `…/ble/igpsport/GattLightLink.kt` — port the raw `android.bluetooth` link (scan-then-connect,
  CCCD/notify setup, chunked writes, setup/scan watchdogs, 1/2/4/8 s reconnect backoff, connection
  priority). Depends on T006, T007.
- [ ] T009 `…/data/LightProfile.kt` — add `IGPSPORT` to `enum class LightProtocol` (additive; existing
  serialized assignments unaffected).

**Checkpoint**: transport + protocol + enum ready; user stories can begin.

---

## Phase 3: User Story 1 — Add and configure a VS800 (Priority: P1) 🎯 MVP

**Goal**: discover, pair, assign role + day/night modes, and drive modes manually (test buttons).

**Independent test**: open settings near a powered VS800 → it appears → pair → assign → tap test
buttons and watch each mode (incl. off) change the beam.

### Tests (write first, must FAIL)

- [ ] T010 [P] [US1] `…test/ble/igpsport/IgpsportModeMappingTest.kt` — mode-id string ↔ protocol-mode
  mapping (`OFF`→0, `MODE_1`→1, …, `MODE_20`→20) and provider option list from a supported-modes list.

### Implementation

- [ ] T011 [US1] `…/ble/igpsport/ScanMatch.kt` — match a VS800 by advertised name + iGPSport advert
  marker (port/trim from the reference project).
- [ ] T012 [US1] `…/ble/igpsport/IgpsportBleController.kt` — implement `LightController`
  (`setMode(deviceId, modeName)` → resolve mode-id → `IgpsportProtocol`); discovery
  (`startDiscovery`/`stopDiscovery` using `ScanMatch`), `connect`, `assignedDeviceIds`, `allConnected`,
  `onDeviceConnected`, and `discoveredLights: StateFlow<List<DiscoveredLight>>` (protocol = IGPSPORT).
  Depends on T008, T009, T011. Satisfies `contracts/light-controller.md`.
- [ ] T013 [US1] `…/data/LightProfile.kt` — add the `IGPSPORT` branch to `modeProviderFor(...)` and an
  `IgpsportModeProvider` exposing OFF + VS800 modes with provisional labels (read the light's supported
  list when available, else the known default). Makes T010 pass.
- [ ] T014 [US1] `…/KarooLightControllerExtension.kt` — instantiate `igpsportController`, register
  `lightControllers[LightProtocol.IGPSPORT]`, and add `igpsportController.discoveredLights` to the
  `combine(...)` discovery merge.
- [ ] T015 [US1] `…/ui/screens/` (LightDetailDialog, SettingsScreen, PreviewData) — audit exhaustive
  `when (protocol)` sites for an `IGPSPORT` branch (label/text); confirm the mode dropdown and test
  buttons work via `modeProviderFor`.

**Checkpoint**: a VS800 can be discovered, configured, and driven manually from settings (MVP).

---

## Phase 4: User Story 2 — Automatic control during rides (Priority: P2)

**Goal**: the engine drives the VS800 by zone, with auto on/off and reconnect, hands-free.

**Independent test**: configure distinct day/night modes + auto mode; start a ride, force a zone
change, end the ride — the light follows and turns off (standby) at the end.

### Tests (write first, must FAIL)

- [ ] T016 [P] [US2] `…test/ble/igpsport/IgpsportBleControllerTest.kt` (with a fake link) — `setMode`
  with `"OFF"` emits `IgpsportProtocol.setMode(0)`; `setMode` while disconnected is queued/dropped
  safely (no throw); unknown mode-id is a logged no-op.

### Implementation

- [ ] T017 [US2] `…/KarooLightControllerExtension.kt` — fold IGPSPORT into the BLE lifecycle:
  `hasBleAssignments`, assigned-id wiring, discovery start/stop, connect-on-assign, and `allConnected`
  must account for iGPSport lights (mirror the Magicshine handling). Depends on T012, T014.
- [ ] T018 [US2] Verify zone dispatch + auto on/off drive the VS800 (engine already calls
  `lightControllers[assignment.protocol]?.setMode(...)`); ensure OFF is standby (beam off, light stays
  reachable for the next ride).
- [ ] T019 [US2] `…/ble/igpsport/GattLightLink.kt` / controller — confirm auto-reconnect after
  motion-sleep / out-of-range resumes control and re-applies current zone mode.

**Checkpoint**: the VS800 is controlled automatically across a ride, like other lights.

---

## Phase 5: User Story 3 — See the VS800's battery (Priority: P3)

**Goal**: battery % shown in settings and on the ride-screen battery field.

**Independent test**: with a connected VS800, battery % appears in settings and on the battery field
alongside other lights.

### Tests (write first, must FAIL)

- [ ] T020 [P] [US3] Extend `IgpsportProtocolTest.kt` — `parseState` yields `batteryPercent` from the
  captured state frame (byte 15).

### Implementation

- [ ] T021 [US3] `…/ble/igpsport/IgpsportBleController.kt` — update per-device `DiscoveredLight`
  `batteryPercent` (and `currentMode`) from state frames; emit on `discoveredLights`.
- [ ] T022 [US3] Confirm `…/datatypes/LightBatteryDataType.kt` renders the IGPSPORT light's
  `batteryPercent` with no code change (it already reads `DiscoveredLight.batteryPercent`); add a note
  if any tweak is needed.

**Checkpoint**: VS800 battery is visible wherever other lights' battery is shown.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T023 On-device: confirm the raw-GATT iGPSport link coexists with the Nordic-lib Magicshine
  controller under the extension's Bluetooth hold (R4 open item); no starvation.
- [ ] T024 On-device: confirm/finalize the VS800 mode labels visually (HIGH/MID/LOW/FLASH A/FLASH
  B/ECO) and update `IgpsportModeProvider`.
- [ ] T025 [P] `README.md` — add the VS800 to the supported-lights table; regenerate `app/manifest.json`
  only if the release flow requires it (otherwise leave to `prepare-release`).
- [ ] T026 Non-regression: verify ANT+ and Magicshine lights behave identically (manual + existing
  tests). *(FR-010, SC-005)*
- [ ] T027 Run the full gate:
  `nix develop --command ./gradlew spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug`.
- [ ] T028 Execute `quickstart.md` on hardware: US1-US3 acceptance scenarios + edge cases.

---

## Dependencies & Execution Order

### Phase dependencies

- Setup (P1) → Foundational (P2) → user stories (P3-P5) → Polish (P6).
- Foundational blocks everything: no story work until T004-T009 are done.

### User story dependencies

- US1 (P1) is the MVP and must land first (controller + wiring + mode provider).
- US2 (P2) depends on US1's controller/registration (T012, T014).
- US3 (P3) depends on US1's controller (T012) for state/battery surfacing.

### Within each story

- Tests before implementation (write failing tests first).
- Helpers → protocol → link → controller → extension wiring → UI.

### Parallel opportunities

- T002/T003 (tests) in parallel; T004/T005 (Crc8/ProtoWire) in parallel.
- T010 and T016/T020 test files are independent of each other.
- T025 (README) can run anytime after the feature stabilizes.

## Parallel Example: Phase 2

```text
# Launch the failing tests together:
T002  Crc8Test.kt
T003  IgpsportProtocolTest.kt
# Then the independent helpers together:
T004  Crc8.kt
T005  ProtoWire.kt
```

## Implementation Strategy

- **MVP first**: complete Phases 1-3 (through US1). At that point a VS800 can be discovered,
  configured, and driven from settings — a demonstrable slice.
- **Incremental**: add US2 (automatic ride control), then US3 (battery), each independently testable.
- **Gate every increment** with `spotlessCheck` + `detekt` + `testDebugUnitTest`.

## Notes

- Follow TDD: each test task's tests must fail before its implementation task.
- Keep the mode-id string format stable (persisted in `LightAssignment`) — changing it later needs a
  migration.
- The throwaway reverse-engineering spike lives in a separate repo and is not part of this work; port
  only the confirmed logic described in `research.md` / `contracts/`.
