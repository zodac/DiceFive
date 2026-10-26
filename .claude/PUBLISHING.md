# DiceFive: Play Store publishing

What a Play Store submission needs beyond the normal build. The GitHub release pipeline is in `DESIGN.md`.

**Done**:
- the in-app licences page;
- the App Bundle, signing and the mapping archive;
- the store descriptions and the privacy policy (drafted);
- the listing graphics (`STORE_ASSETS.md`).

The rest is a checklist for when the submission happens.

## Outside this repo (the maintainer does these; ask, don't attempt)

In order:

1. **A Play Console developer account.** It costs $25 and identity verification takes days, and nothing else can start
   without it.
2. **A hosted privacy policy.** The draft is `docs/privacy-policy.md`.
   - GitHub Pages publishes it (`.github/workflows/pages.yml`, which runs when `docs/` changes; the Pages source must be
     "GitHub Actions") at https://zodac.github.io/DiceFive/privacy-policy.
   - The contact is zodacapps@gmail.com.
   - **Update it, and its "Last updated" date,** when billing changes, a new SDK or `INTERNET` use ships, or when
     `android:allowBackup` changes (it mentions backup). It describes the purchase (section 6) and says there are no
     ads (section 5), as of 2026-10-23.

**Console-only configuration**:
- **The listing.** Text, graphics, category and contact.
- **Declarations.** The content rating, data safety form, and target audience. Each is revisited when the purchase
  ships (`PURCHASES.md`, Phase 7). They're under Play Console > the app > **Policy and programs > App content**; the data safety answers are below.
- **Play App Signing.** Play takes the upload certificate from the first signed `.aab`
  (`keytool -export -rfc -alias upload` if App integrity asks first).
- **Closed testing.** 12+ testers for 14+ days before requesting production.
- **Play Games Services.** Link it, register the SHA-1s and add testers (`PLAY_GAMES.md`).
- **The "Unlock all styles" product** and its license testers: done (`PURCHASES.md`).

## Data safety

