# DiceFive: Google Play Games Services v2 - plan

**Status: Phase 1 is done.** It covers the seam, `GameMode.onlineLeaderboardKey`, the `online` DataStore, the
Settings card (hidden while the service is unavailable), and `ScoreEntry.playedUnmodified`.
- There's no Play Games dependency, no sign-in, and no console setup yet.
- Phase 0 is the maintainer's, and not done.
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
| Sign-in | `GamesSignInClient` | **Automatic** at launch. **No sign-out API.** `isAuthenticated()`, and `signIn()` for a retry. Needs `PlayGamesSdk.initialize()` in `Application.onCreate()` and the `APP_ID` meta-data |
| Player | `PlayersClient.getCurrentPlayer()` | Gamer name, avatar, id |
| Achievements | `AchievementsClient` | ≤400; **2,000 points** total, ≤200 each, multiples of 5; names ≤100 chars, descriptions ≤500; square icons; revealed/hidden/unlocked; incremental 2-10,000 steps; `setStepsImmediate` only moves forward; offline unlocks sync later. **Published ones can't be reset** (only drafts, by testers) |
| Leaderboards | `LeaderboardsClient` | ≤70; ordering fixed once published; daily/weekly/all-time and public/friends are automatic; optional min/max and tamper protection; ties aren't ranked by our rules. **Can't be reset** |
| Saved games | `SnapshotsClient` | Named blobs ≤3 MB; enabled in the console; offline commits cached; conflicts come as `isConflict()` and are settled with `resolveConflict` |
| Sidekick | Added by Play to uploaded bundles | Android 13+, 3 GB+ RAM, a gamer profile, **installed from Play**; enabled per release. Locked achievements show only after 100+ players use the achievements API in 30 days |

**Level Up** (the 2026 programme) is **decided: not a goal**. It also needs PC, controller support and Rewards. This
plan happens to meet its PGS, Sidekick, achievement and cloud-save guidelines anyway.

## Architecture: a platform seam

The same pattern as `PlatformServices`:

