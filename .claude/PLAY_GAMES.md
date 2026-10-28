# DiceFive: Google Play Games Services

**Built on Android; release remaining (see "Release").** Sign-in, achievements mirrored both ways, a leaderboard per
mode, cloud save across devices, and Play Games Sidekick, each checked by the maintainer on real phones. This file is the one source on the subject:
where another doc disagrees, follow this one.

## What Google provides

One SDK: `com.google.android.gms:play-services-games-v2` (v22.1.0; v1 is frozen).

| Feature      | API                               | Limits that shape the design                                                                                                                                                                              |
|--------------|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sign-in      | `GamesSignInClient`               | Automatic whenever the SDK starts. **No sign-out API**. Needs `PlayGamesSdk.initialize()` and the `APP_ID` meta-data                                                                                      |
| Player       | `PlayersClient`                   | Gamer name, avatar, id                                                                                                                                                                                    |
| Achievements | `AchievementsClient`              | ≤400; **2,000 points** total, ≤200 each, multiples of 5; one icon each (Play draws the locked look); incremental 2-10,000 steps, forward only. **Published ones can't be reset** (drafts can, by testers) |
| Leaderboards | `LeaderboardsClient`              | ≤70; ordering fixed once published; daily/weekly/all-time automatic; ties by submission time. **Can't be reset**                                                                                          |
| Saved games  | `SnapshotsClient`                 | Named blobs ≤3 MB; conflicts settled by `resolveConflict`                                                                                                                                                 |
| Sidekick     | Added by Play to uploaded bundles | Android 13+, 3 GB+ RAM, **installed from Play**; enabled per release. Locked achievements show only after 100+ players in 30 days                                                                         |

- **Cost.** The release APK grew 5.49 MB to 5.83 MB (+346 KB; ~+143 KB compressed), with **no new permission**: v2
    talks to the Play services app, so `INTERNET` isn't merged in.
