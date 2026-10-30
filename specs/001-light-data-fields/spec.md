# Feature Specification: Light Data Fields

**Feature Branch**: `001-light-data-fields`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "Implement derstrassi/karoofirefly issue #15 — v0.7 Data Fields: Karoo ride-screen data fields for light status and control (Light Battery Status, Mode Switch, Current Zone)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See light battery status at a glance (Priority: P1)

As a rider, while riding I want a ride-screen data field that shows the battery level of every
configured light so that I can tell whether any light is about to die before it leaves me in the
dark.

**Why this priority**: Running out of light during a ride is the highest-impact failure this
extension can help avoid. A glanceable battery indicator delivers the most rider safety value and
is useful on its own, independent of the other two fields.

**Independent Test**: Configure one or more lights, add the battery data field to a ride profile,
start a ride, and confirm the field shows each light's current battery level and refreshes over
time without any other data field present.

**Acceptance Scenarios**:

1. **Given** at least one light is configured and connected, **When** the rider views the battery
   data field during a ride, **Then** the field shows a battery level for each configured light.
2. **Given** a connected light's battery level changes, **When** the next refresh occurs, **Then**
   the displayed level updates to reflect the new value.
3. **Given** a light is configured but not currently reporting, **When** the rider views the field,
   **Then** the field clearly indicates that the level is unavailable rather than showing a stale or
   misleading value.

---

### User Story 2 - Switch light mode from the ride screen (Priority: P2)

As a rider, I want an interactive ride-screen data field that lets me change the active light
behaviour by tapping it, so that I can override the automatic behaviour without digging into menus
or relying only on the BonusAction buttons.

**Why this priority**: Direct control while riding is a meaningful convenience, but it builds on the
same status plumbing as the display fields and is less critical than simply knowing battery state.

**Independent Test**: Add the mode-switch data field to a ride profile, start a ride, tap the field,
and confirm the active light behaviour changes and the field reflects the newly selected mode.

**Acceptance Scenarios**:

1. **Given** the mode-switch field is on the ride screen, **When** the rider taps it, **Then** the
   active light behaviour advances to the next option in a defined, repeating order.
2. **Given** the rider has cycled to a given mode, **When** the rider views the field, **Then** the
   field shows which mode is currently active.
3. **Given** the rider has manually selected a mode via the field, **When** automatic conditions
   change, **Then** the system applies the documented priority between manual override and automatic
   behaviour consistently with existing behaviour.

---

### User Story 3 - See the current light zone (Priority: P3)

As a rider, I want a simple ride-screen data field that shows the active light zone (Day / Night /
Off) so that I can confirm what the controller is currently doing at a glance.

**Why this priority**: Purely informational and the simplest of the three; valuable for confidence
but not essential to safety or control.

**Decision (implemented)**: This need is served by the existing `light-status` field, which already
displays the active zone. Rather than add a separate overlapping "Current Zone" field, the two are
**merged** — no new current-zone data type is created.

**Independent Test**: Add the current-zone data field to a ride profile, start a ride, and confirm
it displays the active zone and updates when the zone changes.

**Acceptance Scenarios**:

1. **Given** the controller has an active zone, **When** the rider views the current-zone field,
   **Then** the field displays that zone (Day, Night, or Off).
2. **Given** the active zone changes, **When** the next update occurs, **Then** the displayed zone
   updates to match.

---

### Edge Cases

- What does the battery field show when no lights are configured at all?
- How are multiple configured lights presented in a single glanceable field without crowding?
- What happens when the rider taps the mode-switch field while a light is disconnected?
- How does the current-zone field behave during the transition window around sunrise/sunset when the
  zone is changing?
- What does each field display before the first status update has been received after a ride starts?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a ride-screen data field that displays the battery level of
  each configured light.
- **FR-002**: The battery data field MUST be compatible with the Karoo glance view so riders can
  read it without opening a full data page.
- **FR-003**: The battery data field MUST refresh light battery levels over the course of a ride so
  displayed values stay reasonably current.
- **FR-004**: The battery data field MUST clearly distinguish an unavailable/unknown level from a
  genuine low level, and MUST present each protocol in its native granularity: BLE lights as an exact
  percentage, ANT+ lights as their coarse status word (GOOD / OK / LOW / CRITICAL) to avoid implying
  false precision.
- **FR-005**: The system MUST provide an interactive ride-screen data field that lets the rider
  change the active light behaviour by tapping it.
- **FR-006**: The mode-switch field MUST advance through its selectable options in a defined,
  repeating order and MUST indicate the currently active option.
- **FR-007**: Mode changes made through the mode-switch field MUST respect the existing control
  priority between manual override and automatic behaviour.
- **FR-008**: The system MUST provide a ride-screen data field that displays the active light zone
  as Day, Night, or Off. (Satisfied by the existing `light-status` field — see User Story 3
  decision; no separate field is added.)
- **FR-009**: The zone display MUST update when the active zone changes.
- **FR-010**: Each data field MUST be independently selectable so a rider can add any subset of the
  three fields to a ride profile.
- **FR-011**: The mode-switch field MUST cycle the active light zone on each tap, advancing through
  the zones in a defined, repeating order (Day → Night → Off), consistent with the existing
  zone-cycle control (the `cycle-mode` BonusAction).
- **FR-012**: Cycling the zone via the mode-switch field MUST act as a manual override of the
  automatic behaviour, following the existing control priority (Manual Override > Auto Mode >
  Ride State).
- **FR-013**: The mode-switch field MUST indicate which zone is currently active (Day, Night, or
  Off).

### Key Entities *(include if feature involves data)*

- **Configured Light**: A light the rider has set up (ANT+ or BLE), characterised by an identity,
  current battery level, and connection/availability state.
- **Light Zone**: The controller's current high-level state (Day, Night, or Off) derived from the
  engine's auto-mode logic.
- **Light Zone Selection**: The active zone (Day / Night / Off) the mode-switch field reports and
  advances through on each tap, including whether it currently originates from a manual override or
  automatic logic.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A rider can add any of the three data fields to a ride profile and see it populate on
  the ride screen within a few seconds of starting a ride.
- **SC-002**: The battery field reflects a light's battery change within one refresh cycle of the
  change occurring.
- **SC-003**: A rider can change the active light behaviour from the ride screen using only taps on
  the mode-switch field, with no need to leave the ride screen.
- **SC-004**: The current-zone field matches the controller's actual active zone in at least 99% of
  observations during a ride, updating promptly on zone transitions.
- **SC-005**: Each field is glanceable — a rider can read the needed information in a single look
  without interacting with the device.

## Assumptions

- The three data fields integrate with Karoo's existing ride-screen data-field framework, the same
  mechanism used by other ride-screen fields on the device.
- Battery refresh cadence follows the existing status-update behaviour (BLE polled periodically,
  ANT+ reported/cached), rather than introducing a new polling scheme.
- The mode-switch field reuses the existing zone-cycle behaviour (`onCycleMode` / the `cycle-mode`
  BonusAction): tapping cycles Day → Night → Off and sets a manual override, rather than
  controlling per-light hardware modes directly.
- The mode-switch field and the existing `cycle-mode` BonusAction coexist as alternative entry points
  to the same capability.
- Zones are the existing Day / Night / Off states produced by the engine; this feature displays and
  does not redefine them.
- Fields degrade gracefully (showing an unavailable/placeholder state) when lights are disconnected
  or no status has yet been received.
