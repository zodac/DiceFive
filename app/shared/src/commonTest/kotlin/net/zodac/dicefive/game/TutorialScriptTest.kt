package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

/** One id for every game played here, so two play-throughs compare equal. */
private val GAME_ID = Uuid.parse("00000000-0000-0000-0000-000000000001")

/** The Standard tutorial's script is a legal solo game that scores what its coach card says, turn by turn. */
class TutorialScriptTest {

    /** [TutorialScript.STANDARD] played straight through the engine, with what each turn scored, in order. */
    private fun playThrough(): Pair<GameState, List<Int>> {
        val script = TutorialScript.STANDARD
        var state = GameEngine.newGame(
            listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "You")),
            script.gameMode,
            id = GAME_ID,
        )
        val scored = mutableListOf<Int>()
        // The game as it was before the last score, for an Undo step to put back.
        var beforeScore = state
        fun score(category: ScoreCategory) {
            beforeScore = state
            val before = state.players.single()
            state = GameEngine.commitScore(state, category)
            val after = state.players.single()
            // The box's score plus any joker bonus it earned.
            scored += after.totalScore - before.totalScore - (after.upperSectionBonus - before.upperSectionBonus)
        }
        for (step in script.steps) {
            when (step) {
                is TutorialStep.OpenTotals -> Unit
                is TutorialStep.Roll -> {
                    val held = state.dice.map { it.isHeld }
                    state = GameEngine.rollDice(state, TutorialDice(step.dice, held))
                    assertEquals(step.dice, state.dice.map { it.value }, "held dice keep their values: $step")
                }
                is TutorialStep.Hold -> for (die in step.dice) {
                    assertTrue(!state.dice[die].isHeld, "$step holds a die not yet held")
                    state = GameEngine.toggleHold(state, die)
                }
                is TutorialStep.Score -> score(step.category)
                is TutorialStep.Undo -> {
                    state = beforeScore
                    scored.removeAt(scored.lastIndex)
                }
            }
        }
        return state to scored
    }

    @Test
    fun `every turn scores what the coach card says - and the game ends on the last step`() {
        val (state, scored) = playThrough()
        assertTrue(state.isGameOver)
        // 5x, Chance, Large Straight, Threes, a zero in Small Straight, Fours, 4x, Twos, Full House, Fives, Ones with the
        // joker's 100, Sixes, 3x.
        assertEquals(listOf(50, 18, 40, 9, 0, 12, 26, 6, 25, 15, 105, 18, 23), scored)
    }

    @Test
    fun `the upper section reaches 65 for the bonus - and the game ends on 382`() {
        val (state, _) = playThrough()
        val player = state.players.single()
        assertEquals(65, player.upperSectionTotal)
        assertEquals(35, player.upperSectionBonus)
        assertEquals(1, player.fiveOfAKindBonusCount)
        assertEquals(382, player.totalScore)
    }

    @Test
    fun `every turn takes all three rolls to get there - but the one showing a score on the first roll`() {
        val steps = TutorialScript.STANDARD.steps
        // A turn ends at a score that isn't then taken back.
        val rollsPerTurn = mutableListOf(0)
        steps.forEachIndexed { index, step ->
            if (step is TutorialStep.Roll) rollsPerTurn[rollsPerTurn.lastIndex]++
            if (step is TutorialStep.Score && steps.getOrNull(index + 1) !is TutorialStep.Undo) rollsPerTurn += 0
        }
        // Fours, the sixth, is the first-roll score.
        assertEquals(listOf(3, 3, 3, 3, 3, 1, 3, 3, 3, 3, 3, 3, 3), rollsPerTurn.dropLast(1))
    }

    @Test
    fun `turns count from 1 to 13 - and the score taken back with Undo stays in its turn`() {
        val script = TutorialScript.STANDARD
        assertEquals(13, script.turns)
        assertEquals(1, script.turnOf(0))
        val undo = script.steps.indexOfFirst { it is TutorialStep.Undo }
        assertEquals(7, script.turnOf(undo))
        assertEquals(7, script.turnOf(undo + 1))
        assertEquals(8, script.turnOf(undo + 2))
        assertEquals(13, script.turnOf(script.steps.lastIndex))
    }

    @Test
    fun `replaying every step is the game played through - and each turn starts on a fresh turn`() {
        val script = TutorialScript.STANDARD
        val start = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "You")), script.gameMode, id = GAME_ID)
        assertEquals(playThrough().first, script.replay(start, script.steps.size))
        assertEquals(13, script.turnStarts.size)
        script.turnStarts.forEachIndexed { turn, index ->
            val state = script.replay(start, index)
            assertEquals(turn, state.scoresEntered, "turn ${turn + 1}")
            assertEquals(3, state.rollsRemaining, "turn ${turn + 1}")
            assertEquals(turn + 1, script.turnOf(index))
        }
    }
}
