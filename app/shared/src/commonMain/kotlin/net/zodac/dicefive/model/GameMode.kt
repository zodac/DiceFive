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
    /** How many dice are rolled. */
    val diceCount: Int,
    /**
     * How many dice a hand is scored with - what a 5x, a colour set or a straight is measured
     * against. The same as [diceCount] in a mode where every die scores. Fewer, and only the held dice
     * score: there are this many hold slots, and a turn can only be scored once every one is filled
     * (see [scoresHeldDiceOnly]).
     */
    val scoringDiceCount: Int,
    /** Rolls each turn allows, the first included. */
    val rollsPerTurn: Int,
    /** The number faces every die can land on. */
    val dieValues: IntRange,
    /**
     * The colours every die can land on, alongside its number, each equally likely. Empty when dice
     * have no colour. Either way they're drawn in the player's chosen dice style - a coloured die
     * recoloured in its colour (see `DiceStyle.recoloured`).
     */
    val dieColours: List<DieColour>,
    /** The scorecard, in display order. Each one is scored [scoresPerCategory] times, a turn each. */
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
     * The most rolls one player can make in a whole game: every roll of every turn, one turn per box.
     * Declared rather than computed, with the derivation on each entry, and `GameModeTest` plays a
     * game using every roll through the real engine to prove it. What the Flowerpot's plant grows
     * over, blooming on the last of them where the mode allows - see [flowerpotGrowthStage].
     */
    val maxRollsPerGame: Int,
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
    /**
     * How many times each category is scored - a box has this many slots, each filled by its own
     * turn, and the box's score is all of them added up. So a game lasts [categories] times this
     * many turns. See [PlayerState.scorecard].
     */
    val scoresPerCategory: Int = 1,
    /**
     * Whether a finished game's scores go on the Leaderboard - and so count towards a personal best
     * and the score-collection achievements, which are measured against it. Either way they're
     * recorded, and Statistics counts them.
     */
    val countsOnLeaderboard: Boolean = true,
    /**
     * Whether the roll modifiers (Number of Rolls, Stored Rolls - see [RollModifiers]) apply. A mode whose
     * rules are built on its rolls turns them off; the setup screen shows them locked and the game starts
     * with them off.
     */
    val allowsRollModifiers: Boolean = true,
) {

    /**
     * The official rules.
     *
     * Max score: `5*(1+2+3+4+5+6)` upper, maxed = 105, `+35` upper bonus, `+50` the 5x box itself,
     * `+30+30+25+30+40+30` the other six lower boxes maxed = 185, `+12*100` every one of the other 12
     * turns also being a 5x = 1200. `105+35+50+185+1200 = 1575`.
     *
     * Max rolls: 13 boxes, 3 rolls each. `13*3 = 39`.
     */
    STANDARD(
        id = "standard",
        displayName = "Standard",
        description = "The official rules",
        diceCount = 5,
        scoringDiceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = STANDARD_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 1575,
        maxRollsPerGame = 39,
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
     *
     * Max rolls: Standard's 13 boxes plus the 4 colour boxes, 3 rolls each. `17*3 = 51`.
     */
    TRICOLOUR(
        id = "tricolour",
        displayName = "Tricolour",
        description = "Dice also roll red, yellow or blue, with four colour boxes to fill",
        diceCount = 5,
        scoringDiceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE),
        categories = TRICOLOUR_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 2120,
        maxRollsPerGame = 51,
    ),

    /**
     * Beyond the official rules: Standard's dice and scorecard, but only one roll per turn - no holds,
     * no rerolls - against a fixed 10-second turn timer. Running out of time scores the roll in the
     * open category it's worth *least* in, rather than the first open one. The one roll is made
     * automatically as each turn starts.
     *
     * Max score: the same card and bonuses as Standard, so the same perfect game - a single roll can
     * still land five 6s. `1575`.
     *
     * Max rolls: Standard's 13 boxes, 1 roll each. `13*1 = 13`.
     */
    QUICKFIRE(
        id = "quickfire",
        displayName = "Quickfire",
        description = "One roll per turn, and 10 seconds to score it",
        diceCount = 5,
        scoringDiceCount = 5,
        rollsPerTurn = 1,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = STANDARD_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 1575,
        maxRollsPerGame = 13,
        turnTimerSeconds = 10,
        timeoutPick = TimeoutPick.LOWEST_SCORE,
        autoRollAtTurnStart = true,
        allowsRollModifiers = false,
    ),
    /**
     * Beyond the official rules: Standard's scorecard and three rolls, but seven dice are rolled
     * instead of five - and only the five held dice score. There are five hold slots, so at most
     * five dice can be held, and a turn can only be scored once all five are; the board previews what
     * the held dice would score as soon as one is.
     *
     * Max score: only five dice ever score, against Standard's card and bonuses, so the same perfect
     * game. `1575`.
     *
     * Max rolls: Standard's 13 boxes, 3 rolls each. `13*3 = 39`.
     */
    STUD(
        id = "stud",
        displayName = "Stud",
        description = "Roll seven dice, but only the five you hold score",
        diceCount = 7,
        scoringDiceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = STANDARD_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 1575,
        maxRollsPerGame = 39,
    ),

    /**
     * Beyond the official rules: Standard's dice, rolls and scorecard, but every category is scored
     * three times - three slots a box, each its own turn. The upper bonus is still a single bonus,
     * with both its threshold and its amount tripled (189 earns 105). The 5x box's three slots take
     * a 5x for 50 like any other box; the joker rule and its bonus chip only start once all three are
     * used and at least one holds 50. Its scores don't go on the Leaderboard.
     *
     * Max score: three of Standard's card - every box filled three times at its maximum. `3*105 = 315`
     * upper, `+105` bonus, `+3*50 = 150` the 5x box, `+3*185 = 555` the other six lower boxes, `+36*100`
     * every one of the other 36 turns also being a 5x = 3600. `315+105+150+555+3600 = 4725`.
     *
     * Max rolls: Standard's 13 boxes three times over, 3 rolls each. `13*3*3 = 117`.
     */
    THIRD_WIND(
        id = "third_wind",
        displayName = "Third Wind",
        description = "Every category is scored three times",
        diceCount = 5,
        scoringDiceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = STANDARD_CATEGORIES,
        upperBonusThreshold = 189,
        upperBonusAmount = 105,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 4725,
        maxRollsPerGame = 117,
        scoresPerCategory = 3,
        countsOnLeaderboard = false,
    ),
    ;

    /**
     * The scorecard this mode plays with: [categories], plus the Extended Scores modifier's boxes after them
     * when [extendedScores] is on. What a game's own card is read from - never [categories] directly.
     */
    fun categoriesWith(extendedScores: Boolean): List<ScoreCategory> =
        if (extendedScores) categories + ScoreCategory.EXTENDED else categories

    /** How many turns each player takes in a game: one for every slot of every category. */
    val turnsPerGame: Int
        get() = categories.size * scoresPerCategory

    /**
     * Whether only the held dice score: more dice are rolled than a hand is scored with
     * ([diceCount] over [scoringDiceCount]), so the player picks their hand by holding it.
     */
    val scoresHeldDiceOnly: Boolean
        get() = scoringDiceCount < diceCount

    companion object {
        val default: GameMode = STANDARD

        /** The widest any score can be, across every mode - what fixed-width score columns are sized to. */
        val HIGHEST_POSSIBLE_SCORE: Int = entries.maxOf { it.maxPossibleScore }

        fun fromId(id: String): GameMode? = entries.firstOrNull { it.id == id }
    }
}
