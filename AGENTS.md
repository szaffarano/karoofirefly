# AGENTS.md

Guidance for AI agents working in the KarooFireFly repository.

## Project

KarooFireFly is an ANT+ & Bluetooth Smart bike-light controller extension for the Hammerhead
Karoo 3 cycling computer. It sets specific light modes based on time of day and ambient light,
driving ANT+ lights through Karoo's (undocumented) SensorService AIDL API and Magicshine BLE
lights directly.

This is a **maintenance fork** of `derstrassi/karoofirefly`. The Kotlin package namespace is
still `io.github.derstrassi.karoofirefly`, while the published `applicationId` is
`io.github.szaffarano.karoofirefly` — do not "fix" this mismatch, it is intentional.

- Single Gradle module: `app/`
- Language: Kotlin, UI in Jetpack Compose
- Dependencies managed via the version catalog at `gradle/libs.versions.toml`

## Build & verify commands

```bash
./gradlew spotlessApply        # auto-fix formatting (ktlint)
./gradlew spotlessCheck        # verify formatting (CI)
./gradlew detekt               # static analysis (CI)
./gradlew detektBaseline       # regenerate the baseline after burning down issues
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android Lint
./gradlew assembleDebug        # build debug APK
```

The full CI pipeline (`.github/workflows/build.yml`) runs:

```bash
./gradlew spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug
```

Always run `spotlessCheck` and `detekt` before claiming work is complete — both are enforced in CI.

## Environment & credentials

The `karoo-ext` SDK is hosted on GitHub Packages and requires credentials to resolve
dependencies (anything beyond `spotlessCheck`, which needs no Android deps):

- `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`, **or** `USERNAME` / `TOKEN` env vars
  (a GitHub PAT with `read:packages`).

This repo uses Nix + direnv. Copy `.envrc.sample` to `.envrc` and fill in credentials and release
signing vars. JDK 17+ is required (CI uses JDK 25).

## Code quality tooling

- **Spotless + ktlint** — formatting. Config in root `build.gradle.kts`; style shared with
  Android Studio via `.editorconfig`. ktlint owns line length (Detekt's `MaxLineLength` is off).
- **Detekt** `2.0.0-alpha.6` — static analysis. The alpha is required for Kotlin 2.4 compatibility;
  bump to 2.0 stable when released. Config in `config/detekt/detekt.yml`, tuned for Jetpack Compose
  (`@Composable` functions are exempt from `FunctionNaming`, `MagicNumber`, `LongMethod`, and
  complexity rules). Pre-existing findings are in `config/detekt/baseline.xml`; new code is checked
  strictly. Remove entries from the baseline as you fix them — do not regenerate it to hide new issues.
- **Android Lint** — `lintDebug`, report uploaded in CI.
- **pre-commit** — `.pre-commit-config.yaml` runs `spotlessCheck` + `detekt` on `.kt`/`.kts` changes.
  Activate with `pre-commit install`.

## Architecture

Layered design (see README "Architecture" for detail):

1. `karoo/` — SensorService AIDL binding, ANT+ light commands, runtime AIDL transaction-code detection
2. `ble/` — Magicshine BLE scanning, connection, protocol
3. `light/` — `LightController` interface abstracting ANT+ and BLE
4. `engine/` — state machine, sunrise/sunset calculation, ambient light sensor
5. `data/` — DataStore settings, per-light profiles
6. `KarooLightControllerExtension.kt` — KarooExtension service, entry point
7. `ui/` — Jetpack Compose settings screen

The core behaviour is a priority state machine: Manual Override > Auto Mode (time / ambient /
combined) > Ride State.

## Conventions

- Use comments sparingly — only for non-obvious logic.
- Compose functions are PascalCase by convention (tooling is configured to allow this).
- `versionCode` must stay monotonically increasing; it and `versionName` are bumped by the
  `prepare-release` skill (`.agents/skills/prepare-release/`), which also regenerates
  `app/manifest.json`.
- Discuss substantive changes before applying them, and never run a Git/GitHub write operation
  (commit, push, PR) without explicit confirmation.
