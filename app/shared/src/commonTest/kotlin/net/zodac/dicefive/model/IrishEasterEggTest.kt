package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.game.GameEngine

class IrishEasterEggTest {

    @Test
    fun `Ireland - Eire - and accented Eire all match - case- and accent-insensitively`() {
        listOf("Ireland", "ireland", "IRELAND", "Eire", "eire", "Éire", "éire", "  Ireland  ").forEach { name ->
            assertTrue(isIrishPlayerName(name), "\"$name\" should match")
        }
    }

    @Test
    fun `an unrelated name does not match`() {
        assertFalse(isIrishPlayerName("zodac"))
        assertFalse(isIrishPlayerName("Irelandish"))
        assertFalse(isIrishPlayerName(""))
    }

    private fun tricolourGameWithPlayerOneNamed(name: String): GameState = GameEngine.newGame(
        listOf(
            PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = name),
            PlayerConfig(slot = 2, type = PlayerType.AI, name = "CPU"),
        ),
        GameMode.TRICOLOUR,
    )

    @Test
    fun `Tricolour with P1 named Ireland is Luck of the Irish`() {
        assertTrue(tricolourGameWithPlayerOneNamed("Ireland").isLuckOfTheIrish)
    }

    @Test
    fun `standard mode with P1 named Ireland is not Luck of the Irish - it has to be Tricolour`() {
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Ireland")), GameMode.STANDARD)

        assertFalse(state.isLuckOfTheIrish)
    }

    @Test
    fun `Tricolour with P2 rather than P1 named Ireland is not Luck of the Irish`() {
        val state = GameEngine.newGame(
            listOf(
                PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Zoe"),
                PlayerConfig(slot = 2, type = PlayerType.HUMAN, name = "Ireland"),
            ),
            GameMode.TRICOLOUR,
        )

        assertFalse(state.isLuckOfTheIrish)
    }
}
