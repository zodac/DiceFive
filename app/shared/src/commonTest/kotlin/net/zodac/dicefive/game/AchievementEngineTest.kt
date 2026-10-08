package net.zodac.dicefive.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.ScoreSection
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.oneScoreEach

private const val NOW = 1_700_000_000_000L

class AchievementEngineTest {

    /**
     * A finished scorecard totalling [total], built entirely out of CHANCE-style filler so a test
     * only has to state the categories it actually cares about.
     */
    private fun player(
        name: String = "Player 1",
        type: PlayerType = PlayerType.HUMAN,
        total: Int = 150,
        fiveOfAKindBonusCount: Int = 0,
        difficulty: Difficulty = Difficulty.MEDIUM,
        overrides: Map<ScoreCategory, Int> = emptyMap(),
        gameMode: GameMode = GameMode.STANDARD,
    ): PlayerState {
        val filled = gameMode.categories.associateWith { 0 } + overrides
        val bonusTotal = fiveOfAKindBonusCount * gameMode.fiveOfAKindBonusAmount
        // Whatever the overrides didn't account for is parked in CHANCE to hit the asked-for total.
        val accountedFor = filled.filterKeys { it != ScoreCategory.CHANCE }.values.sum() + bonusTotal
        val scorecard = filled + (ScoreCategory.CHANCE to (total - accountedFor))
        return PlayerState(
            name = name,
            type = type,
            difficulty = difficulty,
            gameMode = gameMode,
            scorecard = oneScoreEach(scorecard),
            fiveOfAKindBonusCount = fiveOfAKindBonusCount,
        )
    }

    private fun bot(total: Int, gameMode: GameMode = GameMode.STANDARD, fiveOfAKindBonusCount: Int = 0) =
        player(name = "Bot", type = PlayerType.AI, total = total, gameMode = gameMode, fiveOfAKindBonusCount = fiveOfAKindBonusCount)

    private fun finishedGame(vararg players: PlayerState) =
        GameState(gameMode = players.first().gameMode, players = players.toList(), isGameOver = true)

    private fun evaluate(
        state: GameState,
        context: GameAchievementContext = GameAchievementContext(),
        before: AchievementsState = AchievementsState(),
    ) = AchievementEngine.evaluate(state, context, before, NOW)

    /** Everything a finished [game] unlocks. */
    private fun unlocked(game: GameState, context: GameAchievementContext = GameAchievementContext()) = evaluate(game, context).newlyUnlocked

    /** A partially filled card: only the listed categories are scored, the rest still open. */
    private fun midGamePlayer(
        scored: Map<ScoreCategory, Int>,
        fiveOfAKindBonusCount: Int = 0,
        type: PlayerType = PlayerType.HUMAN,
        gameMode: GameMode = GameMode.STANDARD,
    ) = PlayerState(
        name = "Player 1",
        type = type,
        gameMode = gameMode,
        scorecard = oneScoreEach(gameMode.categories.associateWith { null } + scored),
        fiveOfAKindBonusCount = fiveOfAKindBonusCount,
    )

    private fun inProgress(vararg players: PlayerState) =
        GameState(gameMode = players.first().gameMode, players = players.toList(), isGameOver = false)

    /** What the mid-game pass unlocks for [players] part-way through a game. */
    private fun unlockedMidGame(vararg players: PlayerState) =
        AchievementEngine.evaluateInProgress(inProgress(*players), AchievementsState(), NOW).newlyUnlocked

    // ---- Who wins, and who earns ----------------------------------------------------------------

