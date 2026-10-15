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
   - **Update it, and its "Last updated" date,** when ads, billing, a new SDK or `INTERNET` use ships, or when
     `android:allowBackup` changes (it mentions backup).
3. **An AdMob account**, only once ads work starts.

**Console-only configuration**:
- **The listing.** Text, graphics, category and contact.
- **Declarations.** The content rating, data safety form, and target audience. Each is revisited when ads or billing
  ship.
- **Play App Signing.** Play takes the upload certificate from the first signed `.aab`
  (`keytool -export -rfc -alias upload` if App integrity asks first).
- **Closed testing.** 12+ testers for 14+ days before requesting production.
- **Play Games Services.** Link it, register the SHA-1s and add testers (`PLAY_GAMES.md`).
- **Later.** The Pro product and its license testers, and AdMob ad units.

## Store listing

- **Graphics**: generated (the `store-assets` skill). The hi-res icon follows the launcher icon. The video goes to
  YouTube; Play takes its URL.
- **Short description** (80 chars max), drafted, 79 chars:

  ```
  Roll five dice, fill your scorecard, and outscore friends or AI. Fully offline.
  ```

  **"Fully offline" stops being true** when Play Games, cloud save, ads or Pro ship. Rewrite it then, and declare ads.
- **Full description**: `assets/full-description.txt`, about 2500 of 4000 characters. Keep its counts and lists true:
  - the modes under "Seven ways to play" (and that heading's count);
  - the modifiers under "House rules";
  - "Over 100 achievements" (103 today, 8 of them Easter eggs);
  - "over 90 styles", and its examples.
- **The banned-word rule applies.** Play also rejects listings that lean on another product's trademark. Use 3x/4x/5x.
- **Open-source notices are done in-app** (Settings > Licences, generated with a copyleft guard).
  - The build strips each AndroidX `META-INF/androidx/**/LICENSE.txt`, which is fine because the dialog ships the
    Apache-2.0 text once.
  - Sounds are credited (`app/licensing/asset-sources.json`).
  - Optional: link a hosted copy.
- **Allowing a new licence.** Read it before adding it to `allowedLicenses` (`app/android/build.gradle.kts`).
  - LGPL, MPL and EPL can be workable, but carry real obligations (LGPL relinking is awkward with R8).
  - GPL and AGPL would force the whole app open.

## The app's own licence

**Decided: proprietary, all rights reserved** (`LICENSE`). The source is publicly readable.

**Why**, over PolyForm Strict and open source:
- the maintainer wants read-only code, with no reuse, no store forks and no contributions;
- plain copyright is the most tested footing;
- a permissive licence would allow Pro-unlocked, ad-free rebuilds;
- the proprietary Google SDKs and the App Store conflict with GPL-style licences.

The sole holder can relicense later.

**The notice must exclude third-party parts**, which keep their own licences. `LICENSE` says so and names the font.
- **Sora stays OFL-1.1.** Condition 5: modified or not, it must be distributed under the OFL only. Our `sora.ttf` is a
  modified weight-700 instance. `README.md`, the listings and any EULA must never claim "all files" without that
  carve-out.
- **The MathJax TeX fonts** (the Maths and Poker dice) are OFL, with Reserved Font Names. They ship cut down, so
  renamed "DiceFive Maths" (`scripts/subset-maths-fonts.sh`; `MathsFontCoverageTest` guards the names).
- **The OFL forbids selling the font on its own.** Bundling it in a paid app is fine.
- **Audio.**
  - CC0 needs nothing.
  - CC-BY needs a credit and can't take added restrictions, so it stays outside the all-rights-reserved scope.
  - CC-BY-SA adaptations stay CC-BY-SA.
  - **NC licences are incompatible with ads or Pro.**
- **Every library** in the licences dialog keeps its own licence.
- **App artwork** is `LicenseRef-DiceFive-AllRightsReserved` in `asset-sources.json` (`APP_LICENSE` in
  `VerifyAssetSourcesTask`). Change both together.

**Still open:**
- **iOS licences.** Before an iOS release, generate iOS's own report and apply the copyleft guard to its dependencies
  (`IOS_SUPPORT.md`).
- **GitHub release APKs.** Once Pro exists, decide whether they continue, stop, or become Pro-less (billing doesn't
  work sideloaded).
- **AI authorship.** Copyright in AI-generated material is unsettled. Worth a lawyer's view if the app earns real money.
- **The copyright line is a pseudonym**: "Copyright 2026 zodac.net" (in `LICENSE`, `README.md` and
  `asset-sources.json`).
  - It's valid as a known alternative designation, but enforcing it needs proof of who owns the domain.
  - Revisit when registering for payments, which exposes the legal name anyway:
    "Copyright <legal name> (zodac.net)".

## In-repo build changes

- **App Bundle**: done. `release.yml` builds `bundleRelease` beside the APK (from 1.0.0, or `force_apk`) and attaches
  `DiceFive-<version>.aab`. Uploading to Play is by hand, with "Sidekick is on by default" ticked (`PLAY_GAMES.md`,
  Phase 6).
- **`INTERNET` permission**: not declared yet. Play Games, cloud save, ads and billing need it (SDK manifests merge it;
  ads may want it declared).
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

## Future: ads and a one-time Pro purchase

Not being built, but both change this checklist.

- **Pro.**
  - Needs Play Billing (`com.android.billingclient:billing-ktx`; it merges the BILLING permission) and a non-consumable
    product.
  - License testers are needed, and test purchases only work from internal or closed testing.
  - The app needs an entitlement check (`queryPurchasesAsync`) and a "Restore purchases" option.
- **Ads.**
  - AdMob: `play-services-ads`, the `com.google.android.gms.ads.APPLICATION_ID` meta-data, `AD_ID` (API 33+), and
    Google's UMP consent flow for the EEA, UK and California before any ad request.
- **Together.** Pro's "no ads" must gate the ad-loading code path. The rating, data-safety and listing declarations all
  need re-submitting.

Release notes for each submission are written at release time, not stored here.
