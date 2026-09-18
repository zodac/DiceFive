package net.zodac.dicefive.game

import kotlin.random.Random

/** Generates flavourful, non-duplicate names for AI players at game start. */
object AiNameGenerator {

    private val NAME_POOL = listOf(
        "Snake Eyes",
        "Lucky Roller",
        "Sir Rolls-a-Lot",
        "Dizzy Dice",
        "The Probability Engine",
        "Bones McGraw",
        "Captain Chance",
        "Roll Model",
        "Five-of-a-Kind Fiona",
        "The Boxcar Bandit",
        "Yolanda Yahtzee",
        "Random Randy",
    )

    fun generateNames(count: Int, random: Random = Random.Default): List<String> {
        require(count in 0..NAME_POOL.size) { "Cannot generate $count unique AI names from a pool of ${NAME_POOL.size}" }
        return NAME_POOL.shuffled(random).take(count)
    }
}
