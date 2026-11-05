package net.zodac.dicefive.app

import net.zodac.dicefive.data.orNullOnFailure
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.online.OnlineSettingsRepository
import net.zodac.dicefive.online.OnlineSyncRequests

/** The progress reset Settings offers, leaving every store it touches consistent with the others. */
class ProgressResets(
    private val scores: ScoreRepository,
    private val onlineSettings: OnlineSettingsRepository,
    private val now: () -> Long = ::nowEpochMillis,
) {

    /**
     * Wipes every recorded score, clearing both the Leaderboard and Statistics screens - the latter
     * is computed from the same rows, so there's nothing left to show once they're gone.
     * Achievements measured live against the board (the score-collection ones) lose their progress
     * too, though their unlock timestamps live in the separate achievements DataStore and are
     * untouched.
     *
     * On every device of the synced account too: the reset is marked with its time, and the cloud save drops every score
     * from before it ([OnlineSettingsRepository.scoresResetAt]). The online service's own leaderboards keep theirs.
     */
    suspend fun resetLeaderboard() {
        onlineSettings.setScoresResetAt(now())
        // One that can't be written (a full disk) leaves the scores in place, rather than taking the app down.
        orNullOnFailure { scores.resetLeaderboard() }
        OnlineSyncRequests.request()
    }
}
