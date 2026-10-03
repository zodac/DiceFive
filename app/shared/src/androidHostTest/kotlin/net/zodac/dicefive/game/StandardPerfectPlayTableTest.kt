package net.zodac.dicefive.game

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType

/**
 * The bundled Standard perfect-play table is exactly what Standard's rules give today: solved afresh
 * (about a quarter of a minute) and compared byte for byte, so a rule change that the table wasn't
 * regenerated for fails here rather than quietly leaving Hard playing by the old rules. JVM-only: it
 * reads and writes the file in the source tree.
 *
 * To regenerate after a rule change: `./gradlew :app:shared:testAndroidHostTest
 * --tests '*StandardPerfectPlayTableTest*' -PregeneratePerfectPlayTable`, then commit the file.
 */
class StandardPerfectPlayTableTest {

    private val bundled = File("src/commonMain/composeResources/${StandardPerfectPlayTable.RESOURCE_PATH}")

    @Test
    fun `the bundled table matches Standard's rules`() {
        val solved = StandardPerfectPlayTable.solve().encode()
        if (System.getProperty("dicefive.regeneratePerfectPlayTable") == "true") {
            bundled.writeBytes(solved)
            println("Wrote ${solved.size} bytes to ${bundled.absolutePath}")
            return
        }
        assertTrue(bundled.isFile, "No bundled table at ${bundled.absolutePath} - regenerate it, see the class doc")
        assertContentEquals(solved, bundled.readBytes(), "The bundled table is out of date with Standard's rules - regenerate it, see the class doc")
    }

    @Test
    fun `perfect play expects about 254 and a half points a game`() {
        // Verhoeff's published figure for these rules is 254.59; the table keeps values to 1/32 of a point.
        val table = StandardPerfectPlayTable.decode(bundled.readBytes())
        val fromTheStart = table.valueOf(filledMask = 0, upperTotal = 0, fiveOfAKindScored = false)

        assertTrue(fromTheStart in 254.0..255.0, "Perfect play expects $fromTheStart")
    }

    @Test
    fun `Hard playing by the table averages at least 248 over 200 seeded solo games`() {
        // Seeded, so the same 200 games every run: Hard averaged 239 on these by estimate alone, 253 by the table.
        val table = StandardPerfectPlayTable.decode(bundled.readBytes())
        val scores = (1..SEEDED_GAMES).map { seed ->
            val random = Random(seed)
            var state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.AI, name = "Bot", difficulty = Difficulty.HARD)), GameMode.STANDARD)
            while (!state.isGameOver) state = AiTurnPlayer.playTurn(state, random, table)
            state.players.single().totalScore
        }

        assertTrue(scores.average() >= 248, "Hard averaged ${scores.average()} with the table")
    }

    private companion object {
        const val SEEDED_GAMES = 200
    }
}
