# DiceFive: Google Play Games Services v2 - plan

**Status: Phases 1-5 are done; Phase 6 (Sidekick and release) is under way: the config is built, the console steps remain.** Phase 1 covers the seam, `GameMode.onlineLeaderboardKey`, the
`online` DataStore, the Settings card (hidden while the service is unavailable), and `ScoreEntry.playedUnmodified`.
- Phase 0's minimum is done: the PGS project (APP_ID `308613893005`), the consent screen and an Android credential.
- Phase 2 is done: the SDK, `DiceFiveApplication`, the `APP_ID` meta-data and
  `PlayGamesServices` (state, sign-in, name, avatar), checked on a real phone.
- Phase 3 is done: the 110 achievements are drafts in Play Console (imported from `scripts/export-play-console.sh`),
  `PlayGamesIds.kt` maps them, `OnlineSync` mirrors both ways (each account's apart, never after superuser mode), and
  our unlock banner stands aside for Play's pop-up while mirroring (only for a first unlock on Play, as Play shows
  none for one it has). Achievements with styles behind them get a styles
  banner. The Reset achievements row gives way to "View in Google Play Games"
  while signed in. Checked on a real phone.
- Phase 4 is done: the 7 boards are drafts in Play Console, mapped in `PlayGamesIds.kt` (`playLeaderboardIds`).
  `OnlineSync.reconcile` posts each mode's best unposted score (per account, a high-water mark of when the newest
  posted game was played, moved only once Play takes it), each Game Mode card has a Global button, and the Reset
  leaderboard confirmation says Play keeps its scores. Checked on a real phone.
- Phase 5 is done: Room v2 (`uuid`), stamped settings and in-progress writes, per-install counter shares,
  `CloudSave`/`CloudSaveMerge`/`CloudSaveCodec`/`LocalCloudSave`, the Snapshots transport, "Last saved", the retry line,
  and the sign-in sync's progress on the Settings card. Checked by the maintainer on real devices.
- Phase 6's code is done: `res/xml/sidekick_config.xml` (top-right in both orientations, 300s snooze), pointed at
  from the manifest and pinned by `SidekickConfigTest`. The bundle upload is by hand. Publishing, the closed test and
  the rollout are the maintainer's, in Play Console.
- This file is the one source on the subject: where another doc disagrees, follow this one.

> **The work isn't complete while a decision is Deferred.** A deferred decision blocks the phase named in "Decisions".
> When a phase reaches one, **ask the maintainer**. Never settle it with the recommended default. Record the answer
> here.

**What was asked for**:
1. Google account sign-in, kept apart from Player 1's name.
2. Play achievements mirroring the game's.
3. A Play leaderboard per mode.
4. Cloud save across devices.
5. The swipe panel, which is **Play Games Sidekick**.

The Google code stays out of shared code, because a Game Center version will follow.

## What Google provides (October 2026)

One SDK: `com.google.android.gms:play-services-games-v2` (v22.1.0; v1 is frozen).

| Feature | API | Limits that shape the design |
|---|---|---|
| Sign-in | `GamesSignInClient` | **Automatic** whenever the SDK starts. **No sign-out API**, and no way to turn the automatic one off (`isAuthenticated`, `signIn` and `requestServerSideAccess` are all there is, in 22.1.0). Needs `PlayGamesSdk.initialize()` (the docs say in `Application.onCreate()`) and the `APP_ID` meta-data |
| Player | `PlayersClient.getCurrentPlayer()` | Gamer name, avatar, id |
| Achievements | `AchievementsClient` | ≤400; **2,000 points** total, ≤200 each, multiples of 5 (no minimum documented); names ≤100 chars, descriptions ≤500; one square icon each; revealed/hidden/unlocked; incremental 2-10,000 steps; `setStepsImmediate` only moves forward; offline unlocks sync later. **Published ones can't be reset** (only drafts, by testers) |
| Leaderboards | `LeaderboardsClient` | ≤70; ordering fixed once published; daily/weekly/all-time and public/friends are automatic; optional min/max and tamper protection; ties aren't ranked by our rules. **Can't be reset** |
| Saved games | `SnapshotsClient` | Named blobs ≤3 MB; enabled in the console; offline commits cached; conflicts come as `isConflict()` and are settled with `resolveConflict` |
| Sidekick | Added by Play to uploaded bundles | Android 13+, 3 GB+ RAM, a gamer profile, **installed from Play**; enabled per release. Locked achievements show only after 100+ players use the achievements API in 30 days |