    @Test
    fun `the result at the table - played - won or lost - the win streak and only player 1 earning`() {
        // A finished solo game counts as played but neither won nor lost, and neither extends nor breaks a streak.
        val solo = evaluate(finishedGame(player(total = 150)))
        assertEquals(1, solo.counters[AchievementCounter.GAMES_PLAYED])
        assertEquals(0, solo.counters[AchievementCounter.GAMES_WON])
        assertTrue(Achievement.SOLO_GAME in solo.newlyUnlocked)
        assertFalse(Achievement.FIRST_WIN in solo.newlyUnlocked)
        val soloOnAStreak = evaluate(finishedGame(player()), before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 2)))
        assertEquals(2, soloOnAStreak.counters[AchievementCounter.WIN_STREAK])

        // Beating an AI wins the game and extends the streak.
        val beatBot = evaluate(finishedGame(player(total = 200), bot(total = 180)))
        assertEquals(1, beatBot.counters[AchievementCounter.GAMES_WON])
        assertEquals(1, beatBot.counters[AchievementCounter.WIN_STREAK])
        assertTrue(Achievement.FIRST_WIN in beatBot.newlyUnlocked)
        assertFalse(Achievement.I_ROBOT in beatBot.newlyUnlocked)

        // Losing to an AI is I Robot and resets the streak.
        val lostToBot = evaluate(
            finishedGame(player(total = 120), bot(total = 300)),
            before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 4)),
        )
        assertEquals(0, lostToBot.counters[AchievementCounter.WIN_STREAK])
        assertEquals(0, lostToBot.counters[AchievementCounter.GAMES_WON])
        assertTrue(Achievement.I_ROBOT in lostToBot.newlyUnlocked)

        // Player 2 (the second player passed in - never player 1) wins big; player 1 barely scores. And the winner is a
        // second human, not an AI - so it's not I Robot either, which is specifically about an AI.
        val lostToHuman = evaluate(finishedGame(player(name = "P1", total = 60), player(name = "P2", total = 500)))
        assertFalse(Achievement.FIRST_WIN in lostToHuman.newlyUnlocked, "player 2 winning must not earn player 1 a win")
        assertFalse(Achievement.SCORE_300 in lostToHuman.newlyUnlocked, "player 2's score must not earn player 1 Sharpshooter")
        assertEquals(0, lostToHuman.counters[AchievementCounter.GAMES_WON])
        assertFalse(Achievement.I_ROBOT in evaluate(finishedGame(player(name = "P1", total = 120), player(name = "P2", total = 300))).newlyUnlocked)

        // Only player 1's own feats and scorecard count: player 1 under 100 with no 5x, player 2 over 300 with a real 5x.
        val secondHumansFeats = evaluate(
            finishedGame(player(name = "Alice", total = 90), player(name = "Bob", total = 320, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50))),
        )
        assertFalse(Achievement.SCORE_300 in secondHumansFeats.newlyUnlocked)
        assertFalse(Achievement.FIRST_5X in secondHumansFeats.newlyUnlocked)
        assertTrue(Achievement.SCORE_UNDER_100 in secondHumansFeats.newlyUnlocked)

        // Player 1 outscores the weaker bot, but the strongest one still finished on top overall - "win" means
        // finishing 1st across the whole table, not merely ahead of *someone*.
        val thirdAhead = evaluate(
            finishedGame(
                player(total = 200),
                player(name = "Weak Bot", type = PlayerType.AI, total = 150),
                player(name = "Strong Bot", type = PlayerType.AI, total = 400),
            ),
        )
        assertFalse(Achievement.FIRST_WIN in thirdAhead.newlyUnlocked, "player 1 didn't actually finish 1st, so this must not count as a win")
        assertEquals(0, thirdAhead.counters[AchievementCounter.GAMES_WON])
    }

    @Test
    fun `margins and ties - Landslide - Photo Finish - Pipped To The Post and Tie Break`() {
        val landslide = evaluate(finishedGame(player(total = 300), bot(total = 150)))
        val photoFinish = evaluate(finishedGame(player(total = 201), bot(total = 200)))
        assertTrue(Achievement.WIN_BY_100 in landslide.newlyUnlocked)
        assertFalse(Achievement.WIN_BY_5 in landslide.newlyUnlocked)
        assertTrue(Achievement.WIN_BY_5 in photoFinish.newlyUnlocked)
        assertFalse(Achievement.WIN_BY_100 in photoFinish.newlyUnlocked)
        assertFalse(Achievement.WIN_BY_5 in unlocked(finishedGame(player(total = 202), bot(total = 200))), "winning by 2 is not a photo finish")

        // A tie at the top counts as a win for the human.
        val tie = evaluate(finishedGame(player(total = 200), bot(total = 200)))
        assertTrue(Achievement.FIRST_WIN in tie.newlyUnlocked)
        // A tie is a 0-point margin, not a 1-point one - Photo Finish is exactly 1, no more no less.
        assertFalse(Achievement.WIN_BY_5 in tie.newlyUnlocked)
        assertFalse(Achievement.I_ROBOT in tie.newlyUnlocked)
        // Identical scorecards either side - the house rule has nothing to break here, so this is still a true,
        // unbroken tie, not a Tie Break win.
        assertFalse(Achievement.TIE_BREAK in tie.newlyUnlocked)

        // Matching the top score with fewer 5x wins Tie Break.
        val tieBreakWon = unlocked(finishedGame(player(total = 200, fiveOfAKindBonusCount = 0), bot(total = 200, fiveOfAKindBonusCount = 1)))
        assertTrue(Achievement.FIRST_WIN in tieBreakWon)
        assertTrue(Achievement.TIE_BREAK in tieBreakWon)
        // Losing the tie-break is losing, full stop - a raw-score tie the house rule then decides against player 1
        // must not count as a win anywhere, FIRST_WIN included.
        val tieBreakLost = evaluate(finishedGame(player(total = 200, fiveOfAKindBonusCount = 1), bot(total = 200, fiveOfAKindBonusCount = 0)))
        assertFalse(Achievement.FIRST_WIN in tieBreakLost.newlyUnlocked, "the bot won the tie-break, not player 1")
        assertFalse(Achievement.TIE_BREAK in tieBreakLost.newlyUnlocked, "the bot won the tie-break, not player 1")
        assertEquals(0, tieBreakLost.counters[AchievementCounter.GAMES_WON])
        // Tie Break needs an opponent to tie with.
        assertFalse(Achievement.TIE_BREAK in unlocked(finishedGame(player(total = 200, fiveOfAKindBonusCount = 0))))

        // Losing by exactly one point is Pipped to the Post - losing by more is not.
        assertTrue(Achievement.PIPPED_TO_THE_POST in unlocked(finishedGame(player(total = 199), bot(total = 200))))
        assertFalse(Achievement.PIPPED_TO_THE_POST in unlocked(finishedGame(player(total = 195), bot(total = 200))))
    }

    @Test
    fun `three Hard AI opponents are needed for I Robot`() {
        fun table(difficulty: Difficulty) = finishedGame(
            player(total = 300),
            player(name = "Bot 1", type = PlayerType.AI, total = 100, difficulty = difficulty),
            player(name = "Bot 2", type = PlayerType.AI, total = 100, difficulty = difficulty),
            player(name = "Bot 3", type = PlayerType.AI, total = 100, difficulty = difficulty),
        )

        val medium = evaluate(table(Difficulty.MEDIUM))
        val hard = evaluate(table(Difficulty.HARD))

        assertTrue(Achievement.BEAT_THREE_AI in medium.newlyUnlocked)
        assertFalse(Achievement.NATURAL_INTELLIGENCE in medium.newlyUnlocked)
        assertTrue(Achievement.NATURAL_INTELLIGENCE in hard.newlyUnlocked)
        assertTrue(Achievement.FULL_TABLE in hard.newlyUnlocked)
    }

    // ---- The scorecard ----------------------------------------------------------------------------

    @Test
    fun `scorecard feats - 5x - a scratched 5x - Spotless and the all-zero and low-score achievements`() {
        // 5x counts the box and every bonus chip after it.
        val fiveX = evaluate(finishedGame(player(total = 400, fiveOfAKindBonusCount = 2, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50))))
        assertEquals(3, fiveX.counters[AchievementCounter.SCORED_5X])
        assertTrue(Achievement.FIRST_5X in fiveX.newlyUnlocked)
        assertTrue(Achievement.ENCORE_5X in fiveX.newlyUnlocked)
        assertTrue(Achievement.HAT_TRICK_5X in fiveX.newlyUnlocked)
        assertTrue(Achievement.SCORE_400 in fiveX.newlyUnlocked)

        val scratched = unlocked(finishedGame(player(total = 120, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 0))))
        assertTrue(Achievement.SCRATCHED_5X in scratched)
        assertFalse(Achievement.FIRST_5X in scratched)
        assertFalse(Achievement.NO_ZEROES in scratched)

        // The lowest score the rules allow earns every bad-game achievement at once - and zeroes everything but Chance.
        val lowest = unlocked(finishedGame(player(total = 5)))
        assertTrue(Achievement.EXTREME_LOW_ROLLS in lowest)
        assertTrue(Achievement.LOW_ROLLS in lowest)
        assertTrue(Achievement.SCORE_UNDER_100 in lowest)
        assertTrue(Achievement.ALL_ZEROES in lowest)
        assertFalse(Achievement.SCORE_200 in lowest)

        // Zeroing everything but Chance is its own achievement: Chance is the one box that cannot be zeroed.
        assertTrue(Achievement.ALL_ZEROES in unlocked(finishedGame(player(total = 30))))
        assertFalse(Achievement.ALL_ZEROES in unlocked(finishedGame(player(total = 35, overrides = mapOf(ScoreCategory.FIVES to 5)))))

        // Spotless and How Do You Play This Game are mutually exclusive.
        val spotless = unlocked(finishedGame(player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 })))
        assertTrue(Achievement.NO_ZEROES in spotless)
        assertFalse(Achievement.ALL_ZEROES in spotless)

        // Low Rolls is a range but Extreme Low Rolls is exactly 5.
        val nineteen = unlocked(finishedGame(player(total = 19)))
        assertTrue(Achievement.LOW_ROLLS in nineteen)
        assertFalse(Achievement.EXTREME_LOW_ROLLS in nineteen)
        assertFalse(Achievement.LOW_ROLLS in unlocked(finishedGame(player(total = 20))))
    }

    @Test
    fun `exact and threshold totals - Ton - Nice - Solid Round - Sharpshooter and Exact Change`() {
        assertTrue(Achievement.TON in unlocked(finishedGame(player(total = 100))))
        assertFalse(Achievement.TON in unlocked(finishedGame(player(total = 101))))
        assertFalse(Achievement.TON in unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.CHANCE to 100))), "a running total can still climb past 100")

        assertTrue(Achievement.NICE in unlocked(finishedGame(player(total = 69))))
        assertFalse(Achievement.NICE in unlocked(finishedGame(player(total = 70))))
        assertFalse(Achievement.NICE in unlocked(finishedGame(player(total = 68))))

        assertTrue(Achievement.SCORE_200 in unlocked(finishedGame(player(total = 200))), "200 should unlock Solid Round")
        assertTrue(Achievement.SCORE_200 in unlocked(finishedGame(player(total = 250))), "250 is over 200, so it should unlock Solid Round")
        assertTrue(Achievement.SCORE_300 in unlocked(finishedGame(player(total = 300))), "300 should unlock Sharpshooter")

        // Exact Change needs every upper box to hold precisely its own pip count; one box over its target - two 2s in
        // Twos instead of one - doesn't count.
        val upper = listOf(ScoreCategory.ONES, ScoreCategory.TWOS, ScoreCategory.THREES, ScoreCategory.FOURS, ScoreCategory.FIVES, ScoreCategory.SIXES)
        val exact = upper.withIndex().associate { (index, category) -> category to index + 1 }
        assertTrue(Achievement.EXACT_CHANGE in unlocked(finishedGame(player(total = 21, overrides = exact))))
        assertFalse(Achievement.EXACT_CHANGE in unlocked(finishedGame(player(total = 22, overrides = exact + (ScoreCategory.TWOS to 4)))))
    }

    @Test
    fun `Lower Class reads the lower section without the 5x box - and lands mid-game`() {
        val lower = mapOf(
            ScoreCategory.THREE_OF_A_KIND to 25,
            ScoreCategory.FOUR_OF_A_KIND to 25,
            ScoreCategory.FULL_HOUSE to 25,
            ScoreCategory.SMALL_STRAIGHT to 30,
            ScoreCategory.LARGE_STRAIGHT to 40,
            ScoreCategory.CHANCE to 25,
        )
        // 170 in the lower boxes, and nothing later can take it away.
        val update = unlockedMidGame(midGamePlayer(lower))
        assertTrue(Achievement.LOWER_150 in update)
        assertFalse(Achievement.UPPER_84 in update)

        // 140 without the 5x - a scored 5x on top would cross 150 in the raw lower section total, but must not count
        // towards this achievement.
        val withFiveX = mapOf(
            ScoreCategory.THREE_OF_A_KIND to 25,
            ScoreCategory.FOUR_OF_A_KIND to 25,
            ScoreCategory.FULL_HOUSE to 25,
            ScoreCategory.SMALL_STRAIGHT to 30,
            ScoreCategory.CHANCE to 35,
            ScoreCategory.FIVE_OF_A_KIND to 50,
        )
        assertFalse(Achievement.LOWER_150 in unlockedMidGame(midGamePlayer(withFiveX)))
    }

    @Test
    fun `how the game went - Personal Best - Comeback Kid - Jaws Of Victory - Zero To Hero - Impatient and Naturally Gifted`() {
        val state = finishedGame(player(total = 250))
        assertTrue(Achievement.PERSONAL_BEST in unlocked(state, GameAchievementContext(previousBestScore = 240)))
        assertFalse(Achievement.PERSONAL_BEST in unlocked(state, GameAchievementContext(previousBestScore = 260)))
        assertFalse(Achievement.PERSONAL_BEST in unlocked(state, GameAchievementContext(previousBestScore = null)))

        // Comeback Kid needs both the trailing position and the win; Jaws Of Victory both the lead and the loss.
        val won = finishedGame(player(total = 200), bot(total = 190))
        val lost = finishedGame(player(total = 180), bot(total = 190))
        val trailed = GameAchievementContext(trailedIntoFinalRound = true)
        val led = GameAchievementContext(ledIntoFinalRound = true)
        assertTrue(Achievement.COMEBACK in unlocked(won, trailed))
        assertFalse(Achievement.COMEBACK in unlocked(lost, trailed))
        assertFalse(Achievement.COMEBACK in unlocked(won))
        assertTrue(Achievement.JAWS_OF_VICTORY in unlocked(lost, led))
        assertFalse(Achievement.JAWS_OF_VICTORY in unlocked(won, led))
        assertFalse(Achievement.JAWS_OF_VICTORY in unlocked(lost))

        // Winning with at least three zeroes on the winning scorecard is Zero to Hero. player()'s own defaults are 0
        // for anything not overridden and not Chance, so every OTHER category needs an explicit non-zero override here
        // - otherwise both scorecards below would already be all zeroes except Chance.
        val nonZeroElsewhere = mapOf(
            ScoreCategory.FOURS to 4,
            ScoreCategory.FIVES to 5,
            ScoreCategory.SIXES to 6,
            ScoreCategory.THREE_OF_A_KIND to 10,
            ScoreCategory.FOUR_OF_A_KIND to 10,
            ScoreCategory.FULL_HOUSE to 25,
            ScoreCategory.SMALL_STRAIGHT to 30,
            ScoreCategory.LARGE_STRAIGHT to 40,
            ScoreCategory.FIVE_OF_A_KIND to 50,
        )
        fun zeroToHero(threes: Int) = Achievement.ZERO_TO_HERO in unlocked(
            finishedGame(player(total = 200, overrides = nonZeroElsewhere + mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to threes)), bot(total = 150)),
        )
        assertTrue(zeroToHero(threes = 0))
        assertFalse(zeroToHero(threes = 3))

        // Playing first-roll-only unlocks Impatient - and Naturally Gifted if player 1 also won.
        val wonBig = finishedGame(player(total = 200), bot(total = 150))
        val lostBig = finishedGame(player(total = 100), bot(total = 150))
        val wonFirstRollOnly = unlocked(wonBig, GameAchievementContext(playerOneTookExtraRoll = false))
        val wonWithExtraRolls = unlocked(wonBig, GameAchievementContext(playerOneTookExtraRoll = true))
        val lostFirstRollOnly = unlocked(lostBig, GameAchievementContext(playerOneTookExtraRoll = false))
        assertTrue(Achievement.IMPATIENT in wonFirstRollOnly)
        assertTrue(Achievement.NATURALLY_GIFTED in wonFirstRollOnly)
        assertFalse(Achievement.IMPATIENT in wonWithExtraRolls)
        assertFalse(Achievement.NATURALLY_GIFTED in wonWithExtraRolls)
        assertTrue(Achievement.IMPATIENT in lostFirstRollOnly, "first-roll-only but lost - Impatient still applies")
        assertFalse(Achievement.NATURALLY_GIFTED in lostFirstRollOnly, "first-roll-only but lost - not a win")

        // Superuser mode used to disqualify a game outright. It no longer does: the cheat is gated on a debug build,
        // so the rule protected nobody, and it made the one tool best placed to test achievements useless for testing them.
        val handSet = evaluate(finishedGame(player(total = 500, fiveOfAKindBonusCount = 3)))
        assertTrue(Achievement.SCORE_500 in handSet.newlyUnlocked)
        assertEquals(1, handSet.counters[AchievementCounter.GAMES_PLAYED])
    }

    @Test
    fun `The Solution needs a solo standard game as Phil Woodward - matched ignoring case and spacing - on exactly 255`() {
        fun earned(state: GameState) = Achievement.THE_SOLUTION in unlocked(state)

        assertTrue(net.zodac.dicefive.model.isPhilWoodward("Phil Woodward"))
        assertTrue(net.zodac.dicefive.model.isPhilWoodward(" PHIL\tWoodward "))
        assertFalse(net.zodac.dicefive.model.isPhilWoodward("PhilWoodward"))

        assertTrue(earned(finishedGame(player(name = "Phil Woodward", total = 255))))
        assertTrue(earned(finishedGame(player(name = "  phil   WOODWARD ", total = 255))))
        assertFalse(earned(finishedGame(player(name = "Phil Woodward", total = 254))))
        assertFalse(earned(finishedGame(player(name = "Phil Woodward", total = 256))))
        assertFalse(earned(finishedGame(player(name = "Phil Woodwards", total = 300))))
        assertFalse(earned(finishedGame(player(name = "Player 1", total = 300))))
        assertFalse(earned(finishedGame(player(name = "Phil Woodward", total = 300), player(name = "P2", total = 100))))
        assertFalse(earned(finishedGame(player(name = "Phil Woodward", total = 300, gameMode = GameMode.TRICOLOUR))))
        // Any modifier rules it out.
        val phil = finishedGame(player(name = "Phil Woodward", total = 255))
        assertFalse(earned(phil.copy(turnTimer = TurnTimer.SECONDS_30)))
        assertFalse(earned(phil.copy(rollModifiers = RollModifiers(rollsPerTurn = 5))))
        assertFalse(earned(phil.copy(rollModifiers = RollModifiers(storedRolls = true))))
        assertFalse(earned(phil.copy(extendedScores = true)))
        assertFalse(earned(phil.copy(unluckyDice = UnluckyDice())))
    }

    // ---- The list itself ----------------------------------------------------------------------------

    @Test
    fun `the achievements list - unique ids - one unbroken run per category and secrecy kept to the Easter Eggs`() {
        // The ids are the storage and Play Games keys.
        val ids = Achievement.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        // Declaration order is display order, so a new entry appended to the end of the enum instead of filed into its
        // theme would silently split that theme across the list.
        assertEquals(AchievementCategory.entries.toList(), Achievement.entries.map { it.category }.distinct())

        // The Easter Eggs are the only secret achievements - and none gates Completionist. Every other achievement
        // stays at least title-visible from the start: secrecy is the exception, not the rule.
        val secret = listOf(
            Achievement.BIG_FAN,
            Achievement.GREENFINGERS,
            Achievement.LUCK_OF_THE_IRISH,
            Achievement.NOT_THOSE_DICE,
            Achievement.SHAKEN_NOT_TAPPED,
            Achievement.MAGICIANS_SECRET,
            Achievement.THE_SOLUTION,
        )
        assertEquals(secret, Achievement.entries.filter { it.visibility == AchievementVisibility.SECRET })
        secret.forEach { assertFalse(it in Achievement.COMPLETION_REQUIREMENTS, "$it gates Completionist") }
        // I Robot counts toward Completionist - Completionist does not count toward itself.
        assertTrue(Achievement.NATURAL_INTELLIGENCE in Achievement.COMPLETION_REQUIREMENTS)
        assertFalse(Achievement.COMPLETIONIST in Achievement.COMPLETION_REQUIREMENTS)

        // Miscellaneous is exclusive with hidden visibility, and Easter Eggs with secret, in both directions - see the
        // class doc on [Achievement]. Guards against a new hidden or secret achievement being filed under its subject's
        // usual category (which for a secret one would defeat [AchievementCategory.EASTER_EGGS]'s whole point: its
        // header only ever appearing once something in it is unlocked), or a normal achievement being left in either.
        Achievement.entries.forEach { achievement ->
            val where = "${achievement.name}: category=${achievement.category}, visibility=${achievement.visibility}"
            assertEquals(achievement.category == AchievementCategory.MISCELLANEOUS, achievement.visibility == AchievementVisibility.HIDDEN, where)
            assertEquals(achievement.category == AchievementCategory.EASTER_EGGS, achievement.visibility == AchievementVisibility.SECRET, where)
        }
    }

    @Test
    fun `the achievements list - the score ladder - the first-roll feats and the Game Modes are each in order`() {
        assertEquals(
            listOf(
                Achievement.PERSONAL_BEST,
                Achievement.TON,
                Achievement.SCORE_200,
                Achievement.SCORE_300,
                Achievement.SCORE_400,
                Achievement.SCORE_500,
            ),
            Achievement.entries.filter { it.category == AchievementCategory.SCORING },
        )

        // The three first-roll feats are listed together - least unlikely first - adjacent, not merely in order.
        val misc = Achievement.entries.filter { it.category == AchievementCategory.MISCELLANEOUS }
        assertEquals(
            listOf(Achievement.FIRST_ROLL_FULL_HOUSE, Achievement.FIRST_ROLL_LARGE_STRAIGHT, Achievement.FIRST_ROLL_5X),
            misc.filter { it.id.contains("first_roll") },
        )
        assertEquals(1, misc.indexOf(Achievement.FIRST_ROLL_LARGE_STRAIGHT) - misc.indexOf(Achievement.FIRST_ROLL_FULL_HOUSE))
        assertEquals(1, misc.indexOf(Achievement.FIRST_ROLL_5X) - misc.indexOf(Achievement.FIRST_ROLL_LARGE_STRAIGHT))

        // The Game Modes achievements are ordered and count towards Completionist.
        val gameModes = Achievement.entries.filter { it.category == AchievementCategory.GAME_MODES }
        assertEquals(
            listOf(
                Achievement.NON_STANDARD_MODE,
                Achievement.TRICOLOUR_WIN,
                Achievement.TRICOLOUR_ALL_COLOURS,
                Achievement.QUICKFIRE_WIN,
                Achievement.QUICKFIRE_SCORE,
                Achievement.STUD_WIN,
                Achievement.STUD_LUCKY_SEVEN,
                Achievement.THIRD_WIND_WIN,
                Achievement.THIRD_WIND_NO_ZEROES,
                Achievement.HIT_LIST_WIN,
                Achievement.HIT_LIST_RIGHT_ON_TARGET,
            ),
            gameModes,
        )
        assertTrue(gameModes.all { it in Achievement.COMPLETION_REQUIREMENTS })
    }

    // ---- Unlocking, counters and progress ---------------------------------------------------------

    @Test
    fun `an unlock happens once - counters accumulate and Completionist unlocks with the last of its requirements`() {
        // An already-unlocked achievement is not unlocked again - and the check is per-achievement, not a blanket freeze:
        // a still-locked one the same game earns still unlocks.
        val alreadyUnlocked = evaluate(finishedGame(player()), before = AchievementsState(unlockedAt = mapOf(Achievement.SOLO_GAME to 1L)))
        assertFalse(Achievement.SOLO_GAME in alreadyUnlocked.newlyUnlocked)
        assertTrue(Achievement.IMPATIENT in alreadyUnlocked.newlyUnlocked)

        // Dice rolled accumulate towards Well Rolled.
        val rolled = GameAchievementContext(diceRolledByPlayerOne = 25)
        assertEquals(25, evaluate(finishedGame(player()), rolled).counters[AchievementCounter.DICE_ROLLED])
        assertEquals(65, evaluate(finishedGame(player()), rolled, AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to 40))).counters[AchievementCounter.DICE_ROLLED])

        // Completionist unlocks once everything it waits on is done.
        val everythingElse = Achievement.COMPLETION_REQUIREMENTS.filterNot { it == Achievement.SOLO_GAME }.associateWith { 1L }
        val last = evaluate(finishedGame(player()), before = AchievementsState(unlockedAt = everythingElse, counters = mapOf(AchievementCounter.GAMES_PLAYED to 100)))
        assertTrue(Achievement.SOLO_GAME in last.newlyUnlocked)
        assertTrue(Achievement.COMPLETIONIST in last.newlyUnlocked)

        // The superuser force-unlock path (AchievementsViewModel.forceUnlock) calls unlockNow with exactly the
        // achievement being force-unlocked - here, Completionist itself, in the state a tester forcing it last would
        // realistically be in: every requirement already unlocked. It mustn't be doubled up.
        val forced = AchievementEngine.unlockNow(
            setOf(Achievement.COMPLETIONIST),
            AchievementsState(unlockedAt = Achievement.COMPLETION_REQUIREMENTS.associateWith { 1L }, counters = emptyMap()),
            now = 2L,
        )
        assertEquals(1, forced.newlyUnlocked.count { it == Achievement.COMPLETIONIST })

        // unlockNow raises a single mid-game achievement without touching counters.
        val now = AchievementEngine.unlockNow(setOf(Achievement.FIRST_ROLL_5X), AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 3)), NOW)
        assertEquals(listOf(Achievement.FIRST_ROLL_5X), now.newlyUnlocked)
        assertEquals(3, now.counters[AchievementCounter.GAMES_PLAYED])
        assertTrue(now.progressed.isEmpty())
        assertEquals(mapOf(Achievement.FIRST_ROLL_5X to NOW), now.unlockedAt())
    }

    @Test
    fun `progress is announced every step of a streak - but only at the milestones of a total`() {
        // A win streak announces progress every single step. STREAK_3 is at 2 of 3 too, so it also reports - but only
        // ones still locked do.
        val streak = evaluate(finishedGame(player(total = 200), bot(total = 100)), before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 1)))
        assertEquals(2, streak.progressed.single { it.achievement == Achievement.STREAK_10 }.current)
        assertTrue(streak.progressed.any { it.achievement == Achievement.STREAK_3 })

        // A cumulative total only announces progress at milestones: 1 -> 2 of 100 isn't worth interrupting anyone for,
        // 24 -> 25 of 100 crosses the first quarter.
        fun gamesProgressed(before: Int) = evaluate(finishedGame(player()), before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to before)))
            .progressed.any { it.achievement == Achievement.GAMES_100 }
        assertFalse(gamesProgressed(1))
        assertTrue(gamesProgressed(24))

        // Well Rolled announces progress every 1,000 dice - not every quarter of its 10,000 target: 990 -> 995 doesn't
        // cross a thousand, 999 -> 1,000 does, nowhere near the default cadence's 2,500-dice first quarter.
        fun diceProgressed(before: Int, rolled: Int) = evaluate(
            finishedGame(player()),
            context = GameAchievementContext(diceRolledByPlayerOne = rolled),
            before = AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to before)),
        ).progressed.any { it.achievement == Achievement.DICE_10000 }
        assertFalse(diceProgressed(before = 990, rolled = 5))
        assertTrue(diceProgressed(before = 999, rolled = 1))

        // Professional Roller announces progress every 1,000 career points - not every quarter of its 100,000 target:
        // 199 -> 249 doesn't cross a thousand, 999 -> 1,000 does.
        fun pointsProgressed(before: Int, total: Int) = evaluate(
            finishedGame(player(total = total)),
            context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(totalPoints = before)),
        ).progressed.any { it.achievement == Achievement.PROFESSIONAL_ROLLER }
        assertFalse(pointsProgressed(before = 199, total = 50))
        assertTrue(pointsProgressed(before = 999, total = 1))

        // An achievement being unlocked right now does not also report progress.
        val unlocking = evaluate(finishedGame(player()), before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 9)))
        assertTrue(Achievement.GAMES_10 in unlocking.newlyUnlocked)
        assertFalse(unlocking.progressed.any { it.achievement == Achievement.GAMES_10 })
    }

    @Test
    fun `career points accumulate across the leaderboard - player 1's only`() {
        val nearly = GameAchievementContext(previousLeaderboard = LeaderboardTotals(totalPoints = 99_800))
        assertFalse(Achievement.PROFESSIONAL_ROLLER in unlocked(finishedGame(player(total = 150)), nearly))
        assertTrue(Achievement.PROFESSIONAL_ROLLER in unlocked(finishedGame(player(total = 250)), nearly))
        assertEquals(100_000, AchievementEngine.progressOf(Achievement.PROFESSIONAL_ROLLER, emptyMap(), LeaderboardTotals(totalPoints = 120_000)))

        // 400 short of the target on player 1's own points alone (99,400 + 500 = 99,900); adding player 2's 500 as
        // well (the bug this guards against) would have crossed it at 100,400.
        assertFalse(
            Achievement.PROFESSIONAL_ROLLER in unlocked(
                finishedGame(player(name = "A", total = 500), player(name = "B", total = 500)),
                GameAchievementContext(previousLeaderboard = LeaderboardTotals(totalPoints = 99_400)),
            ),
            "player 2's score must not count towards player 1's Professional Roller",
        )
    }

    @Test
    fun `score bands - complete only with every score in range - this game's included - measured against the leaderboard`() {
        fun context(scores: Set<Int>) = GameAchievementContext(previousLeaderboard = LeaderboardTotals(distinctScores = scores))

        // A band unlocks only once every score in it has been recorded.
        val allButOne = ((5..50) - 42).toSet()
        assertFalse(Achievement.TALLY in unlocked(finishedGame(player(total = 300)), context(allButOne)))
        assertTrue(Achievement.TALLY in unlocked(finishedGame(player(total = 42)), context(allButOne)))
        // The leaderboard read happens before the insert, so the engine has to add this game's own score itself.
        assertTrue(Achievement.TALLY in unlocked(finishedGame(player(total = 50)), context((5..49).toSet())))

        // Bands only count scores inside their own range.
        val oneBand = unlocked(finishedGame(player(total = 300)), context((5..50).toSet()))
        assertTrue(Achievement.TALLY in oneBand)
        assertFalse(Achievement.BOOKKEEPER in oneBand)
        assertFalse(Achievement.HISTORIAN in oneBand)

        // Band progress is measured against the leaderboard - not a stored counter.
        assertEquals(23, AchievementEngine.progressOf(Achievement.TALLY, emptyMap(), LeaderboardTotals(distinctScores = (5..27).toSet())))
        assertEquals(0, AchievementEngine.progressOf(Achievement.BOOKKEEPER, emptyMap(), LeaderboardTotals(distinctScores = (5..27).toSet())))
        assertEquals(46, AchievementEngine.progressOf(Achievement.TALLY, emptyMap(), LeaderboardTotals(distinctScores = (5..50).toSet())))

        // A band announces progress at its quarter marks: 11 of 46 is under the first quarter; 12 crosses it.
        val quarter = evaluate(finishedGame(player(total = 16)), context((5..15).toSet()))
        assertTrue(quarter.progressed.any { it.achievement == Achievement.TALLY && it.current == 12 })

        // Every band's target matches the size of its range, and the bands tile 5 to 300 without gaps or overlap.
        val bands = Achievement.entries.filter { it.scoreBand != null }
        assertEquals(6, bands.size)
        bands.forEach { assertEquals(it.scoreBand!!.count(), it.target, it.name) }
        assertEquals(46, Achievement.TALLY.target)
        assertEquals(50, Achievement.BOOKKEEPER.target)
        assertEquals((5..300).toSet(), bands.mapNotNull { it.scoreBand }.flatten().toSet())
    }

    // ---- Game start and mid-game --------------------------------------------------------------------
    // Gathering GameStartContext's booleans needs an async SettingsRepository read only GameViewModel can do (see
    // checkGameStartAchievements), but deciding what they earn is this pure engine's job, same as
    // evaluate/evaluateInProgress for the rest of a game.

    @Test
    fun `game start - The Journey Begins once - then each flag GameViewModel hands it and no counters touched`() {
        val first = AchievementEngine.evaluateAtGameStart(GameStartContext(), AchievementsState(), NOW)
        val second = AchievementEngine.evaluateAtGameStart(GameStartContext(), AchievementsState(unlockedAt = first.unlockedAt()), NOW)
        assertEquals(listOf(Achievement.THE_JOURNEY_BEGINS), first.newlyUnlocked)
        assertTrue(second.isEmpty)

        // Already past The Journey Begins, so it doesn't muddy the rest. Each flag is decided by
        // GameViewModel.checkGameStartAchievements - Big Fan needs a two-player game with a human P2 named exactly
        // "zodac"; Luck of the Irish comes from GameState.isLuckOfTheIrish (see IrishEasterEggTest) - and this engine
        // just trusts the flag it's handed.
        val begun = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))
        fun atStart(context: GameStartContext) = AchievementEngine.evaluateAtGameStart(context, begun, NOW)
        assertTrue(atStart(GameStartContext()).isEmpty)
        assertEquals(listOf(Achievement.FRESH_COAT_OF_PAINT), atStart(GameStartContext(playedNonDefaultStyle = true)).newlyUnlocked)
        assertEquals(listOf(Achievement.BIG_FAN), atStart(GameStartContext(hasHumanPlayerNamedZodac = true)).newlyUnlocked)
        assertEquals(listOf(Achievement.LUCK_OF_THE_IRISH), atStart(GameStartContext(hasIrishPlayerOneInTricolour = true)).newlyUnlocked)
        assertEquals(listOf(Achievement.I_DID_IT_MY_WAY), atStart(GameStartContext(customizedGameSettings = true)).newlyUnlocked)
        // Starting any non-Standard mode unlocks Rules - and Standard does not.
        assertTrue(atStart(GameStartContext(gameMode = GameMode.STANDARD)).isEmpty)
        assertEquals(listOf(Achievement.NON_STANDARD_MODE), atStart(GameStartContext(gameMode = GameMode.TRICOLOUR)).newlyUnlocked)
        assertEquals(listOf(Achievement.NON_STANDARD_MODE), atStart(GameStartContext(gameMode = GameMode.QUICKFIRE)).newlyUnlocked)

        // Game start touches no counters and skips an already-unlocked style.
        val styled = AchievementEngine.evaluateAtGameStart(
            GameStartContext(playedNonDefaultStyle = true),
            AchievementsState(unlockedAt = mapOf(Achievement.FRESH_COAT_OF_PAINT to 1L), counters = mapOf(AchievementCounter.GAMES_PLAYED to 7)),
            NOW,
        )
        assertTrue(Achievement.FRESH_COAT_OF_PAINT !in styled.newlyUnlocked)
        assertEquals(7, styled.counters[AchievementCounter.GAMES_PLAYED])
    }

    @Test
    fun `mid-game - banked feats unlock at once - nothing a later turn could change does and no counters move`() {
        // A maxed box unlocks mid-game - without waiting for the results screen - but an AI maxing one earns the device nothing.
        assertTrue(Achievement.SIXES_30 in unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.SIXES to 30))))
        assertFalse(Achievement.SIXES_30 in unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.ONES to 1)), midGamePlayer(mapOf(ScoreCategory.SIXES to 30), type = PlayerType.AI)))

        // 30 + 50 + 200 bonus = 280 - already well past the 200 rung, but leaving this game now records nothing on the
        // leaderboard, so SCORE_200 must not have fired off a total that's about to disappear.
        val banked = unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.CHANCE to 30, ScoreCategory.FIVE_OF_A_KIND to 50), fiveOfAKindBonusCount = 2))
        assertTrue(Achievement.FIRST_5X in banked)
        assertTrue(Achievement.ENCORE_5X in banked)
        assertTrue(Achievement.HAT_TRICK_5X in banked)
        assertTrue(Achievement.CHANCE_30 in banked)
        assertFalse(Achievement.SCORE_200 in banked, "a score threshold must wait for the game to actually finish")

        // An almost-empty card is not "Spotless", "Cold Dice" or a solo win - those need an ending.
        val almostEmpty = unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.ONES to 3)))
        assertFalse(Achievement.NO_ZEROES in almostEmpty)
        assertFalse(Achievement.SCORE_UNDER_100 in almostEmpty)
        assertFalse(Achievement.LOW_ROLLS in almostEmpty)
        assertFalse(Achievement.SOLO_GAME in almostEmpty)

        // Mid-game touches no counters - so an undo can't inflate a running total.
        val update = AchievementEngine.evaluateInProgress(
            inProgress(midGamePlayer(mapOf(ScoreCategory.SIXES to 30))),
            AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 7)),
            NOW,
        )
        assertTrue(update.counters.isEmpty())
        assertTrue(update.progressed.isEmpty())

        // Each pass keeps to its own half: mid-game earns nothing once the game is over, and the end-of-game pass
        // nothing from a game that isn't.
        assertTrue(AchievementEngine.evaluateInProgress(finishedGame(player(total = 300)), AchievementsState(), NOW).isEmpty)
        assertTrue(evaluate(GameState(players = listOf(player()), isGameOver = false)).isEmpty)
    }

    /** Player 1 part-way through a game in [mode], having rolled [rolls] times, as [evaluateInProgress] sees it after a roll. */
    private fun greenfingersAfter(rolls: Int, mode: GameMode = GameMode.STANDARD, vararg others: PlayerState): Boolean {
        val me = midGamePlayer(emptyMap(), gameMode = mode).copy(rollCount = rolls)
        return Achievement.GREENFINGERS in unlockedMidGame(if (mode == GameMode.QUICKFIRE) me.copy(disabledCategories = QUICKFIRE_OFF) else me, *others)
    }

    @Test
    fun `using every roll of every turn unlocks Greenfingers - one roll short does not - in each mode - for player 1 only`() {
        assertTrue(greenfingersAfter(39))
        assertFalse(greenfingersAfter(38))
        // Tricolour's card is longer, so its sunflower takes every roll of its 17 turns.
        assertTrue(greenfingersAfter(51, GameMode.TRICOLOUR))
        assertFalse(greenfingersAfter(50, GameMode.TRICOLOUR))
        // Quickfire's takes every roll of its six turns - 18.
        assertTrue(greenfingersAfter(18, GameMode.QUICKFIRE))
        assertFalse(greenfingersAfter(17, GameMode.QUICKFIRE))
        // Another player's sunflower does not unlock it.
        assertFalse(greenfingersAfter(20, GameMode.STANDARD, midGamePlayer(emptyMap()).copy(name = "Player 2", rollCount = 39)))
    }

    // ---- Game modes ------------------------------------------------------------------------------

    @Test
    fun `each mode's win achievement needs a win in that mode against an opponent`() {
        fun table(mode: GameMode, humanTotal: Int, withBot: Boolean = true) =
            if (withBot) finishedGame(player(total = humanTotal, gameMode = mode), bot(total = 200, gameMode = mode)) else finishedGame(player(total = humanTotal, gameMode = mode))

        for ((mode, achievement) in listOf(GameMode.TRICOLOUR to Achievement.TRICOLOUR_WIN, GameMode.QUICKFIRE to Achievement.QUICKFIRE_WIN, GameMode.STUD to Achievement.STUD_WIN)) {
            assertTrue(achievement in unlocked(table(mode, 300)), "$achievement for a win")
            assertFalse(achievement in unlocked(table(mode, 100)), "$achievement for a loss")
            // Same rule as First Victory and the win counter: a solo game has nobody to beat.
            assertFalse(achievement in unlocked(table(mode, 300, withBot = false)), "$achievement solo")
            assertFalse(achievement in unlocked(table(GameMode.STANDARD, 300)), "$achievement for a Standard win")
        }

        val bot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 }
        val strongBot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 30 }
        assertTrue(Achievement.THIRD_WIND_WIN in unlocked(finishedGame(thirdWindNoZeroes(), bot)))
        assertFalse(Achievement.THIRD_WIND_WIN in unlocked(finishedGame(thirdWindNoZeroes(), strongBot)))
        assertFalse(Achievement.THIRD_WIND_WIN in unlocked(finishedGame(thirdWindNoZeroes())))
        assertFalse(Achievement.THIRD_WIND_WIN in unlocked(finishedGame(player(total = 250), bot(total = 100))))

        fun hitListWin(state: GameState) = Achievement.HIT_LIST_WIN in unlocked(state)
        assertTrue(hitListWin(finishedGame(hitListPlayer(total = 200), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))))
        assertFalse(hitListWin(finishedGame(hitListPlayer(total = 100), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))))
        assertFalse(hitListWin(finishedGame(hitListPlayer(total = 200))))
        assertFalse(hitListWin(finishedGame(player(total = 200), bot(total = 150))))
    }

    @Test
    fun `Tricolour - all four colour boxes scored is Tricolour Me Impressed and How Do You Play This Game zeroes them too`() {
        // It unlocks mid-game, the moment the fourth goes in.
        val threeOfFour = mapOf(ScoreCategory.REDS to 40, ScoreCategory.YELLOWS to 40, ScoreCategory.BLUES to 40)
        assertFalse(Achievement.TRICOLOUR_ALL_COLOURS in unlockedMidGame(midGamePlayer(threeOfFour, gameMode = GameMode.TRICOLOUR)))
        assertTrue(Achievement.TRICOLOUR_ALL_COLOURS in unlockedMidGame(midGamePlayer(threeOfFour + (ScoreCategory.COLOURED_HOUSE to 25), gameMode = GameMode.TRICOLOUR)))
        // A zero in any colour box doesn't count.
        val zeroYellow = mapOf(ScoreCategory.REDS to 40, ScoreCategory.YELLOWS to 0, ScoreCategory.BLUES to 40, ScoreCategory.COLOURED_HOUSE to 25)
        assertFalse(Achievement.TRICOLOUR_ALL_COLOURS in unlockedMidGame(midGamePlayer(zeroYellow, gameMode = GameMode.TRICOLOUR)))

        assertFalse(Achievement.ALL_ZEROES in unlocked(finishedGame(player(total = 45, overrides = mapOf(ScoreCategory.REDS to 40), gameMode = GameMode.TRICOLOUR))))
        assertTrue(Achievement.ALL_ZEROES in unlocked(finishedGame(player(total = 5, gameMode = GameMode.TRICOLOUR))))
    }

    // ---- Quickfire: a card with boxes switched off -------------------------------------------------

    /** A Quickfire draw leaving Sixes, 3x, 4x, both straights and Chance on. */
    private val QUICKFIRE_OFF = setOf(
        ScoreCategory.FIVE_OF_A_KIND,
        ScoreCategory.ONES,
        ScoreCategory.TWOS,
        ScoreCategory.THREES,
        ScoreCategory.FOURS,
        ScoreCategory.FIVES,
        ScoreCategory.FULL_HOUSE,
    )

    /** A Quickfire player whose [off] boxes are switched off - empty, as in a real game - and the rest scored as [player] says. */
    private fun quickfirePlayer(
        total: Int = 100,
        overrides: Map<ScoreCategory, Int> = emptyMap(),
        off: Set<ScoreCategory> = QUICKFIRE_OFF,
        name: String = "Player 1",
        type: PlayerType = PlayerType.HUMAN,
    ): PlayerState {
        val scored = player(name = name, type = type, total = total, overrides = overrides, gameMode = GameMode.QUICKFIRE)
        return scored.copy(disabledCategories = off, scorecard = scored.scorecard.mapValues { (category, slots) -> if (category in off) emptyList() else slots })
    }

    private fun quickfireGame(vararg players: PlayerState, off: Set<ScoreCategory> = QUICKFIRE_OFF) =
        finishedGame(*players).copy(disabledCategories = off)

    @Test
    fun `scoring 150 in Quickfire unlocks Six Of The Best - win or lose - but not with extra rolls or boxes or in another mode`() {
        fun game(mode: GameMode, total: Int) = quickfireGame(quickfirePlayer(total = total)).copy(gameMode = mode)

        assertTrue(Achievement.QUICKFIRE_SCORE in unlocked(game(GameMode.QUICKFIRE, 150)))
        assertFalse(Achievement.QUICKFIRE_SCORE in unlocked(game(GameMode.QUICKFIRE, 149)))
        assertFalse(Achievement.QUICKFIRE_SCORE in unlocked(game(GameMode.STANDARD, 200)))
        assertFalse(Achievement.QUICKFIRE_SCORE in unlocked(game(GameMode.QUICKFIRE, 200).copy(rollModifiers = RollModifiers(rollsPerTurn = 5))))
        assertFalse(Achievement.QUICKFIRE_SCORE in unlocked(game(GameMode.QUICKFIRE, 200).copy(extendedScores = true)))
        val lost = quickfireGame(quickfirePlayer(total = 160), quickfirePlayer(total = 200, name = "Bot", type = PlayerType.AI))
        assertTrue(Achievement.QUICKFIRE_SCORE in unlocked(lost))
    }

    @Test
    fun `a Quickfire card earns none of the full-card achievements - Spotless - the low scores - the ladder - the bonus or the sections`() {
        // A Quickfire card with no zero doesn't unlock Spotless - a full Standard one does.
        val enabled = GameMode.QUICKFIRE.categories - QUICKFIRE_OFF
        assertFalse(Achievement.NO_ZEROES in unlocked(quickfireGame(quickfirePlayer(total = 100, overrides = enabled.associateWith { 10 }))))
        assertTrue(Achievement.NO_ZEROES in unlocked(finishedGame(player(total = 100, overrides = GameMode.STANDARD.categories.associateWith { 10 }))))

        // A low Quickfire total doesn't unlock Cold Dice or Low Rolls or Rock Bottom - a Standard one does.
        for ((total, achievements) in listOf(
            90 to listOf(Achievement.SCORE_UNDER_100),
            15 to listOf(Achievement.SCORE_UNDER_100, Achievement.LOW_ROLLS),
            5 to listOf(Achievement.SCORE_UNDER_100, Achievement.LOW_ROLLS, Achievement.EXTREME_LOW_ROLLS),
        )) {
            val quickfire = unlocked(quickfireGame(quickfirePlayer(total = total)))
            val standard = unlocked(finishedGame(player(total = total)))
            for (achievement in achievements) {
                assertFalse(achievement in quickfire, "$achievement at $total in Quickfire")
                assertTrue(achievement in standard, "$achievement at $total in Standard")
            }
        }

        // Zeroing every Quickfire box but Chance doesn't unlock How Do You Play This Game.
        assertFalse(Achievement.ALL_ZEROES in unlocked(quickfireGame(quickfirePlayer(total = 20))))
        assertTrue(Achievement.ALL_ZEROES in unlocked(finishedGame(player(total = 20))))

        // The score ladder isn't climbed in Quickfire however high the total.
        assertFalse(Achievement.SCORE_200 in unlocked(quickfireGame(quickfirePlayer(total = 210))))
        assertTrue(Achievement.SCORE_200 in unlocked(finishedGame(player(total = 210))))

        // A scaled Quickfire upper bonus doesn't unlock Bonus Round - nor do the section totals unlock Upper or Lower Class.
        val onlySixes = GameMode.QUICKFIRE.categories.filter { it != ScoreCategory.SIXES && it.section == ScoreSection.UPPER }.toSet() + ScoreCategory.FIVE_OF_A_KIND
        val bonus = midGamePlayer(mapOf(ScoreCategory.SIXES to 18), gameMode = GameMode.QUICKFIRE).copy(disabledCategories = onlySixes)
        assertEquals(35, bonus.upperSectionBonus)
        assertFalse(Achievement.UPPER_BONUS in unlockedMidGame(bonus))
        assertTrue(Achievement.UPPER_BONUS in unlockedMidGame(midGamePlayer(PlayerState.UPPER_CATEGORIES.associateWith { 3 * (PlayerState.UPPER_CATEGORIES.indexOf(it) + 1) })))
        val upper = quickfirePlayer(
            overrides = mapOf(ScoreCategory.SIXES to 30, ScoreCategory.FIVES to 25, ScoreCategory.FOURS to 20, ScoreCategory.THREES to 15),
            off = setOf(ScoreCategory.FIVE_OF_A_KIND, ScoreCategory.ONES, ScoreCategory.TWOS, ScoreCategory.FULL_HOUSE, ScoreCategory.SMALL_STRAIGHT, ScoreCategory.LARGE_STRAIGHT),
        )
        assertEquals(90, upper.upperSectionTotal)
        assertFalse(Achievement.UPPER_84 in unlocked(quickfireGame(upper, off = upper.disabledCategories)))
        val lower = quickfirePlayer(
            total = 200,
            overrides = mapOf(ScoreCategory.THREE_OF_A_KIND to 30, ScoreCategory.FOUR_OF_A_KIND to 30, ScoreCategory.LARGE_STRAIGHT to 40, ScoreCategory.SMALL_STRAIGHT to 30),
        )
        assertTrue(lower.lowerSectionTotalExcludingFiveOfAKind >= 150)
        assertFalse(Achievement.LOWER_150 in unlocked(quickfireGame(lower)))
    }

    // ---- Luck Of The Draw -------------------------------------------------------------------------

    private fun wonGame(mode: GameMode = GameMode.STANDARD) = finishedGame(player(total = 300, gameMode = mode), bot(total = 200, gameMode = mode))

    private fun luckOfTheDraw(state: GameState, timeouts: Int) =
        Achievement.LUCK_OF_THE_DRAW in unlocked(state, GameAchievementContext(playerOneTimeouts = timeouts))

    @Test
    fun `winning with 3 or fewer boxes scored yourself is Luck Of The Draw - measured against each mode's own card`() {
        // Standard's 13 boxes: 10 timeouts leaves 3 scored by hand.
        assertTrue(luckOfTheDraw(wonGame(), timeouts = 10))
        assertTrue(luckOfTheDraw(wonGame(), timeouts = 13))
        assertFalse(luckOfTheDraw(wonGame(), timeouts = 9))
        // It needs a win against an opponent.
        assertFalse(luckOfTheDraw(finishedGame(player(total = 100), bot(total = 200)), timeouts = 13))
        assertFalse(luckOfTheDraw(finishedGame(player(total = 300)), timeouts = 13))
        // Quickfire's six boxes: 3 timeouts leaves 3 by hand, 2 leaves 4.
        val quickfire = quickfireGame(quickfirePlayer(total = 300), quickfirePlayer(total = 200, name = "Bot", type = PlayerType.AI))
        assertTrue(luckOfTheDraw(quickfire, timeouts = 3))
        assertFalse(luckOfTheDraw(quickfire, timeouts = 2))
        // Tricolour's 17 boxes: 14 timeouts leaves 3 by hand, 13 leaves 4.
        assertTrue(luckOfTheDraw(wonGame(GameMode.TRICOLOUR), timeouts = 14))
        assertFalse(luckOfTheDraw(wonGame(GameMode.TRICOLOUR), timeouts = 13))
        // Third Wind's 39 turns: 36 timeouts leaves 3.
        val thirdWind = finishedGame(thirdWindNoZeroes(), thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 })
        assertTrue(luckOfTheDraw(thirdWind, timeouts = 36))
        assertFalse(luckOfTheDraw(thirdWind, timeouts = 35))
        // Extended Scores counts its turns too: 13 boxes need 10 timeouts, 16 boxes 13.
        fun won(extended: Boolean): GameState {
            val me = player(total = 300).let { if (extended) it.copy(extendedScores = true, scorecard = it.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) }) else it }
            return finishedGame(me, bot(total = 100)).copy(extendedScores = extended)
        }
        assertTrue(luckOfTheDraw(won(extended = false), timeouts = 10))
        assertFalse(luckOfTheDraw(won(extended = true), timeouts = 10))
        assertTrue(luckOfTheDraw(won(extended = true), timeouts = 13))
    }

    // ---- Modifiers ----------------------------------------------------------------------------------

    private fun modifiedGame(vararg players: PlayerState) = finishedGame(*players.map { it.copy(rollsModified = true) }.toTypedArray())
        .copy(rollModifiers = RollModifiers(storedRolls = true))

    private fun extendedGame(vararg players: PlayerState) =
        finishedGame(*players.map { it.copy(extendedScores = true, scorecard = it.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) }) }.toTypedArray())
            .copy(extendedScores = true)

    @Test
    fun `roll modifiers don't hand out the score ladder - the section thresholds - Spotless - Bonus Round or Impatient`() {
        val strong = player(total = 520, overrides = GameMode.STANDARD.categories.associateWith { 30 })
        val plain = unlocked(finishedGame(strong))
        val modified = unlocked(modifiedGame(strong))
        for (achievement in listOf(Achievement.SCORE_200, Achievement.SCORE_300, Achievement.SCORE_400, Achievement.SCORE_500, Achievement.UPPER_84, Achievement.LOWER_150)) {
            assertTrue(achievement in plain, "$achievement in a plain game")
            assertFalse(achievement in modified, "$achievement with roll modifiers")
        }

        val clean = player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 } + mapOf(ScoreCategory.SIXES to 30))
        assertTrue(Achievement.NO_ZEROES in unlocked(finishedGame(clean)))
        assertFalse(Achievement.NO_ZEROES in unlocked(modifiedGame(clean)))
        assertTrue(Achievement.UPPER_BONUS in unlocked(finishedGame(clean)))
        assertFalse(Achievement.UPPER_BONUS in unlocked(modifiedGame(clean)))

        // A single roll chosen with Number of Rolls isn't Impatient - nor Naturally Gifted for a win. Quickfire's three
        // rolls still are.
        assertFalse(Achievement.IMPATIENT in unlocked(finishedGame(player()).copy(rollModifiers = RollModifiers(rollsPerTurn = 1))))
        assertTrue(Achievement.IMPATIENT in unlocked(finishedGame(player())))
        val firstRollOnly = GameAchievementContext(playerOneTookExtraRoll = false)
        fun won(mode: GameMode) = finishedGame(player(total = 200, gameMode = mode), bot(total = 150, gameMode = mode))
        val oneRoll = unlocked(won(GameMode.STANDARD).copy(rollModifiers = RollModifiers(rollsPerTurn = 1)), firstRollOnly)
        assertFalse(Achievement.IMPATIENT in oneRoll)
        assertFalse(Achievement.NATURALLY_GIFTED in oneRoll)
        val quickfire = unlocked(won(GameMode.QUICKFIRE), firstRollOnly)
        assertTrue(Achievement.IMPATIENT in quickfire)
        assertTrue(Achievement.NATURALLY_GIFTED in quickfire)
    }

    @Test
    fun `Extended Scores and Unlucky Dice don't hand out the score ladder or Zero To Hero - and How Do You Play This Game zeroes their boxes too`() {
        val strong = player(total = 520, overrides = GameMode.STANDARD.categories.associateWith { 30 })
        val plain = unlocked(finishedGame(strong))
        val extended = unlocked(extendedGame(strong))
        for (achievement in listOf(Achievement.SCORE_200, Achievement.SCORE_300, Achievement.SCORE_400, Achievement.SCORE_500)) {
            assertTrue(achievement in plain, "$achievement in a plain game")
            assertFalse(achievement in extended, "$achievement with Extended Scores")
        }

        val threeZeroes = player(total = 200, overrides = mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to 0, ScoreCategory.SIXES to 30))
        assertTrue(Achievement.ZERO_TO_HERO in unlocked(finishedGame(threeZeroes, bot(total = 100))))
        assertFalse(Achievement.ZERO_TO_HERO in unlocked(extendedGame(threeZeroes, bot(total = 100))))
        assertFalse(Achievement.ZERO_TO_HERO in unlocked(finishedGame(threeZeroes, bot(total = 100)).copy(unluckyDice = UnluckyDice())))

        val standardZeroes = player(total = 5)
        val allZeroes = standardZeroes.copy(extendedScores = true, scorecard = standardZeroes.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) })
        val withEvens = allZeroes.copy(scorecard = allZeroes.scorecard + (ScoreCategory.EVENS to listOf(12)))
        assertFalse(Achievement.ALL_ZEROES in unlocked(finishedGame(withEvens).copy(extendedScores = true)))
        assertTrue(Achievement.ALL_ZEROES in unlocked(finishedGame(allZeroes).copy(extendedScores = true)))
    }

    // ---- Third Wind: every box scored three times -----------------------------------------------

    /** A finished Third Wind card - all three slots of every box filled - each slot scoring [slotScore]. */
    private fun thirdWindPlayer(
        name: String = "Player 1",
        type: PlayerType = PlayerType.HUMAN,
        fiveOfAKindBonusCount: Int = 0,
        slotScore: (category: ScoreCategory, slot: Int) -> Int,
    ) = PlayerState(
        name = name,
        type = type,
        gameMode = GameMode.THIRD_WIND,
        scorecard = GameMode.THIRD_WIND.categories.associateWith { category -> List(GameMode.THIRD_WIND.scoresPerCategory) { slotScore(category, it) } },
        fiveOfAKindBonusCount = fiveOfAKindBonusCount,
    )

    /** A Third Wind card with no zero anywhere: 10 in every slot - 390 points, and a 5x box of three 10s (never a 5x). */
    private fun thirdWindNoZeroes(name: String = "Player 1", type: PlayerType = PlayerType.HUMAN) =
        thirdWindPlayer(name = name, type = type) { _, _ -> 10 }

    @Test
    fun `Third Time's The Charm is Third Wind's Spotless - no zero in all 39 slots - and each card earns only its own`() {
        val thirdWind = unlocked(finishedGame(thirdWindNoZeroes()))
        assertTrue(Achievement.THIRD_WIND_NO_ZEROES in thirdWind)
        assertFalse(Achievement.NO_ZEROES in thirdWind)

        val oneZero = thirdWindPlayer { category, slot -> if (category == ScoreCategory.LARGE_STRAIGHT && slot == 2) 0 else 10 }
        assertFalse(Achievement.THIRD_WIND_NO_ZEROES in unlocked(finishedGame(oneZero)))

        val standard = unlocked(finishedGame(player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 })))
        assertTrue(Achievement.NO_ZEROES in standard)
        assertFalse(Achievement.THIRD_WIND_NO_ZEROES in standard)
    }

    @Test
    fun `Third Wind's tripled card - no thresholds - Personal Best or Zero To Hero - but its 5x box and Sixes count per slot`() {
        // 30 in every slot: 540 upper, 870 lower without the 5x box - past every rung in one go.
        val tripled = unlocked(finishedGame(thirdWindPlayer { _, _ -> 30 }))
        for (achievement in listOf(Achievement.SCORE_200, Achievement.SCORE_300, Achievement.SCORE_400, Achievement.SCORE_500, Achievement.UPPER_84, Achievement.LOWER_150)) {
            assertFalse(achievement in tripled, "$achievement")
        }

        // It isn't on the Leaderboard, so never a New Personal Best.
        assertFalse(Achievement.PERSONAL_BEST in unlocked(finishedGame(thirdWindNoZeroes()), GameAchievementContext(previousBestScore = 100)))

        // Three zeroes over 39 turns is the usual run of things.
        val threeZeroes = thirdWindPlayer { category, _ -> if (category == ScoreCategory.LARGE_STRAIGHT) 0 else 10 }
        assertFalse(Achievement.ZERO_TO_HERO in unlocked(finishedGame(threeZeroes, thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 })))

        // Three 50s in its 5x box are a Hat Trick with no bonus chip.
        val hatTrick = evaluate(finishedGame(thirdWindPlayer { category, _ -> if (category == ScoreCategory.FIVE_OF_A_KIND) 50 else 10 }))
        assertEquals(3, hatTrick.counters[AchievementCounter.SCORED_5X])
        assertTrue(Achievement.ENCORE_5X in hatTrick.newlyUnlocked)
        assertTrue(Achievement.HAT_TRICK_5X in hatTrick.newlyUnlocked)

        // Six Appeal needs a 30 in one Sixes slot - not three slots adding up to it.
        assertTrue(Achievement.SIXES_30 in unlocked(finishedGame(thirdWindPlayer { category, slot -> if (category == ScoreCategory.SIXES && slot == 1) 30 else 6 })))
        assertFalse(Achievement.SIXES_30 in unlocked(finishedGame(thirdWindPlayer { category, slot -> if (category == ScoreCategory.SIXES) listOf(12, 12, 6)[slot] else 6 })))
    }

    // ---- Hit List -------------------------------------------------------------------------------------

    /** A Hit List card dealt [HIT_LIST]: every box holding what [scores] gives it, zero otherwise, and [total] made up in the first target. */
    private fun hitListPlayer(
        name: String = "Player 1",
        type: PlayerType = PlayerType.HUMAN,
        scores: Map<ScoreCategory, Int> = emptyMap(),
        total: Int? = null,
    ): PlayerState {
        val filled = GameMode.HIT_LIST.categories.associateWith { 0 } + scores
        val topUp = total?.let { it - filled.values.sum() } ?: 0
        return PlayerState(
            name = name,
            type = type,
            gameMode = GameMode.HIT_LIST,
            hitList = HIT_LIST,
            scorecard = oneScoreEach(filled + (ScoreCategory.TARGET_1 to (filled.getValue(ScoreCategory.TARGET_1) + topUp))),
        )
    }

    @Test
    fun `Right On Target unlocks mid-game for an exact hit on a five-number target - not a plain hit or one with any places`() {
        val (fiveNumbers, target) = HIT_LIST.entries.first { it.value.called.size == 5 }.toPair()
        val (withAnyPlace, anyTarget) = HIT_LIST.entries.first { it.value.called.size < 5 }.toPair()
        fun earned(scores: Map<ScoreCategory, Int>) = Achievement.HIT_LIST_RIGHT_ON_TARGET in unlockedMidGame(hitListPlayer(scores = scores))

        assertTrue(earned(mapOf(fiveNumbers to target.exactPoints)))
        assertFalse(earned(mapOf(fiveNumbers to target.points)))
        assertFalse(earned(mapOf(withAnyPlace to anyTarget.exactPoints)))
        // The Alibi never takes an exact hit's points, whatever it holds.
        assertFalse(earned(mapOf(ScoreCategory.ALIBI to target.points)))
    }

    @Test
    fun `a Hit List card earns no ladder rung - low score - Zero To Hero or Spotless - misses and partial hits are its usual run`() {
        val high = unlocked(finishedGame(hitListPlayer(total = 500)))
        assertFalse(Achievement.SCORE_200 in high)
        assertFalse(Achievement.SCORE_500 in high)
        assertTrue(Achievement.SCORE_200 in unlocked(finishedGame(player(total = 200))))

        assertFalse(Achievement.SCORE_UNDER_100 in unlocked(finishedGame(hitListPlayer(total = 60))))
        assertFalse(Achievement.LOW_ROLLS in unlocked(finishedGame(hitListPlayer(total = 15))))

        // Every box but the first is a zero: far more than three.
        assertFalse(Achievement.ZERO_TO_HERO in unlocked(finishedGame(hitListPlayer(total = 200), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))))

        assertFalse(Achievement.NO_ZEROES in unlocked(finishedGame(hitListPlayer(scores = GameMode.HIT_LIST.categories.associateWith { 5 }))))
        assertTrue(Achievement.NO_ZEROES in unlocked(finishedGame(player(overrides = GameMode.STANDARD.categories.associateWith { 5 }, total = 100))))
    }

    private companion object HitListCard {
        /** One Hit List game's targets, drawn the way a real game draws them. */
        val HIT_LIST = GameMode.HIT_LIST.drawHitList(Random(7))
    }
}
