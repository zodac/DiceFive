package net.zodac.dicefive.model

import kotlin.random.Random
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.mode_hit_list
import net.zodac.dicefive.resources.mode_hit_list_description
import net.zodac.dicefive.resources.mode_quickfire
import net.zodac.dicefive.resources.mode_quickfire_description
import net.zodac.dicefive.resources.mode_standard
import net.zodac.dicefive.resources.mode_standard_description
import net.zodac.dicefive.resources.mode_stud
import net.zodac.dicefive.resources.mode_stud_description
import net.zodac.dicefive.resources.mode_third_wind
import net.zodac.dicefive.resources.mode_third_wind_description
import net.zodac.dicefive.resources.mode_tricolour
import net.zodac.dicefive.resources.mode_tricolour_description
import org.jetbrains.compose.resources.StringResource

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
 * Hit List's card: twelve targets, from the easiest (three numbers named, two any places) to the hardest, then the
 * Alibi. Points come from the odds of hitting the shape in a whole turn chasing it (three rolls, holding what matches):
 * about 67% for "abc··" down to 11% for "aabbc" - roughly inversely, rounded to fives, with a little extra on the
 * hardest. An exact hit's odds are the same for every shape with the same number of named places: 7.5% with two any
 * places, 3.2% with one, 1.3% with none.
 */
private val HIT_LIST_SHAPES = listOf(
    TargetShape("abc··", points = 10),
    TargetShape("abc··", points = 10),
    TargetShape("aab··", points = 15),
    TargetShape("aab··", points = 15),
    TargetShape("abcd·", points = 20),
    TargetShape("abcd·", points = 20),
    TargetShape("aabc·", points = 25),
    TargetShape("aabb·", points = 30),
    TargetShape("abcde", points = 40),
    TargetShape("abcde", points = 40),
    TargetShape("aabcd", points = 55),
    TargetShape("aabbc", points = 75),
)