**Level Up** (the 2026 programme) is **decided: not a goal**. It also needs PC, controller support and Rewards. This
plan happens to meet its PGS, Sidekick, achievement and cloud-save guidelines anyway.

## Architecture: a platform seam

The same pattern as `PlatformServices`:

```
app/shared/.../online/        GameServices.kt (interface + GameServicesState), NoGameServices.kt,
                              OnlineSync.kt (reconciler), CloudSave.kt (the save + CloudSaveMerge, pure, fully tested),
                              CloudSaveCodec.kt (versioned JSON+gzip blob), CloudSaveStore.kt (LocalCloudSave: stores <-> save)
                                                                              - nothing here names Google or Apple
app/shared/.../ui/settings/OnlineAccountCard.kt   labelled by the service's own name
app/android/.../online/       PlayGamesServices.kt, PlayGamesIds.kt, res/values/games-ids.xml  - the only gms.games imports
app/shared/src/iosMain/.../online/   (later) GameCenterServices
```

```kotlin
interface GameServices {
    val serviceName: String                       // "Google Play Games" / "Game Center" - never hard-coded in shared UI
    val state: StateFlow<GameServicesState>       // Unavailable | SignedOut | SigningIn | SignedIn(account)
    suspend fun signIn()
    suspend fun unlockedAchievementIds(): Set<String>?   // null = couldn't ask
    suspend fun unlock(achievementId: String)
    suspend fun setProgress(achievementId: String, steps: Int)   // forward-only, idempotent
    suspend fun submitScore(leaderboardKey: String, score: Long)
    suspend fun readCloudSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead  // Found | Empty | Failed
    suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray?  // what was committed
    val canShowAchievementsUi: Boolean;  fun showAchievementsUi()
    val canShowLeaderboardUi: Boolean;   fun showLeaderboardUi(leaderboardKey: String?)
}
data class OnlineAccount(val displayName: String, val avatar: ImageBitmap?, val accountKey: String)
```

- **Local storage stays the source of truth.** The game never waits on or reads from the network. A signed-out,
  offline or sideloaded app behaves exactly as today.
- **Mirroring is idempotent, not a queue.** `OnlineSync.reconcile()` compares the whole local state with the service
  and sends the difference. It runs on sign-in, on app start and after each finished game, so a failed send is retried
  next time.
- **One hook per existing event.**
  - `AchievementUpdate.newlyUnlocked` asks for a reconcile.
  - `finishGame`/`persistHumanScores` ask for a score submission and a save write.
  - The engine and `GameMode` never learn the network exists.
- **Per-mode data is a field** (`onlineLeaderboardKey`), mapped per platform. A mode with no mapping isn't submitted
  there, and a test reports it.
- **Activity scope.** PGS clients need an `Activity`, so `PlayGamesServices` is built in `MainActivity`, re-attached
  across config changes, and passed as `LocalGameServices`.
- **Play-only (decided).** A release build uses Play Games only when installed by Google Play (`com.android.vending`);
  a debug build always does; and neither does without Google Play services (`PlayGamesServices.usesPlayGames`).
  Everything else gets `NoGameServices` and no card. A check at run time, not a missing credential: the GitHub APK is
  signed with the upload key, which may be registered.
- **Never block on the network.** Every call runs in the app scope with a timeout, and errors go into `state`. Our
  banners stay local and instant.

## 1. Account and name

- **Sign-in is the player's choice, from Settings only (decided).** Nothing signs in or asks to at launch.
  - The SDK signs in by itself whenever it starts, so it isn't started (`PlayGamesSdk.initialize`) until the player
    taps **Sign in** on the card.
  - The SDK also starts itself at every launch through its own `PlayGamesInitProvider`, which the app's manifest
    removes (`tools:node="remove"`; `PlayGamesServicesTest` checks it stays gone).
  - A successful sign-in sets `signed_in` in the `online` store. From then on the SDK starts with the app and signs in
    again quietly.
- **Sign out (decided)** replaces the old "Use Google Play Games on this device" switch. v2 has no sign-out API, so it
  is ours: it clears `signed_in` and stops every call. The SDK stays signed in until the app next starts, unused, and
  isn't started again. The account itself is untouched.
