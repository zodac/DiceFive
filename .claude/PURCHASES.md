# DiceFive: the "Unlock all styles" purchase

**In progress: the seam is built (Phase 2), nothing is visible yet.** One one-time Google Play purchase that unlocks
every style without earning it. Android only. This file is the plan, in phases. Resume at the phase marked **Next**.
Commits go under `[Purchases]`.

What the player gets:
- An **Unlock all** button in the Styles screen's top bar, showing Play's localised price.
- Once bought, every style and colour is usable, Easter eggs included. Achievement banners still pop; **styles
  banners stop** (there is nothing left to unlock).
- A reinstall or a new phone gets it back automatically (no Restore button on Android).

Researched October 2026 (Play Billing Library 9.1.0). Re-check the console paths when starting: Google renamed "in-app
products" to "one-time products" in 2025 and moves menus often.

## What Google provides

- **Library**: `com.android.billingclient:billing-ktx` (9.1.0, June 2026). It merges the
  `com.android.vending.BILLING` permission and talks to the Play Store app over IPC.
  - Every version from 8.0 also pulls in Google's telemetry (`com.google.android.datatransport`), which asks for
    `INTERNET` and `ACCESS_NETWORK_STATE`. The play manifest removes both (decided 2026-10-22), so the app still has no
    network permission and only that telemetry goes unsent (`PlayBillingStoreTest`). Phase 6 confirms purchases work so.
  - It also pulls in `play-services-location` and `play-services-places-placereport`, which add no permission.
- **Cost.** The play release APK grew 6.28 MB to 6.53 MB (+243 KB, unsigned, R8). The github APK has none of it.
- **Minimum version**: Play rejects updates using Billing Library below 8 since 31 August 2026. The monthly CI bump
  keeps it current; 9.0 needs AndroidX core 1.9+ (already met).
- **Product**: a **one-time product** with one purchase option ("Buy"). "Non-consumable" isn't a console setting: a
  product is consumable only if the app calls `consumePurchase()`. This app never consumes it.
- **Client essentials** (9.x):
  - `BillingClient.newBuilder(context).setListener(...)`
    `.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())`
    `.enableAutoServiceReconnection()`: then never call `startConnection()` again from `onBillingServiceDisconnected`.
  - `queryProductDetails` (suspend) for the price string; an unfetched product comes back with its own status code.
  - `launchBillingFlow(activity, ...)`: needs an `Activity`, as `PlayGamesServices` does.
  - `queryPurchasesAsync(QueryPurchasesParams(INAPP))`: what the player owns on this Play account. Call it on
    connection and on every `ON_RESUME` (a pending purchase can complete while the app is closed). This **is** restore:
    there is no separate restore API.
  - **Grant only on `PURCHASED`**, never `PENDING` (cash and slow payment methods).
  - **Acknowledge** every `PURCHASED` purchase with `acknowledgePurchase` once granted. Unacknowledged after **3 days**
    (3 **minutes** for a license tester) Play refunds it and revokes it.
  - A refunded or revoked purchase simply stops appearing in `queryPurchasesAsync`.
- **Who can buy**: only an install whose signature matches Play's (a Play-track install), or any build with the right
  package name on a **license tester's** account. A GitHub-release APK fails for everyone else.
- **Fees**: 15% of the first $1M a year once enrolled in the 15% tier (otherwise 30%).

## Design

### Architecture (to build)

```
app/shared/.../purchases/            Store (interface, StoreState, ProductKey, LocalStore), NoStore,
                                     PurchasesRepository (the cached entitlement) - nothing here names Google
app/android/src/play/.../purchases/  PlayBillingStore - the only com.android.billingclient imports, in the play
                                     flavour alone
```

- **Mirrors the Play Games seam** (`PLAY_GAMES.md`, "Architecture"): an interface in shared, a no-op default
  (`NoStore`), the Play implementation made once per process by `Distribution.store` in `MainActivity` and
  re-attached across configuration changes, handed to `DiceFiveApp` and provided through `LocalStore`.
- **The play app only.** The library is `playImplementation`; the github flavour's `Distribution.store` is `NoStore`,
  and `GithubFlavourTest` pins that neither the library nor the `BILLING` permission is in that app.
- **Calls.** One connection (`enableAutoServiceReconnection`). Play's purchase updates and the "what's owned" asks go
  through one queue, in order, so an ask already under way never overwrites a newer purchase. Every call has a
  15-second timeout but the purchase sheet; failures are logged as `adb logcat -s DiceFivePurchases`, with Play's
  response code, and a product Play didn't send logs its `UnfetchedProduct` status (not yet active, say).
- **Play-only, same gate as Play Games**: a release build uses Play Billing only when installed by Play
  (`com.android.vending`), a debug build always, neither without the Play Store. Otherwise `NoStore`: no button.
  GitHub APKs therefore never show the purchase.
