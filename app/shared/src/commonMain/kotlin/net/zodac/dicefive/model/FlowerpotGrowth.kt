package net.zodac.dicefive.model

/** The Flowerpot cup's plant fully grown: a sunflower in bloom - see [flowerpotGrowthStage]. */
const val FLOWERPOT_FULL_BLOOM = 4

/** How many rolls grow the Flowerpot's plant into each stage short of full bloom, in every mode: the seedling, the bud, the bud opening. */
private val FLOWERPOT_STAGE_ROLLS = listOf(10, 20, 30)

/** The fewest rolls the Flowerpot's sunflower ever blooms on: every roll of a Standard game. */
const val SUNFLOWER_MIN_ROLLS = 39

/**
 * How many rolls bring the Flowerpot's plant into bloom in this mode: every roll of the game
 * ([GameMode.maxRollsPerGame]), but never fewer than [SUNFLOWER_MIN_ROLLS]. So a longer game
 * (Tricolour's 51 rolls) blooms only on its own last roll, and a shorter one (Quickfire's 13) never
 * blooms at all.
 */
val GameMode.rollsToBloom: Int
    get() = maxOf(SUNFLOWER_MIN_ROLLS, maxRollsPerGame)

/**
 * How far the Flowerpot cup's plant has grown after [rolls] rolls in this mode: 0 is bare soil, then
 * a seedling at 10 rolls, a stalk with a bud at 20, the bud opening at 30 - the same in every mode -
 * and at [FLOWERPOT_FULL_BLOOM] a sunflower in bloom, on [rollsToBloom]. Purely cosmetic, except that
 * player 1 bringing it into bloom earns [Achievement.GREENFINGERS].
 */
fun GameMode.flowerpotGrowthStage(rolls: Int): Int =
    if (rolls >= rollsToBloom) FLOWERPOT_FULL_BLOOM else FLOWERPOT_STAGE_ROLLS.count { rolls >= it }

/** Whether this player's rolls have brought the Flowerpot's plant into bloom - see [flowerpotGrowthStage]. */
val PlayerState.hasGrownSunflower: Boolean
    get() = gameMode.flowerpotGrowthStage(rollCount) == FLOWERPOT_FULL_BLOOM
