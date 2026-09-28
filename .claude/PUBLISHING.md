# DiceFive: Play Store publishing reference

Requirements for getting this app onto the Play Store that aren't produced by the normal build
and aren't covered anywhere else. `.claude/DESIGN.md`'s Phase 12 covers the *GitHub* release
pipeline (signed `assembleRelease` APK attached to a tagged GitHub Release on every push to
`main`) - that is a different thing from a Play Store submission, which this file is about.

Nothing in this file is done yet; it's a checklist for whenever that submission actually happens.

## Outside this repo

A meaningful chunk of this is accounts and console configuration that happens nowhere near the
codebase - listed here up front, in the order it actually needs doing, so a session working
through this doc knows what to ask the user to go do versus what it can build itself.

**Accounts to register (do these first - verification/propagation lag):**

1. **Google Play Console developer account** - $25 one-time fee, plus identity verification that
   can take several days. Nothing else below can start (no app listing, no closed testing track,
   no Play Games Services link) until this exists. Do this before anything else in this file.
2. **A hosted privacy policy URL.** Needed the moment Google account sign-in, cloud save, ads, or
   purchases are in scope - which is immediately, since sign-in/cloud save/achievements are asked
   for now. A GitHub Pages page off this repo is a reasonable host; the content itself still needs
   writing (what's collected: Play Games account id, gameplay/achievement data now, advertising id
   and purchase data later).
3. **AdMob account** - only once ads work actually starts, not before. Linked to the Play Console
   listing once created.

**Console configuration with no repo equivalent (done by the user, in Play Console /
Google Cloud Console, not by editing anything here):**

- Filling in the store listing itself: descriptions, screenshots, feature graphic, category,
  contact email.
- The content rating questionnaire, data safety form, and target audience/content declarations.
  Each is re-visited (not just re-checked) once ads or billing ship, since both add questions.
- Enrolling in Play App Signing and generating/uploading the upload key's certificate - a key this
  session cannot generate on your behalf sight-unseen, since it's yours to keep.
- Creating the closed testing track and adding the 12+ testers Google requires for a new personal
  account, and leaving it running the required 14+ days before requesting production access.
- Linking Play Games Services to the app (Play Console walks this through registering the SHA-1
  fingerprints against a Cloud project's OAuth clients - see the Play Games Services section
  below) and adding Play Games Services testers.
- Once built: creating the Pro in-app product under Monetize > Products, adding license-tester
  accounts for it, and creating AdMob ad units.

**What a session here can actually do once accounts/console setup catch up:** add the Play
Games/Billing/Ads SDK dependencies, write the sign-in/achievement-sync/cloud-save/billing/ad code,
add the `INTERNET` permission and whatever `<meta-data>` entries a given SDK needs, and set up the
`.aab` build/signing path - all covered in the sections below.

## Store listing

- **Hi-res icon.** The Play Console wants its own 512x512 PNG for the store listing page,
  separate from the in-app adaptive icon (`app/src/main/res/drawable/ic_launcher_foreground.xml`
  / `@color/ic_launcher_background`) and not produced by `assembleDebug`/`assembleRelease` at
  all. Export the same artwork (the cup, dice, gold-on-navy brand colours - see `UI.md`'s Colour
  section for the palette) as a flat 512x512 PNG by hand and upload it separately.
- **Feature graphic and screenshots** - not yet drafted. Screenshots should come from the real
  app once there's a build worth showing, not mocked up.
- **Short description** (80 chars), **full description** (4000 chars), category, and contact
  email - not yet drafted; entered directly in Play Console alongside the privacy policy URL from
  "Outside this repo" above.
- **Open-source licenses / third-party notices - done in-app** (`DESIGN.md` Phase 17): Settings >
  "Licences", generated from the dependency graph at build time, with a build-failing
  guard against copyleft or unrecognised licenses. The build strips each AndroidX artifact's bundled
  `META-INF/androidx/**/LICENSE.txt` (a size optimisation) - that's fine now, since the dialog ships
  the Apache-2.0 text once for all of them. The sound effects are credited too (Freesound, CC0 and
  CC-BY 4.0 - see `app/aboutlibraries/asset-sources.json`). Remaining, optionally: linking a hosted copy from the Play listing /
  `README.md` for people who want to read it before installing (not required - the licenses only
  require that the text goes out *with* the app).
- **Adding or allowing a license**: if a new dependency fails the build on strict mode, read its
  license before adding it to `allowedLicenses` in `app/build.gradle.kts`. LGPL/MPL/EPL can often be
  complied with in a closed app (conditions on the library only) but each has real obligations
  (LGPL: the user must be able to relink/replace the library, awkward with R8); GPL/AGPL would
  require the whole app to be released under the GPL.
