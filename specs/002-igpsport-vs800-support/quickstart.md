# Quickstart: Validating iGPSport VS800 Support

How to build, install, and verify the feature on real hardware. Requires a Karoo 3 (adb-reachable) and
a powered iGPSport VS800 nearby, not connected to the iGPSport phone app.

## Build

From the repo root, in the Nix dev shell:

```bash
nix develop --command ./gradlew assembleDebug
```

Run the full quality gate before claiming completion:

```bash
nix develop --command ./gradlew spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Unit tests (no hardware)

`testDebugUnitTest` must cover the VS800 protocol:

- Maxim CRC8 known vector.
- `setMode(1)` / `setMode(0)` frame bytes (service 105, sub 7, field-5 wrapper; OFF = empty inner).
- `readSupportedModes` reply parse → `[1,2,7,4,5,20]`.
- `parseState` of the captured state frame → mode, battery, minutes.

## On-device validation (maps to spec user stories)

### US1 — Add and configure (P1)

1. Power on the VS800 nearby; open KarooFireFly settings.
2. Confirm the VS800 appears in the discovered-lights list. *(FR-001)*
3. Pair it; assign a role and pick a day mode and a night mode; confirm they persist. *(FR-002)*
4. Tap each mode's test button and watch the beam: each steady level, each flash, and off. Confirm the
   labels match what the light does; finalize any provisional labels. *(FR-003, FR-007)*

### US2 — Automatic control (P2)

1. Configure distinct day and night modes; enable auto on/off and an auto mode (time or ambient).
2. Start a ride → the VS800 switches to the current zone's mode within ~5 s. *(FR-004, FR-005, SC-002)*
3. Force a zone change (e.g. adjust the day/night boundary or cover the ambient sensor) → the VS800
   follows. *(FR-004)*
4. End the ride → the beam turns off but the light stays connected; start another ride → it comes back
   on automatically. *(FR-005, FR-006, SC-003)*

### US3 — Battery (P3)

1. With the VS800 connected, confirm its battery % shows in settings. *(FR-009)*
2. Add the light battery field to a ride page; confirm the VS800's battery appears alongside other
   lights. *(FR-009, SC-004)*

### Edge cases

- Start a ride with the VS800 off/out of range, then power it on → it connects and applies the zone
  mode without rider action. *(FR-008, SC-006)*
- Let the VS800 motion-sleep mid-ride, then move it → it reconnects and resumes. *(FR-008)*
- Connect the iGPSport phone app to the light → KarooFireFly shows it unavailable, not falsely
  controlled. *(FR-011)*
- With an ANT+ and/or Magicshine light also configured, confirm those behave exactly as before.
  *(FR-010, SC-005)*

## Done criteria

- Full CI gate passes (`spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug`).
- US1-US3 acceptance scenarios pass on hardware; edge cases behave as described.
- No regression observed on existing ANT+ / Magicshine lights.
