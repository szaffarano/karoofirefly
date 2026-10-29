---
name: prepare-release
description: >
    Prepare a new KarooFireFly release. Use when the user asks to prepare/cut/draft a release, bump the app
    version, or open a release PR. Bumps versionName/versionCode, regenerates the manifest with release notes, and
    opens a release-v* PR that the Release workflow publishes on merge.
---

# Prepare a new release

This skill prepares a release **PR**. It does not tag or publish anything — the
`.github/workflows/release.yml` workflow does the real release (tag, signed APK,
GitHub release) automatically when the `release-v*` PR is merged to `main`.

## Preconditions

- Clean working tree on an up-to-date `main` (`git switch main && git pull`).
- `./gradlew` works locally (Android SDK available).

## Steps

1. **Determine the new version.** Ask the user for the target `versionName`
   (e.g. `0.7.0`) unless they already gave it. Pre-releases use a suffix such as
   `0.7.0-beta1`.

   A version is a **pre-release** when `versionName` contains a `-` (e.g.
   `-beta1`, `-rc1`). Pre-releases are **sideload-only test builds**: they publish
   a GitHub prerelease APK but MUST NOT touch the Karoo-facing manifest, because
   the manifest's static `latest/download` URL only ever resolves to the latest
   *stable* release. Skip the manifest steps for pre-releases (see step 6).

2. **Read current values** from `app/build.gradle.kts`:
   - `versionName` (current release)
   - `versionCode` (integer)

3. **Compute the new `versionCode`** as `current versionCode + 1`. It MUST stay
   strictly greater than the previously published value or Android rejects the
   update.

4. **Collect release notes** from commits since the last tag:

   ```bash
   LAST_TAG="$(git describe --tags --abbrev=0)"
   git log --no-merges --pretty='* %s' "${LAST_TAG}..HEAD"
   ```

   Summarize into concise, user-facing bullet points. Confirm the notes with the
   user before continuing.

5. **Update `app/build.gradle.kts`**: set the new `versionName` and the
   incremented `versionCode` in `defaultConfig`.

6. **Regenerate the manifest** — **stable releases only.**

   - **Stable** (`versionName` has no `-`): regenerate so the PR carries the
     final `app/manifest.json`:

     ```bash
     RELEASE_NOTES='<the confirmed bullet points>' ./gradlew :app:generateManifest
     ```

     Verify `app/manifest.json` now shows the new `latestVersion`,
     `latestVersionCode`, and `releaseNotes`. `latestApkUrl` is static
     (`/releases/latest/download/karoo-light-controller.apk`) and does not change.

   - **Pre-release** (`versionName` has a `-`): **do not** run `generateManifest`
     and **do not** modify `app/manifest.json`. Leaving it untouched keeps the
     Karoo auto-update channel pointed at the last stable release while the
     prerelease APK stays available for manual sideloading.

7. **Open the release PR** (ask for confirmation before the push / PR — see repo
   AGENTS.md: sign commits with `-S`, prefer the GitHub MCP tool, confirm write
   operations). Stage `app/manifest.json` **only for stable releases**:

   ```bash
   git switch -c release-v<versionName>
   git add app/build.gradle.kts
   git add app/manifest.json   # stable releases only — omit for pre-releases
   git commit -S -m "Release v<versionName>"
   git push -u origin release-v<versionName>
   ```

   Then open a PR targeting `main` titled `Release v<versionName>` with the
   release notes as the body. For a pre-release, note in the PR body that it is a
   sideload-only test build that does not update the Karoo manifest.

8. **Hand off.** Tell the user: once the `release-v<versionName>` PR is merged,
   the Release workflow tags `v<versionName>`, builds the signed APK, and
   publishes the GitHub release with `karoo-light-controller.apk`. No tagging or
   manual release step is required.

## Notes

- The branch name **must** match `release-v*` — that prefix is what triggers
  `release.yml` on merge.
- Only non-prerelease versions become the `latest` release, which is what the
  manifest's static APK URL points to. A `-beta`/`-rc` version publishes as a
  GitHub prerelease, does **not** update the latest-download URL, and (per
  step 6) must leave `app/manifest.json` untouched — it is sideload-only.
- `versionCode` is still incremented for pre-releases so each build installs over
  the previous one.
