# Feature Specification: iGPSport VS800 Light Support

**Feature Branch**: `002-igpsport-vs800-support`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "Add support for the iGPSport VS800 bike light to KarooFireFly, so it can
be discovered, configured, and driven from the Karoo like the lights already supported."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Add and configure a VS800 (Priority: P1)

A rider with an iGPSport VS800 opens KarooFireFly's settings, sees the VS800 appear among discovered
lights, pairs it, assigns it a role (front/rear), and chooses which mode it should use by day and by
night. From settings they can tap a mode to preview it and watch the beam change on the light.

**Why this priority**: Being able to see, pair, and manually drive the VS800 is the foundation of the
feature. Without it nothing else is possible, and on its own it already delivers value (a rider can
control the light's mode from the Karoo).

**Independent Test**: Open settings with the VS800 powered on nearby; confirm it is discovered, pair
it, assign a role and day/night modes, then use the test buttons and verify the beam changes to each
selected mode, including off.

**Acceptance Scenarios**:

1. **Given** a powered-on VS800 nearby and settings open, **When** the rider opens the light list,
   **Then** the VS800 appears as a discoverable light.
2. **Given** the VS800 is paired, **When** the rider assigns a role and picks a day mode and a night
   mode, **Then** the choices are saved and shown for that light.
3. **Given** the VS800 is paired, **When** the rider taps a mode's test button, **Then** the light
   switches to that mode (steady level, flash, or off) and returns to its prior state afterward.

---

### User Story 2 - Automatic control during rides (Priority: P2)

Once configured, the VS800 behaves like every other light KarooFireFly controls: it turns on when a
ride starts, switches between the rider's day and night modes as the active zone changes (by time of
day or ambient light), and turns off when the ride ends — all without the rider touching it.

**Why this priority**: Automatic, hands-free control is the main reason the rider wants the light in
KarooFireFly rather than driving it manually. It builds directly on US1.

**Independent Test**: Configure the VS800 with distinct day and night modes, enable auto on/off and
an auto mode (time or ambient), then start a ride in one zone and force a zone change; verify the
light turns on, follows the zone change, and turns off at ride end.

**Acceptance Scenarios**:

1. **Given** a configured VS800 and auto-on enabled, **When** a ride starts, **Then** the light
   switches to the mode for the current zone.
2. **Given** a ride in progress, **When** the active zone changes from day to night (or back),
   **Then** the VS800 switches to the corresponding configured mode.
3. **Given** a ride in progress with auto-off enabled, **When** the ride ends, **Then** the VS800's
   beam turns off while the light remains reachable for the next ride.

---

### User Story 3 - See the VS800's battery (Priority: P3)

The rider can see the VS800's battery level, both in settings and on the ride-screen battery field
alongside their other lights, so they know whether it needs charging before a ride.

**Why this priority**: Useful and expected for a managed light, but not required to control it. It is
an additive convenience on top of US1/US2.

**Independent Test**: With a configured, connected VS800, add the light battery field to a ride page
and open settings; verify the VS800's battery percentage is shown in both places and updates.

**Acceptance Scenarios**:

1. **Given** a connected VS800, **When** the rider views settings, **Then** the light's battery
   percentage is displayed.
2. **Given** a connected VS800 and the battery field on a ride page, **When** the field renders,
   **Then** the VS800's battery appears alongside any other configured lights.

---

### Edge Cases

- **Light not connected at ride start**: the system keeps trying to reach a configured VS800 and
  applies the correct mode once it connects, without blocking the ride.
- **Light asleep / out of range then returns**: after the VS800 motion-sleeps or drifts out of range,
  the system reconnects automatically and resumes control.
- **"Off" semantics**: turning the light off from the Karoo turns the beam off but leaves the light
  reachable so it can be switched on again automatically for the next ride.
- **Single connection**: if the iGPSport phone app is already connected to the VS800, the Karoo
  cannot control it at the same time; the light is shown as unavailable until the other app releases
  it.
- **Mixed fleet**: a VS800 configured alongside existing ANT+ and Magicshine lights must not disturb
  those lights' behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST discover a nearby iGPSport VS800 and present it for pairing in the settings
  light list.
- **FR-002**: Users MUST be able to assign a discovered VS800 a role (front/rear) and a mode for each
  zone (day and night), and have those choices persist.
- **FR-003**: System MUST present the VS800's available modes with clear, rider-facing labels covering
  off, the steady brightness levels, and the flashing patterns the light supports.
- **FR-004**: System MUST set the VS800 to the configured mode when the active zone changes under the
  existing time-based, ambient-light, or combined auto modes.
- **FR-005**: System MUST turn the VS800 on at ride start and off at ride end according to the
  existing auto on/off and pause settings.
- **FR-006**: System MUST be able to turn the VS800's beam off on command while keeping the light
  reachable so it can be driven again without manual intervention.
- **FR-007**: Users MUST be able to preview (test) each VS800 mode from settings and see the beam
  change.
- **FR-008**: System MUST reconnect automatically to a previously paired VS800 after it sleeps or
  goes out of range, and resume control.
- **FR-009**: System MUST display the VS800's battery level in settings and on the ride-screen
  battery field, at the granularity the light reports.
- **FR-010**: Adding VS800 support MUST NOT change or regress the behavior of existing ANT+ or
  Magicshine lights.
- **FR-011**: System MUST surface the VS800 as unavailable (rather than appearing to control it) when
  it is connected to another device such as the iGPSport phone app.

### Key Entities *(include if feature involves data)*

- **VS800 light**: a Bluetooth bike light the rider owns, with a set of selectable modes (off, steady
  levels, flashes) and a battery level it reports.
- **Light assignment**: the rider's configuration for a managed light — its role (front/rear) and the
  mode it should use in each zone (day/night). The VS800 reuses this existing concept.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A rider can discover, pair, and fully configure a VS800 (role + day/night modes) in
  under 3 minutes, using only the Karoo (no phone app or cables).
- **SC-002**: When the active zone changes during a ride, the VS800 reflects the newly configured
  mode within 5 seconds.
- **SC-003**: With auto on/off enabled, the VS800 turns on at ride start and off at ride end in 100%
  of rides where the light is reachable.
- **SC-004**: The VS800's battery level is visible wherever other lights' battery is shown, and
  matches the value reported by the light.
- **SC-005**: Existing ANT+ and Magicshine lights behave identically before and after this feature
  (no observable regression).
- **SC-006**: After the VS800 sleeps or briefly leaves range mid-ride, control resumes automatically
  once it is reachable again, with no rider action.

## Assumptions

- The rider has an iGPSport VS800 (a front light) and a Hammerhead Karoo 3 on a recent software
  version.
- Only the VS800 model is in scope for this feature. Other iGPSport models may be added later but are
  not targeted or tested here.
- Advanced iGPSport-specific capabilities are out of scope for this feature: the custom-mode editor,
  the light's own "smart" feature toggles (auto light / auto sleep / auto dim / low-battery saving),
  and a remaining-runtime estimate.
- The VS800's control behavior (how to set modes, turn it off, read its current mode and battery) has
  been validated on real hardware during discovery, so the feature is technically feasible.
- The feature reuses KarooFireFly's existing automation engine (zones, time/ambient control, auto
  on/off), its settings UI and per-light configuration model, and its ride-screen battery field.
- "Off" is a reachable standby for this light: the beam is off but the Karoo can switch it on again;
  fully powering the light down remains a manual action on the light itself.
- The VS800 communicates with one device at a time; concurrent control by the iGPSport phone app and
  the Karoo is not supported by the light.
