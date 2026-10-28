package net.zodac.dicefive.online

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** No online service: previews, tests, and builds on a device without one. Every call does nothing. */
object NoGameServices : GameServices {

    override val serviceName: String = ""

    override val state: StateFlow<GameServicesState> = MutableStateFlow(GameServicesState.Unavailable)

    override suspend fun signIn() = Unit

    override suspend fun signOut() = Unit

    override suspend fun unlockedAchievementIds(): Set<String>? = null

    override suspend fun unlock(achievementId: String) = Unit

    override suspend fun setProgress(achievementId: String, steps: Int) = Unit

    override suspend fun submitScore(leaderboardKey: String, score: Long): Boolean = false

    override suspend fun readCloudSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead = CloudSaveRead.Failed

    override suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray? = null

    override val canShowAchievementsUi: Boolean = false

    override fun showAchievementsUi() = Unit

    override val canShowLeaderboardUi: Boolean = false

    override fun showLeaderboardUi(leaderboardKey: String?) = Unit
}
