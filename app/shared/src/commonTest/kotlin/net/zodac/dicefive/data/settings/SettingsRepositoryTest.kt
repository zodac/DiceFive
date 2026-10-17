package net.zodac.dicefive.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice

/** Every value [flow] emits from now on, collected in the background. */
private fun <T> TestScope.emissions(flow: Flow<T>): List<T> =
    mutableListOf<T>().also { seen -> backgroundScope.launch { flow.collect { seen += it } } }

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @Test
    fun `a snapshot reads every setting as its flow does - the defaults with nothing saved`() = runTest {
        val repository = SettingsRepository(InMemoryPreferences())
        val empty = repository.snapshot()
        assertEquals(null, empty.playerCount)
        assertEquals(null, empty.playerNameFor(1))
        assertEquals(TurnTimer.NONE, empty.turnTimer)
        assertEquals(TurnTimer.SECONDS_60, empty.turnTimerLength)
        assertEquals(RollModifiers.DEFAULT_ROLLS, empty.rollsPerTurnLength)
        assertEquals(GameMode.default, empty.gameMode)
        assertEquals(UnluckyDice(UnluckyDice.DEFAULT_ODDS_PERCENT, UnluckyDice.DEFAULT_MAX_DICE), empty.unluckyDice)

        repository.setPlayerCount(3)
        repository.setPlayerName(2, "Bea")
        repository.setPlayerType(2, PlayerType.AI)
        repository.setPlayerDifficulty(2, Difficulty.HARD)
        repository.setPlayerColour(2, PlayerColour.entries.last())
        repository.setTurnTimer(TurnTimer.SECONDS_60)
        repository.setRollModifiers(RollModifiers(rollsPerTurn = RollModifiers.MAX_ROLLS, storedRolls = true), rollsPerTurnLength = RollModifiers.MAX_ROLLS)
        repository.setExtendedScores(true)
        repository.setUnluckyDice(true, UnluckyDice(UnluckyDice.MAX_ODDS_PERCENT, UnluckyDice.MAX_MAX_DICE))
        repository.setGameMode(GameMode.TRICOLOUR)

        val saved = repository.snapshot()
        assertEquals(repository.playerCount.first(), saved.playerCount)
        assertEquals(repository.playerNameFor(2).first(), saved.playerNameFor(2))
        assertEquals(repository.playerTypeFor(2).first(), saved.playerTypeFor(2))
        assertEquals(repository.playerDifficultyFor(2).first(), saved.playerDifficultyFor(2))
        assertEquals(repository.playerColourFor(2).first(), saved.playerColourFor(2))
        assertEquals(repository.turnTimer.first(), saved.turnTimer)
        assertEquals(repository.turnTimerLength.first(), saved.turnTimerLength)
        assertEquals(repository.rollModifiers.first(), saved.rollModifiers)
        assertEquals(repository.rollsPerTurnLength.first(), saved.rollsPerTurnLength)
        assertEquals(repository.extendedScores.first(), saved.extendedScores)
        assertEquals(repository.unluckyDiceEnabled.first(), saved.unluckyDiceEnabled)
        assertEquals(repository.unluckyDice.first(), saved.unluckyDice)
        assertEquals(repository.gameMode.first(), saved.gameMode)
        assertEquals(3, saved.playerCount)
        assertEquals("Bea", saved.playerNameFor(2))
        assertEquals(GameMode.TRICOLOUR, saved.gameMode)
    }

    @Test
    fun `a setting's flow emits when that setting changes - not on a write to another`() = runTest {
        val repository = SettingsRepository(InMemoryPreferences())
        val sound = emissions(repository.soundEnabled)
        val diceStyle = emissions(repository.diceStyleId)
        runCurrent()

        repository.setPlayerName(1, "Alice")
        repository.setVibrationEnabled(false)
        repository.setStylesPageFit("fit")
        runCurrent()
        assertEquals(listOf(true), sound)
        assertEquals(listOf("classic_ivory"), diceStyle)

        repository.setSoundEnabled(false)
        repository.setDiceStyleId("marble")
        runCurrent()
        assertEquals(listOf(true, false), sound)
        assertEquals(listOf("classic_ivory", "marble"), diceStyle)
    }

    @Test
    fun `the achievements emit when an unlock or a counter changes - not on any other write to their file`() = runTest {
        val store = InMemoryPreferences()
        val repository = AchievementsRepository(store)
        val states = emissions(repository.state)
        runCurrent()

        store.edit { it[stringPreferencesKey("unrelated")] = "x" }
        runCurrent()
        assertEquals(1, states.size)

        val achievement = Achievement.entries.first()
        val counter = AchievementCounter.entries.first()
        repository.record(mapOf(achievement to 5L), mapOf(counter to 2))
        runCurrent()
        assertEquals(2, states.size)
        assertEquals(5L, states.last().unlockedAt[achievement])
        assertEquals(2, states.last().counters[counter])
    }

    @Test
    fun `whether a game is saved emits when that changes - not on every move saved`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val saved = emissions(repository.hasInProgressGame)
        runCurrent()

        for (value in 1..3) {
            repository.save(GameState(players = listOf(PlayerState(name = "P1", type = PlayerType.HUMAN)), dice = List(5) { Die(value = value) }))
            runCurrent()
        }
        repository.clear()
        runCurrent()
        assertEquals(listOf(false, true, false), saved)
    }
}