- **`StoreState`**: `Unavailable`, `Loading`, `ForSale(price: String?)`, `Pending`, `Owned`. The price is null while
  Play hasn't sent it: the account is known not to own the product even then, which the entitlement needs.
  `BILLING_UNAVAILABLE` at connection (an old Play Store, a country without Play billing) is `Unavailable`.
- **Product id is permanent once created**: `unlock_all_styles` (`ProductKey.UNLOCK_ALL_STYLES`). It is the app's
  only purchase.

### Entitlement

- **Local cache is the source of truth while playing**, as everywhere else: a new DataStore file `purchases`
  (`PreferencesFile.PURCHASES`), key `owns_all_styles` (permanent). No reset clears it; it is **not** in the cloud
  save (the Play Store account owns the purchase, and may differ from the Play Games account).
- Read at launch into `AppContainer`, so the Styles screen, the menu logo and the game draw unlocked styles with no
  network and no wait.
- **Updated from Play** (`PurchasesRepository.follow`, collected by `DiceFiveApp`): on connection, `ON_RESUME` and a
  purchase update. `Owned` sets it; `ForSale` or `Pending` (a successful query without a paid purchase: a refund)
  clears it. `Loading` and `Unavailable` (no answer, or a failed one) change nothing (`StoreState.ownsAllStyles`).
- **Revoking keeps picks.** Existing rule: a locked saved pick draws the default but stays saved, so a refund just
  re-locks, and earning the style later brings the pick back.
- **No server verification.** There's no backend, the source is public, and it's cosmetic. Optionally verify
  `Purchase.signature` against the app's licence key (Play Console > Monetise with Play > Monetisation setup) on the
  device. A patched APK can unlock styles anyway; that's accepted.
- **Debug builds**: a superuser switch ("Pretend all styles are owned") swaps in a fake `Store`, for renders, the
  emulator (it has no Play Store) and screenshots. Never in release (`isDebugBuild`, as the superuser tests pin).

### Styles rule change

Today `StyleCatalog.isUnlocked(id, achievements)` and `StyleUnlock.isMet(achievements)` take an `AchievementsState`.
- Replace that parameter with a small value, e.g. `StyleAccess(achievements, ownsAllStyles)`, so the compiler finds
  every caller (about 20: `MenuViewModel`, `GameViewModel`, `SettingsViewModel`, `StylesScreen`, `OnlineSync`,
  `AchievementsViewModel`, ...). `SavedStyles` carries the flag.
- With `ownsAllStyles`, every `AchievementCount`, `SpecificAchievement` and `AnyAchievement` lock is met.
- **Secret colours** (`StyleColour.secretAchievement`, the Easter eggs) are included: `isAvailable` is met with the
  flag too, so they appear on the Styles screen for owners. Their achievements still unlock as normal.
- **Reset achievements** (`SettingsViewModel.resetLockedStylePicks`) re-locks picks against empty achievements; it
  must use the current `ownsAllStyles`, so an owner's picks survive a reset.
- **Fresh Coat Of Paint** reads `unlockedById`, so an owner can earn it on a bought style. Fine: Classic colours make
  it easy already.
- **Eggcellent Discovery**: buying gives Egg dice and the Chicken cup early, so the golden egg's 1-in-500 roll comes
  sooner; the gold Egg colour itself is bought outright. The achievement still needs the roll. Record this in
  `GAME_MODES_AUDIT.md`'s style notes.
- Unit tests: `StyleCatalogTest` (every lock met with the flag, secrets not), the reset path, `SavedStyles`.

### Banners and achievement rows

- `announce(update)` (`AchievementAnnouncements.kt`) takes `ownsAllStyles` and emits **no** `StylesUnlocked` events
  when it's true: neither the per-achievement reward banner nor the count banner. Unlock and progress banners are
  unchanged. Signed in to Play Games, Play's pop-up still replaces the unlock banner, so an owner sees only Play's.
- `AchievementAnnouncementsTest`-style tests: owned emits only unlock and progress events, in order.
- **The style star** (`StyleRewardStar` on unlock banners and Achievements rows, and the rows' "Show which style this
  unlocks" action) is unchanged for owners.
- Lock tooltips and the progress dialog on the Styles screen disappear naturally (nothing is locked).

### The Styles screen button

- Label "Unlock all" plus Play's price (`ProductDetails...formattedPrice`, already localised by Play; never format it
  ourselves). States:
  - `ForSale`: enabled button. Tap launches Play's sheet.
  - `Loading` or the price not fetched: disabled, no price.
  - `Pending`: disabled, "Payment pending".
  - `Owned`: the button is removed.
  - `Unavailable`: not shown.
