package net.zodac.dicefive.model

private val STANDARD_CATEGORIES = listOf(
    ScoreCategory.ONES,
    ScoreCategory.TWOS,
    ScoreCategory.THREES,
    ScoreCategory.FOURS,
    ScoreCategory.FIVES,
    ScoreCategory.SIXES,
    ScoreCategory.THREE_OF_A_KIND,
    ScoreCategory.FOUR_OF_A_KIND,
    ScoreCategory.FULL_HOUSE,
    ScoreCategory.SMALL_STRAIGHT,
    ScoreCategory.LARGE_STRAIGHT,
    ScoreCategory.FIVE_OF_A_KIND,
    ScoreCategory.CHANCE,
)

private val TRICOLOUR_CATEGORIES = STANDARD_CATEGORIES + listOf(
    ScoreCategory.REDS,
    ScoreCategory.YELLOWS,
    ScoreCategory.BLUES,
    ScoreCategory.COLOURED_HOUSE,
)

/**
 * A set of rules a game can be played under, chosen on the setup screen and carried on [GameState]
 * (and each [PlayerState]) for the life of the game.
 *
 * **Everything that can differ between modes lives here**, even where every mode shipped so far
 * agrees on it - the dice, the rolls, the scorecard, the bonuses and the ceiling - so the engine, the
 * AI, the achievements and the board all read the rules from one place rather than from constants
 * scattered through them. A new mode is a new entry here, plus whatever genuinely new scoring rule it
 * brings (a new [ScoreCategory], say); nothing else should have to learn it exists.
 *
 * [id] is the stable storage key, written to settings and to a saved in-progress game, so **never
 * change one** - rename [displayName] instead. (The enum names have changed before: [STANDARD] was
 * once `CLASSIC`, which `GameStateJson` still reads.)
 */
enum class GameMode(
    val id: String,
    val displayName: String,
    /** One line for the setup screen, saying what's different about this mode. */
    val description: String,
    /** How many dice are rolled - and so how many a 5x, a colour set or a straight is measured against. */
    val diceCount: Int,
    /** Rolls each turn allows, the first included. */
    val rollsPerTurn: Int,
    /** The number faces every die can land on. */
    val dieValues: IntRange,
    /**
     * The colours every die can land on, alongside its number, each equally likely. Empty when dice
     * have no colour, in which case they're drawn in the player's chosen dice style instead - see
     * [usesPlayerDiceStyle].
     */
    val dieColours: List<DieColour>,
    /** The scorecard, in display order. One turn per category, so this is also the length of a game. */
    val categories: List<ScoreCategory>,
    /** The upper-section total that earns [upperBonusAmount]. */
    val upperBonusThreshold: Int,
    val upperBonusAmount: Int,
    /** The chip every 5x after the first earns under the joker rule - see `ScoreCalculator`. */
    val fiveOfAKindBonusAmount: Int,
    /**
     * The highest total these rules allow - a "perfect game", where every turn is a 5x and every box
     * holds its maximum. Declared rather than computed, with the derivation on each entry, and
     * `GameModeTest` plays that perfect game through the real engine to prove it.
     */
    val maxPossibleScore: Int,
    /**
     * A per-turn time limit the rules themselves set, in seconds, replacing whatever the setup
     * screen's Turn Timer is set to (that row is disabled while such a mode is picked). Null leaves
     * the timer to the player - see [GameState.turnSeconds].
     */
    val turnTimerSeconds: Int? = null,
    /** Which open category a turn that runs out of time is scored in - see [TimeoutPick]. */
    val timeoutPick: TimeoutPick = TimeoutPick.FIRST_OPEN,
    /**
     * Whether a human's turn starts with the cup tapped for them - the game screen makes the same
     * tap a player would (see [GameState.awaitsAutoRoll]), so the roll is indistinguishable from
     * one they made themselves. An AI's turn needs nothing: its turn loop starts with a roll in
     * every mode.
     */
    val autoRollAtTurnStart: Boolean = false,
) {
    /**
     * The official rules.
     *
     * Max score: `5*(1+2+3+4+5+6)` upper, maxed = 105, `+35` upper bonus, `+50` the 5x box itself,
     * `+30+30+25+30+40+30` the other six lower boxes maxed = 185, `+12*100` every one of the other 12
     * turns also being a 5x = 1200. `105+35+50+185+1200 = 1575`.
     */
    STANDARD(
        id = "standard",
        displayName = "Standard",
        description = "The official rules",
        diceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = STANDARD_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 1575,
    ),

    /**
     * Beyond the official rules: every die also lands red, yellow or blue, and four colour boxes join
     * the card - five of one colour (40 each) and a coloured house (25, and a joker free-fill like
     * Full House).
     *
     * Max score: Standard's 105 upper `+35` bonus `+50` 5x box `+185` other lower boxes, `+40+40+40`
     * the three single-colour boxes (five 6s all one colour - a 5x and a colour set at once) `+25` the
     * coloured house, `+16*100` every one of the other 16 turns also being a 5x = 1600.
     * `105+35+50+185+120+25+1600 = 2120`.
     */
    TRICOLOUR(
        id = "tricolour",
        displayName = "Tricolour",
        description = "Dice also roll red, yellow or blue, with four colour boxes to fill",
        diceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE),
        categories = TRICOLOUR_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 2120,
    ),

    /**
     * Beyond the official rules: Standard's dice and scorecard, but only one roll per turn - no holds,
     * no rerolls - against a fixed 10-second turn timer. Running out of time scores the roll in the
     * open category it's worth *least* in, rather than the first open one. The one roll is made
     * automatically as each turn starts.
     *
     * Max score: the same card and bonuses as Standard, so the same perfect game - a single roll can
     * still land five 6s. `1575`.
     */
    QUICKFIRE(
        id = "quickfire",
        displayName = "Quickfire",
        description = "One roll per turn, and 10 seconds to score it",
        diceCount = 5,
        rollsPerTurn = 1,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = STANDARD_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 1575,
        turnTimerSeconds = 10,
        timeoutPick = TimeoutPick.LOWEST_SCORE,
        autoRollAtTurnStart = true,
    ),
    ;

    /** Whether dice are drawn in the player's own dice style (the Styles screen) - only when the rules
     * don't give them a colour of their own to show instead. */
    val usesPlayerDiceStyle: Boolean
        get() = dieColours.isEmpty()

    companion object {
        val default: GameMode = STANDARD

        /** The widest any score can be, across every mode - what fixed-width score columns are sized to. */
        val HIGHEST_POSSIBLE_SCORE: Int = entries.maxOf { it.maxPossibleScore }

        fun fromId(id: String): GameMode? = entries.firstOrNull { it.id == id }
    }
}
