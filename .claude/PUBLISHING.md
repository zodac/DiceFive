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
- **Open-source licenses / third-party notices page - not yet drafted, and currently a real gap.**
  `app/build.gradle.kts`'s `packaging.resources.excludes` drops every AndroidX artifact's own
  bundled `META-INF/androidx/**/LICENSE.txt` (a release-size optimization - each is a duplicate
  copy of the same Apache-2.0 text), but this app has no substitute anywhere that gives recipients
  a copy of that license, which Apache-2.0 §4(a) requires. Investigated during that change: even
  *without* the exclusion, only 9 of the ~60-70 distinct Apache-2.0-licensed AndroidX/Kotlin/Compose
  artifacts this app actually ships bundle their own license text in the first place (the rest -
  Compose runtime/ui/foundation/material3, Kotlin stdlib, kotlinx.coroutines, most of Navigation and
  Lifecycle, Activity, Window, DataStore itself, etc. - never did), so restoring the exclusion would
  not have been a real fix either, just a partial, misleading one. The actual fix: one consolidated
  page (Apache-2.0's text once, plus the list of artifacts it covers - nearly everything here uses
  that one license) hosted the same way as the privacy policy (e.g. GitHub Pages), linked from
  **both** places this app is actually distributed - the Play Store listing (once it exists) *and*
  `README.md` (the GitHub release pipeline in `DESIGN.md` Phase 12 already publishes signed APKs
  publicly today, independent of any Play submission). No in-app screen needed for either link.

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