Play Console > the app > Policy and programs > App content > **Data safety**. The answers are the maintainer's to give
(Google says the developer alone is responsible for them). This draft follows what the app sends while signed in to
Play Games (`PLAY_GAMES.md`) and Google's list for the SDK
([Play Games data disclosure](https://developer.android.com/games/pgs/data-collection)). Nothing leaves the device
until the player signs in.

- **Does the app collect or share user data?** Yes: Play Games and cloud save send data off the device.
- **Encrypted in transit?** Yes (Play Games Services uses HTTPS).
- **Account creation**: "My app does not allow users to create an account". DiceFive has no account or identity of
  its own; sign-in uses the player's existing Google Play Games profile, which Google owns. So no account-deletion URL
  is required. Then:
  - **Can users log in with accounts created outside the app?** Yes, **Other**: "Optional sign-in with an existing
    Google Play Games profile, for achievements, leaderboards and cloud save."
- **Can users ask for deletion?** Yes: from their Play Games profile (one game's data, or the whole account) or their
  Google Account. If the form asks for a URL, give the privacy policy, whose section 3 says how.
- **Data types, all collected, none shared** (Google acting for the app isn't "sharing"), all **optional** (sign-in is
  the player's choice), purpose **App functionality** unless noted:
  - Personal info > **Name**: the player names typed in travel in the cloud save.
  - Personal info > **User IDs**: the Play Games player id and gamer name.
  - Photos and videos > none. The avatar is Google's, shown, not collected.
  - App activity > **In-app actions** (scores, achievements, progress counters) and **Other user-generated content**
    (the saved game: settings, styles, the game in progress).
  - App info and performance > **Crash logs** and **Diagnostics**: collected by the Play Games SDK itself, purpose
    Analytics (Google's list says "to improve the stability of our SDKs").
- **Revisit** when billing or analytics ship, or the cloud save gains a new kind of data.

## Store listing

- **Graphics**: generated (the `store-assets` skill). The hi-res icon follows the launcher icon. The video goes to
  YouTube; Play takes its URL.
- **Short description** (80 chars max), drafted, 77 chars:

  ```
  Roll five dice and outscore friends or AI. Offline unless you use Play Games.
  ```

  The full tagline (`README.md`'s first line, 120 chars) doesn't fit. Keep both true when the purchase ships.
- **Full description**: `assets/full-description.txt`, about 3000 of 4000 characters. Keep its counts and lists true:
  - the modes under "Seven ways to play" (and that heading's count);
  - the modifiers under "House rules";
  - "Over 100 achievements" (110 today: 100 count towards completion, plus 10 Easter eggs);
  - "over 90 styles", and its examples;
  - what "Google Play Games" says sign-in brings (`PLAY_GAMES.md`).
- **The banned-word rule applies.** Play also rejects listings that lean on another product's trademark. Use 3x/4x/5x.
- **Open-source notices are done in-app** (Settings > Licences, generated with a copyleft guard).
  - The build strips each AndroidX `META-INF/androidx/**/LICENSE.txt`, which is fine because the dialog ships the
    Apache-2.0 text once.
  - Sounds are credited (`app/licensing/asset-sources.json`).
  - Optional: link a hosted copy.
- **Allowing a new licence.** Read it before adding it to `LicencePolicy` (`build-logic/.../LicencePolicy.kt`).
  - LGPL, MPL and EPL can be workable, but carry real obligations (LGPL relinking is awkward with R8).
  - GPL and AGPL would force the whole app open.

## The app's own licence

**Decided: proprietary, all rights reserved** (`LICENSE`). The source is publicly readable.

**Why**, over PolyForm Strict and open source:
- the maintainer wants read-only code, with no reuse, no store forks and no contributions;
- plain copyright is the most tested footing;
- a permissive licence would allow styles-unlocked, ad-free rebuilds;
- the proprietary Google SDKs and the App Store conflict with GPL-style licences.

The sole holder can relicense later.

**The notice must exclude third-party parts**, which keep their own licences. `LICENSE` says so and names the font.
- **Sora stays OFL-1.1.** Condition 5: modified or not, it must be distributed under the OFL only. Our `sora.ttf` is a
  modified weight-700 instance. `README.md`, the listings and any EULA must never claim "all files" without that
  carve-out.
- **The MathJax TeX fonts** (the Maths and Poker dice) are OFL, with Reserved Font Names. They ship cut down, so
  renamed "DiceFive Maths" (`scripts/subset-maths-fonts.sh`; `MathsFontCoverageTest` guards the names).
- **Andika** (the D20 dice's numbers) is OFL, with Reserved Font Names "Andika" and "SIL". It ships cut down to the
  digits, with its crossed 7, so renamed "DiceFive D20" (`scripts/subset-d20-font.sh`; `D20FontCoverageTest` guards
  the names).
- **The OFL forbids selling the font on its own.** Bundling it in a paid app is fine.
- **Audio.**
  - CC0 needs nothing.
  - CC-BY needs a credit and can't take added restrictions, so it stays outside the all-rights-reserved scope.
  - CC-BY-SA adaptations stay CC-BY-SA.
  - **NC licences are incompatible with the purchase.**
- **Every library** in the licences dialog keeps its own licence.
- **App artwork** is `LicenseRef-DiceFive-AllRightsReserved` in `asset-sources.json` (`APP_LICENSE` in
  `VerifyAssetSourcesTask`). Change both together.

**Still open:**
- **AI authorship.** Copyright in AI-generated material is unsettled. Worth a lawyer's view if the app earns real money.
- **The copyright line is a pseudonym**: "Copyright 2026 zodac.net" (in `LICENSE`, `README.md` and
  `asset-sources.json`).
  - It's valid as a known alternative designation, but enforcing it needs proof of who owns the domain.
  - Revisit when registering for payments, which exposes the legal name anyway:
    "Copyright <legal name> (zodac.net)".

## In-repo build changes

- **App Bundle**: done. `android-release.yml` builds the play flavour's (`bundlePlayRelease`) beside the github APK and
  attaches `DiceFive-<version>.aab` (`DESIGN.md`, "Distribution"). Uploading to Play is by hand, with "Sidekick is on by default" ticked (`PLAY_GAMES.md`,
  "Release").
- **`INTERNET` permission**: not declared, and Play Games doesn't add it. The v2 SDK talks to the Play services app,
  which does the network work (`PLAY_GAMES.md`, "What Google provides"). The Billing Library's telemetry merges it,
  and the play manifest removes it (`PURCHASES.md`, "What Google provides").
- **Signing**: done. Play App Signing holds the app key. The upload key backs the `RELEASE_KEY*` secrets (alias
  `upload`, SHA-256 `DD:9E:C8:0C:…:1E:A6:18`); the maintainer keeps a copy, and Play support can reset it if lost.
- **`mapping.txt`**: done. Each release attaches `DiceFive-<version>-mapping.txt`, and the `.aab` carries it too
  (`BUNDLE-METADATA/.../proguard.map`), which Play uses to deobfuscate vitals. The attached copy is for GitHub-APK
  traces.
- **Native debug symbols**: done. The app has no native code, but AndroidX ships some (DataStore, Compose
  graphics-path). The release build type's `ndk.debugSymbolLevel = "SYMBOL_TABLE"` puts their symbol tables in the
  `.aab` (`BUNDLE-METADATA/com.android.tools.build.debugsymbols/`), so Play needs no separate upload. This needs the NDK pinned by
  `ndkVersion`: the sandbox image bakes it and CI installs it. No library may be listed in `keepDebugSymbols`,
  because AGP leaves a kept library out of the extracted tables. Graphics-path ships pre-stripped, so its frames stay
  as addresses.

## The purchase, and no ads

- **The "Unlock all styles" purchase**, the app's only one: `PURCHASES.md` has the plan, the payments profile, the
  product, license testers and the declarations it changes. GitHub release APKs continue, with no purchase shown.
- **No ads, ever** (decided 2026-10-23): no ad SDK, no `AD_ID`, and "Contains ads: No" in Play Console.

Release notes for each submission are written at release time, not stored here.
