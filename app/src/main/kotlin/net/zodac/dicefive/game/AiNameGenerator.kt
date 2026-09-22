package net.zodac.dicefive.game

import kotlin.random.Random

/** Generates flavourful, non-duplicate names for AI players at game start. */
object AiNameGenerator {

    // Every name is within GameSetupState.MAX_PLAYER_NAME_LENGTH, which is what human names are
    // capped at: an AI is a player like any other in the in-game header, and a name that doesn't
    // fit there gets ellipsised regardless of who chose it. AiNameGeneratorTest guards this - the
    // longer, more florid originals ("The Probability Engine") were cut for exactly this reason.
    private val NAME_POOL = listOf(
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

    /** How many distinct names exist - the ceiling on [generateNames], and what its tests sweep. */
    val poolSize: Int get() = NAME_POOL.size

    fun generateNames(count: Int, random: Random = Random.Default): List<String> {
        require(count in 0..NAME_POOL.size) { "Cannot generate $count unique AI names from a pool of ${NAME_POOL.size}" }
        return NAME_POOL.shuffled(random).take(count)
    }
}
