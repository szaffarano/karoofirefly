# Quickstart & Validation: Light Data Fields

Validation guide proving the three data fields work end-to-end. Implementation details live in
`tasks.md` (after `/speckit.tasks`); this file is a run/validation guide only.

## Prerequisites

- JDK 17+.
- A Hammerhead Karoo 3 (or emulator image) for on-device scenarios.
- At least one configured light (ANT+ or BLE) assigned in the extension settings.

## Build & static checks

```bash
./gradlew spotlessCheck detekt           # formatting + static analysis (CI gates)
./gradlew testDebugUnitTest              # unit tests (incl. new mapping/cycle tests)
./gradlew lintDebug assembleDebug        # Android Lint + debug APK
```

Expected: all green. New unit tests for zone-label mapping, battery display formatting, and cycle
ordering pass (see `research.md` R5, `data-model.md`).

## Scenario A — Light Battery Status (FR-001..FR-004, SC-001/SC-002)

1. Add the **Light Battery** field to a ride profile data page; start a ride (or preview).
2. Expected: one row per configured light — BLE shows an exact `%`, ANT+ shows its status word
   (GOOD/OK/LOW/CRITICAL); disconnected/unknown shows `—`; no assignments shows "No lights".
3. Change a BLE light's charge (or simulate) and wait one poll cycle (~60s).
4. Expected: the displayed value updates; no stale value shown for a disconnected light.
5. VERIFIED on-device.

## Scenario B — Mode Switch (FR-005..FR-007, FR-011..FR-013, SC-003)

> Tap delivery was validated on Karoo 3 (research R3) and the real engine-wired behaviour is
> VERIFIED on-device.

1. Add the **Mode Switch** field to a ride profile; start a ride.
2. Expected: field shows the active zone (Day / Night / Off).
3. Tap the field repeatedly.
4. Expected: active zone advances Day → Night → Off → Day, identical to the `cycle-mode` BonusAction,
   and the engine enters manual override.
5. Confirm the engine enters manual override (the existing `cycle-mode` BonusAction reflects the
   same active zone).

## Scenario C — Current Zone (FR-008/FR-009, SC-004) — served by `light-status` (merged)

No separate Current Zone field exists; the existing **Light Status** field covers this.

1. Add the **Light Status** field; start a ride.
2. Expected: field shows the active zone, matching the controller.
3. Trigger a zone change (time/ambient threshold, ride start/pause, or Scenario B).
4. Expected: the label updates promptly (this is the case the `debounce` fix restored).

## Regression checks (Constitution I)

- The existing **Light Status** field still renders unchanged.
- `toggle-lights` and `cycle-mode` BonusActions still behave as before; Mode Switch coexists with
  `cycle-mode`.

## Done when

- Scenarios A and C pass on-device; Scenario B passes OR its limitation is recorded and the field
  ships display-only; all build/static/test gates are green.