```
app/shared/.../online/        GameServices.kt (interface + GameServicesState), NoGameServices.kt,
                              OnlineSync.kt (reconciler), CloudSaveCodec.kt (stores <-> versioned JSON+gzip blob),
                              CloudSaveMerge.kt (pure, fully tested)          - nothing here names Google or Apple
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
    suspend fun readCloudSave(): ByteArray?
    suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray)
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
- **Play-only (decided).** The GitHub release key's SHA-1 isn't registered, so sideloads get `NoGameServices` and no
  card.
- **Never block on the network.** Every call runs in the app scope with a timeout, and errors go into `state`. Our
  banners stay local and instant.

## 1. Account and name

- **Sign-in** relies on automatic sign-in at launch. If that fails, the card shows **Sign in**.
- **There's no sign-out.** Instead the card has a local **"Use Google Play Games on this device"** switch that stops
  every call.
- **The gamer name appears only on the Settings card** ("Signed in as <name>").
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
- **Switching accounts.** The device remembers a salted hash of the last synced player id (in the `online` store).
  - A different account triggers a prompt: "**Link it to <name>** / **Keep it separate**".
  - "Separate" switches syncing off.

## 2. Achievements

There are 103 (out of Play's 400). `Achievement.id` is the stable key. Every achievement is `NORMAL` or `HIDDEN`;
`SECRET` is unused.

| Local | Play |
|---|---|
| `NORMAL` / `HIDDEN` | Revealed / Hidden |
| `SECRET` | not registered (local only) |
| `ProgressStyle.NONE` | Standard (`unlock`) |
| `CUMULATIVE`, target ≤10,000 | Incremental, same target (`setSteps`, forward-only and idempotent) |
| `PROFESSIONAL_ROLLER` (100,000 points) | Incremental, **10,000 steps of 10** |
| Ledger bands | Incremental, read from the scores table |
| `STREAK` | Standard (steps can't go back; the local bar stays) |

- **Local → Play** happens always, in reconcile only. That includes achievements earned before linking.
- **Play → local** happens on sign-in. Play-unlocked but locally locked achievements unlock quietly, with today's time
  and one "N achievements restored" message. Their styles unlock too. This restores a reinstall even without cloud
  save.
- **Mode guards** need nothing new; the mirror copies only what the engine decided.
- **Console work, prepared by code.**
  - **An export task** writes every achievement's id, text (en/es/ar), state, type and steps, under a banned-word
    guard.
  - **A Robolectric render task** exports 512x512 icons from `AchievementIcons.kt`. They're reviewed before upload.
  - **Points** use a tier formula (~19 average; ~300 held back for future modes), checked by a script.
  - Everything stays **draft** until ready, because only drafts can be reset.

## 3. Leaderboards per mode

**There are 7**, one per `GameMode`, in New Game order.

- **Submitted**: seat 1's final score, **only under the mode's own rules** (no modifiers, so no timer; the same test as
  `countsOnLeaderboard`). Off-Combined modes still get their own board.
- **Ordering**: larger is better, 0 to `maxPossibleScore`, with tamper protection on.
- **Ties** are ordered by submission time on Play. Our tie-break stays local; the online button's description can say
  so in a line.
- **The backlog (decided: yes).** On an account's first link on a device, submit seat 1's best score per mode:
  - rows with `isPrimaryPlayer` and `playedUnmodified` (`onLeaderboard` can't answer it for always-off modes, which is
    why that column exists);
  - after the first cloud merge, if cloud save is on;
  - also on a "Link" from the switch prompt ("Separate" submits nothing);
  - recorded per account in the `online` store;
  - skipping any row over `maxPossibleScore`.

  Appearing on that day's and week's boards is accepted.
- **A new mode** adds a console board and an `onlineLeaderboardKey` (`OnlineLeaderboardKeyTest` fails without one).
- **Viewing** uses Play's own screen (`getLeaderboardIntent`), opened from each mode card.

## 4. Cloud save

There's one snapshot, `dicefive-progress`: versioned JSON, gzipped.

| Data | In the save? | Merge |
|---|---|---|
| Achievement unlocks | Yes | Union; the earliest timestamp wins |
| Achievement counters | Yes, **per device** | G-counter: the sum across devices |
| Win streak | Yes | From the device that finished a game most recently |
| Scores | Yes | Union of rows by a new `ScoreEntry.uuid` (a Room migration from v1) |
| Dismissed stats cards | Yes | Union |
| Style picks, New Game setup | Yes | Last writer wins per key (timestamps in a side map) |
| Sound, vibration, animation level, page fit, confirm-leaving | **No** (per device) | - |
| In-progress game | **Yes (decided)** | Newest wins; an older unfinished game is lost |

- **Merge automatically; don't pick a save.** The data is facts, so nothing needs throwing away.
  - The only prompt comes on the first link when both the device and the cloud have progress: "**Combine** / **Use
    only the saved progress** / **Not now**", recommending Combine.
- **Resets travel too.** Each store carries a **reset marker** timestamp, and merges drop anything older.
- **When it syncs.**
  - Read and merge on sign-in and app start.
  - Write after a game, after a Styles change (debounced), and on going to the background.
  - Conflicts use `RESOLUTION_POLICY_MANUAL` → `CloudSaveMerge`, retrying up to 10 times.
- **Size.** ~120 bytes per score row before gzip. A test pins a 20,000-game save under 1.5 MB.
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
  - **Tab position: deferred, decided on a device.** The middle-right default covers the scorecard and tray, so
    `TOP_RIGHT` or `TOP_LEFT` is likely.
  - **Snooze: decided, 300s.**
- **Recommendation: enable it.**
  - Stage the rollout at 5%, comparing crashes, ANRs and engagement.
  - Check frame cost on a 3-4 GB device against the 60fps floor.
  - Confirm the tab doesn't cover a control TalkBack users need.

## Where it appears in the app

No new menu button: the menu is full, and adding one would move existing elements.

1. **Settings, a card at the top**, titled `serviceName` (never "Google" in shared UI).
   - Avatar, then "Signed in as <name>", or "Not signed in" with **Sign in**.
   - A "Use <service> on this device" switch.
   - "Save progress to the cloud", on by default, with "Last saved <time>".
   - A quiet error line ("Couldn't reach Google Play Games - will retry").
   - Hidden when `Unavailable`.
2. **Achievements**: "View in Google Play Games" in the count banner's footer, when signed in.
3. **Leaderboard**: a "Global leaderboard" action per mode card. The local tables stay.
4. **Game Over**: nothing. **Decided: submissions are quiet.**
5. **First launch**: nothing of ours. Check Play's "Welcome back" pop-up doesn't hide the logo too long.

- **Accessibility.**
  - The card is one group, with its status as `stateDescription`.
  - The switches announce their state.
  - The buttons are labelled, not icon-only.
  - The avatar is decorative.
  - Play's own screens are Google's: check them on a device.
- **Strings.** All in en/es/ar, with the service name as an argument. "Last saved" goes through `formatTimestamp`.

## The reset buttons

Play's achievements and leaderboards can't be reset once published; cloud save can.

- **Reset achievements (decided: disabled while linked).** Otherwise Play would restore everything at the next sign-in.
  - The row stays visible, with the text "Your achievements are linked to Google Play Games and can't be reset"
    (spoken as disabled, with the reason).
  - To reset, turn the service off first. The confirmation says Play keeps its copy.
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
  - `OnlineSettingsRepository` (`online.preferences_pb`: `use_on_device` and `cloud_save_enabled`, both default on).
  - `OnlineAccountCard`.
  - `onlineLeaderboardKey` (equal to each `id`; `OnlineLeaderboardKeyTest`).
  - `playedUnmodified`.
  - `DiceFiveApp`'s optional `gameServices` defaults to `NoGameServices`.
  - The card's TalkBack text is tested (`OnlineAccountCardTest`), but not heard on a device.
- **Phase 2: sign-in and the card.**
  - Add `play-services-games-v2`, an `Application` subclass calling `initialize`, the `APP_ID` meta-data, and
    `PlayGamesServices` (state, sign-in, name, avatar).
  - Add the account-switch prompt.
  - Before committing:
    - record the release APK size before and after, and what R8 kept;
    - check AboutLibraries lists the Play services licence, and add it to `allowedLicenses` with a comment;
    - update the privacy policy and the "Fully offline" line.
  - Verify on a real phone.
- **Phase 3: the achievements mirror.**
  - `OnlineSync`: reconcile, restore, and the superuser guard.
  - The mapping table and its test: every non-`SECRET` achievement mapped, steps ≤10,000, Professional Roller scaled.
  - The export and icon tasks, with renders reviewed before upload.
  - The Reset achievements row, and "View in Google Play Games".
  - Achievements stay in draft.
- **Phase 4: leaderboards.**
  - Create 7 boards (limits from `maxPossibleScore`, tamper protection on).
  - Submit from `finishGame`, plus the backlog on first link.
  - The "Global leaderboard" actions, the new step in `GAME_MODES.md`'s checklist, and the Reset leaderboard wording.
- **Phase 5: cloud save.**
  - Room v2 (`uuid`, a migration and the exported schema), timestamped settings writes, and per-device counters.
  - The codec and merge, tested for: union, G-counter sums, streak recency, last writer wins, reset markers, and a newer
    blob read safely by an older app.
  - The transport, the Combine prompt, and "Last saved".
  - Test on two devices, including offline edits on both.
- **Phase 6: Sidekick and release.**
  - `sidekick_config.xml`, and the bundle upload path.
  - Publish the achievements and boards.
  - A closed test, then a 5% rollout watching vitals, and the frame cost with the overlay open.
- **Later: iOS.** `GameCenterServices`: `authenticateHandler`, `GKAchievement` (`percentComplete`, `isHidden`),
  `GKLeaderboard` per key, and the shared blob.
  - Game Center **can** reset achievements, so reset rules become `GameServices` properties (`canResetAchievements`),
    never `if (iOS)`.
  - `GKAccessPoint` has the same placement concern as Sidekick.

**Before Phase 0 is done**, build the pure logic first, unit-tested against a fake `GameServices`:
- the codec and merge, plus `uuid`;
- the mapping table and test, and the export and icon tasks (points can't be finalised);
- the reset rules, the backlog selection and the superuser guard.

**Keep `play-services-games-v2` off `main` until Phase 0 exists.** Every push to `main` ships a GitHub release, and the
SDK merges `INTERNET` in. Shipping it would mean updating the privacy policy and the "Fully offline" text, and
accounting for the APK size, all for no benefit in a Play-only feature. If the SDK is built anyway, missing ids fall
back to `NoGameServices`.

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
| 2 | Reset achievements while linked | **Decided: disabled, with the reason** | Phase 3 |
| 3 | Reset leaderboard when signed in | **Decided: all devices**; Play bests stay | Phase 4/5 |
| 4 | Submit pre-link bests | **Decided: yes**, once per account per device | Phase 4 |
| 5 | In-progress game in the cloud | **Decided: yes**, newest wins | Phase 5 |
| 6 | GitHub builds sign in? | **Decided: Play-only** | Phase 0/2 |
| 7 | Achievement points | **Deferred**: the tier formula (~300 held back), or set by hand | Phase 3, before publishing |
| 8a | Sidekick tab position | **Deferred**: decided on a device | Phase 6 |
| 8b | Sidekick snooze | **Decided: 300s** | Phase 6 |
| 9 | Play's unlock pop-up | **Deferred**: suppress if v2 allows (`setViewForPopups` existed in v1), or accept | Phase 3 |
| 10 | Commit category | **Decided: `[Play Games]`** | Phase 1 |
| 11 | Locked icon on Play | **Deferred**: own icon greyed (recommended), or a padlock | Phase 3, with the renders |
| 12 | Game Over note | **Decided: stay quiet** | Phase 4 |

**Still deferred: 7, 8a, 9, 11.**

## Sources

[Sign-in](https://developer.android.com/games/pgs/android/android-signin) ·
[Achievements](https://developer.android.com/games/pgs/achievements) ·
[Leaderboards](https://developer.android.com/games/pgs/leaderboards) ·
[Saved games](https://developer.android.com/games/pgs/android/saved-games) ·
[Sidekick](https://developer.android.com/games/pgs/play-games-sidekick) ·
[Sidekick SDK](https://developer.android.com/games/pgs/play-games-sidekick-sdk) ·
[Level Up](https://developer.android.com/games/guidelines) ·
[Release notes](https://developer.android.com/games/docs/release-notes)