- **The app's own license - decided: proprietary, all rights reserved** (`LICENSE` at the repo root),
  with the source kept publicly readable on GitHub. Chosen over PolyForm Strict 1.0.0 and over any
  open-source license because:
  - the maintainer wants the code read-only (no reuse, no forks on app stores) and takes no outside
    contributions, and plain copyright - which is all "all rights reserved" relies on - is the most
    tested legal footing there is. PolyForm is well drafted but has little or no case history, and
    would add permissions (non-commercial use, a 32-day cure period) that weren't wanted;
  - a permissive license would let anyone publish the game with Pro unlocked and no ads;
  - the planned Google SDKs (AdMob, Play Billing, Play Games Services) are proprietary, and the
    planned **iOS** version would ship through the App Store - GPL-style licenses conflict with both.
  - The sole copyright holder can relicense later; copies already released stay under the terms
    they were released with.
- **The notice must stay scoped to DiceFive's own work.** It must never claim the bundled
  third-party parts, which keep their own licenses - `LICENSE` says so, and names the font:
  - **The Sora font must stay under the OFL.** OFL-1.1 condition 5: the font, "modified or
    unmodified, in part or in whole, must be distributed entirely under this license, and must not
    be distributed under any other license". Our `sora.ttf` is a modified (static weight-700)
    instance, still covered. `README.md`, store listings and any future EULA wording must not claim
    "all files" without that carve-out.
  - **The OFL also forbids selling the font on its own** (condition 1). Bundling it in a paid app
    is fine; offering the font file by itself is not.
  - **Audio**: CC0 needs nothing. CC-BY needs a credit, and §2(a)(5)(B) forbids adding terms that
    restrict the original - so the clip stays outside the all-rights-reserved scope (volume
    normalisation is too mechanical to create a new work anyway). CC-BY-SA adaptations must stay
    CC-BY-SA. Anything NC (non-commercial) is incompatible with ads/Pro and must be replaced.
  - The same applies to every library listed in the licenses dialog - not the app's to relicense.
  - The app's own artwork is recorded as `LicenseRef-DiceFive-AllRightsReserved` in
    `app/aboutlibraries/asset-sources.json` (constant `APP_LICENSE` in `VerifyAssetSourcesTask`) -
    change both together if the license ever changes.
