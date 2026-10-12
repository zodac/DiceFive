package net.zodac.dicefive.online

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared game needs from a platform's online game service - Google Play Games on Android, Game Center
 * on iOS - and nothing that names either. Each platform implements it once; [NoGameServices] is the version for
 * previews, tests and builds with no service. See `.claude/PLAY_GAMES.md`.
 *
 * Local storage stays the source of truth: the game never waits on, or reads from, an implementation. Every call
 * is suspending and none throws to the UI - a failure goes into [state] or comes back as null.
 */
interface GameServices {

    /** "Google Play Games" / "Game Center": shown in Settings, never hard-coded in shared UI. */
    val serviceName: String

    val state: StateFlow<GameServicesState>

    /** A manual retry. Signing in at launch is the platform's business, and there is no sign-out. */
    suspend fun signIn()

    /** The ids of the achievements the service says are unlocked, or null if it couldn't be asked. */
    suspend fun unlockedAchievementIds(): Set<String>?

    suspend fun unlock(achievementId: String)

    /** Forward-only and idempotent: [steps] lower than the service already holds changes nothing. */
    suspend fun setProgress(achievementId: String, steps: Int)

    suspend fun submitScore(leaderboardKey: String, score: Long)

    suspend fun readCloudSave(): ByteArray?

    /** Saves [data], calling [merge] with the local and the remote copy if the service finds they conflict. */
    suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray)

    /** Whether the service's own achievements screen can be opened (signed in, and the platform has one). */
    val canShowAchievementsUi: Boolean

    fun showAchievementsUi()

    val canShowLeaderboardUi: Boolean

    /** Opens the service's leaderboard for [leaderboardKey] ([net.zodac.dicefive.model.GameMode.onlineLeaderboardKey]), or all of them if null. */
    fun showLeaderboardUi(leaderboardKey: String?)
}

/** Where a [GameServices] is in signing in. */
sealed interface GameServicesState {

    /** There is no service on this device (not installed, a sideload, a preview). The Settings card is hidden. */
    data object Unavailable : GameServicesState

    data object SignedOut : GameServicesState

    data object SigningIn : GameServicesState

    data class SignedIn(val account: OnlineAccount) : GameServicesState
}

/**
 * The signed-in account. [displayName] is the service's own gamer name: it is shown in one place, the Settings
 * card, and never fills or renames a player's seat. [accountKey] identifies the account on this device (a
 * hash, not the raw id) so a different one signing in can be noticed.
 */
data class OnlineAccount(val displayName: String, val avatar: ImageBitmap?, val accountKey: String)

val LocalGameServices = staticCompositionLocalOf<GameServices> { NoGameServices }
