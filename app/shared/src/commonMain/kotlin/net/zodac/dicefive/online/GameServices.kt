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

    /** Signs in, from the player's tap in Settings: nothing signs in before the first one. */
    suspend fun signIn()

    /** Stops using the service on this device until [signIn] again. The account itself is untouched. */
    suspend fun signOut()

    /** Whether the service has the achievement [achievementId] ([net.zodac.dicefive.model.Achievement.id]) at all. */
    fun mirrors(achievementId: String): Boolean

    /** The ids of the achievements the service says are unlocked, or null if it couldn't be asked. */
    suspend fun unlockedAchievementIds(): Set<String>?

    suspend fun unlock(achievementId: String)

    /** Forward-only and idempotent: [steps] lower than the service already holds changes nothing. */
    suspend fun setProgress(achievementId: String, steps: Int)

    /**
     * Posts [score] to the leaderboard for [leaderboardKey] ([net.zodac.dicefive.model.GameMode.onlineLeaderboardKey]).
     * True once the service has taken it; false if it couldn't be sent, or the service has no such board.
     */
    suspend fun submitScore(leaderboardKey: String, score: Long): Boolean

    /**
     * The account's cloud save ([CloudSaveCodec.SNAPSHOT_NAME]). Where the service holds two copies that conflict (one
     * written offline, say), it settles them with [merge] first.
     */
    suspend fun readCloudSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead

    /**
     * Saves [data] as the account's cloud save, merged by [merge] with whatever the service holds by then, so a save
     * another device wrote since it was read isn't lost. The bytes the service took, or null if it took none.
     */
    suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray?

    /** Whether the service's own achievements screen can be opened (signed in, and the platform has one). */
    val canShowAchievementsUi: Boolean

    fun showAchievementsUi()

    /** Whether the service's own leaderboard screen can be opened (signed in, and the platform has one). */
    val canShowLeaderboardUi: Boolean

    /** Opens the service's leaderboard for [leaderboardKey] ([net.zodac.dicefive.model.GameMode.onlineLeaderboardKey]), or all of them if null. */
    fun showLeaderboardUi(leaderboardKey: String?)
}

/** What [GameServices.readCloudSave] found. */
sealed interface CloudSaveRead {

    data class Found(val bytes: ByteArray) : CloudSaveRead {
        override fun equals(other: Any?): Boolean = other is Found && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** The account has no cloud save yet. */
    data object Empty : CloudSaveRead

    /** The service couldn't be asked: offline, or not set up for saves. */
    data object Failed : CloudSaveRead
}

/** Where a [GameServices] is in signing in. */
sealed interface GameServicesState {

    /** There is no service on this device (not installed, a sideload, a preview). The Settings card is hidden. */
    data object Unavailable : GameServicesState

    /**
     * Not signed in. [failure] is why the last sign-in didn't work, in the service's own terms (Google's status code,
     * say), shown so a player can report it; null before any attempt, and after signing out.
     */
    data class SignedOut(val failure: String? = null) : GameServicesState

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
