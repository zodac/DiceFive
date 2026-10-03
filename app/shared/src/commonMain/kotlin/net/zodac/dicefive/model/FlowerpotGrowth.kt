package net.zodac.dicefive.model

/** The Flowerpot cup's plant fully grown: a sunflower in bloom - see [flowerpotGrowthStage]. */
const val FLOWERPOT_FULL_BLOOM = 4

/** The rolls per turn a mode must give for the Flowerpot's sunflower to bloom at all - see [growsSunflower]. */
const val SUNFLOWER_ROLLS_PER_TURN = 3

/**
 * Whether the Flowerpot's plant can bloom in this mode: only with [SUNFLOWER_ROLLS_PER_TURN] rolls
 * a turn (Standard, Tricolour). Quickfire's single roll is made for the player, so its plant grows
 * but never blooms.
 */
val GameMode.growsSunflower: Boolean
    get() = rollsPerTurn == SUNFLOWER_ROLLS_PER_TURN

/**
 * How far the Flowerpot cup's plant has grown after [rolls] rolls in this mode: 0 is bare soil, then a
 * seedling, a stalk with a bud, the bud opening, and at [FLOWERPOT_FULL_BLOOM] a sunflower in bloom.
 * The stages are spread evenly over every roll of the game ([GameMode.maxRollsPerGame]): stage `k`
 * comes at `ceil(k * maxRollsPerGame / 4)` rolls - 10, 20, 30 in Standard's 39; 13, 26, 39 in
 * Tricolour's 51. The bloom is never on that schedule's rounding: it comes only once [rolls] reaches
 * [GameMode.maxRollsPerGame] - the game's last possible roll - and only if [growsSunflower]; short
 * of it the plant stops at the bud opening. Purely cosmetic, except that player 1 bringing it into
 * bloom earns [Achievement.GREENFINGERS].
 */
fun GameMode.flowerpotGrowthStage(rolls: Int): Int = flowerpotGrowthStage(rolls, maxRollsPerGame, growsSunflower)

/** [GameMode.flowerpotGrowthStage]'s arithmetic, for a game of [maxRolls] rolls - split out so a test can try every length. */
internal fun flowerpotGrowthStage(rolls: Int, maxRolls: Int, canBloom: Boolean): Int {
    if (canBloom && rolls >= maxRolls) {
        return FLOWERPOT_FULL_BLOOM
    }
    // ceil(stage * maxRolls / 4) <= rolls  <=>  stage * maxRolls <= rolls * 4, in exact integer arithmetic.
    return (1 until FLOWERPOT_FULL_BLOOM).count { stage -> stage * maxRolls <= rolls * FLOWERPOT_FULL_BLOOM }
}

/** Whether this player's rolls have brought the Flowerpot's plant into bloom - see [flowerpotGrowthStage]. */
val PlayerState.hasGrownSunflower: Boolean
    get() = gameMode.flowerpotGrowthStage(rollCount) == FLOWERPOT_FULL_BLOOM
