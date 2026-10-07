package net.zodac.dicefive

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.ScoreCalculator
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.ui.game.DiceTray
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.game.GameBoard
import net.zodac.dicefive.ui.game.ReadOnlyScoreboard
import net.zodac.dicefive.ui.game.ScoreGrid
import net.zodac.dicefive.ui.game.TotalsButton
import net.zodac.dicefive.ui.game.HitListTotalsButton
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader gets from the game board's drawn art: each die named by its face and whether
 * it's held, with hold/release as its action, and each score box named with what it has or would
 * score, with scoring as its action - none of which the art itself says.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BoardSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hasStateDescription(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    @Test
    fun `each die says its face and whether it's held - and its action holds it`() {
        val toggled = mutableListOf<Int>()
        compose.setContent {
            DiceFiveTheme {
                DiceTray(
                    dice = listOf(Die(2), Die(5, isHeld = true), Die(3), Die(6), Die(1)),
                    gameMode = GameMode.STANDARD,
                    enabled = true,
                    showDice = true,
                    rolling = false,
                    onToggleHold = { toggled += it },
                    modifier = Modifier.width(500.dp),
                )
            }
        }

        compose.onNodeWithContentDescription("Die 2, 5").assert(hasStateDescription("Held"))
        compose.onNodeWithContentDescription("Die 4, 6").assert(hasStateDescription("Not held")).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(3), toggled)
    }

    @Test
    fun `a die locked by Unlucky Dice says so and offers no hold`() {
        val toggled = mutableListOf<Int>()
        compose.setContent {
            DiceFiveTheme {
                DiceTray(
                    dice = listOf(Die(2), Die(5, isUnlucky = true), Die(3), Die(6), Die(1)),
                    gameMode = GameMode.STANDARD,
                    enabled = true,
                    showDice = true,
                    rolling = false,
                    onToggleHold = { toggled += it },
                    modifier = Modifier.width(500.dp),
                )
            }
        }

        val locked = compose.onNodeWithContentDescription("Die 2, 5")
        locked.assert(hasStateDescription("Locked in chains, can't be held or scored"))
        locked.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithContentDescription("Die 1, 2").assert(hasStateDescription("Not held"))
    }

    @Test
    fun `a locked die in a seven dice mode offers no hold either`() {
        compose.setContent {
            DiceFiveTheme {
                DiceTray(
                    dice = List(7) { Die(it % 6 + 1, isUnlucky = it == 6) },
                    gameMode = GameMode.STUD,
                    enabled = true,
                    showDice = true,
                    rolling = false,
                    onToggleHold = {},
                    modifier = Modifier.width(500.dp),
                )
            }
        }

        val locked = compose.onNodeWithContentDescription("Die 7, 1")
        locked.assert(hasStateDescription("Locked in chains, can't be held or scored"))
        locked.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun `a score box says what it would score - and its action scores it`() {
        val scored = mutableListOf<ScoreCategory>()
        val player = PlayerState(name = "Tester", type = PlayerType.HUMAN)
        compose.setContent {
            DiceFiveTheme {
                ScoreGrid(
                    categories = GameMode.STANDARD.categories,
                    player = player,
                    dice = listOf(Die(5), Die(5), Die(2), Die(3), Die(1)),
                    canScore = true,
                    showPreview = true,
                    available = ScoreCategory.entries.toSet(),
                    onScoreCategory = { scored += it },
                    modifier = Modifier.width(300.dp).height(380.dp),
                )
            }
        }

        compose.onNodeWithContentDescription("Fives").assert(hasStateDescription("Would score 10")).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(ScoreCategory.FIVES), scored)
    }

    @Test
    fun `the Extended Scores boxes are named and say what they would score`() {
        val scored = mutableListOf<ScoreCategory>()
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Tester")), extendedScores = true)
            .copy(phase = TurnPhase.ROLLED, rollsRemaining = 2, dice = listOf(6, 6, 5, 5, 1).map { Die(it) })
        compose.setContent {
            DiceFiveTheme {
                GameBoard(
                    state = state,
                    rolling = false,
                    canUndo = false,
                    onScoreCategory = { scored += it },
                    onCupTap = {},
                    onUndo = {},
                    modifier = Modifier.width(360.dp),
                )
            }
        }

        compose.onNodeWithContentDescription("Two Pair").assert(hasStateDescription("Would score 22")).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Evens").assert(hasStateDescription("Would score 12"))
        compose.onNodeWithContentDescription("Odds").assert(hasStateDescription("Would score 11"))

        assertEquals(listOf(ScoreCategory.TWO_PAIR), scored)
    }

    @Test
    fun `a disabled box says it is disabled and offers no score - unlike an open or a scored one`() {
        val scored = mutableListOf<ScoreCategory>()
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Tester")), GameMode.QUICKFIRE)
        val off = setOf(ScoreCategory.FIVE_OF_A_KIND, ScoreCategory.THREES)
        val ready = state.copy(
            disabledCategories = off,
            players = state.players.map { it.copy(disabledCategories = off, scorecard = it.scorecard + (ScoreCategory.CHANCE to listOf(22))) },
            phase = TurnPhase.ROLLED,
            rollsRemaining = 2,
            dice = listOf(3, 3, 3, 3, 3).map { Die(it) },
        )
        compose.setContent {
            DiceFiveTheme {
                GameBoard(
                    state = ready,
                    rolling = false,
                    canUndo = false,
                    onScoreCategory = { scored += it },
                    onCupTap = {},
                    onUndo = {},
                    modifier = Modifier.width(360.dp),
                )
            }
        }

        // Five 3s, and neither the 3s nor the 5x box they'd go in can be used.
        for (name in listOf("3x", "5x", "Threes")) {
            val box = compose.onNodeWithContentDescription(name)
            if (name == "3x") {
                box.assert(hasStateDescription("Would score 15")).performSemanticsAction(SemanticsActions.OnClick)
            } else {
                box.assert(hasStateDescription("Disabled for this game, can't be scored"))
                box.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
                box.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            }
        }
        // A scored box is a different thing, and says so.
        compose.onNodeWithContentDescription("Chance").assert(hasStateDescription("Scored 22"))
        compose.onNodeWithContentDescription("Chance").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))

        assertEquals(listOf(ScoreCategory.THREE_OF_A_KIND), scored)
    }

    @Test
    fun `a player tab says the player's place - and the drawn place isn't a stop of its own`() {
        val players = listOf(
            PlayerState(name = "Alex", type = PlayerType.HUMAN).let { it.copy(scorecard = it.scorecard + (ScoreCategory.CHANCE to listOf(20))) },
            PlayerState(name = "Robo", type = PlayerType.AI).let { it.copy(scorecard = it.scorecard + (ScoreCategory.FULL_HOUSE to listOf(25))) },
            PlayerState(name = "Sam", type = PlayerType.HUMAN).let { it.copy(scorecard = it.scorecard + (ScoreCategory.SIXES to listOf(20))) },
        )
        compose.setContent {
            DiceFiveTheme {
                PlayerHeaderBar(players = players, currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}, modifier = Modifier.width(400.dp))
            }
        }

        compose.onNode(hasText("Alex") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(hasStateDescription("Current turn, tied 2nd place"))
        compose.onNode(hasText("Robo") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assert(hasStateDescription("1st place"))
        // "=2nd" would be read as "equals 2nd" - the tab's state says it instead.
        compose.onAllNodesWithText("=2nd", useUnmergedTree = true).assertCountEquals(0)
    }

    // The places come from the platform's ordinal formatter (ICU on Android), not a hand-written
    // English suffix - this checks it says what the hand-written one did for every place a game has.
    @Test
    fun `every place a four-player game can have is said as an English ordinal`() {
        assertFourPlacesSaidInEnglish()
    }

    // The ordinals follow the strings' language, not the device's: the app has no German yet, so a
    // German phone gets English text - and English places with it, not "1." among English words.
    @Test
    @Config(qualifiers = "de")
    fun `on a phone in a language the app isn't in the places stay English like the text around them`() {
        assertEquals("de", Locale.getDefault().language)
        assertFourPlacesSaidInEnglish()
    }

    private fun assertFourPlacesSaidInEnglish() {
        val players = listOf(30, 25, 20, 10).mapIndexed { index, chance ->
            PlayerState(name = "P${index + 1}", type = PlayerType.HUMAN).let { it.copy(scorecard = it.scorecard + (ScoreCategory.CHANCE to listOf(chance))) }
        }
        compose.setContent {
            DiceFiveTheme {
                PlayerHeaderBar(players = players, currentPlayerIndex = 1, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}, modifier = Modifier.width(400.dp))
            }
        }

        listOf("1st place", "Current turn, 2nd place", "3rd place", "4th place").forEachIndexed { index, state ->
            compose.onNode(hasText("P${index + 1}") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assert(hasStateDescription(state))
        }
    }

    @Test
    fun `another player's scorecard says which box was their last turn's score`() {
        val player = PlayerState(name = "Robo", type = PlayerType.AI).let {
            it.copy(scorecard = it.scorecard + (ScoreCategory.THREES to listOf(9)) + (ScoreCategory.FULL_HOUSE to listOf(25)), lastScoredCategory = ScoreCategory.FULL_HOUSE)
        }
        compose.setContent {
            DiceFiveTheme {
                ReadOnlyScoreboard(player = player, modifier = Modifier.width(400.dp))
            }
        }

        compose.onNodeWithContentDescription("Full House").assert(hasStateDescription("Scored 25, last turn's score"))
        compose.onNodeWithContentDescription("Threes").assert(hasStateDescription("Scored 9"))
    }

    @Test
    fun `a Third Wind box says each slot's score - what the dice would score next - and how many are left`() {
        val player = PlayerState(name = "Tester", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND).let {
            it.copy(scorecard = it.scorecard + (ScoreCategory.FIVES to listOf(15, 10)) + (ScoreCategory.SIXES to listOf(12, 6, 18)))
        }
        val dice = listOf(Die(5), Die(5), Die(2), Die(3), Die(1))
        val scored = mutableListOf<ScoreCategory>()
        compose.setContent {
            DiceFiveTheme {
                ScoreGrid(
                    categories = GameMode.THIRD_WIND.categories,
                    player = player,
                    dice = dice,
                    canScore = true,
                    showPreview = true,
                    available = ScoreCalculator.availableCategories(player, dice).toSet(),
                    onScoreCategory = { scored += it },
                    modifier = Modifier.width(300.dp).height(380.dp),
                )
            }
        }

        compose.onNodeWithContentDescription("Fives").assert(hasStateDescription("Scored 15, 10, would score 10")).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Chance").assert(hasStateDescription("Would score 16, 2 more open"))
        // Full: nothing to preview, and nothing to tap.
        compose.onNodeWithContentDescription("Sixes").assert(hasStateDescription("Scored 12, 6, 18")).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        assertEquals(listOf(ScoreCategory.FIVES), scored)
    }

    @Test
    fun `another player's Third Wind box says which of its scores was their last turn's`() {
        val player = PlayerState(name = "Robo", type = PlayerType.AI, gameMode = GameMode.THIRD_WIND).let {
            it.copy(scorecard = it.scorecard + (ScoreCategory.FULL_HOUSE to listOf(0, 25)), lastScoredCategory = ScoreCategory.FULL_HOUSE)
        }
        compose.setContent {
            DiceFiveTheme {
                ReadOnlyScoreboard(player = player, modifier = Modifier.width(400.dp))
            }
        }

        compose.onNodeWithContentDescription("Full House").assert(hasStateDescription("Scored 0, 25, 1 open, last turn's score 25"))
        compose.onNodeWithContentDescription("Threes").assert(hasStateDescription("3 open"))
    }

    @Test
    fun `the 5x box of a Third Wind card adds its bonus - and the last turn's score comes last`() {
        val player = PlayerState(name = "Robo", type = PlayerType.AI, gameMode = GameMode.THIRD_WIND).let {
            it.copy(
                scorecard = it.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(50, 0, 50)),
                fiveOfAKindBonusCount = 2,
                lastScoredCategory = ScoreCategory.FIVE_OF_A_KIND,
            )
        }
        compose.setContent {
            DiceFiveTheme {
                ReadOnlyScoreboard(player = player, modifier = Modifier.width(400.dp))
            }
        }

        compose.onNode(hasStateDescription("Scored 50, 0, 50, plus 200 bonus, last turn's score 50")).assertExists()
    }

    @Test
    fun `the Totals button says every total - and a tap shows them`() {
        compose.setContent {
            DiceFiveTheme { TotalsButton(upperTotal = 70, upperBonus = 35, lowerTotal = 140) }
        }

        compose.onNodeWithContentDescription("Totals")
            .assert(hasStateDescription("Upper 70, bonus 35 earned, lower 140"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        compose.waitForIdle()

        // The tooltip is a table: the label and its number are texts of their own.
        compose.onNodeWithText("Lower").assertExists()
        compose.onNodeWithText("140").assertExists()
    }

    @Test
    fun `the Totals tooltip groups a total's thousands`() {
        compose.setContent {
            DiceFiveTheme { TotalsButton(upperTotal = 70, upperBonus = 35, lowerTotal = 1088) }
        }

        compose.onNodeWithContentDescription("Totals").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("1,088").assertExists()
    }

    // Grouping follows the strings' language like the ordinals do: no German yet, so English text keeps its commas.
    @Test
    @Config(qualifiers = "de")
    fun `on a phone in a language the app isn't in a total's thousands are grouped like the text around them`() {
        compose.setContent {
            DiceFiveTheme { TotalsButton(upperTotal = 70, upperBonus = 35, lowerTotal = 1088) }
        }

        compose.onNodeWithContentDescription("Totals").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("1,088").assertExists()
    }

    @Test
    fun `the Totals button says when the bonus isn't earned yet`() {
        compose.setContent {
            DiceFiveTheme { TotalsButton(upperTotal = 40, upperBonus = 0, lowerTotal = 90) }
        }

        compose.onNodeWithContentDescription("Totals").assert(hasStateDescription("Upper 40, no bonus yet, lower 90"))
    }

    @Test
    fun `a Hit List target says what it calls - what it would score and how close the dice are - and the Alibi what it stands in for`() {
        val scored = mutableListOf<ScoreCategory>()
        // The rest call three 5s, which these dice never hit.
        val hitList = ScoreCategory.TARGETS.associateWith { HitTarget(listOf(5, 5, null, null, 5), points = 25) } + mapOf(
            ScoreCategory.TARGET_1 to HitTarget(listOf(4, 1, 3, 2, null), points = 20),
            ScoreCategory.TARGET_2 to HitTarget(listOf(5, null, 5, null, 1), points = 15),
            ScoreCategory.TARGET_3 to HitTarget(listOf(1, 2, 3, 4, 5), points = 40),
            ScoreCategory.TARGET_4 to HitTarget(listOf(null, 6, null, 1, 4), points = 10),
        )
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Tester")), GameMode.HIT_LIST).let { game ->
            game.copy(
                hitList = hitList,
                players = game.players.map { it.copy(hitList = hitList) },
                phase = TurnPhase.ROLLED,
                rollsRemaining = 1,
                dice = listOf(4, 1, 3, 2, 6).map { Die(it) },
            )
        }
        compose.setContent {
            DiceFiveTheme {
                GameBoard(state = state, rolling = false, canUndo = false, onScoreCategory = { scored += it }, onCupTap = {}, onUndo = {}, modifier = Modifier.width(360.dp))
            }
        }

        compose.onNodeWithContentDescription("Target 1, 4, 1, 3, 2, any. 20 points, 40 exact")
            .assert(hasStateDescription("Would score 40, exact hit"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Target 4, any, 6, any, 1, 4. 10 points, 20 exact").assert(hasStateDescription("Would score 10, hit, 0 of 3 in place"))
        compose.onNodeWithContentDescription("Target 3, 1, 2, 3, 4, 5. 40 points, 80 exact").assert(hasStateDescription("Would score 15, partial hit, 4 of 5 rolled, 1 in place"))
        compose.onNodeWithContentDescription("Target 2, 5, any, 5, any, 1. 15 points, 30 exact").assert(hasStateDescription("Would score 0, 1 of 3 rolled, 0 in place"))
        // The best target hit is the 20, which the Alibi takes undoubled.
        compose.onNodeWithContentDescription("Alibi").assert(hasStateDescription("Would score 20"))

        assertEquals(listOf(ScoreCategory.TARGET_1), scored)
    }

    @Test
    fun `the Hit List Totals button says the targets' total and the Alibi's`() {
        compose.setContent {
            DiceFiveTheme { HitListTotalsButton(targetsTotal = 60, alibiTotal = 20) }
        }

        compose.onNodeWithContentDescription("Totals").assert(hasStateDescription("Targets 60, alibi 20"))
    }
}
