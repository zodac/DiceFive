package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.ui.game.GameSetupState

/**
 * Generates flavourful, non-duplicate names for AI players at game start - a full name never
 * truncated, since half a word looks like a bug rather than a tight layout. Instead, each player
 * count has its own pool, every entry already within [GameSetupState.maxAiNameLength] for that
 * count, so a name never has to be cut down (or arrives pre-ellipsised) once it lands in a real
 * tab. [AiNameGeneratorTest] sweeps every pool against its own cap so a name added later that
 * doesn't fit fails the build instead of showing up ellipsised in a real game.
 */
object AiNameGenerator {

    // Roomiest tabs of the three (2 players): the original, more florid pool, all of which already
    // fit maxAiNameLength(2).
    private val NAMES_2P = listOf(
        "Snake Eyes",
        "Dizzy Dice",
        "Roll Model",
        "Hot Streak",
        "Boxcar Bo",
        "Sir Rolls",
        "Cap'n Luck",
        "Random Ray",
        "Ms Fortune",
        "Double Six",
        "Big Bones",
        "Wildcard",
    )

    // Tighter tabs (3 players): shorter than NAMES_2P, all within maxAiNameLength(3).
    private val NAMES_3P = listOf(
        "Wildcard",
        "Dice Guy",
        "Hot Dice",
        "Big Luck",
        "Lone Ace",
        "Six Shot",
        "Lucky Al",
        "Ace Roll",
        "Dice Fox",
        "Roll Pro",
    )

    // Tightest tabs (4 players, sharing the row with three others): all within maxAiNameLength(4).
    private val NAMES_4P = listOf(
        "Ace",
        "Lucky",
        "Joker",
        "Wager",
        "Fluke",
        "Cubes",
        "Rolls",
        "Gambit",
        "Payout",
        "Bonesy",
    )

    private fun poolFor(playerCount: Int): List<String> = when (playerCount) {
        2 -> NAMES_2P
        3 -> NAMES_3P
        // 1-player mode never has an AI seat to name; 4 is the tightest and safest default for
        // any other value this is called with.
        else -> NAMES_4P
    }

    /** How many distinct names [playerCount]'s own pool has - the ceiling on [generateNames] for
     * that count, and what [AiNameGeneratorTest] sweeps. */
    fun poolSize(playerCount: Int): Int = poolFor(playerCount).size

    fun generateNames(count: Int, playerCount: Int, random: Random = Random.Default): List<String> {
        val pool = poolFor(playerCount)
        require(count in 0..pool.size) { "Cannot generate $count unique AI names from a pool of ${pool.size}" }
        return pool.shuffled(random).take(count)
    }
}