- **Level Up** (Google's 2026 programme) is not a goal, so its Game Stats are out of scope.

## Architecture

```text
app/shared/.../online/   GameServices (interface, GameServicesState, OnlineAccount, LocalGameServices), NoGameServices,
                         OnlineSync (reconciler), CloudSave + CloudSaveMerge (pure), CloudSaveCodec, CloudSaveStore,
                         OnlineSettingsRepository  - nothing here names Google
app/shared/.../ui/settings/OnlineAccountCard.kt   labelled by GameServices.serviceName
app/android/src/play/.../online/  PlayGamesServices, PlayGamesIds, PlayGamesAchievements, res/values/games-ids.xml
                         - the only gms.games imports, in the play flavour alone
```

- **Local storage is the source of truth.** The game never waits on or reads from the network; a signed-out, offline
    or sideloaded app behaves as if Play didn't exist. Every call has a timeout, and errors go into `state`.
- **Mirroring is idempotent, not a queue.** `OnlineSync.reconcile()` compares local state with the service and sends
    the difference, so a failed send is retried next time. Triggers: sign-in, app start, after a game, leaving a game
    part-way, 5s after a synced-setting change, `ON_STOP` and `ON_START`. No polling.
- **The engine and `GameMode` never learn the network exists.** Per-mode data is a field (`onlineLeaderboardKey`).
- **The play app only.** Play Games is the `play` flavour's (`DESIGN.md`, "Distribution"): the SDK, the manifest's
    `APP_ID`, Sidekick and the disabled init provider are all in `src/play`. The github APK (`net.zodac.dicefive.github`)
    has none of it, so a Play Games credential, which is per package, can't sign it in either; it has progress files
    instead, which the play app never has, signed in or not.
- **Play-only installs.** A play release build uses Play Games only when installed by Play (`com.android.vending`) on a
    phone with Play services; a debug build never does, as its public key has no credential ("Play Console")
    (`PlayGamesServices.usesPlayGames`). Otherwise: `NoGameServices`, no card, as in the github APK. The SDK is still
    compiled into a play debug build, for its tests.
- `PlayGamesServices` needs an `Activity`, so it's built in `MainActivity` and re-attached across config changes.
- **Superuser mode** stops the whole sync (achievements, scores, save) on that install; a test pins it.

## Account and sign-in

- **Only from Settings.** The SDK isn't started (`PlayGamesSdk.initialize`) until the player taps **Sign in**, and its
    auto-start `PlayGamesInitProvider` is disabled in the manifest (`PlayGamesServicesTest`). A sign-in sets `start_at_launch`
    (the `play_games` SharedPreferences, read in `Application.onCreate`, before DataStore could be); from then on the SDK
    starts with the app and signs in quietly.
- **That choice stays on its device.** Backups and device transfers leave `play_games` out (`res/xml/backup_rules.xml`
    up to Android 11, `data_extraction_rules.xml` from 12; `PlayFlavourTest`), so a restored phone asks before signing
    in. From Android 12 everything else is backed up, the `online` store included: its mirroring block must survive a
    restore. **Up to Android 11 only the settings are** (`backup_rules.xml`): there `adb backup` and `adb restore` need
    only USB debugging, so an edited backup would otherwise reach Play Games (a file import by another name). The cloud
    save restores the rest for a signed-in player; an excluded file isn't restored either. `PlayFlavourTest` pins both.
- **A failed sign-in says why** on the card, in red: "Couldn't sign in: " and the first failure's log message
    (`GameServicesState.SignedOut.failure`), untranslated, for a player to report. It goes on the next Sign in tap.
    Cancelling the account picker shows it too ("not authenticated"): the SDK can't tell the two apart.
- **Sign out is ours** (v2 has none): it clears `start_at_launch` and stops every call. It waits for the sign-in's sync to
    finish (`OnlineSync.signInSynced`).
- **The gamer name appears only on the Settings card** and never touches seat 1's name. Sanitised, ellipsised, up to
    two lines.
- **Seat 1 reports to the signed-in account**, whatever name it holds; seats 2-4 never do.
- **Choosing the account** is Play's: its default, or long-press the icon > Choose profile. No in-app picker.
- **Accounts are kept apart.** `synced_account` (salted hash) is the last account to sync. That account, or the first
    ever, **merges**. A different one **wipes** the synced data (achievements, counters, scores, synced settings, the
    game in progress; no reset marker) and restores its own, once the service has answered. Signing out wipes nothing.

## Achievements

- **Local → Play** in every reconcile, including unlocks from before linking. Progress uses forward-only steps.
- **Play → local** on sign-in: Play's unlocks unlock quietly here (with their styles), restoring a reinstall.
- **Our unlock banner shows alongside Play's pop-up** while signed in: Play's at the top, ours at the bottom, so they
    don't overlap. TalkBack announces both. Ours can be held and long-pressed to open the achievement. Progress nudges
    and styles banners are unchanged.
- **Reset achievements** is hidden while signed in (Play would restore it); "View in Google Play Games" takes its row.
    A reset made signed out doesn't travel: the same account's next sync restores the unlocks and counters.
- **Mapping**: `PlayGamesIds.kt`. `PlayGamesAchievements.kt` sets each one's Play type and visibility (`playType`,
    `playHidden`, `onPlay`). Every non-`SECRET` achievement is mapped, steps ≤10,000, Professional Roller scaled
    (`PlayGamesIdsTest`).
- **Points** by tier (`PlayConsoleAchievements.kt`, test sources): trivial 5, easy 10, medium 15, hard 25, very hard
    45, extreme 70, Completionist 100, every Easter egg 5. That spends all 2,000 (`PlayConsoleExportTest`), so a new
    achievement needs points freed first.

## Leaderboards

- **7 boards**, one per `GameMode` (`playLeaderboardIds`): larger is better, 0 to `maxPossibleScore`, tamper
    protection on.
- **Submitted**: seat 1's score, only under the mode's own rules (`isPrimaryPlayer` and `playedUnmodified`; rows over
    `maxPossibleScore` skipped). Each reconcile sends each mode the best score since the newest game that account took
    from this device (`scores_posted:<account>:<key>`, moved only once Play confirms). So a first link sends the backlog,
    after the cloud merge. Game Over says nothing.
- **Viewing**: a "Global leaderboard" button on each mode card opens Play's screen. The local tables stay.
- **Reset leaderboard** clears every device (a `scores_reset_at` marker in the save). Play's boards keep their
    entries, and the confirmation says so.
- **A new mode** needs a console board, an icon and an `onlineLeaderboardKey` (`OnlineLeaderboardKeyTest`).

## Cloud save

One snapshot, `dicefive-progress` (`CloudSaveCodec.SNAPSHOT_NAME`, permanent): versioned JSON, gzipped (okio).
Always on while signed in, with no switch and no prompt. Every sync merges; nothing is picked or thrown away.

