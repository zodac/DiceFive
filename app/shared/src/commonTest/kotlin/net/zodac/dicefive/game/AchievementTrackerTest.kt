package net.zodac.dicefive.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.LoadedDice
import net.zodac.dicefive.ScriptedDice
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.UnluckyDice

/**
 * [AchievementTracker] on its own, fed the states [GameEngine] makes. `GameAchievementsWiringTest` covers the same
 * achievements through `GameViewModel`; this pins the tracker's own bookkeeping - what carries from one event to the
 * next, and what a new game forgets.
 */
class AchievementTrackerTest {

    private fun soloGame(): GameState = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P1")))

    /** Player 1's roll of all 3s, reported to [tracker] - the state it lands in. */
    private fun AchievementTracker.roll(state: GameState): GameState =
        GameEngine.rollDice(state, LoadedDice(3)).also { onRoll(state, it) }

    @Test
    fun `an undo into a different box is I Didn't Mean That - the same box or no undo is not`() {
        val tracker = AchievementTracker()
        tracker.startGame()
        assertEquals(emptySet(), tracker.onCommitted(ScoreCategory.THREES, byPlayerOne = true))
        tracker.onUndo()
        assertEquals(setOf(Achievement.I_DIDNT_MEAN_THAT), tracker.onCommitted(ScoreCategory.CHANCE, byPlayerOne = true))

        // Once only per undo: the next commit has nothing undone to differ from.
        assertEquals(emptySet(), tracker.onCommitted(ScoreCategory.FOURS, byPlayerOne = true))

        tracker.onUndo()
        assertEquals(emptySet(), tracker.onCommitted(ScoreCategory.FOURS, byPlayerOne = true), "the same box again")
    }

    @Test
    fun `I Didn't Mean That never carries over from one game to the next - whether it ended or was left`() {
        val ended = AchievementTracker()
        ended.startGame()
        ended.onCommitted(ScoreCategory.THREES, byPlayerOne = true)
        ended.onUndo()
        ended.onGameOver()
        ended.startGame()
        assertEquals(emptySet(), ended.onCommitted(ScoreCategory.CHANCE, byPlayerOne = true), "after the game ended")

        val left = AchievementTracker()
        left.startGame()
        left.onCommitted(ScoreCategory.THREES, byPlayerOne = true)
        left.onUndo()
        left.startGame()
        assertEquals(emptySet(), left.onCommitted(ScoreCategory.CHANCE, byPlayerOne = true), "after leaving for a new game")

        // The last commit of a game is forgotten at its end too, so an undo can't reach back to it.
        val lastBox = AchievementTracker()
        lastBox.startGame()
        lastBox.onCommitted(ScoreCategory.THREES, byPlayerOne = true)
        lastBox.onGameOver()
        lastBox.onUndo()
        assertEquals(emptySet(), lastBox.onCommitted(ScoreCategory.CHANCE, byPlayerOne = true))
    }

    @Test
    fun `a resumed game keeps the box its undo took back - it's the same game`() {
        val before = AchievementTracker()
        before.startGame()
        before.onCommitted(ScoreCategory.THREES, byPlayerOne = true)
        before.onUndo()
        assertEquals(ScoreCategory.THREES, before.undoneCategory, "saved with the game")

        val resumed = AchievementTracker()
        resumed.startGame(resumedUndo = before.undoneCategory)
        assertEquals(setOf(Achievement.I_DIDNT_MEAN_THAT), resumed.onCommitted(ScoreCategory.CHANCE, byPlayerOne = true))
        assertEquals(null, resumed.undoneCategory, "a score follows it, so there's nothing left to save")
    }

    @Test
    fun `a resumed game whose last score Undo can still take back - undone into another box is I Didn't Mean That`() {
        val resumed = AchievementTracker()
        resumed.startGame(resumedLastCommitted = ScoreCategory.THREES)
        resumed.onUndo()
        assertEquals(ScoreCategory.THREES, resumed.undoneCategory)
        assertEquals(setOf(Achievement.I_DIDNT_MEAN_THAT), resumed.onCommitted(ScoreCategory.CHANCE, byPlayerOne = true))

        // Undone back into the same box is no change of mind.
        val same = AchievementTracker()
        same.startGame(resumedLastCommitted = ScoreCategory.THREES)
        same.onUndo()
        assertEquals(emptySet(), same.onCommitted(ScoreCategory.THREES, byPlayerOne = true))
    }

    @Test
    fun `another seat's score clears an undo - and earns nothing itself`() {
        val tracker = AchievementTracker()
        tracker.startGame()
        tracker.onCommitted(ScoreCategory.THREES, byPlayerOne = true)
        tracker.onUndo()
        assertEquals(emptySet(), tracker.onCommitted(ScoreCategory.CHANCE, byPlayerOne = false))
        assertEquals(emptySet(), tracker.onCommitted(ScoreCategory.FOURS, byPlayerOne = true))
    }

    @Test
    fun `dice rolled and cup taps are counted through a game - and a new game starts them from zero`() {
        val tracker = AchievementTracker()
        tracker.startGame()
        tracker.roll(soloGame())
        assertEquals(5, tracker.endOfGameContext(null, LeaderboardTotals()).diceRolledByPlayerOne)

        // Only a tap with no rolls left counts, and only on player 1's turn.
        val outOfRolls = soloGame().copy(rollsRemaining = 0)
        val bot = PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot")
        val botsTurn = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P1"), bot)).copy(currentPlayerIndex = 1, rollsRemaining = 0)
        repeat(3) {
            assertEquals(emptySet(), tracker.onCupTapWithNoRollsLeft(soloGame()))
            assertEquals(emptySet(), tracker.onCupTapWithNoRollsLeft(botsTurn))
        }
        assertEquals(emptySet(), tracker.onCupTapWithNoRollsLeft(outOfRolls))
        assertEquals(emptySet(), tracker.onCupTapWithNoRollsLeft(outOfRolls))
        assertEquals(setOf(Achievement.NO_MORE_ROLLS), tracker.onCupTapWithNoRollsLeft(outOfRolls))

        tracker.startGame()
        assertEquals(0, tracker.endOfGameContext(null, LeaderboardTotals()).diceRolledByPlayerOne)
        assertEquals(emptySet(), tracker.onCupTapWithNoRollsLeft(outOfRolls), "the taps start again")
    }

