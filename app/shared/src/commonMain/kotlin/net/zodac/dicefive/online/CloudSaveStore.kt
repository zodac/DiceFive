package net.zodac.dicefive.online

import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository

/**
 * This device's side of the cloud save, for [OnlineSync]: read it whole, merge a save into it store by store, or wipe
 * it. Each merge follows [CloudSaveMerge]'s rules, so nothing here is lost; [OnlineSync] takes them one step at a time.
 */
interface CloudSaveStore {

    /** Everything on this device that the cloud save holds. */
    suspend fun snapshot(): CloudSave

    suspend fun mergeSettings(save: CloudSave)

    suspend fun mergeGame(save: CloudSave)

    suspend fun mergeAchievements(save: CloudSave)

    /** The scores, and the leaderboard reset marker with them. */
    suspend fun mergeScores(save: CloudSave)

    /**
     * Wipes everything the cloud save holds but the achievements ([net.zodac.dicefive.data.achievements.AchievementStore.resetAll]
     * is theirs), as on a new device, ready for another account's save. Sets no reset marker: nothing of it travels.
     */
    suspend fun wipe()
}

/** [CloudSaveStore] over the app's own repositories. */
class LocalCloudSave(
    private val achievements: AchievementsRepository,
    private val scores: ScoreRepository,
    private val settings: SettingsRepository,
    private val inProgressGame: InProgressGameRepository,
    private val online: OnlineSettingsRepository,
) : CloudSaveStore {

    override suspend fun snapshot(): CloudSave = CloudSave(
        achievements = achievements.cloudAchievements(),
        scoresResetAtEpochMillis = online.scoresResetAt(),
        scores = scores.cloudScores(),
        settings = settings.cloudSettings(),
        inProgressGame = inProgressGame.cloudGame(),
    )

    override suspend fun mergeSettings(save: CloudSave) = settings.mergeCloud(save.settings)

    override suspend fun mergeGame(save: CloudSave) = inProgressGame.mergeCloud(save.inProgressGame)

    // Under the store's lock, so no game reads the counters before this and writes them after.
    override suspend fun mergeAchievements(save: CloudSave) = achievements.locked { achievements.mergeCloud(save.achievements) }

    override suspend fun mergeScores(save: CloudSave) {
        val resetAt = maxOf(online.scoresResetAt(), save.scoresResetAtEpochMillis)
        online.setScoresResetAt(resetAt)
        scores.mergeCloud(resetAt, save.scores)
    }

    override suspend fun wipe() {
        online.setScoresResetAt(0)
        scores.resetLeaderboard()
        settings.wipeSynced()
        inProgressGame.wipe()
    }
}

/** All of [save] merged in, in [OnlineSync]'s order: settings, the game in progress, achievements, scores. */
suspend fun CloudSaveStore.merge(save: CloudSave) {
    mergeSettings(save)
    mergeGame(save)
    mergeAchievements(save)
    mergeScores(save)
}