- **The gamer name appears only on the Settings card**: on its own, in the brand gold (`primary`), at 15sp - a size up
  from the lines under it, one below the service's name (maintainer's choice). TalkBack still hears "signed in as".
  - It **never** touches seat 1's name, so the name cap stays a pure layout rule.
  - It's sanitised (`sanitizePlayerName`) and ellipsised, not capped, on its own row of up to two lines.
  - TalkBack hears the full name: "Google Play Games, signed in as <full name>".
- **Everything is reported for seat 1, whatever name it holds,** to whichever account is signed in. Seats 2-4 are never
  submitted. The accepted cost: a friend in seat 1 plays to the owner's account.
- **Choosing the account happens outside the game.** v2 has no picker.
  - The system setting: Android Settings > Google > All services > Settings for Google apps > Play Games > Sign-in
    account.
  - Per game: long-press the icon > **Choose profile**.
  - Play asks once itself if no default is set.
  - The card names the account and where to change it.
- **Each account's synced data is kept apart (decided).** The device remembers the account it last synced with
  (`synced_account` in the `online` store, a salted hash).
  - The first account to sync, or the same one again, **merges** with the device: progress made signed out goes up
    (Google's guidance).
  - A **different** account **wipes** the device's synced data and restores its own. Today that's achievements and
    their counters (as Reset achievements does), then everything else in the cloud save (scores, synced settings, the
    game in progress; `CloudSaveStore.wipe`, which sets no reset marker). The last account's data is already online and
    returns when it signs in again. Nothing is wiped until the service has answered, about the save too.
  - Signing out wipes nothing: local state carries on, and merges into the next sign-in if it's the same account.
  - Accepted: anything the last account hadn't yet sent is lost on a switch.
  - No prompt. Sign in always uses Play's default account; changing it happens in Play Games' own settings.
- **Local → Play** happens always, in reconcile only. That includes achievements earned before linking.
- **Play → local** happens on sign-in. Play-unlocked but locally locked achievements unlock quietly, with today's time
  and **no message (decided)**. Their styles unlock too. This restores a reinstall even without cloud
  save.
- **Mode guards** need nothing new; the mirror copies only what the engine decided.
- **Console work, prepared by code.** `scripts/export-play-console.sh` writes the gitignored `play-console/`:
  - `achievements-import.zip`, for Achievements > **Import achievements**: `AchievementsMetadata.csv` (English, the
    default locale), `AchievementsLocalizations.csv` (es-ES, es-419 and ar, which must be added as translations in Play
    Console first), `AchievementsIconsMappings.csv` and the icons. No header rows, keyed by the English name.
  - Play's docs say names and descriptions shouldn't hold commas, but quoted (standard CSV) they import fine.
  - `achievements.csv` (one sheet with a header: id, points, tier, type, steps, state, text) and `icons/<id>.png`
    (512px: the glyph on the unlock banner's colour), for review.
  - **Type and state** come from `PlayGamesAchievements.kt` (`playType`, `playHidden`, `onPlay`), which the mirror uses
    too.
  - **Points** are by tier (`PlayConsoleAchievements.kt`, test sources): trivial 5, easy 10, medium 15, hard 25, very
    hard 45, extreme 70 (Historian, Professional Roller), Completionist 100, and every Easter egg 5. That spends all 2,000 (`PlayConsoleExportTest`), so a new
    achievement needs points freed up first.
  - Everything stays **draft** until ready, because only drafts can be reset.

## 3. Leaderboards per mode

**There are 7**, one per `GameMode`, in New Game order.

- **Submitted**: seat 1's final score, **only under the mode's own rules** (no modifiers, so no timer; the same test as
  `countsOnLeaderboard`). Off-Combined modes still get their own board.
- **How (built).** Not a queue: each `OnlineSync.reconcile` (on sign-in, at launch, and after every game, which always
  records an achievement update) sends each mode the best score from the games played since the newest one that
  account last took from this device (`scores_posted:<account>:<key>` in the `online` store,
  `ScoreDao.bestUnpostedScore`). So a first link sends the backlog, games played signed out or offline go up at the
  next sync (the best of them), and a send Play didn't confirm (`submitScoreImmediate`) is retried. Rows over
  `maxPossibleScore` are skipped. Superuser mode blocks scores as it does achievements (its dice can be set by hand).
- **Ordering**: larger is better, 0 to `maxPossibleScore`, with tamper protection on.
- **Ties** are ordered by submission time on Play. Our tie-break stays local; the online button's description can say
  so in a line.
- **The backlog (decided: yes).** On an account's first link on a device, submit seat 1's best score per mode:
  - rows with `isPrimaryPlayer` and `playedUnmodified` (`onLeaderboard` can't answer it for always-off modes, which is
    why that column exists);
  - after the cloud save's merge, so rows from the account's other devices go up too (Play keeps each player's best);
  - recorded per account in the `online` store;
  - skipping any row over `maxPossibleScore`.

  Appearing on that day's and week's boards is accepted.
- **Made by hand in Play Console** (it has no import for leaderboards), from `scripts/export-play-console.sh`'s
  `leaderboards.csv` (list order, limits, ordering, tamper protection, and the name in en/es/ar; es-ES and es-419 share
  the Spanish) and `leaderboard-icons/<key>.png`.
  - **Icons** (512px, `PlayConsoleLeaderboards.kt`, test sources: Play Console only, never shown in the game): a
    glyph per mode in the achievements' gold on their tile, bar Tricolour's dice in red, yellow and blue.
    `PlayConsoleLeaderboardsExportTest` checks each mode has one of its own.
  - An icon can be changed at any time; only the ordering is fixed once published.
- **A new mode** adds a console board, an icon and an `onlineLeaderboardKey` (`OnlineLeaderboardKeyTest` fails without one).
- **Viewing** uses Play's own screen (`getLeaderboardIntent`), opened from each mode card.

## 4. Cloud save

There's one snapshot, `dicefive-progress` (`CloudSaveCodec.SNAPSHOT_NAME`, permanent): versioned JSON, gzipped (okio,
already in the APK through DataStore).

| Data | In the save? | Merge |
|---|---|---|
| Achievement unlocks | Yes | Union; the earliest timestamp wins |
| Achievement counters | Yes, **per device** | `CounterMerge` on each counter. `SUM`: each share, added up. A share is one install's count from its last reset on (`counter_share_id`, in the achievements file, so a reset starts a new one); this one is the total less the other shares, kept in `counter_shares`. `FLAGS` (5x numbers): union |
| Win streak | Yes | `LATEST`: from the device that finished a game most recently (`counter_at_WIN_STREAK`) |
| Scores | Yes | Union of rows by `ScoreEntry.uuid` (Room v2) |
| Dismissed stats cards | Yes | Union, the later dismissal kept; dropped once a later game under the name (any case) is in the save |
| Style picks, New Game setup, sound, vibration, confirm-leaving | Yes (sound, vibration and confirm-leaving **decided: account state**, by the maintainer after testing) | Last writer wins per key (`synced_at:<key>` stamps, set only when a value changes; an unset key travels too). A value set before stamps existed is stamped 0 and beats one never set, so it travels at the first sync |
| Animations level, Styles page fit | **No (decided)**: about the device - what it can draw, and its screen | - |
| In-progress game | **Yes (decided)** | Newest wins (`saved_at`, stamped on save and on clear); an older unfinished game is lost. One this app can't read is never taken |

- **Merge automatically; don't pick a save. No prompt (decided).** The data is facts, so nothing needs throwing away.
  The first account (or the same again) merges; a different one wipes and restores (section 1).
- **Ids, not enums.** The save holds ids as strings, so an achievement, counter or setting a newer app added is carried
  through an older app's merge, not dropped.
- **Versions.** Every format is gzipped JSON with a top-level `version`; fields are only added within one. An app reads
  a newer version's fields it knows (merging them in) but **never writes over it** (`CloudSave.writableHere`; a merge
  keeps the higher version). A save that can't be read at all (corrupt) is replaced by this device's.
- **Resets.**
  - **Reset leaderboard travels**: `scores_reset_at` in the `online` store, the later of each copy's, and merges drop
    every score and dismissal at or before it, on every device.
  - **Reset achievements doesn't travel (decided)**: no marker, so the same account's next sync brings back unlocks and
    counters, as Play brings back its unlocks, on top of anything counted since (the reset started a new share). The
    style picks it reset are unstamped, so the cloud's come back too.
- **Targets reached together.** After a merge, `AchievementEngine.evaluateProgress` unlocks (quietly, then mirrored, so
  Play's pop-up announces it) anything the combined totals reach: a counter, a score band, career points, every mode.
- **When it syncs.** Every `OnlineSync.reconcile`: read, merge in, then write the union back only if this device added
  anything - before posting scores, so a sync started by going to the background saves first. That's on sign-in and
  app start, after a game, on leaving the game screen part-way through, 5s after the last synced-setting change (a
  Styles pick), on going to the background (`ON_STOP`) and on coming back (`ON_START`). No polling: a device sees
  another's changes at its own next trigger, so handing over is "put one down, pick the other up".
  - The write opens the snapshot again and merges with whatever another device committed since the read; the result
    is merged in here too.
  - Conflicts use `RESOLUTION_POLICY_MANUAL` → `CloudSaveMerge`, retrying up to 10 times.
  - "Last saved" (`cloud_saved:<account>`) is when the save last matched this device, written or not.
  - "Couldn't reach Google Play Games - will retry" shows on the card while the last sync failed to reach Play for
    achievements or the save (`OnlineSync.unreachable`).
  - Superuser mode stops it entirely, as it stops the mirror.
- **Size.** Measured: 20,000 games gzip to about 637 KB (~32 bytes a game); a test pins it under 1.5 MB.
- **iOS** shares the blob and merge, over `GKSavedGame` or iCloud key-value storage (1 MB).
- **Android backup** (`allowBackup`) already restores one device. Cloud save adds simultaneous devices.

## 5. Sidekick

An overlay Play draws: an edge tab that slides out achievements, streaks, Play Points, offers, videos and recording
tools. We don't build it.

- **What it needs.**
  - An AAB uploaded to Play, with "Sidekick on by default" ticked (Testing > Advanced settings). The sidekick SDK is
    APK-only, and we don't need it.
  - PGS achievements.
  - Players on Android 13+ with 3 GB+ RAM and a Play install. It never appears in sideloads.
- **What we control** (`com.google.android.finsky.deku.OVERLAY_CONFIG` → `res/xml/sidekick_config.xml`).
  - **Tab position: decided, `TOP_RIGHT` in portrait and landscape** (from renders of all six positions on phone and
    7-inch tablet). The middle-right default sits on the Σ totals button (portrait) or the cup (landscape), and
    `TOP_LEFT` on the back arrow. Top-right covers only the far end of the last player's header card, which is no
    control. The positions are physical edges, the same in Arabic. A player's own drag outranks them.
  - **Snooze: decided, 300s.** It hides the tab for that long in a player's first session; Play caps it at 30 minutes.
  - Both are in `res/xml/sidekick_config.xml`, read through the `OVERLAY_CONFIG` meta-data, and pinned by
    `SidekickConfigTest`.
- **Recommendation: enable it.**
  - Stage the rollout at 5%, comparing crashes, ANRs and engagement.
  - Check frame cost on a 3-4 GB device against the 60fps floor.
  - Confirm the tab doesn't cover a control TalkBack users need.

## Where it appears in the app

No new menu button: the menu is full, and adding one would move existing elements.

1. **Settings, a card at the top**, titled `serviceName` (never "Google" in shared UI).
   - Avatar, then the gold gamer name with **Sign out**, or "Not signed in" with **Sign in**, the button on the
     header's row.
   - **Sign out waits for the sign-in's sync (maintainer's request).** While signing in, and until that sync has
     finished (got through or not: `OnlineSync.signInSynced`, cleared on signing out), the button stays **Sign in**,
     greyed and disabled.
   - No switches. **Cloud save is always on while signed in (decided)**; "Last saved <time>" shows under the header.
   - **While a sign-in's sync runs** (`OnlineSync.progress`; the quiet syncs after it show nothing), a full-width
     block under the header names the step and how far through it is ("Merging scores (5/7)"), after a spinner (a
     still sync icon under reduced motion) and above a progress bar that glides 400ms from step to step (snapping
     under reduced motion). The seven steps (`SyncStep`), in the maintainer's order: downloading, settings, the game
     in progress, achievements (with Play's unlocks restored), scores (then any target the merged totals reach),
     uploading, leaderboard scores. The merges go by in a blink; the download and upload take the time. TalkBack
     hears the step in the heading ("..., Merging scores, step 5 of 7"); the block itself is hidden from it. The
     maintainer asked for it, after testing, so a sign-in doesn't look finished while it isn't.
   - A quiet error line ("Couldn't reach Google Play Games - will retry").
   - Hidden when `Unavailable`.
2. **Achievements**: "View in Google Play Games" takes Reset achievements' place in Settings while signed in.
3. **Leaderboard**: a "Global leaderboard" action per mode card. The local tables stay.
4. **Game Over**: nothing. **Decided: submissions are quiet.**
5. **First launch**: nothing of ours. Check Play's "Welcome back" pop-up doesn't hide the logo too long.

- **Accessibility.**
  - The card is one group, with its status as `stateDescription`.
  - The buttons are labelled, not icon-only.
  - The avatar is decorative.
  - Play's own screens are Google's: check them on a device.
- **Strings.** All in en/es/ar, with the service name as an argument. "Last saved" goes through `formatTimestamp`.

## The reset buttons

Play's achievements and leaderboards can't be reset once published; cloud save can.

- **Reset achievements (decided: hidden while signed in).** Play would restore everything at the next sign-in. In its
  place, the same row shape: **"View in Google Play Games"** ("See your achievements there"), opening Play's own
  achievements screen. Signed out, the row is back as before.
- **Reset leaderboard (decided: all devices).** It clears local and cloud copies, with a reset marker. The Play boards
  keep their entries, and the confirmation says so. There's no "this device only" option, because the cloud would
  restore it.
- **Superuser unlocks are never mirrored.** A flag in the `online` store stops achievement mirroring on that install,
  and a test pins that a superuser unlock makes no `GameServices` call.

## Phases

Each phase ends in a debug APK. Commits use **`[Play Games]`** (decided), which also covers the shared `online/` seam.
Game Center work later uses `[iOS]`.

- **Phase 0 (the maintainer's, no code).**
  - The Play Console account, the app, signing, and a closed track.
  - A PGS project, with OAuth clients for the debug SHA-1 and the upload and app-signing SHA-1s (not the GitHub key).
  - Testers, and Saved Games turned on.
  - **The minimum for debug-APK testing**:
    1. Create the app.
    2. Create and link the PGS project.
    3. Add an Android credential for `net.zodac.dicefive` with the debug SHA-1
       `76:7A:8F:8E:BF:D9:A9:58:4E:4C:21:D4:0B:9D:6E:51:1B:EB:21:A0`.
    4. Add PGS testers.
    5. Turn on Saved Games.
- **Phase 1: the seam. Done.**
  - `online/GameServices.kt` (with `GameServicesState`, `OnlineAccount` and `LocalGameServices`) and `NoGameServices`.
  - `OnlineSettingsRepository` (`online.preferences_pb`: `signed_in`, default off. `use_on_device` and
    `cloud_save_enabled` were never released and are no longer read).
  - `OnlineAccountCard`.
  - `onlineLeaderboardKey` (equal to each `id`; `OnlineLeaderboardKeyTest`).
  - `playedUnmodified`.
  - `DiceFiveApp`'s optional `gameServices` defaults to `NoGameServices`.
  - The card's TalkBack text is tested (`OnlineAccountCardTest`), but not heard on a device.
- **Phase 2: sign-in and the card.**
  - Add `play-services-games-v2`, an `Application` subclass calling `initialize`, the `APP_ID` meta-data, and
    `PlayGamesServices` (state, sign-in, name, avatar).
  - Before committing:
    - record the release APK size before and after, and what R8 kept;
    - check AboutLibraries lists the Play services licence, and add it to `allowedLicenses` with a comment;
    - update the privacy policy and the "Fully offline" line.
  - Verify on a real phone.
- **Phase 3: the achievements mirror. Done.**
  - `OnlineSync`: reconcile, restore, and the superuser guard.
  - The mapping table and its test: every non-`SECRET` achievement mapped, steps ≤10,000, Professional Roller scaled.
  - The export and icon tasks, with renders reviewed before upload.
  - The Reset achievements row, and "View in Google Play Games".
  - Achievements stay in draft.
- **Phase 4: leaderboards. Done.**
  - Create 7 boards (limits from `maxPossibleScore`, tamper protection on), from the exported sheet and icons.
  - Submit after each game, plus the backlog on first link (built as `OnlineSync`'s high-water mark; section 3).
  - The "Global leaderboard" actions, the new step in `GAME_MODES.md`'s checklist, and the Reset leaderboard wording.
- **Phase 5: cloud save. Done.**
  - Room v2 (`uuid`, `MIGRATION_1_2`, the exported schema, a Robolectric migration test), timestamped settings and
    in-progress writes, and per-device counter shares.
  - The codec and merge, tested for: union, G-counter sums, streak recency, last writer wins, reset markers, and a newer
    blob read safely by an older app (`CloudSaveMergeTest`, `CloudSaveCodecTest`, `OnlineSyncTest`, the stores' own).
  - The transport (`PlayGamesServices`: Snapshots, manual conflicts), "Last saved" and the retry line. No Combine
    prompt (decided).
  - The sign-in sync's progress on the card, with Sign in held disabled until it ends, and the gold gamer name.
  - Checked by the maintainer across devices. Saved Games is on in Play Console (Phase 0, step 5).
- **Phase 6: Sidekick and release.**
  - `sidekick_config.xml`: **done** (section 5).
  - **The bundle upload path (decided: by hand).** CI doesn't upload to Play, and has no Play credentials.
    1. Get a signed `.aab`. `release.yml` attaches `DiceFive-<version>.aab` to the GitHub release from 1.0.0, or
       before it with the `force_apk` input on a manual run.
    2. In Play Console, create an internal or closed testing release and upload it.
    3. Tick **Sidekick is on by default** on that release (or set it once for every new bundle: Testing > Advanced
       settings > Play Games Sidekick).
    4. Install from Play's testing link on an Android 13+ phone with 3 GB+ RAM. A sideloaded APK never shows Sidekick.
  - **On that install, check** (the maintainer's): the tab sits top-right in both orientations and covers no control,
    TalkBack can still reach the header and back arrow, Play's "Welcome back" pop-up doesn't hide the logo too long,
    and the frame rate holds 60fps with the overlay open on a 3-4 GB device.
  - **Publish the achievements and boards** in Play Console (the maintainer's). Their ids, points and board ordering
    are permanent from then on (Risks). Settle decision 7's points first.
  - A closed test, then a 5% rollout watching crashes, ANRs and engagement against the release before.
- **Later: iOS.** `GameCenterServices`: `authenticateHandler`, `GKAchievement` (`percentComplete`, `isHidden`),
  `GKLeaderboard` per key, and the shared blob.
  - Game Center **can** reset achievements, so reset rules become `GameServices` properties (`canResetAchievements`),
    never `if (iOS)`.
  - `GKAccessPoint` has the same placement concern as Sidekick.

**Before Phase 0 is done**, build the pure logic first, unit-tested against a fake `GameServices`:
- the codec and merge, plus `uuid`;
- the mapping table and test, and the export and icon tasks (points can't be finalised);
- the reset rules, the backlog selection and the superuser guard.

**The SDK's cost** (v22.1.0, measured): the release APK grows 5.49 MB to 5.83 MB (+346 KB; about +143 KB
compressed), almost all of it in the second dex. It adds **no permission**: v2 talks to the Play services app, so
`INTERNET` is not merged in. Every push to `main` ships a GitHub release, so before the SDK lands there the "Fully
offline" text and the privacy policy must still be right for a Play install.

## Testing

- **Debug APKs test almost everything** once Phase 0's minimum is done. They use package `net.zodac.dicefive` (no
  suffix) and are always signed with the repo's debug keystore.
  - That covers sign-in and account switching, both mirror directions, the superuser guard, leaderboards and the
    backlog, and cloud save across two devices.
  - Draft achievements and boards can be reset for repeats.
  - The phone needs Play services and an account on the **PGS testers** list. Anyone else fails sign-in in a way that
    looks like a bug.
- **These need the Play release flow**:
  - Sidekick: the internal testing track, installed from Play, on Android 13+ with 3 GB+;
  - the Play-signed build's own SHA-1;
  - locked achievements in Sidekick, which need 100+ players and can't be seen in testing.
- **The sandbox emulator** is a Google APIs image without Play, so plan on a real phone.

## Optional extra: Game Stats (Level Up only)

Not in scope. Build it only if Level Up becomes a goal. Its rules: 5+ repeating stats with 1+ competitive (LU-GS-GAA);
a progression stat if there's a progression mechanic (LU-GS-GAB, exemption LU-GS-EAB); and each stat with a display
string, a unique icon and an aggregation (LU-GS-GAC).

| Stat | Source | Aggregation | Competitive |
|---|---|---|---|
| Games played | `AchievementCounter.GAMES_PLAYED` | sum | yes |
| Games won | `GAMES_WON` | sum | yes |
| Dice rolled | `DICE_ROLLED` | sum | yes |
| 5x scored | `SCORED_5X` | sum | yes |
| Career points | `ScoreDao.primaryPlayerTotalPoints` | sum | yes |
| (optional) Achievements unlocked | `AchievementsState` | max | no |

- **Building it.** An `OnlineSync` step reports each counter's increase since the last report (per device, in the
  `online` store), with reviewed icons and en/es/ar strings.
- **iOS** has no equivalent: `supportsGameStats = false`.
- **Stats only grow,** so the reset confirmations must mention them.

## Risks

- **Play's own unlock pop-up** would duplicate ours (decision 9).
- **Ids and points are permanent on Google's side.**
  - Retiring an achievement is a migration (keep the mapping, stop reporting it), not a deletion.
  - A rule change that makes old scores unfair needs a **new leaderboard key**; the old board stays frozen.
- **Testers must be PGS testers** until publishing.
- **Banned words** apply to every console string; the export goes through the `StringChecks` guard.
- **The data safety form and privacy policy** need updating before Phase 2 reaches anyone outside the team.

## Decisions

| # | Decision | Answer | Needed by |
|---|---|---|---|
| 1 | Level Up a goal? | **Decided: no** | - |
| 2 | Reset achievements while signed in | **Decided: hidden**, "View in Google Play Games" in its place | Phase 3 |
| 3 | Reset leaderboard when signed in | **Decided: all devices**; Play bests stay | Phase 4/5 |
| 4 | Submit pre-link bests | **Decided: yes**, once per account per device | Phase 4 |
| 5 | In-progress game in the cloud | **Decided: yes**, newest wins | Phase 5 |
| 6 | GitHub builds sign in? | **Decided: Play-only** | Phase 0/2 |
| 7 | Achievement points | **Decided: set by hand** from an exported sheet; Easter eggs 0 if Play allows it (the docs give no minimum - test with a draft), so the points come from the normal achievements | Phase 3, before publishing |
| 8a | Sidekick tab position | **Decided: `TOP_RIGHT`** in portrait and landscape, from renders; checked on a Play install in the closed test | Phase 6 |
| 8b | Sidekick snooze | **Decided: 300s** | Phase 6 |
| 9 | Play's unlock pop-up | **Decided: Play's replaces ours.** While signed in, an achievement mirrored to Play shows no unlock banner of ours (Play's pop-up instead); progress nudges and styles banners stay. Ships with the mirror, never before | Phase 3 |
| 10 | Commit category | **Decided: `[Play Games]`** | Phase 1 |
| 11 | Locked icon on Play | **Settled by Play**: one icon per achievement (`iconUrl`), and Play draws its locked look. The maintainer's choice was a padlock, which Play can't do without padlocking the unlocked icon too | - |
| 12 | Game Over note | **Decided: stay quiet** | Phase 4 |
| 13 | Sign-in at launch | **Decided: never before the player signs in from Settings**; Sign out replaces the device switch | Phase 2 |
| 14 | Cloud save switch | **Decided: none**; cloud save is always on while signed in | Phase 2 |
| 15 | Styles behind one achievement | **Decided: they get a styles banner too** (as count locks do), whether or not signed in, so the reward still shows when Play's pop-up replaces the unlock banner | Phase 3 |
| 16 | Another account signing in | **Decided: wipe the synced data and restore that account's**; the first account (or the same again) merges; no prompt; nothing wiped on sign-out | Phase 3 |
| 17 | First-link Combine prompt | **Decided: none.** Every sync merges (a different account wipes and restores, per 16) | Phase 5 |
| 18 | Reset achievements while signed out | **Decided: doesn't travel.** No marker; the same account's next sync restores unlocks and counters | Phase 5 |

**Nothing is deferred.**

## Sources

[Sign-in](https://developer.android.com/games/pgs/android/android-signin) ·
[Achievements](https://developer.android.com/games/pgs/achievements) ·
[Leaderboards](https://developer.android.com/games/pgs/leaderboards) ·
[Saved games](https://developer.android.com/games/pgs/android/saved-games) ·
[Sidekick](https://developer.android.com/games/pgs/play-games-sidekick) ·
[Sidekick SDK](https://developer.android.com/games/pgs/play-games-sidekick-sdk) ·
[Level Up](https://developer.android.com/games/guidelines) ·
[Release notes](https://developer.android.com/games/docs/release-notes)