| Data                                                                              | Merge                                                                                                                                                                                                                                                                                       |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Achievement unlocks                                                               | Union, earliest time                                                                                                                                                                                                                                                                        |
| Achievement counters                                                              | `CounterMerge`: `SUM` keeps one share per install (`counter_share_id`, new on each reset) and adds them; `FLAGS` union; win streak `LATEST`                                                                                                                                                 |
| Scores                                                                            | Union by `ScoreEntry.uuid`, less any at or before `scores_reset_at`                                                                                                                                                                                                                         |
| Dismissed stats cards                                                             | Union, later kept; dropped once a later game under the name is in the save                                                                                                                                                                                                                  |
| Style picks, New Game setup, Statistics sort, sound, vibration, confirm-leaving   | Last writer wins per key (`synced_at:<key>`); an unstamped one (a pick after Reset achievements) beats one never set                                                                                                                                                                        |
| In-progress game                                                                  | Newest wins (`saved_at`, never earlier than the last); of two copies of one game (`GameState.id`) the one with more rolls wins (`furtherOn`), so neither a clock set back nor another device takes back a roll; one finished here (`finished_id`) or one this app can't read is never taken |
| Animations level, Orientation, Styles page fit, Locked and Unlocked only switches | Not saved: they're about the device                                                                                                                                                                                                                                                         |

- **Ids, not enums**, so an older app carries a newer one's achievements, counters and settings through a merge.
- **Versions.** Fields are only added within a `version`. An app reads a newer version's known fields but never writes
    over it (`CloudSave.writableHere`). An unreadable (corrupt) save is replaced by this device's.
- **After a merge**, `AchievementEngine.evaluateProgress` unlocks whatever the combined totals reach (quietly, then
    mirrored, so Play's pop-up announces it).
- **The write** reopens the snapshot and merges with anything committed since the read (`RESOLUTION_POLICY_MANUAL`,
    up to 10 retries). It writes only if this device added something, and before posting scores.

## Settings card

`OnlineAccountCard`, the top card in Settings, titled `serviceName` (shared UI never says "Google"); hidden when
`Unavailable`.

- Avatar (decorative), the gamer name in gold, and **Sign out**; or "Not signed in" and **Sign in**. Sign in stays
    greyed while signing in and until that sync ends.
- "Last saved `<time>`" (`cloud_saved:<account>`, via `formatTimestamp`) under the header.
- While a sign-in's sync runs (`OnlineSync.progress`), a block names the step and count ("Merging scores (5/7)") with
    a spinner and a 400ms gliding bar (a still icon and snapping under reduced motion): `StepProgress`, which the
    github app's import shows too, with the same merge steps (`MergeStep`). Seven steps (`SyncStep`):
    downloading, settings, the game in progress, achievements, scores, uploading, leaderboard scores.
- "Couldn't reach Google Play Games - will retry" while the last sync failed (`OnlineSync.unreachable`).
- **TalkBack**: one group with its status as `stateDescription`, and the sync step in the heading ("…, Merging scores,
    step 5 of 7"); the progress block itself is hidden. Pinned in `OnlineAccountCardTest`.
- About's Privacy section adds the sign-in sentence wherever a service exists (`about_privacy_body_online`,
    `AboutDialogTest`).

## Sidekick

Play's overlay: an edge tab that slides out achievements, streaks, offers and recording tools. We don't build it.

- `res/xml/sidekick_config.xml`, through the `com.google.android.finsky.deku.OVERLAY_CONFIG` meta-data
    (`SidekickConfigTest`): **`TOP_RIGHT` in portrait and landscape**, chosen from renders. The middle-right default
    covers the Σ totals button or the cup, and `TOP_LEFT` the back arrow. Positions are physical edges (the same in
    Arabic), and a player's own drag outranks them. **Snooze 300s** in a player's first session.
- No Sidekick SDK: that's for APKs. Play adds Sidekick to an uploaded bundle.

## Play Console

- **Project**: APP_ID `308613893005`; Saved Games on. Android credentials, one per signing key (SHA-1):
    - Play's app signing key `B3:92:54:2F:11:F1:67:02:F4:84:FF:CE:67:53:B1:DA:5E:78:D2:FB` (every install from a Play
        track: players get only this);
    - the upload key `FD:64:0E:6D:55:7E:13:7C:77:2E:BD:0A:02:C9:03:00:9D:45:80:A9` (the `RELEASE_KEY*` secrets; only
        builds signed by hand). It also signs the github APK, but that's another package, which no credential names.
    - **Never the debug key** (`76:7A:8F:8E:BF:D9:A9:58:4E:4C:21:D4:0B:9D:6E:51:1B:EB:21:A0`): it's committed to the
        public repo (`app/android/debug.keystore`), so with a credential anyone could build the app from changed source
        and post any score or unlock to the published boards. Its credential was deleted on 2026-10-22; never add it back.
  A build whose key has no credential fails sign-in with Google's `DEVELOPER_ERROR`, which only logcat shows (its
  `PlayGamesServices[SignInAuthenticator]` lines print the build's SHA-1); the app sees only "not authenticated".
