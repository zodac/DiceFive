package net.zodac.dicefive

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.game.TotalsButton
import net.zodac.dicefive.ui.game.TurnTimerBadge
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import net.zodac.dicefive.ui.menu.MenuScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A phone set to Spanish gets the Spanish strings, and with them the formatters' Spanish: ordinals, grouped numbers, and the
 * punctuation Spanish writes (¿ ? ¡ !). The first language, so these also show the machinery works end to end.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "es")
class SpanishTextTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the main menu is in Spanish`() {
        compose.setContent {
            DiceFiveTheme {
                MenuScreen(
                    hasInProgressGame = false,
                    onContinue = {}, onNewGame = {}, onScores = {}, onStatistics = {}, onAchievements = {}, onStyles = {}, onRules = {}, onSettings = {},
                )
            }
        }

        listOf("Jugar", "Logros", "Estilos", "Clasificación", "Estadísticas", "Reglas", "Ajustes").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `places are said as Spanish ordinals`() {
        val players = listOf(30, 25).mapIndexed { index, chance ->
            PlayerState(name = "P${index + 1}", type = PlayerType.HUMAN).let { it.copy(scorecard = it.scorecard + (ScoreCategory.CHANCE to listOf(chance))) }
        }
        compose.setContent {
            DiceFiveTheme { PlayerHeaderBar(players = players, currentPlayerIndex = 1, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}, modifier = Modifier.width(400.dp)) }
        }

        val state = { name: String ->
            compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        }
        assertEquals("1.º puesto", state("P1"))
        assertEquals("Turno actual, 2.º puesto", state("P2"))
    }

    @Test
    fun `thousands are grouped the Spanish way`() {
        compose.setContent { DiceFiveTheme { TotalsButton(upperTotal = 70, upperBonus = 35, lowerTotal = 34521) } }
        compose.onNodeWithContentDescription("Totales").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("34.521").assertExists()
    }

    @Test
    fun `the turn timer's seconds carry their unit and say the time in words`() {
        compose.setContent { DiceFiveTheme { TurnTimerBadge(secondsRemaining = 12) } }

        compose.onNodeWithContentDescription("Tiempo restante: 12 segundos").assertExists()
    }

    @Test
    fun `the leave-game question has Spanish's opening question mark`() {
        val confirmation = net.zodac.dicefive.ui.game.LeaveGameConfirmation()
        confirmation.request { }
        compose.setContent { DiceFiveTheme { net.zodac.dicefive.ui.game.LeaveGameConfirmationDialog(confirmation) } }

        compose.onNodeWithText("¿Salir de la partida?").assertExists()
    }
}