- **Still open, related:**
  - **iOS**: the licences dialog is shared code now, and `asset-sources.json` already covers the
    shared font and icons - but the report it lists is generated from the *Android* build's
    dependency graph, and iOS currently shows an empty one (`IosPlatformServices.loadLicenceReports`).
    Before an iOS release, generate iOS's own report and apply the copyleft guard to the iOS
    dependency graph (AboutLibraries supports Kotlin/Compose Multiplatform) - see
    `IOS_SUPPORT.md` Phase 5. Apple's standard EULA covers App Store users - no custom EULA needed
    unless wanted.
  - **The public GitHub release APKs** give the game away outside Play; once Pro exists, the Play
    Billing check won't work in a sideloaded copy. Decide before Pro ships whether GitHub releases
    continue, stop, or become a Pro-less build.
  - **AI authorship**: much of the code and artwork was written with Claude. Copyright protection
    for AI-generated material is unsettled, which could weaken enforcement of the notice. Worth a
    lawyer's view, together with the notice itself, if the app starts earning real money.
  - **The copyright line is a pseudonym for now**: "Copyright 2026 zodac.net" (in `LICENSE`,
    `README.md` and the app's own entries in `asset-sources.json`). The maintainer owns zodac.net and
    has no company, so ownership is theirs personally either way. A domain can't own copyright, but a
    notice may use "a generally known alternative designation of the owner" (17 U.S.C. §401(b)(3)).
    The cost is that enforcing it means first proving who is behind zodac.net (the domain
    registration helps). Deliberately kept anonymous until payments ship. **Revisit when
    registering for Play/App Store payments**: individual seller accounts expose the legal name
    anyway (Apple shows it as the seller; Play's monetisation/EU trader details can), at which point
    "Copyright <legal name> (zodac.net)" costs no extra privacy and removes the proof step. Changing
    it only affects future releases' notices; ownership doesn't change.

## App build & manifest changes (in-repo)

The account/console side of this is covered in "Outside this repo" above - this is what actually
changes in the codebase to get a submittable build.

- **App Bundle, not APK.** The GitHub release pipeline (Phase 12) only ever produces a signed
  `assembleRelease` **APK** - Play has required an `.aab` for new apps' production tracks since
  2021, nothing in `app/build.gradle.kts` currently runs `bundleRelease`, and CI has no step for
  it; this needs its own build/signing path, separate from (or alongside) the GitHub one.
- **`INTERNET` permission.** `AndroidManifest.xml` currently declares none at all - Play Games
  Services, cloud save, ads and billing all need `android.permission.INTERNET` (Games Services
  and billing add it via their own SDK's merged manifest; ads may still expect it declared).
- Signing for the Play upload build should use the dedicated upload key from Play App Signing
  (see "Outside this repo") - not `app/debug.keystore`, and not necessarily the same keystore the
  GitHub release pipeline's `ANDROID_RELEASE_*` secrets point at either, since that key was chosen
  for a different pipeline before Play App Signing was in the picture.
- **Archive `mapping.txt` per release.** `DESIGN.md` Phase 12 turned on R8 shrinking and renaming
  for the `release` build type, which means `app/build/outputs/mapping/release/mapping.txt` is now
  a real deobfuscation mapping, not an empty file - without it, a renamed-class stack trace is
  unreadable. Play Console auto-detects and uses this mapping to deobfuscate Android vitals crash/
  ANR reports (Google's automatic crash collection, no SDK needed) as long as it's uploaded/bundled
  with the same build - either included in the `.aab` upload or attached manually under App
  integrity > Deobfuscation files in Play Console. Nothing currently keeps a copy of this file past
  a given local/CI build; the Play upload build/signing path this section describes needs to save
  it somewhere retrievable per version before this matters.

## Google Play Games Services - achievements, cloud save, sign-in

Covers three of the requested features at once: they're all facets of the one Play Games
Services (v2) integration, not three separate SDKs. `DESIGN.md`'s Phase 13 "Google Play Games"
entry already designed the achievement id mapping for this (secret achievements excluded) - none
of it is built yet. No Play Games dependency exists in `app/build.gradle.kts` today.

- **Link the app in Play Console** under Play Games Services, register the package name plus the
  **SHA-1 certificate fingerprint** for both the debug keystore (already in-repo) and whatever
  release/upload key ends up signing production builds - each needs its own OAuth client under the
  hood, which Play Console/Google Cloud Console generates once the fingerprint is registered.
- **Sign-in.** Add `com.google.android.gms:play-services-games-v2`. v2 is mostly *auto* sign-in
  (a silent attempt on launch, no explicit "Sign in with Google" button needed for the common
  case) - still needs a signed-out fallback UI state for whenever it fails/is declined, since
  nothing here currently has one.
- **Achievements.** Create each non-secret `Achievement.id` as a Play Games achievement definition
  in Play Console (hidden vs. revealed matching `AchievementVisibility.HIDDEN`/`NORMAL` - see
  `Achievement.kt`), then a thin sync layer that calls the SDK off `AchievementUpdate.newlyUnlocked`
  (the exact hook `DESIGN.md` Phase 13 already earmarks). Local storage stays the source of truth;
  Play Games is a mirror, so this device already works fully offline/signed-out.
- **Cloud save.** Play Games v2 still exposes this via the **Snapshots API** for arbitrary
  save-game blobs. Decide what "user state" actually means here before wiring it up - likely
  candidates: `SettingsRepository`'s DataStore (theme, name, style picks), the achievements
  DataStore, and the scores Room database (leaderboard/statistics) - and a conflict-resolution
  policy for two devices signed into the same account (last-write-wins vs. a merge, especially for
  the leaderboard's additive rows vs. settings' overwrite-in-place values). Snapshots have their
  own size ceiling and their own UI conventions (a save-picker) worth checking against how small
  this app's actual state is before assuming a full picker UI is warranted.
- Every one of these needs testers added as **Play Games Services testers** in Play Console before
  achievements/sign-in work for anyone outside the closed-testing track.

## Future: ads + a one-time Pro purchase

Not being built now, but both change the checklist above if/when they land, so noting the shape
in advance:

- **In-app purchase (Pro, one-time)** needs the **Play Billing Library**
  (`com.android.billingclient:billing-ktx`), a non-consumable in-app product created in Play
  Console > Monetize > Products, and license-tester accounts configured before real purchases can
  be tested (test purchases don't work until the app has at least reached internal/closed
  testing). The library's merged manifest adds the `com.android.vending.BILLING` permission
  automatically. Needs an entitlement check (`BillingClient.queryPurchasesAsync`) gating whatever
  Pro unlocks, plus a "Restore purchases" affordance for a reinstall/new device.
- **Non-intrusive ads** most likely means AdMob: `com.google.android.gms:play-services-ads`, an
  AdMob account linked to the Play Console listing, ad unit ids, and the
  `com.google.android.gms.ads.APPLICATION_ID` `<meta-data>` entry in the manifest. Targeting
  API 33+ also needs the separate `com.google.android.gms.permission.AD_ID` permission. Serving
  ads to EEA/UK/California users needs a consent flow - Google's **User Messaging Platform (UMP)**
  SDK - before any ad request goes out, which is its own small integration, not just a
  checkbox.
- **The two interact**: Pro's "no ads" needs the billing entitlement check gating the ad-loading
  code path, not just gating a settings toggle - and the content rating/data-safety/store-listing
  declarations above ("Contains ads", advertising ID collection, purchase data) all need
  re-submitting once either ships, even though the app was already live.

## Everything else

Release notes are the one piece of store-listing metadata that's per-submission text, not a
checklist item - drafted at release time, not stored here.
