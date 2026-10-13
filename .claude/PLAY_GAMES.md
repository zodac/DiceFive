# DiceFive: online game services (Google Play Games Services v2) - proposal and plan

**Status: Phase 1 done** (the seam, `GameMode.onlineLeaderboardKey`, the `online` DataStore, the Settings card hidden
while the service is unavailable, `ScoreEntry.playedUnmodified`). No Play Games dependency, no sign-in, no console
configuration: Phase 0 is the maintainer's and not yet done. This file is the one place for the subject; where another
doc disagrees, follow this one. Decisions are marked **Decided:** or **Deferred:**, and all of them are listed in
[Decisions](#decisions).

> **This work is not complete while any decision is still open.** A decision may be deferred, but a
> deferred one blocks completion of the phase that needs it (named in the list) and of the work as
> a whole. When a phase reaches a deferred decision, ask the maintainer before building past it.
> Don't settle it with the recommended default.

What was asked for:

1. Link the game to a Google account, with the account's name kept apart from "Player 1" on the
   New Game screen.
2. Real Google Play achievements, mirroring the in-game ones.
3. A Google Play leaderboard for each game mode.
4. Cloud storage of the account's progress, so a player can move between devices.
5. An investigation of the "swipe" panel some games show (this is **Play Games Sidekick**).

Constraints: the Google code must stay apart from the shared code because an iOS (Game Center)
version will follow. This file also covers where all of this appears in the app, and what the two
Settings reset buttons mean once some of the data lives on Google's servers.

---

## Contents

- [What Google provides (as of October 2026)](#what-google-provides-as-of-october-2026)
- [Architecture: a platform seam, not a Google dependency](#architecture-a-platform-seam-not-a-google-dependency)
- [1. Account linking and the player's name](#1-account-linking-and-the-players-name)
- [2. Achievements](#2-achievements)
- [3. Leaderboards per game mode](#3-leaderboards-per-game-mode)
- [4. Cloud save](#4-cloud-save)
- [5. The swipe panel: Play Games Sidekick](#5-the-swipe-panel-play-games-sidekick)
- [Where it appears in the app](#where-it-appears-in-the-app)
- [The reset buttons](#the-reset-buttons)
- [Phased plan](#phased-plan)
- [Before Phase 0 is done](#before-phase-0-is-done)
- [Testing](#testing)
- [Optional extra: Game Stats (Level Up)](#optional-extra-game-stats-level-up)
- [Risks and gotchas](#risks-and-gotchas)
- [Decisions](#decisions)
- [Sources](#sources)

---

## What Google provides (as of October 2026)

All five requests come from one SDK, `com.google.android.gms:play-services-games-v2` (v22.1.0,
September 2026; v1 is frozen). Sidekick is added to the build by Play itself (see section 5).

| Feature | API | Limits and behaviour that shape this design |
|---|---|---|
| Sign-in | `GamesSignInClient` | **Automatic** at launch, with no button needed in the normal case. **No sign-out API**: the account is managed in the OS and Play Games settings. `isAuthenticated()`; `signIn()` for a manual retry. Needs `PlayGamesSdk.initialize()` in `Application.onCreate()` and the `com.google.android.gms.games.APP_ID` meta-data. |
| Player | `PlayersClient.getCurrentPlayer()` | Display name (the gamer name), avatar URI, player id. The id may not be trusted server-side. That doesn't matter here, because there is no server. |
| Achievements | `AchievementsClient` | Up to **400**. **2,000 points** in total, at most 200 each, in multiples of 5. Name up to 100 characters, description up to 500. Each needs a square icon. States: revealed, hidden, unlocked. An incremental achievement has **2 to 10,000 steps**. `setStepsImmediate` only ever moves forward. Unlocks made offline sync later. A published achievement **can't be reset**; only draft ones can (by testers, from the console or the Management API). |
| Leaderboards | `LeaderboardsClient` | Up to **70**. Ordering is fixed once published. Daily, weekly and all-time spans are created automatically, plus public and friends collections. Optional minimum and maximum scores, and tamper protection (on by default for new leaderboards). Equal scores aren't ranked by our tie-break rules. A published leaderboard **can't be reset**; the developer can only hide a player. |
| Saved games | `SnapshotsClient` | Named byte blobs of up to **3 MB** each. Enabled in Play Console. The save picker UI is optional. Commits made offline are cached and uploaded later. Conflicts arrive as `isConflict()`, carrying both versions, and are settled with `resolveConflict` using merged contents. |
| Sidekick | Added by Play, or the `com.google.android.play:sidekick` SDK for APK-only releases | Android 13+, at least 3 GB RAM, a Play Games gamer profile, and the app **installed from Play**. Enabled per release in Play Console. Shows achievements. Locked ones appear only after the game earns an "achievements badge" (100+ players calling the achievements API in 30 days). |

**Google Play Games Level Up** (the 2026 programme with the PGS v2, Sidekick, achievements, Game Stats
and cloud-save guidelines) is **optional**. It brings store promotion and Quests/Rewards, but it also
asks for things this app doesn't do: PC distribution, keyboard/controller play, Rewards offers and
form-factor coverage. This plan happens to meet its PGS, Sidekick, achievement and cloud-save
guidelines along the way (10+ achievements with unique names and descriptions, 4 reachable in the
first hour, cloud save with a conflict policy). It doesn't aim for the rest. **Decided:
Level Up is not a goal.** Game Stats stays documented as an
[optional extra](#optional-extra-game-stats-level-up), built only if that changes.

---

## Architecture: a platform seam, not a Google dependency

This uses the same pattern as `PlatformServices` (see `IOS_SUPPORT.md`): an interface in
`commonMain`, implemented once per platform, with a do-nothing version for previews, tests and builds
that have no service.

```
app/shared/src/commonMain/kotlin/net/zodac/dicefive/
├── online/                          NEW - platform-neutral; nothing here names Google or Apple
│   ├── GameServices.kt              the interface (below) + GameServicesState
│   ├── NoGameServices.kt            "not available": previews, tests, sideloads without Play
│   ├── OnlineSync.kt                the reconciler: local stores -> GameServices (idempotent)
│   ├── CloudSaveCodec.kt            local stores <-> one versioned save blob (JSON + gzip)
│   └── CloudSaveMerge.kt            merging two blobs (pure, fully unit tested)
├── model/GameMode.kt                + onlineLeaderboardKey (a stable string, like Achievement.id)
└── ui/settings/OnlineAccountCard.kt the Settings card, shared, labelled by the service's own name

app/android/src/main/kotlin/net/zodac/dicefive/
└── online/                          NEW - the only code that imports com.google.android.gms.games
    ├── PlayGamesServices.kt         implements GameServices on PGS v2
    ├── PlayGamesIds.kt              Achievement.id / GameMode key -> Play's generated ids
    └── res/values/games-ids.xml     exported from Play Console (ids aren't secrets)

app/shared/src/iosMain/.../online/   (later) GameCenterServices: GKLocalPlayer, GKAchievement,
                                      GKLeaderboard, GKSavedGame or iCloud key-value storage
```

A sketch of the interface, with every call suspending and none throwing to the UI:

```kotlin
interface GameServices {
    /** "Google Play Games" / "Game Center" - shown in Settings, never hard-coded in shared UI. */
    val serviceName: String
    val state: StateFlow<GameServicesState>   // Unavailable | SignedOut | SigningIn | SignedIn(account)
    suspend fun signIn()                      // manual retry; auto sign-in is the platform's business
    suspend fun unlockedAchievementIds(): Set<String>?            // null = couldn't ask
    suspend fun unlock(achievementId: String)
    suspend fun setProgress(achievementId: String, steps: Int)    // forward-only, idempotent
    suspend fun submitScore(leaderboardKey: String, score: Long)
    suspend fun readCloudSave(): ByteArray?
    suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray)
    val canShowAchievementsUi: Boolean;  fun showAchievementsUi()          // Play's own screens
    val canShowLeaderboardUi: Boolean;   fun showLeaderboardUi(leaderboardKey: String?)
}
data class OnlineAccount(val displayName: String, val avatar: ImageBitmap?, val accountKey: String)
```

Rules that hold it together:

- **Local storage stays the source of truth.** The game, achievements engine, Leaderboard and
  Statistics never wait on, or read from, the network. `OnlineSync` *mirrors* local state out, and
  merges cloud state *in*. A signed-out, offline or sideloaded copy behaves exactly as it does
  today.
- **Mirroring is idempotent, not a queue.** Unlocks and `setSteps` are safe to repeat, and Play keeps
  a player's best leaderboard score. So rather than keeping an outbox of "unlocks not sent yet",
  `OnlineSync.reconcile()` compares the whole local state with the service and sends the
  difference. It runs on sign-in, on app start, and after each finished game. Nothing is lost when
  a send fails; the next reconcile sends it again.
- **One hook per event, where the events already happen.** `AchievementUpdate.newlyUnlocked` asks for a reconcile of achievements. `GameViewModel.finishGame`
  / `persistHumanScores` asks for a score submission and a cloud-save write. Neither the engine nor
  `GameMode` learns that a network exists.
- **Per-mode online data is a field on `GameMode`** (`onlineLeaderboardKey`), not a
  `when (mode)` in the Google code. This is the same rule `GAME_MODES.md` sets for every per-mode
  rule. The platform layer maps the key to its own id. Play's ids are generated by the console;
  Game Center's are chosen by us. A mode with no mapping on a platform is simply not submitted
  there, and a test reports it.
- **Activity scope.** PGS v2 clients are created from an `Activity`, while `AndroidPlatformServices`
  holds only the application context. `PlayGamesServices` is therefore built in `MainActivity`,
  re-attached across configuration changes, and handed to `DiceFiveApp` next to `PlatformServices`
  (as `LocalGameServices`).
- **Builds without Play.** The GitHub release APK and devices without Play services get
  `NoGameServices` once PGS reports it is unavailable, so the Settings card is hidden rather than
  broken. **Decided: Play Games is Play-only.** The GitHub release key's SHA-1 is *not* registered,
  so a sideloaded GitHub build never signs in. It shows no Play Games card and behaves as it does
  today.

---

## 1. Account linking and the player's name

**Sign-in.** We rely on PGS v2's automatic sign-in at launch. When it fails or the player has
declined it, the Settings card offers a single **Sign in** button (`signIn()`). There is **no
sign-out**, because v2 doesn't have one. Instead, the card has a local **"Use Google Play Games on this
device"** switch. Turning it off stops every call (no mirroring, no cloud save) without touching the
account. This is the only kind of "off" Google allows, and it is honest about it.

**The name - kept completely apart from Player 1, as suggested.** The account's gamer name appears
in **one place**: the Settings card, as "Signed in as <name>" next to the avatar.

- It **never** fills, renames or replaces seat 1's name on the New Game screen. That name stays what
  the player typed, remembered per slot by `SettingsRepository.playerNameFor`, and limited by
  `GameSetupState.maxPlayerNameLength` (14/12/10/8 at 1-4 players). This keeps the cap a pure
  layout rule (`UI.md`, "Constraints worth knowing"). A gamer name of arbitrary length never reaches the
  in-game header, the Game Over page, the local Leaderboard or Statistics, so none of their
  measurements change.
- On the Settings card it is run through `sanitizePlayerName` (the same protection against invisible,
  direction-changing and "glitch" text that typed names get) and is **ellipsised, not capped**. It sits
  on a row of its own that can take two lines at large font sizes. For TalkBack, the card reads
  "Google Play Games, signed in as <full name>", so the full name is heard even when it is cut short
  on screen.
- Google's own surfaces (online leaderboards, Sidekick, the "Welcome back" pop-up) show the gamer
  name in Google's UI. That is outside our layout and our cap.
- **Who an online score or achievement belongs to.** Achievements are already player-1 only
  (`AchievementEngine`), and `ScoreEntry.isPrimaryPlayer` marks seat 1's rows. So everything is
  reported for **seat 1, whatever name is typed in it**, to whichever account is signed in on the
  device. Seats 2-4 (pass-and-play friends) are never submitted. The ledger achievements still
  count every human's scores, as `DESIGN.md` sets out. Accepted cost: if a friend
  plays as seat 1 on the owner's phone, the result goes on the owner's account. That is the same
  trade-off the achievements already make.

**Choosing which Google account (several on one phone).** The game **doesn't get an account picker
at start-up**. PGS v2 signs in silently with the device's **default games account**, and there is no
v2 API for a game to list or choose accounts. The player chooses outside the game:

- *Default for every game:* Android Settings > Google > All services > Settings for Google apps >
  Play Games > Sign-in account > Change.
- *Just this game:* long-press DiceFive's launcher icon > **Choose profile**. This is the "quick
  profile switcher", which appears only when the device has more than one Google account.
- If no default has been set and there are several accounts, Play Games itself asks once on the
  first sign-in. That is Google's screen, not ours.

Google warns that changing profiles for a game "may load different progress". So the Settings card
should say which account is in use, and mention, in its description text, where to change it.
Calling `signIn()` again doesn't switch accounts. It only retries the same default. The prompt below
is what makes a switch safe.

**Switching accounts on one device.** The device remembers which account it last synced with (a
salted hash of the player id, in a new `online` DataStore file). If a *different* account signs in,
nothing merges automatically. The card asks: "This device's progress was linked to another Google
Play Games account. **Link it to <name>** / **Keep it separate**". Choosing "separate" switches online
syncing off on this device until the player decides otherwise.

---

## 2. Achievements

**What's there today:** 103 achievements (`model/Achievement.kt`), well inside Play's 400.
`Achievement.id` is a stable snake_case key (the enum constant, itself the English title, in lower case) that never
changes once released. Every achievement is `NORMAL` or `HIDDEN` (the Easter Eggs included: title shown, description
"???"); no achievement uses `AchievementVisibility.SECRET`.

**Mapping, one row per achievement, in a generated table checked by a test:**

| Local | Play | Notes |
|---|---|---|
| `NORMAL` | Revealed | |
| `HIDDEN` | Hidden | Play blanks the description and icon until unlocked, the same as "???". |
| `SECRET` | **Not registered** | A local-only surprise, never synced; unused today. |
| `ProgressStyle.NONE` | Standard | `unlock`. |
| `ProgressStyle.CUMULATIVE`, target ≤ 10,000 | Incremental, same target | `setSteps(localProgress)`: it only moves forward and is idempotent, so a repeat or a second device is harmless. |
| `PROFESSIONAL_ROLLER` (100,000 career points) | Incremental, **10,000 steps of 10 points** | The only target above Play's 10,000-step ceiling. |
| Ledger bands (46/50 distinct scores) | Incremental | Progress is read from the scores table as it is today. |
| `ProgressStyle.STREAK` (win streaks) | Standard | Already decided: Play steps can't go backwards. The local progress bar stays. |

**Direction of sync.**

- **Local → Play (always):** every locally unlocked achievement is unlocked on Play, and every
  cumulative one gets `setSteps`. That includes achievements earned before the account was linked.
  It only happens in reconcile, never inline in the game.
- **Play → local (on sign-in):** any achievement Play says is unlocked but is locked on the device is
  **unlocked locally** with today's timestamp, without a banner (one quiet "N achievements restored
  from Google Play Games" message). This restores a reinstall or a new phone *even without cloud
  save*. It also unlocks the styles tied to those achievements, so they stay in step.
- Mode-blocked achievements (see the `GAME_MODES.md` audit) and the cheat rules need nothing new.
  The engine has already decided what is earned; the mirror copies only that.

**Console work the code can't do, prepared by a script the code *can* write:**

- **Text:** a generator task writes a CSV/JSON of every achievement's id, title, description,
  state, type and steps for English, Spanish and Arabic (the app's `values-xx`). It is pasted into
  Play Console, or pushed with the Publishing/Management API. Play's limits (100 characters for a
  name, 500 for a description) are far looser than ours (38 characters on one line), so the in-app
  text is reused as it is. The trademark rule applies to the console text too, and a guard test
  covers the generated file.
- **Icons:** each achievement needs a square icon. `ui/achievements/AchievementIcons.kt` already draws
  each one in Compose, so a Robolectric render task (the `STYLE_ART.md` technique) exports 512×512
  PNGs. As with all art, the renders are sent for review before anything is uploaded. **Deferred
  (blocks Phase 3):** whether a locked icon is each achievement's own icon, greyed (recommended), or
  one generic padlock. To be settled alongside the icon renders.
- **Points (2,000 in total, multiples of 5):** 103 achievements average about 19 points. A proposed
  formula, by tier: one-shot easy 5, medium 10, hard 20, ladders rising 5→50, the 6 ledger bands and
  Completionist 50-100, and **about 300 points held back** for future modes. A script computes and
  checks the total. **Deferred (blocks publishing in Phase 3/6):** use the tier formula, or set
  points by hand.
- **Draft until ready:** everything stays in draft while testing, because only draft achievements
  can be reset for testers.

---

## 3. Leaderboards per game mode

**One Play leaderboard per `GameMode`**: Standard, Tricolour, Third Wind, 7 Dice Stud, D20, Hit List and Quickfire.
That is 7 of the 70 allowed, in the same order as the New Game menu. This matches the local
Leaderboard's per-mode cards (`ScoresViewModel.modeBoards`), which list every mode, including those
kept off the Combined table.

- **What is submitted:** seat 1's final score from a finished game, but **only when the game ran
  under that mode's own rules**. No modifiers, which also means no turn timer (the same test that
  takes a Standard game off the local Leaderboard, `GameState.countsOnLeaderboard`). Comparing like
  with like is the point of a per-mode table. AI scores are never recorded, as today.
  `countsOnLeaderboard = false` modes (Stud, Third Wind and so on) **do** get their own online table:
  that flag keeps them off the *combined* local board, and doesn't mean they can't be compared with
  themselves.
- **Ordering:** larger is better, numeric. **Upper limit = `GameMode.maxPossibleScore`** and lower
  limit = 0, with tamper protection on, so an obviously fake score is rejected by Google.
- **Ties:** Play ranks equal scores by when they were submitted, not by our tie-break rules
  (`game/TieBreak`). The local Leaderboard keeps the house rules, and the online one is "best score
  per player". The Rules page needs no change. It is worth one line on the online button's
  description.
- **Scores from before linking:** Play keeps each player's best, so submitting seat 1's best local
  score per mode once, when the account is first linked, is cheap. But it would appear on *today's*
  daily and weekly tables. **Decided: submit the backlog.** When an account is
  first linked on a device, seat 1's best local score in each mode is submitted to that mode's Play
  leaderboard. Daily and weekly tables showing it on that day and week is an accepted side effect.
  - **Which rows count:** `isPrimaryPlayer` rows with `ScoreEntry.playedUnmodified`, the same test a new game's
    submission uses. (`onLeaderboard` can't answer it for a mode that is always off the Combined board, where it is
    `false` whether or not a modifier was on - which is why that column exists. Every row records it.)
  - **When:** once per account per device, after the first cloud-save merge if cloud save is already
    on, so the best of all devices' merged rows is used. It also runs when a *different* account is
    linked through the account-switch prompt and the player chose to link this device's progress.
    "Keep it separate" submits nothing. Done is recorded in the `online` store against the account
    key. Repeating it is harmless anyway, because Play keeps the best score.
  - Each score is still checked against the mode's `maxPossibleScore` limit. A row over it (a
    corrupted row, or a rule change since) is skipped rather than rejected by Google.
- **A new mode** adds a leaderboard in the console plus its `onlineLeaderboardKey`. `GAME_MODES.md`'s
  checklist gains that line. A test fails if a mode has no key, so a new mode can't be forgotten.
- **Viewing:** Play's own leaderboard screen (`getLeaderboardIntent`) opened from each mode card on
  the Leaderboard screen. We don't redraw Google's tables ourselves.

---

## 4. Cloud save

**What is saved, as one versioned snapshot named `dicefive-progress`** (JSON, gzipped; far below 3 MB):

| Data | Today | In the save? | Merge rule |
|---|---|---|---|
| Achievement unlocks | `achievements` DataStore | Yes | **Union**. For an achievement unlocked on both, the earliest timestamp wins. |
| Achievement counters (games played, dice rolled...) | same | Yes, **per device** | Each device keeps its own counter (a G-counter). The total is the sum, so two phones that each played 10 games make 20, not 10. |
| Win streak counter | same | Yes | Taken from the device that **finished a game most recently**. A streak can't be merged; it belongs to whichever device's timeline is newest. |
| Scores (Leaderboard + Statistics) | Room `scores` | Yes | **Union of rows**, identified by a new `ScoreEntry.uuid` column (a Room migration from version 1; existing rows are given one). |
| Dismissed Statistics cards | `DismissedPlayerStats` | Yes | Union. |
| Style picks, New Game setup (names, seats, CPU difficulty, colours, mode, modifiers) | `settings` DataStore | Yes | **Last writer wins per key**. Every write gets a timestamp, kept in a side map in the save. |
| Sound, vibration, animation level, Styles page fit, "confirm before leaving" | same | **No**: these depend on the device (a tablet and a phone want different animation levels and fits) | - |
| In-progress game | `in_progress_game` DataStore | **Yes (decided)** | Last writer wins. "Continue" on device B carries on device A's game. Two unfinished games: the newer one wins, and the older one is lost. |

**Why merge automatically rather than "pick a save".** Level Up's guideline says "usually the player
decides", but it only *requires* a conflict policy. This app's data is a set of facts (unlocks,
finished games), not one linear save, so a merge never needs to throw any of it away. Showing a
picker would make a player choose between two halves of their own history. The only prompt is the
**first link on a device that already has its own progress while the cloud also has some**. Even
then, the recommendation is to merge, with a one-line explanation:

> "Your progress on this device will be combined with your saved progress from Google Play Games."
> **Combine** / **Use only the saved progress** / **Not now**

**Resets must travel too.** Union merging would bring back anything a reset deleted, the moment
another device synced. Each store therefore carries a **reset marker** (a timestamp). A merge drops
anything older than the newest marker it sees. See [The reset buttons](#the-reset-buttons) for what
each reset does once an account is linked.

**When it syncs:**

- Read and merge on sign-in and on app start.
- Write after each finished game, after a Styles change (debounced) and when the app goes to the
  background.
- With `RESOLUTION_POLICY_MANUAL`, a conflict calls `CloudSaveMerge`, retrying up to 10 times as
  Google's sample does.
- Offline commits are cached by Play itself.

**Size check:** a score row is about 120 bytes of JSON before gzip, so 10,000 games come to about
1.2 MB raw and well under 300 KB gzipped. A test pins the encoded size of a 20,000-game save below
1.5 MB, as headroom against the 3 MB limit.

**iOS:** the blob format and merge are shared code, so only the transport changes: `GKSavedGame`,
which needs iCloud, or iCloud key-value storage (1 MB). The same blob even lets a player who switches
platforms carry progress over, though linking a Google account to an Apple one is out of scope.

**Android backup.** `android:allowBackup="true"` already restores local data on a new phone through
Google's device backup. Cloud save covers what backup doesn't: two devices *at once*, and devices that
don't share a backup.

---

## 5. The swipe panel: Play Games Sidekick

**What it is.** The panel is **Play Games Sidekick**, an overlay Play draws over the game. It has a
small tab on the screen edge (middle-right by default) that slides a panel out. The panel holds the
player's achievements, streaks, Play Points, Play Pass offers, Quests (for enrolled games), the
official YouTube videos added to the store listing, and screenshot, screen-recording, livestream and
do-not-disturb tools. Gemini Live is optional. We don't build it. Play adds it.

**What it needs from us:**

- **An Android App Bundle uploaded to Play**, with "Sidekick is on by default" ticked on the release
  (Testing > Advanced settings > Play Games Sidekick, to keep it on for later bundles). The
  `com.google.android.play:sidekick` SDK is only for APK-only publishing, which we won't do. The AAB
  path is already a `PUBLISHING.md` requirement.
- **PGS v2 achievements** (section 2) for the achievements tab. Until 100+ players have called the
  achievements API within 30 days, players only see what they've unlocked. After that, locked
  achievements appear as well.
- Players need Android 13+, at least 3 GB RAM, a gamer profile and a Play install. Everyone else
  simply doesn't see it (our `minSdk` stays 26). It is never present in GitHub sideloads.

**What we control** (`AndroidManifest.xml` meta-data `com.google.android.finsky.deku.OVERLAY_CONFIG`
→ `res/xml/sidekick_config.xml`):

- **Where the tab sits.** The middle-right default is in the way on this portrait game screen: the
  scorecard's right column and the dice tray. **Deferred (blocks Phase 6): decided on a device**,
  looking at Sidekick over the real screens. `TOP_RIGHT` or `TOP_LEFT`, by the screen title and back
  arrow, are the likely candidates. A player who drags the tab keeps their own position.
- **Snooze**, so the tab doesn't sit on a brand-new player's first game. **Decided: 300 seconds**
  (5 minutes; the maximum is 30).
- Players can turn it off in Play Store settings.

**Recommendation: enable it.** It costs us almost nothing in code. Being Play's own overlay, it reads
our achievements without any extra work. It meets Level Up's Sidekick guideline in case that becomes
a goal. Google's advice is a **5% staged rollout** comparing crashes, ANRs and engagement first. We
should also check frame cost on a 3-4 GB device with the overlay open, against the 60 fps floor. Its
accessibility is Google's, but we confirm that its tab doesn't cover a control TalkBack users need.

---

## Where it appears in the app

**Recommended: a Settings card, plus links where the data is already shown. No new menu button.**
The main menu stays as it is: "adding an element must not move or re-space existing ones", and the
menu's six destinations are already a full column.

1. **Settings, a new card at the top** (above Sound/Vibration), titled with `GameServices.serviceName`.
   The shared UI never writes "Google". Layout: avatar, "Signed in as <name>" (or "Not signed in" with a
   **Sign in** button), the **"Use <service> on this device"** switch, "Save progress to the cloud"
   (a switch, on by default when signed in) with "Last saved <time>", and a quiet status line for
   errors ("Couldn't reach Google Play Games - will retry"). The card is hidden entirely when the
   service is `Unavailable`. The existing cards keep their order below it.
2. **Achievements screen:** a "View in Google Play Games" button in the unlocked-count banner's
   footer, opening Play's own achievements UI. It is shown only when signed in.
3. **Leaderboard screen:** each per-mode card gets a "Global leaderboard" action (an icon button
   with a label) opening that mode's Play leaderboard. The local tables stay as they are.
4. **Game Over:** nothing new on screen. **Decided: the online submission happens quietly**, with no
   "Posted to Google Play Games" line.
5. **First launch:** nothing of ours. Play's own "Welcome back" pop-up appears at the top of the
   screen. Check that it doesn't hide the menu logo for too long.

**Accessibility (per `UI.md`'s checklist, verified when built):** the card is one TalkBack group with
the status as its `stateDescription`. Both switches announce their state. "Sign in", "View in Google
Play Games" and "Global leaderboard" are real buttons with labels, not icon-only buttons. The
avatar is decorative (`contentDescription = null`). Every action can be reached with plain taps.
Play's own screens and Sidekick are Google's, which we check on a device but can't change.

**i18n (per `I18N.md`):** every new string goes into Compose resources in en, es and ar, with
"Last saved" times through the existing `formatTimestamp`. The service's *name* comes from the
platform, but the sentences around it are ours, with the name as an argument. The console text
(achievement and leaderboard names) is translated in Play Console from the same strings.

---

## The reset buttons

Play's achievements and leaderboards **cannot be reset once published**. Players can't do it and
neither can we (only draft items can be reset, for testers). Cloud save, on the other hand, *can* be
cleared. So each button needs its meaning settled:

**"Reset achievements"** (today: clears the `achievements` DataStore and drops any style picks it
locks again).

- **Not signed in:** works as it does today.
- **Signed in:** a local reset is undone at the next sign-in, because Play would unlock everything again
  (section 2, Play → local). The cloud save would bring it all back too. That makes it a button that
  appears to work and then quietly doesn't. **Decided:** while an account is linked, the row
  stays visible but **disabled**, with its description changed to "Your achievements are linked to
  Google Play Games and can't be reset" (read by TalkBack as disabled, with the reason). The only way
  to reset is to turn the account off on this device first. Even then, the confirmation says Play
  keeps its copy, and that switching syncing back on restores them.

**"Reset leaderboard"** (today: deletes every score row, clearing the Leaderboard, Statistics and the
ledger's progress).

- This still has a real purpose: clearing pass-and-play friends' names and history from a shared
  device.
- **Signed in:** it resets the **local and cloud** copies, with a reset marker so other devices
  clear as well. The **Google Play leaderboards keep their entries**. The confirmation says exactly
  that: "This clears the scores and statistics on all your devices. Your best scores on the Google
  Play leaderboards stay." The online best is untouched, and the next game simply competes with it.
- **Decided: all devices only.** No "this device only" option, because with cloud save on, a
  device-only delete would come straight back from the cloud.

**The debug "superuser" mode** (the Achievements screen's tester shortcut, which writes real unlocks
through `forceLock` and the bulk unlock) must **never be mirrored**. While it has been used, a flag
in the `online` store stops achievement mirroring on that install. A test pins that a superuser
unlock produces no `GameServices` call. Debug builds that are registered as testers can use draft
achievements, which can be reset, from the console.

---

## Phased plan

**Phase 1 as built:** `online/GameServices.kt` (interface, `GameServicesState`, `OnlineAccount`, `LocalGameServices`),
`NoGameServices`, `OnlineSettingsRepository` (the `online.preferences_pb` file: `use_on_device`, `cloud_save_enabled`,
both on by default), `ui/settings/OnlineAccountCard.kt`, `GameMode.onlineLeaderboardKey` (equal to each `id` today;
pinned by `OnlineLeaderboardKeyTest`), and `ScoreEntry.playedUnmodified` (recorded for every game). `DiceFiveApp` takes an
optional `gameServices` that defaults to `NoGameServices`, so no platform passes one yet. Not in Phase 1: the card's
"Last saved" and error lines (they need cloud save), the account-switch prompt (Phase 2). The card's TalkBack text
(header read as one heading, "<service>, signed in as <full name>") is tested in `OnlineAccountCardTest`; what real
TalkBack says has not been heard on a device.

Each phase ends in a debug APK (per `CLAUDE.md`), and its commits use the category below.
**New commit category `[Play Games]`** (decided): Google Play Games Services integration (sign-in,
achievement and leaderboard sync, cloud save, Sidekick). It was added to `CLAUDE.md`'s table
in the commit that added this plan. The shared seam (`online/`) also goes under it while Play Games is its only
user. Game Center work later uses `IOS_SUPPORT.md`'s planned `[iOS]` category.

**Phase 0 - Accounts and console (the maintainer's part; no code).** The Play Console developer
account (`PUBLISHING.md` step 1), the app listing, Play App Signing and an upload key, and a closed
testing track. Create the Play Games Services project. Register OAuth clients for the **debug
keystore's SHA-1** and the upload/app-signing SHA-1, plus the GitHub key if we decide to. Add PGS
testers. Turn on Saved Games. ("Plus the GitHub key if we decide to" is settled: we don't.)
Nothing below can be tested on a device until this exists, but a large part of it can be built and
unit tested first. See [Before Phase 0 is done](#before-phase-0-is-done) for the order.

**The minimum Phase 0 for debug-APK testing** (the rest, such as the upload key, Play App Signing
and the closed track, can wait until release):

1. Create the app in Play Console.
2. Create the Play Games Services project and link it to the app.
3. Register an Android credential for package `net.zodac.dicefive` with the debug key's SHA-1,
   `76:7A:8F:8E:BF:D9:A9:58:4E:4C:21:D4:0B:9D:6E:51:1B:EB:21:A0`.
4. Add the maintainer's Google account (and any other testers) as **Play Games Services testers**.
5. Turn on Saved Games.

Achievements and leaderboards can be created later, from the exported files (Phases 3 and 4).

**Phase 1 - The seam, with nothing behind it. Done.** `online/GameServices`, `NoGameServices`,
`LocalGameServices`, `GameMode.onlineLeaderboardKey` (with a test that every mode has one), the
`online` DataStore file and `ScoreEntry.playedUnmodified`. The Settings card exists but is hidden while the service is
`Unavailable`, so the app looks the same as without it. Tests: card visibility, the switch, TalkBack text. No new
dependency.

**Phase 2 - Sign-in and the account card (Android).** Add `play-services-games-v2`.
`PlayGamesSdk.initialize` goes in an `Application` subclass. Add the `APP_ID` meta-data,
`PlayGamesServices` (state, `signIn`, the player's name and avatar) and the account-switch prompt.
Before committing:

- Measure the release APK size before and after (record both, and what R8 kept).
- Check that AboutLibraries' report lists the Play services licence and that the copyleft guard
  accepts it (it is Google's proprietary SDK licence; add it to `allowedLicenses` with a comment).
- Update the privacy policy (`docs/privacy-policy.md`: the Play Games account and gameplay data) and
  the store's "Fully offline" line (`PUBLISHING.md`).

Verify on the sandbox emulator with a Play image, and on a real phone.

**Phase 3 - Achievements mirror.** Add `OnlineSync` with achievement reconcile (local → Play), restore
(Play → local) and the superuser guard. Add the mapping table and its test (every non-`SECRET`
achievement mapped, steps ≤ 10,000, `PROFESSIONAL_ROLLER` scaled). Add tooling: the
console-export task (text in 3 languages, states, steps, points total ≤ 2,000) and the icon render
task. **Renders go to the maintainer for review before upload.** Change the "Reset achievements" row
(signed-in behaviour) and add Achievements → "View in Google Play Games". Achievements stay in
draft until Phase 6.

**Phase 4 - Leaderboards.** Create 7 leaderboards (limits from `maxPossibleScore`, tamper protection
on). Add submission from `finishGame` (seat 1 only, the mode's own rules only), the one-time backlog submission on first link (per mode, seat 1's best `playedUnmodified` score), the Leaderboard screen's "Global leaderboard" actions, the new step in
`GAME_MODES.md`'s checklist for a new mode, and the Reset leaderboard wording.

**Phase 5 - Cloud save.** Room version 2 (`ScoreEntry.uuid`, migration plus exported schema), timestamps on
settings writes, per-device counters, `CloudSaveCodec` and `CloudSaveMerge` with thorough unit tests
(union, G-counter sums, streak recency, last writer wins, reset markers, a newer blob version from a
newer app read safely by an older one), the snapshot transport, the first-link "Combine" prompt, and
the "Last saved" line. Test with two emulators or phones on one account, including offline edits on
both.

**Phase 6 - Sidekick and release.** Add `sidekick_config.xml` (tab position, snooze) and the
`bundleRelease` + upload path from `PUBLISHING.md` if it doesn't exist yet. Publish the achievements
and leaderboards. Run a closed test with Sidekick, then a 5% staged production rollout watching
vitals. Check frame cost with the overlay open.

**Later - iOS (once the iOS app in `IOS_SUPPORT.md` exists).** `GameCenterServices` in `iosMain`:
`GKLocalPlayer.authenticateHandler`, `GKAchievement` (incremental as `percentComplete`, hidden as
`isHidden`), `GKLeaderboard` per `onlineLeaderboardKey`, and the shared blob over `GKSavedGame` or
iCloud key-value storage. Game Center **does** let a player reset their own achievements
(`GKAchievement.resetAchievements`), so the reset rows' rules become per-platform properties of
`GameServices` (`canResetAchievements`), never an `if (iOS)` in the UI. Game Center has no Sidekick
equivalent, and its access point (`GKAccessPoint`) is an optional overlay with the same placement
concern as Sidekick.

**Optional - Game Stats.** See [Optional extra: Game Stats](#optional-extra-game-stats-level-up).
It can be done after Phase 3, at any point.

---

## Before Phase 0 is done

The console isn't configured yet, so the work goes on without it. **Phase 0 is needed for device testing, not for
building.**

**Order of work:**

1. Phase 1 (done).
2. **The pure-logic parts of later phases**, all unit tested against a fake `GameServices`:
   - Phase 5's `CloudSaveCodec`/`CloudSaveMerge` and the `ScoreEntry.uuid` column. This is the
     riskiest logic in the plan, and none of it needs Google.
   - Phase 3's achievement mapping table and its test, the console-export task, and the icon render
     task. The renders go to the maintainer for review. Points are a deferred decision, so the
     points script can be written but not finalised.
   - The reset-row rules, the backlog selection (seat 1, unmodified, best per mode, skipping a score
     above the mode's `maxPossibleScore`) and the superuser guard.
3. **Stop before adding `play-services-games-v2`**, or add it only on a branch kept off `main`,
   until Phase 0 exists. Without the console, the wrapper (`PlayGamesServices`) can only use
   placeholder ids. It would compile, but nothing could show it works. If it's built anyway, missing
   ids must fall back to `NoGameServices`, so the Settings card stays hidden.

**Why to keep the SDK off `main`:** the release pipeline (`DESIGN.md`, "Release pipeline") publishes a GitHub
release on **every push to `main`**. A commit adding the SDK would ship it, and the `INTERNET`
permission it merges in, in the next GitHub APK. That build would gain nothing from it (Play-only, by
decision 6), but it would still need:

- the privacy policy updated,
- the store text's "Fully offline" changed (`PUBLISHING.md`),
- the APK size change accounted for.

Phase 1 and the pure-logic work change nothing a player sees, so they are safe on `main`.

## Testing

**Debug APKs can test almost all of this.** Play Games recognises an app by package name plus
signing-key fingerprint:

- Debug builds use the release package name, `net.zodac.dicefive` (there is no `applicationIdSuffix`).
- They are always signed with the repo's `app/android/debug.keystore`, so every debug APK, built
  anywhere, has the same SHA-1 (above).

Once Phase 0's minimum is done, a sideloaded debug APK can test:

- **Sign-in:** automatic sign-in, the Settings card, and switching accounts (long-press > Choose
  profile, or the system setting).
- **Achievements:** both directions of the mirror, and the superuser guard. **Draft** achievements
  can be reset for testers in the console, so a test can be repeated.
- **Leaderboards:** submissions, per-mode tables and the first-link backlog. Draft leaderboards can
  be reset too.
- **Cloud save:** merging and offline edits, on two devices with the same account.

Conditions: the phone needs Play services and a Google account on the **PGS testers** list. While the
game's Play Games configuration is unpublished, anyone else fails sign-in in a way that looks like a
bug.

**Needs the Play release flow:**

- **Sidekick.** It only appears in an app installed from Play, and Play adds it to an uploaded bundle.
  Use the **internal testing** track: upload a bundle, add testers, install from Play. That track
  doesn't need the 12-tester, 14-day closed test, which is only for production access. The device
  also needs Android 13+ and at least 3 GB RAM.
- **The Play-signed build.** Play App Signing re-signs uploads with Google's key, which has its own
  SHA-1 to register. Confirm that a Play-installed build signs in before release.
- **Locked achievements in Sidekick:** shown only after 100+ players have used the achievements API
  in 30 days. They can't be seen in testing.

**Emulator:** PGS needs a Google Play system image, and whether `sandbox/emulator.sh` boots one is
unchecked (`EMULATOR.md`). Plan on a real phone for every PGS check, and use the emulator only if it
turns out to have Play.

## Optional extra: Game Stats (Level Up)

**Not part of the requested scope.** It's worth doing if Level Up becomes a goal, or if richer
Sidekick and Play profile content is wanted. **Game Stats** are numbers Play keeps per player, such
as "games played", which Google shows on the player's profile and in Sidekick, and uses for
"competitive engagement" features (comparisons with friends, challenges). Level Up's rules for them:

- **LU-GS-GAA:** at least **5 repeating stats** (actions that happen regularly), **at least 1** of
  them usable for competition.
- **LU-GS-GAB:** if the game has a main progression mechanic, **1 extra progression stat**. The
  exemption LU-GS-EAB covers games without one. Achievements completed, or best score, could serve if
  wanted.
- **LU-GS-GAC:** each stat needs a display string, a **unique icon** and aggregation rules (sum,
  max and so on), set up in Play tools.

**Proposed stats.** Every one already exists locally, so this adds no new tracking:

| Stat | Source | Aggregation | Competitive? |
|---|---|---|---|
| Games played | `AchievementCounter.GAMES_PLAYED` | sum | yes |
| Games won | `AchievementCounter.GAMES_WON` | sum | yes |
| Dice rolled | `AchievementCounter.DICE_ROLLED` | sum | yes |
| 5x scored | `AchievementCounter.SCORED_5X` | sum | yes |
| Career points | `ScoreDao.primaryPlayerTotalPoints` | sum | yes |
| *(progression, optional)* Achievements unlocked | `AchievementsState` | max | no |

**How it would be built:**

- An `OnlineSync` step that reports each counter's increase since the last report, after each
  finished game. A per-device "last reported" value goes in the `online` store, so a reconcile never
  counts anything twice.
- Icons rendered with the achievement icon tooling (and reviewed first).
- Strings in en, es and ar.
- On iOS there is no equivalent. `GameServices` exposes `supportsGameStats = false` there, and the
  step is skipped.

**Watch out:** stats only grow. A local "Reset leaderboard" or "Reset achievements" doesn't lower
them, just like the online leaderboard. The reset confirmations' wording (see
[The reset buttons](#the-reset-buttons)) must mention "statistics on Google Play Games" too if this
is built.

---

## Risks and gotchas

- **Never block the game on the network.** Every `GameServices` call runs in the app scope, with a
  timeout, swallowing errors into `state`. Achievement banners stay local and instant. Play's own
  unlock pop-up would duplicate ours. **Deferred (blocks Phase 3):** suppress it if v2 allows
  (`setViewForPopups` / gravity existed in v1; check v2), or accept it. To be decided after seeing it
  on a device.
- **Id stability works in both directions now.** An `Achievement.id` or `onlineLeaderboardKey`, once
  published, is permanent on Google's side as well. Removing an achievement locally can't remove it
  from Play: published achievements can't be deleted, only hidden from new players. So retiring one
  is a migration (keep the mapping, stop reporting it), not a deletion.
- **Points are permanent.** Hold some back, as described above.
- **Changing a rule doesn't reset Play.** A mode whose rules change keeps its online scores from the old rules. If a rule change makes old scores unfair, the fix is a **new
  leaderboard key** for that mode (the old board stays, frozen). That is a deliberate choice in the
  mode change's commit.
- **Test accounts.** Testers must be Play Games testers until publishing, or nothing works for them
  and the errors look like a sign-in bug.
- **Emulator.** `EMULATOR.md`'s emulator needs a Google Play system image for PGS. Check what the
  sandbox can run; otherwise PGS is verified on a real phone only.
- **Trademark rule:** applies to every console string as well. The generated export goes through the
  same guard as `StringChecks`.
- **Data safety form and privacy policy** need updating before Phase 2 reaches any tester outside the
  team.

---

## Decisions

**Rule: this work is not complete while any decision below is Deferred.** Each deferred decision
blocks the phase named next to it. Ask the maintainer when that phase reaches it, and record the
answer here (and at the inline marker). Never settle one silently with the recommended default.

| # | Decision | Answer | Needed by |
|---|---|---|---|
| 1 | Is Level Up a goal? | **Decided: no.** Game Stats stays an optional extra. | - |
| 2 | "Reset achievements" while linked | **Decided: disabled while linked**, with the reason given. Works as today when not signed in. | Phase 3 |
| 3 | "Reset leaderboard" when signed in | **Decided: all devices** (local + cloud). Play's online bests stay, and the confirmation says so. | Phase 4/5 |
| 4 | Submit pre-link best scores | **Decided: yes.** Seat 1's best unmodified score per mode, once per account per device on first link. | Phase 4 |
| 5 | In-progress game in the cloud | **Decided: yes**, newest wins. | Phase 5 |
| 6 | GitHub release builds sign in? | **Decided: Play-only.** GitHub key not registered. | Phase 0/2 |
| 7 | Achievement points | **Deferred:** tier formula (~300 held back), or by hand. | Phase 3 (before publishing) |
| 8a | Sidekick tab position | **Deferred: decided on a device** (`TOP_RIGHT`/`TOP_LEFT` likely). | Phase 6 |
| 8b | Sidekick snooze | **Decided: 300 seconds.** | Phase 6 |
| 9 | Play's unlock pop-up alongside ours | **Deferred:** suppress it if v2 allows, or accept it. | Phase 3 |
| 10 | Commit category | **Decided: `[Play Games]`.** | Phase 1 |
| 11 | Locked achievement icon on Play | **Deferred:** own icon greyed (recommended), or a generic padlock. | Phase 3 (with the renders) |
| 12 | Game Over note on submission | **Decided: stay quiet.** | Phase 4 |

**Still deferred: 7, 8a, 9, 11.**

---

## Sources

- [Platform authentication for Android games](https://developer.android.com/games/pgs/android/android-signin)
- [Achievements](https://developer.android.com/games/pgs/achievements)
- [Leaderboards](https://developer.android.com/games/pgs/leaderboards)
- [Saved games for Android games](https://developer.android.com/games/pgs/android/saved-games)
- [Play Games Sidekick](https://developer.android.com/games/pgs/play-games-sidekick) and
  [Sidekick SDK](https://developer.android.com/games/pgs/play-games-sidekick-sdk)
- [Google Play Games Level Up guidelines](https://developer.android.com/games/guidelines)
- [Android games release notes](https://developer.android.com/games/docs/release-notes)