- **Sheets**: `scripts/export-play-console.sh` writes the gitignored `assets/play-console/`:
    - `achievements/achievements-import.zip` for Achievements > Import achievements (metadata, es-ES/es-419/ar
        localisations, icon mappings, icons; no header rows, keyed by English name). Add the translations in Play Console
        first.
    - `achievements/achievements.csv` and `achievements/achievement-icons/<id>.png` (512px) for review.
    - `achievements/achievement-ids.xml` isn't the script's: it's Achievements > Get resources (Android), saved there by
        hand after an import. `playAchievementIds` (`PlayGamesIds.kt`) is updated from it.
    - `leaderboards/leaderboards.csv` and `leaderboards/leaderboard-icons/<key>.png` (`PlayConsoleLeaderboards.kt`),
        entered by hand: Play has no leaderboard import. An icon can change any time; a board's ordering can't once published.
    - `product-icons/unlock_all_styles.png`: the purchase's icon (`PURCHASES.md`, Phase 3).
- Everything stays **draft** until publishing, because only drafts can be reset.

## Release

The code is done. The rest is the maintainer's, in Play Console:

1. **Publish** the achievements and boards. From then on their ids, points and ordering are permanent.
2. **Upload a signed `.aab` by hand** (CI has no Play credentials). Take it from the GitHub release, put it on an
   internal or closed track, and tick **Sidekick is on by default**.
3. **Check on a Play install** (Android 13+, 3 GB+): the tab sits top-right in both orientations over no control;
   TalkBack still reaches the back arrow and header; Play's sign-in pop-up doesn't hide the logo long (its wording, "Hey
   there" or "Welcome back", is Play's own, not ours); 60fps
   holds with the overlay open on a 3-4 GB phone.
4. Run a closed test (`PUBLISHING.md`), then a 5% rollout watching crashes, ANRs and engagement.

## Testing

- **Sign-in is tested on a Play-signed build**: the bundle on an internal testing track, installed from Play. The phone
    needs Play services and, until publishing, an account on the **PGS testers** list; anyone else fails sign-in in a
    way that looks like a bug.
- **Debug APKs have no Play Games**: no card, no sign-in (their key has no credential, above). The debug APK handed to
    testers is the github app's (`debug-apk` skill); the play flavour's debug variant only runs its unit tests and lint.
    The card, the sync and the cloud save are tested against fakes (`OnlineAccountCardTest`, `OnlineSyncTest`, ...).
- **Only the Play release flow** shows Sidekick, the Play-signed build's own sign-in, and Sidekick's locked
    achievements (100+ players).
- **The sandbox emulator** has no Play: use a real phone.
- Pure logic is tested against fakes: `OnlineSyncTest`, `CloudSaveMergeTest`, `CloudSaveCodecTest`, and the stores'.

## Risks and gotchas

- **Ids and points are permanent on Google's side.** Retiring an achievement means keeping its mapping and no longer
    reporting it. A rule change that makes old scores unfair needs a **new leaderboard key**; the old board stays frozen.
- **Banned words** apply to every console string; the export goes through the `StringChecks` guard.
- **Testers must be PGS testers** until publishing.
- **The data safety form** must declare Play Games and cloud save (draft answers: `PUBLISHING.md`, "Data safety").

Play work is committed as `[Play Games]`.

[Sign-in](https://developer.android.com/games/pgs/android/android-signin) ·
[Achievements](https://developer.android.com/games/pgs/achievements) ·
[Leaderboards](https://developer.android.com/games/pgs/leaderboards) ·
[Saved games](https://developer.android.com/games/pgs/android/saved-games) ·
[Sidekick](https://developer.android.com/games/pgs/play-games-sidekick) ·
[Data disclosure](https://developer.android.com/games/pgs/data-collection)
