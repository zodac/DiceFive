package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.game.GameEngine

class IrishEasterEggTest {

    private fun tricolourGameWithPlayerOneNamed(name: String): GameState = GameEngine.newGame(
        listOf(
            PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = name),
            PlayerConfig(slot = 2, type = PlayerType.AI, name = "CPU"),
        ),
        GameMode.TRICOLOUR,
    )

    @Test
    fun `Luck of the Irish is Tricolour with player 1 named Ireland or Eire - case and accent insensitively`() {
        listOf("Ireland", "ireland", "IRELAND", "Eire", "eire", "Éire", "éire", "  Ireland  ").forEach { name ->
            assertTrue(isIrishPlayerName(name), "\"$name\" should match")
        }
        for (name in listOf("zodac", "Irelandish", "")) assertFalse(isIrishPlayerName(name), "\"$name\" shouldn't match")

        assertTrue(tricolourGameWithPlayerOneNamed("Ireland").isLuckOfTheIrish)
        // It has to be Tricolour...
        assertFalse(GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Ireland")), GameMode.STANDARD).isLuckOfTheIrish)
        // ...and player 1 rather than player 2.
        val playerTwo = GameEngine.newGame(
            listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Zoe"), PlayerConfig(slot = 2, type = PlayerType.HUMAN, name = "Ireland")),
            GameMode.TRICOLOUR,
        )
        assertFalse(playerTwo.isLuckOfTheIrish)
    }
}
