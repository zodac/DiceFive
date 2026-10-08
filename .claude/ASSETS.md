# DiceFive: Google Play Store Assets Generation Guide

This document details how store listing assets (app icon, phone, and 7-inch tablet screenshots) are generated programmatically using the project's Robolectric and Compose UI testing infrastructure, and how to regenerate them in the future.

---

## Generated Assets Overview

All generated assets are stored in the root `assets/` directory (which is git-ignored):
- `assets/app_icon_512.png` (512×512 px high-res app icon featuring the cup with falling dice artwork)
- `assets/feature_graphic.png` (TODO - deleted for manual design/customization)
- `assets/screenshots/phone/` (Full suite of 6 phone UI screenshots captured with clean state ordering)
- `assets/screenshots/tablet_7/` (Full suite of 6 7-inch tablet UI screenshots captured with clean state ordering)

Each suite includes:
- `main_menu.png` (Main Menu with Continue option)
- `game_board.png` (Rich mid-game board showing Player 1 and Player 2 scores, scored categories, and held/unheld dice)
- `styles.png` (Styles catalog with expanded 'Dice Cups' gallery view showing both closed and open gallery states on the same screen)
- `rules.png` (Game rules and scoring reference, captured cleanly without banners)
- `leaderboard.png` (Leaderboard statistics and scores, captured cleanly without banners)
- `achievements.png` (Unlocked achievements captured last, featuring an active notification banner demonstrating how unlock notifications work)

---

## How They Were Generated

We utilize Robolectric's native graphics mode (every unit test's, set in `app/android/src/test/resources/robolectric.properties`) combined with Compose UI testing rules (`createAndroidComposeRule<MainActivity>()`).

The test class [StoreAssetGeneratorTest.kt](../app/android/src/test/kotlin/net/zodac/dicefive/StoreAssetGeneratorTest.kt):
1. **Player Naming:** Names players `Player 1` and `Player 2` in database and game states.
2. **Strict Capture Ordering:** Captures Main Menu, Game Board, Styles, Rules, and Leaderboard first while achievement state is pristine and no event is emitted, guaranteeing 100% clean, popup-free screenshots.
3. **Banner Demonstration:** Emits an `AchievementEvent.Unlocked` event *only* during the final capture of the Achievements screen to illustrate active notification banners (anchored to the bottom via bottom-center alignment).
4. **Styles Gallery Expansion:** Automatically clicks `"Dice Cup gallery"` during the Styles capture to reveal both closed catalog rows and the open gallery view on the same screenshot.
5. **App Icon Generation:** Renders `R.drawable.ic_launcher_foreground` at high resolution with anti-aliasing.
6. **Tablet Assets:** Generates 7-inch tablet assets (`tablet_7`) using tablet qualifiers (`w600dp-h960dp-mdpi`), omitting 10-inch tablets as requested.

---

## How to Regenerate Assets in Future

To regenerate all store listing assets at any time:

1. Run the asset generator test task via Gradle:
   ```bash
   ./gradlew :app:android:testDebugUnitTest --tests net.zodac.dicefive.StoreAssetGeneratorTest -PgenerateStoreAssets
   ```
   Without `-PgenerateStoreAssets` the generator is skipped: it checks nothing, so ordinary test runs (and CI) don't
   pay for it.
2. Inspect the output files in the root `assets/` folder.
