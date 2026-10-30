# KarooFireFly Constitution

## Core Principles

### I. Public API Compatibility

Public APIs MUST remain backward-compatible across releases. Breaking changes to any
publicly consumed interface require an explicit major-version bump and a documented
migration path for consumers. Rationale: downstream integrations and the published
`applicationId` depend on stable contracts; silent breakage erodes trust and strands users.

### II. Respect Service Boundaries

Changes MUST stay within the established service and layer boundaries (`karoo/`, `ble/`,
`light/`, `engine/`, `data/`, `ui/`). Cross-boundary interaction MUST go through the defined
interfaces (e.g. `LightController`) rather than reaching into another layer's internals.
Rationale: the layered design isolates ANT+, BLE, and state-machine concerns; honoring the
boundaries keeps the system testable and prevents regressions from leaking across layers.

### III. Established Test Suites (NON-NEGOTIABLE)

The repository's established unit and integration test suites MUST be run and MUST pass
before any work is considered complete. No change merges on unverified or skipped tests.
Rationale: automated suites are the objective gate for correctness; claiming completion
without passing evidence is not permitted.

## Governance

This constitution supersedes conflicting practices for the KarooFireFly repository. All pull
requests and reviews MUST verify compliance with the principles above; deviations MUST be
justified in writing and approved before merge.

Amendments require a documented proposal, maintainer approval, and a corresponding version
bump. Versioning follows semantic versioning: MAJOR for backward-incompatible governance or
principle removals/redefinitions, MINOR for new or materially expanded principles/sections,
PATCH for clarifications and non-semantic refinements.

Compliance is reviewed at code-review time and before each release; unresolved violations
block merge and release.

**Version**: 1.0.0 | **Ratified**: 2026-10-30 | **Last Amended**: 2026-10-30