    @Test
    fun `the rabbit is a Human turn's - the hold sequence player 1's - and a golden egg landing player 1's roll`() {
        val tracker = AchievementTracker()
        tracker.startGame()
        val twoHumans = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P1"), PlayerConfig(slot = 2, type = PlayerType.HUMAN, name = "P2")))
        val withBot = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P1"), PlayerConfig(slot = 2, type = PlayerType.AI, name = "Bot")))
        assertEquals(setOf(Achievement.THE_MAGICIANS_SECRET), tracker.onRabbitSeen(twoHumans.copy(currentPlayerIndex = 1)))
        assertEquals(emptySet(), tracker.onRabbitSeen(withBot.copy(currentPlayerIndex = 1)))

        assertEquals(setOf(Achievement.TIME_WASTING), tracker.onHoldSequenceDone(twoHumans))
        assertEquals(emptySet(), tracker.onHoldSequenceDone(twoHumans.copy(currentPlayerIndex = 1)))

        val before = soloGame()
        val golden = GameEngine.rollDice(before, Random(1), goldenOneIn = 1)
        assertTrue(Achievement.EGGCELLENT_DISCOVERY in tracker.onRoll(before, golden))
        // Golden already, and rolled again: nothing new landed.
        val rerolled = GameEngine.rollDice(golden, Random(1), goldenOneIn = 1)
        assertTrue(Achievement.EGGCELLENT_DISCOVERY !in tracker.onRoll(golden, rerolled))
        assertTrue(Achievement.EGGCELLENT_DISCOVERY !in tracker.onRoll(before, GameEngine.rollDice(before, Random(1))))
    }

    @Test
    fun `every die matching is Lucky Seven in Stud alone - five matching in Standard is not`() {
        fun earns(mode: GameMode): Boolean {
            val tracker = AchievementTracker()
            tracker.startGame()
            val start = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "P1")), mode)
            return Achievement.LUCKY_SEVEN in GameEngine.rollDice(start, LoadedDice(3)).let { tracker.onRoll(start, it) }
        }
        assertTrue(earns(GameMode.STUD))
        assertFalse(earns(GameMode.STANDARD))
    }

    @Test
    fun `rolling with every die held is What Was the Point - and a second roll is an extra roll taken`() {
        val tracker = AchievementTracker()
        tracker.startGame()
        val first = tracker.roll(soloGame())
        assertFalse(tracker.endOfGameContext(null, LeaderboardTotals()).playerOneTookExtraRoll)

        val allHeld = (0 until 5).fold(first) { state, index -> GameEngine.toggleHold(state, index) }
        assertTrue(Achievement.WHAT_WAS_THE_POINT_OF_THAT in GameEngine.rollDice(allHeld, LoadedDice(3)).let { tracker.onRoll(allHeld, it) })
        assertTrue(tracker.endOfGameContext(null, LeaderboardTotals()).playerOneTookExtraRoll)
        // The held dice didn't move, so none of them were rolled.
        assertEquals(5, tracker.endOfGameContext(null, LeaderboardTotals()).diceRolledByPlayerOne)
    }

    @Test
    fun `Rainy Day Fund - a turn starting with 100 rolls under Stored Rolls - not 99 - and not without the modifier`() {
        fun earns(rolls: Int, stored: Boolean): Boolean {
            val tracker = AchievementTracker()
            tracker.startGame()
            val start = soloGame().copy(rollModifiers = RollModifiers(rollsPerTurn = 9, storedRolls = stored), rollsRemaining = rolls, turnRolls = rolls)
            return Achievement.RAINY_DAY_FUND in GameEngine.rollDice(start, LoadedDice(3)).let { tracker.onRoll(start, it) }
        }
        assertTrue(earns(100, stored = true))
        assertFalse(earns(99, stored = true))
        assertFalse(earns(100, stored = false))

        // Only as the turn starts: its second roll, with 99 left, is no new turn.
        val tracker = AchievementTracker()
        tracker.startGame()
        val start = soloGame().copy(rollModifiers = RollModifiers(rollsPerTurn = 9, storedRolls = true), rollsRemaining = 100, turnRolls = 100)
        val first = tracker.roll(start)
        assertFalse(Achievement.RAINY_DAY_FUND in GameEngine.rollDice(first, LoadedDice(3)).let { tracker.onRoll(first, it) })
    }

    @Test
    fun `Bad Omen - every die chained by Unlucky Dice at once - not four of five`() {
        fun earns(maxDice: Int): Boolean {
            val tracker = AchievementTracker()
            tracker.startGame()
            // Five 3s rolled, each die's odds drawn as 3 (under 50, so all five are chained), then the order the cap
            // takes them in shuffled by four draws of 0 - so as many dice as the cap allows are chained.
            val start = soloGame().copy(unluckyDice = UnluckyDice(oddsPercent = 50, maxDice = maxDice))
            val dice = ScriptedDice(List(5) { 3 } + List(5) { 3 } + List(4) { 0 })
            return Achievement.BAD_OMEN in GameEngine.rollDice(start, dice).let { tracker.onRoll(start, it) }
        }
        assertTrue(earns(5))
        assertFalse(earns(4))
    }
}