- **In the top bar**, beside the title, so no card moves and `StylesWarmUp`'s `PageFit` is unaffected. A row above
  the cards was rejected: it would move every card and change the measured page height. Render it (the `render`
  skill) at 320dp, 2x font and in Arabic before building: the title and a priced button must share the bar.
- A successful purchase: a short confirmation ("All styles unlocked"), padlocks fade off. Spoken through a
  `liveRegion` or announcement, asserted word for word.
- **Accessibility** (`.claude/UI.md` checklist): a real `Button` with the price in its label ("Unlock all styles,
  £2.99"), `stateDescription` "Payment pending" when pending, 48dp target, readable at 2x font, RTL in Arabic.
  TalkBack must reach it before the first card. Play's purchase sheet is Play's own UI.

### Restoring: no button on Android

Settings gets nothing. The launch and `ON_RESUME` queries restore a purchase on a reinstall or a new phone, and Google
doesn't ask for a control. A player who bought on another Play Store account switches account in the Play Store and
reopens the app.

### Strings

All in Compose resources, en-GB, Spanish and Arabic (`I18N.md`). New: the button label and its spoken form, pending,
purchased confirmation, the debug superuser label (debug text stays as `I18N.md` says). British spelling. "Purchase"
not "IAP". Every spoken line asserted word for word.

## Phases

### Phase 0: decisions (done)

Decided (2026-10-21):
- **Price**: later, in Play Console at Phase 3 (the code doesn't depend on it). A launch offer is optional then.
- **Secret colours are included**: the purchase unlocks every style and colour, Easter eggs too.
- **The style star stays as it is** for owners.
- **Owned**: the button is removed.
- **Placement**: a top-bar action beside the title (renders confirm the look in Phase 5).
- **GitHub release APKs** keep publishing, with no purchase shown (the Play-only gate).
- **No Restore purchases button on Android** ("Restoring: no button on Android").

### Phase 1: the Google account side, part 1 (maintainer; Next: steps 2, 4 and 6)

Payments verification can take days, so start early. In order:

1. **Merchant account** (done 2026-10-22). The developer account already has a **payments profile**: it was made, with
   your legal name and address, when you paid the $25 registration. Selling turns that profile into a **merchant** one.
   Start wherever the console offers it (it moves): the app's **Monetise with Play** section ("Set up a merchant
   account"), or **Download reports > Financial > Set up a merchant account now**.
   - It reuses the existing payments profile. The link between a developer account and its payments profile is made
     **once and can never be changed** (changing it means a new developer account and moving the app), so check the
     legal name and address it shows before submitting.
   - It asks for business details: the name buyers see on receipts (the legal name), a physical address (no PO
     box), a business website, the product category, a customer support email (zodacapps@gmail.com) and the name
     on card statements (e.g. "DiceFive").
   - The business country **can't be changed** later.
   - Afterwards it's managed at **pay.google.com** (Google payments centre) and Play Console > **Settings >
     Payments profile**.
2. **What players see.** For a personal developer account, Play shows only the developer email by default; some
   regions require the phone number or full address too (Play Console Help, "developer account information"). Check
   what the console says it will show before submitting. The legal name appears on receipts, which also settles the
   pseudonymous copyright question (`PUBLISHING.md`: "Copyright <legal name> (zodac.net)").
3. **Bank account** (connected 2026-10-22). Payments profile > "How you get paid" > **Add payment method**, in the same
   country as the profile. It **must be verified**, by a small test deposit whose amount you enter: Google removes
   developers with unverified bank accounts.
4. **Tax information** in the payments profile (e.g. W-8BEN for a non-US individual; Google asks what applies).
   For UK/EU sales Google collects and remits VAT on digital goods itself.
5. **15% service fee tier** (confirmed 2026-10-22). Play Console > **Settings > Account groups** (create a group
   saying you have no other developer accounts), then accept the 15% tier's terms. Without this the fee is 30%.
6. **License testers.** Play Console (the account level, not the app) > **Settings > License testing**: add your
   Google account(s) and any testers, licence response "RESPOND_NORMALLY". These accounts get test cards and are
   never charged.

### Phase 2: the library and the seam (code; done 2026-10-22, but the upload)

Built as below, with the network permissions removed (see "What Google provides"). Left: the internal testing upload,
the maintainer's.

- Add `billing-ktx` to `libs.versions.toml` and the Android module only. It must pass the licence guard
  (`allowedLicenses`; the library is under the Android SDK licence terms, so check how the guard classifies it, as
  for `play-services-games-v2`). Record the APK size change, as `PLAY_GAMES.md` does.
- Shared: `Store`, `StoreState`, `NoStore`, `ProductKey`, `PurchasesRepository` (+ `PreferencesFile.PURCHASES`).
  Pure tests against a fake `Store`: owned, refund clears, failed query keeps, pending never grants.
- Android: `PlayBillingStore` (connect, product details, purchase flow, acknowledge, query on resume), the Play-only
  gate, Robolectric tests of the gate and the mapping from `Purchase` to `StoreState` (the
  `PlayGamesServicesTest` pattern). No UI yet.
- Confirm the merged manifest: `BILLING` added, no `INTERNET`.
- Ship it to the **internal testing** track (by hand, as today). Play wants a bundle containing the BILLING permission
  uploaded to a track before it lets you create products.

### Phase 3: the Google account side, part 2 (maintainer)

Once Phase 1's merchant account is active and Phase 2's bundle is on internal testing (Play enables product
setup only after a build containing the Billing Library is on a track):

1. Play Console > the app > **Monetise with Play > Products > One-time products > Create one-time product**.
   - Product id `unlock_all_styles` (**permanent**, can't be reused even if deleted).
   - Name and description, in English, Spanish and Arabic (they appear on Play's purchase sheet; banned-word rule
     applies).
   - Add a purchase option (**Buy**), set the price, and let Play convert it for every country. Tick it as digital
     content. Leave "multi-quantity" off.
   - **Activate** the product and its purchase option (a draft can't be bought).
2. Add your license-tester accounts to the **internal testing** track's tester list, and install from its opt-in link
   once (afterwards debug builds with package `net.zodac.dicefive` can buy too, as license testers).
3. Optional: install **Play Billing Lab** on the test phone for country overrides.

### Phase 4: entitlement in the styles rules (code)

The "Styles rule change" section: `StyleAccess`, `SavedStyles`, the reset path, the cached flag read at launch, the
debug fake store. Unit tests. Nothing visible yet but the superuser switch.

### Phase 5: the UI (code; renders first)

- Render the top-bar button (`render` skill: phone sizes, 2x font, Arabic, each `StoreState`). Agree the look, then
  build.
- Banners: `announce` gated; the star unchanged.
- Strings in three languages; spoken text asserted word for word; the accessibility checklist; then the debug APK.

### Phase 6: test on a phone (maintainer, with Claude's checklist)

On a real phone signed in to Play as a license tester (the emulator has no Play Store):
- Price shows; purchase with the "always approves" test card; padlocks go; no styles banners on the next unlock.
- **Acknowledgement**: wait 5 minutes; the purchase must still be there (an unacknowledged test purchase refunds at 3
  minutes, which would show the bug).
- "Slow test card, approves" and "declines": `Pending` shows, then owned or back to for sale, including with the app
  closed meanwhile.
- "Always declines": nothing granted, a clear message.
- **Refund**: Play Console > Order management > the test order > Refund (tick revoke). Next resume: styles re-lock,
  picks return when re-earned.
- **Restoring**: uninstall and reinstall; styles unlock by themselves at launch. Then a second phone on the same
  Google account.
- **Offline**: aeroplane mode after buying; styles stay unlocked.
- Reset achievements as an owner: picks kept.
- TalkBack through the button and its pending state.

### Phase 7: declarations and docs (maintainer + code)

- **Privacy policy** (`docs/privacy-policy.md`): purchases go through Google Play; the app stores only "owns all
  styles" on the device. Update "Last updated".
- **Data safety**: review; Google processes the payment. Purchase history is the likely question: the app reads the
  player's purchase of this one product from Play to unlock styles. Answer per Google's current guidance.
- **Content rating questionnaire**: re-answer ("digital purchases": yes). The listing then shows "In-app purchases".
- **Target audience**: if it includes children, the Families policy rules for purchases apply; check before
  submitting.
- **Store listing and `README.md`**: mention that every style can be earned for free or unlocked with a one-time
  purchase. Keep the "Fully offline" tagline true (buying needs Play; playing never does).
- Update `DESIGN.md` (Table art, locks), `UI_SCREENS.md` (banners, Styles screen), `PUBLISHING.md` (checklist "Later"
  item, data safety), `CLAUDE.md` (move this from "Planned"). Release notes are the maintainer's.

### Phase 8: release

Closed test with real (non-tester) accounts too: a tester's behaviour differs from a real buyer's, and real testers on
a track **are charged**. Then the staged rollout as for Play Games (`PLAY_GAMES.md`, "Release").

[Billing release notes](https://developer.android.com/google/play/billing/release-notes) ·
[Integrate](https://developer.android.com/google/play/billing/integrate) ·
[Testing](https://developer.android.com/google/play/billing/test) ·
[Payments profile](https://support.google.com/googleplay/android-developer/answer/7161426) ·
[Merchant account](https://support.google.com/googleplay/android-developer/answer/3092739) ·
[Getting ready](https://developer.android.com/google/play/billing/getting-ready) ·
[One-time products](https://support.google.com/googleplay/android-developer/answer/16430488) ·
[15% tier](https://support.google.com/googleplay/android-developer/answer/10632485)
