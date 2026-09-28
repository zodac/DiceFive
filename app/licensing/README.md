# Open-source license report config

Read by the AboutLibraries Gradle plugin (`aboutLibraries { collect { configPath } }` in
`app/android/build.gradle.kts`, via `licensingDir`). It sits beside the code modules rather than in
one, because it covers what both ship - `app/shared`'s font and icons as well as `app/android`'s
sounds - and an iOS build will need the same records. The library list shown in Settings > Licences is generated from
the real dependency graph on every build - nothing here lists dependencies. This folder only holds
what the plugin can't discover for itself:

- `licenses/<SPDX id>.json` - the full text of each allowed license, copied from
  [SPDX](https://github.com/spdx/license-list-data/tree/main/json/details) (`licenseText`), with any
  `Copyright (c) <year> <owner>` template line removed - the per-library copyright goes in the
  library entry instead. The build runs offline, so without this file a license would ship as a name
  with no text; `verifyLicenseReport<Variant>` fails the build if one is missing.
- `libraries/<name>.json` - an entry for something that isn't a Gradle dependency (a bundled font,
  sound or image - it gets a new `uniqueId`), or an override merged into a generated one (same
  `uniqueId`). A library under a license that requires its copyright notice to be reproduced (BSD,
  MIT, ISC, OFL) needs one of these whose `description` is exactly that notice, starting
  `Copyright` - the build fails until it has one.

- `asset-sources.json` - where every bundled asset file came from: each file under any of `app/`'s
  source sets' `res/` (except `values*/`), `rawAudioSource/` and `assets/`, and under any of
  `shared/`'s `composeResources/` (the shared UI's font and icons), keyed by its path relative to the
  repository root.
  Every entry - the app's own artwork included - needs:
  - `description` - what it is (and how it was modified, if it was);
  - `source` - where it came from: a download URL, or for the app's own work the Claude Code
    session and commit that created it;
  - `license` - an SPDX id, or `LicenseRef-DiceFive-AllRightsReserved` for the app's own work
    (see `LICENSE` at the repo root). For a third-party asset, the license of its source material.

  Then either:
  - **the app's own work**: `copyright` - its notice, starting `Copyright`; or
  - **third-party**: `libraries` - the `uniqueId` of the `libraries/` entry for *each* source it's
    made from (a remix of two recordings lists both). Each entry must list the same license, have a
    `website` (the source URL) and a `description` starting with its copyright/credit line
    (`Copyright waived by <author> under CC0 1.0 ...` for CC0). Put any credit text the license asks
    for - and, for CC-BY, a note of what was changed - in that description: it's what the
    Licences dialog shows. Third-party copyright is never duplicated in the manifest.

  `verifyAssetSources` fails the build on an unlisted asset, an incomplete or inconsistent entry, or
  an entry whose file is gone - so nothing can ship without a recorded source.

**Adding an asset**: add its `asset-sources.json` entry. If it's third-party, also add a
`libraries/` entry per source (license SPDX id, copyright/credit line as `description`, source URL as
`website`). If its license isn't already allowed, `allowedLicensesMap` in `app/android/build.gradle.kts`
needs it too (scoped to that entry), plus its text under `licenses/`.