private val HIT_LIST_CATEGORIES = ScoreCategory.TARGETS.take(HIT_LIST_SHAPES.size) + ScoreCategory.ALIBI

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
    val displayName: StringResource,
    /** One line for the setup screen, saying what's different about this mode. */
    val description: StringResource,
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
     * Boxes of [categories] that are off the card in every game of this mode - shown, but never open to score
     * in (see [PlayerState.disabledCategories]).
     */
    val disabledCategories: Set<ScoreCategory> = emptySet(),
    /**
     * How many more upper boxes of [categories] are switched off, drawn at random when a game starts.
     */
    val randomUpperDisabledCategories: Int = 0,
    /**
     * How many more lower boxes of [categories] (not [disabledCategories]) are switched off, drawn at random when a game starts.
     */
    val randomLowerDisabledCategories: Int = 0,
    /**
     * How many more boxes of [categories] (not [disabledCategories]) are switched off, drawn at random when a
     * game starts - see [drawDisabledCategories]. The same ones for every player of that game.
     */
    val randomDisabledCategories: Int = randomUpperDisabledCategories + randomLowerDisabledCategories,
    /**
     * The shapes of the targets a game of this mode draws, one per [ScoreCategory.TARGETS] box on [categories], in
     * order - see [drawHitList]. Empty in every mode without targets.
     */
    val hitListShapes: List<TargetShape> = emptyList(),
    /** Whether the Extended Scores modifier can be switched on for this mode - its boxes need a card they mean something on. */
    val allowsExtendedScores: Boolean = true,
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
        displayName = Res.string.mode_standard,
        description = Res.string.mode_standard_description,
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
        displayName = Res.string.mode_tricolour,
        description = Res.string.mode_tricolour_description,
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
        displayName = Res.string.mode_third_wind,
        description = Res.string.mode_third_wind_description,
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
        displayName = Res.string.mode_stud,
        description = Res.string.mode_stud_description,
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
     * Beyond the official rules: Standard's dice and three rolls, but none of its boxes. The card is twelve targets,
     * drawn fresh each game (the same for every player) from [HIT_LIST_SHAPES], and an Alibi. A target names a
     * number for each die's place, left to right, with up to two places any die fills. Every named number among the
     * dice, in any order, hits it for its points; every one in its own place is an exact hit, for double. Short of a hit,
     * a partial hit scores half the points times the share of its numbers rolled, rounded to the nearest 5 - once at
     * least two of them are rolled. The Alibi takes a hit in place of its target - the highest-scoring open target the
     * dice hit, never doubled - leaving the target open to try again; it takes no partial hits. The Extended Scores
     * modifier isn't allowed. Its scores don't go on the Leaderboard, since each game's card is different.
     *
     * Max score: every target an exact hit, `2*(10+10+15+15+20+20+25+30+40+40+55+75) = 710`, `+75` the Alibi taking
     * the 75-point target before its exact hit. `710+75 = 785`.
     *
     * Max rolls: twelve targets and the Alibi, 3 rolls each. `13*3 = 39`.
     */
    HIT_LIST(
        id = "hit_list",
        displayName = Res.string.mode_hit_list,
        description = Res.string.mode_hit_list_description,
        diceCount = 5,
        scoringDiceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = HIT_LIST_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 785,
        maxRollsPerGame = 39,
        countsOnLeaderboard = false,
        hitListShapes = HIT_LIST_SHAPES,
        allowsExtendedScores = false,
    ),
    /**
     * Beyond the official rules: Standard's dice, three rolls and scorecard, but every game starts with
     * seven of the thirteen boxes switched off - the 5x box always, and six more of the other twelve at
     * random. A disabled box can't be scored in, so a game is only the six turns left. Every other rule
     * applies, and the upper bonus is scaled to the upper boxes that remain: three of each number still
     * earns it (`3*(1+2+...)` over the enabled upper numbers, 63 with all six). With no upper box left there
     * is no bonus. Its scores don't go on the Leaderboard, since each game's card is different.
     *
     * Max score: the best six boxes there can be, with no 5x and so no joker or chips - Large Straight 40,
     * and five of the 30s (Sixes, 3x, 4x, Small Straight, Chance) = 190, `+35` the upper bonus, which five
     * 6s earn on their own (30 against a threshold of 18). `190+35 = 225`.
     *
     * Max rolls: six boxes, 3 rolls each. `6*3 = 18`.
     */
    QUICKFIRE(
        id = "quickfire",
        displayName = Res.string.mode_quickfire,
        description = Res.string.mode_quickfire_description,
        diceCount = 5,
        scoringDiceCount = 5,
        rollsPerTurn = 3,
        dieValues = 1..6,
        dieColours = emptyList(),
        categories = STANDARD_CATEGORIES,
        upperBonusThreshold = 63,
        upperBonusAmount = 35,
        fiveOfAKindBonusAmount = 100,
        maxPossibleScore = 225,
        maxRollsPerGame = 18,
        countsOnLeaderboard = false,
        disabledCategories = setOf(ScoreCategory.FIVE_OF_A_KIND),
        randomUpperDisabledCategories = 3,
        randomLowerDisabledCategories = 3,
    ),

    ;

    /**
     * The scorecard this mode plays with: [categories], plus the Extended Scores modifier's boxes after them
     * when [extendedScores] is on and the mode allows them ([allowsExtendedScores]). What a game's own card is read
     * from - never [categories] directly.
     */
    fun categoriesWith(extendedScores: Boolean): List<ScoreCategory> =
        if (extendedScores && allowsExtendedScores) categories + ScoreCategory.EXTENDED else categories

    /** How many boxes of [categories] are switched off in a game: [disabledCategories], and the random ones drawn. */
    val disabledCategoryCount: Int
        get() = disabledCategories.size + randomDisabledCategories

    /** How many turns each player takes in a game: one for every slot of every box that isn't switched off. */
    val turnsPerGame: Int
        get() = (categories.size - disabledCategoryCount) * scoresPerCategory

    /**
     * The boxes a new game of this mode switches off: [disabledCategories] and [randomDisabledCategories] more
     * picked from the rest of [categories] with [random]. Nothing is drawn from [random] in a mode without
     * random ones, so every other mode's seeded games come out as they always did.
     */
    fun drawDisabledCategories(random: Random): Set<ScoreCategory> {
        if (randomDisabledCategories == 0) return disabledCategories
        if (randomUpperDisabledCategories == 0 && randomLowerDisabledCategories == 0) {
            return disabledCategories + (categories - disabledCategories).shuffled(random).take(randomDisabledCategories)
        }
        val upperCandidates = categories.filter { it.section == ScoreSection.UPPER && it !in disabledCategories }
        val lowerCandidates = categories.filter { it.section == ScoreSection.LOWER && it !in disabledCategories }
        val chosenUpper = upperCandidates.shuffled(random).take(randomUpperDisabledCategories)
        val chosenLower = lowerCandidates.shuffled(random).take(randomLowerDisabledCategories)
        return disabledCategories + chosenUpper + chosenLower
    }

    /** Whether this mode's card is a list of targets (see [hitListShapes]). */
    val hasHitList: Boolean
        get() = hitListShapes.isNotEmpty()

    /**
     * The targets a new game of this mode deals every player: one drawn from each of [hitListShapes] with [random],
     * for each target box in order - none of them the same. Nothing is drawn from [random] in a mode without them, so
     * every other mode's seeded games come out as they always did.
     */
    fun drawHitList(random: Random): Map<ScoreCategory, HitTarget> {
        if (!hasHitList) return emptyMap()
        val drawn = mutableListOf<HitTarget>()
        for (shape in hitListShapes) {
            var target = HitTarget.draw(shape, random)
            while (drawn.any { it.places == target.places }) target = HitTarget.draw(shape, random)
            drawn += target
        }
        return categories.filter { it.isTarget }.zip(drawn).toMap()
    }

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
