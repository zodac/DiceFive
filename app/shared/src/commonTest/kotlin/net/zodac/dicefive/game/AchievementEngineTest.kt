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
    private fun unlockedMidGame(vararg players: PlayerState, growingFlowerpotInPlay: Boolean = false) =
        AchievementEngine.evaluateInProgress(inProgress(*players), AchievementsState(), NOW, growingFlowerpotInPlay = growingFlowerpotInPlay).newlyUnlocked

    // ---- Who wins, and who earns ----------------------------------------------------------------

    @Test
    fun `the result at the table - played - won or lost - the win streak and only player 1 earning`() {
        // A finished solo game counts as played but neither won nor lost, and neither extends nor breaks a streak.
        val solo = evaluate(finishedGame(player(total = 150)))
        assertEquals(1, solo.counters[AchievementCounter.GAMES_PLAYED])
        assertEquals(0, solo.counters[AchievementCounter.GAMES_WON])
        assertTrue(Achievement.PRACTICE_MAKES_PERFECT in solo.newlyUnlocked)
        assertFalse(Achievement.FIRST_VICTORY in solo.newlyUnlocked)
        val soloOnAStreak = evaluate(finishedGame(player()), before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 2)))
        assertEquals(2, soloOnAStreak.counters[AchievementCounter.WIN_STREAK])

        // Beating an AI wins the game and extends the streak.
        val beatBot = evaluate(finishedGame(player(total = 200), bot(total = 180)))
        assertEquals(1, beatBot.counters[AchievementCounter.GAMES_WON])
        assertEquals(1, beatBot.counters[AchievementCounter.WIN_STREAK])
        assertTrue(Achievement.FIRST_VICTORY in beatBot.newlyUnlocked)
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
        assertFalse(Achievement.FIRST_VICTORY in lostToHuman.newlyUnlocked, "player 2 winning must not earn player 1 a win")
        assertFalse(Achievement.SHARPSHOOTER in lostToHuman.newlyUnlocked, "player 2's score must not earn player 1 Sharpshooter")
        assertEquals(0, lostToHuman.counters[AchievementCounter.GAMES_WON])
        assertFalse(Achievement.I_ROBOT in evaluate(finishedGame(player(name = "P1", total = 120), player(name = "P2", total = 300))).newlyUnlocked)

        // Only player 1's own feats and scorecard count: player 1 under 100 with no 5x, player 2 over 300 with a real 5x.
        val secondHumansFeats = evaluate(
            finishedGame(player(name = "Alice", total = 90), player(name = "Bob", total = 320, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50))),
        )
        assertFalse(Achievement.SHARPSHOOTER in secondHumansFeats.newlyUnlocked)
        assertFalse(Achievement.FIVE_X in secondHumansFeats.newlyUnlocked)
        assertTrue(Achievement.COLD_DICE in secondHumansFeats.newlyUnlocked)

        // Player 1 outscores the weaker bot, but the strongest one still finished on top overall - "win" means
        // finishing 1st across the whole table, not merely ahead of *someone*.
        val thirdAhead = evaluate(
            finishedGame(
                player(total = 200),
                player(name = "Weak Bot", type = PlayerType.AI, total = 150),
                player(name = "Strong Bot", type = PlayerType.AI, total = 400),
            ),
        )
        assertFalse(Achievement.FIRST_VICTORY in thirdAhead.newlyUnlocked, "player 1 didn't actually finish 1st, so this must not count as a win")
        assertEquals(0, thirdAhead.counters[AchievementCounter.GAMES_WON])
    }

    @Test
    fun `margins and ties - Landslide - Photo Finish - Pipped To The Post and Tie Break`() {
        val landslide = evaluate(finishedGame(player(total = 300), bot(total = 150)))
        val photoFinish = evaluate(finishedGame(player(total = 201), bot(total = 200)))
        assertTrue(Achievement.LANDSLIDE in landslide.newlyUnlocked)
        assertFalse(Achievement.PHOTO_FINISH in landslide.newlyUnlocked)
        assertTrue(Achievement.PHOTO_FINISH in photoFinish.newlyUnlocked)
        assertFalse(Achievement.LANDSLIDE in photoFinish.newlyUnlocked)
        assertFalse(Achievement.PHOTO_FINISH in unlocked(finishedGame(player(total = 202), bot(total = 200))), "winning by 2 is not a photo finish")

        // A tie at the top counts as a win for the human.
        val tie = evaluate(finishedGame(player(total = 200), bot(total = 200)))
        assertTrue(Achievement.FIRST_VICTORY in tie.newlyUnlocked)
        // A tie is a 0-point margin, not a 1-point one - Photo Finish is exactly 1, no more no less.
        assertFalse(Achievement.PHOTO_FINISH in tie.newlyUnlocked)
        assertFalse(Achievement.I_ROBOT in tie.newlyUnlocked)
        // Identical scorecards either side - the house rule has nothing to break here, so this is still a true,
        // unbroken tie, not a Tie Break win.
        assertFalse(Achievement.TIE_BREAK in tie.newlyUnlocked)

        // Matching the top score with fewer 5x wins Tie Break.
        val tieBreakWon = unlocked(finishedGame(player(total = 200, fiveOfAKindBonusCount = 0), bot(total = 200, fiveOfAKindBonusCount = 1)))
        assertTrue(Achievement.FIRST_VICTORY in tieBreakWon)
        assertTrue(Achievement.TIE_BREAK in tieBreakWon)
        // Losing the tie-break is losing, full stop - a raw-score tie the house rule then decides against player 1
        // must not count as a win anywhere, FIRST_VICTORY included.
        val tieBreakLost = evaluate(finishedGame(player(total = 200, fiveOfAKindBonusCount = 1), bot(total = 200, fiveOfAKindBonusCount = 0)))
        assertFalse(Achievement.FIRST_VICTORY in tieBreakLost.newlyUnlocked, "the bot won the tie-break, not player 1")
        assertFalse(Achievement.TIE_BREAK in tieBreakLost.newlyUnlocked, "the bot won the tie-break, not player 1")
        assertEquals(0, tieBreakLost.counters[AchievementCounter.GAMES_WON])
        // Tie Break needs an opponent to tie with.
        assertFalse(Achievement.TIE_BREAK in unlocked(finishedGame(player(total = 200, fiveOfAKindBonusCount = 0))))

        // Losing by exactly one point is Pipped to the Post - losing by more is not.
        assertTrue(Achievement.PIPPED_TO_THE_POST in unlocked(finishedGame(player(total = 199), bot(total = 200))))
        assertFalse(Achievement.PIPPED_TO_THE_POST in unlocked(finishedGame(player(total = 195), bot(total = 200))))
    }

    @Test
    fun `three Hard AI opponents and not finishing last are needed for How Do You Do Fellow CPUs`() {
        fun table(difficulty: Difficulty) = finishedGame(
            player(total = 300),
            player(name = "Bot 1", type = PlayerType.AI, total = 100, difficulty = difficulty),
            player(name = "Bot 2", type = PlayerType.AI, total = 100, difficulty = difficulty),
            player(name = "Bot 3", type = PlayerType.AI, total = 100, difficulty = difficulty),
        )

        val medium = evaluate(table(Difficulty.MEDIUM))
        val hard = evaluate(table(Difficulty.HARD))

        assertTrue(Achievement.LAST_HUMAN_STANDING in medium.newlyUnlocked)
        assertFalse(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS in medium.newlyUnlocked)
        assertTrue(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS in hard.newlyUnlocked)
        assertTrue(Achievement.FULL_TABLE in hard.newlyUnlocked)

        // Not winning is fine, so long as somebody scored less - but last place (or tied for it) is not.
        fun placed(humanTotal: Int) = evaluate(
            finishedGame(
                player(total = humanTotal),
                player(name = "Bot 1", type = PlayerType.AI, total = 100, difficulty = Difficulty.HARD),
                player(name = "Bot 2", type = PlayerType.AI, total = 200, difficulty = Difficulty.HARD),
                player(name = "Bot 3", type = PlayerType.AI, total = 300, difficulty = Difficulty.HARD),
            ),
        )
        assertTrue(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS in placed(150).newlyUnlocked)
        assertFalse(Achievement.LAST_HUMAN_STANDING in placed(150).newlyUnlocked)
        assertFalse(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS in placed(50).newlyUnlocked)
        assertFalse(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS in placed(100).newlyUnlocked)
    }

    // ---- The scorecard ----------------------------------------------------------------------------

    @Test
    fun `scorecard feats - 5x - a scratched 5x - Spotless and the all-zero and low-score achievements`() {
        // 5x counts the box and every bonus chip after it.
        val fiveX = evaluate(finishedGame(player(total = 400, fiveOfAKindBonusCount = 2, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50))))
        assertEquals(3, fiveX.counters[AchievementCounter.SCORED_5X])
        assertTrue(Achievement.FIVE_X in fiveX.newlyUnlocked)
        assertTrue(Achievement.ENCORE in fiveX.newlyUnlocked)
        assertTrue(Achievement.HAT_TRICK in fiveX.newlyUnlocked)
        assertTrue(Achievement.HIGH_ROLLER in fiveX.newlyUnlocked)

        val scratched = unlocked(finishedGame(player(total = 120, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 0))))
        assertTrue(Achievement.SCRATCHED in scratched)
        assertFalse(Achievement.FIVE_X in scratched)
        assertFalse(Achievement.SPOTLESS in scratched)

        // The lowest score the rules allow earns every bad-game achievement at once - and zeroes everything but Chance.
        val lowest = unlocked(finishedGame(player(total = 5)))
        assertTrue(Achievement.ROCK_BOTTOM in lowest)
        assertTrue(Achievement.LOW_ROLLS in lowest)
        assertTrue(Achievement.COLD_DICE in lowest)
        assertTrue(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in lowest)
        assertFalse(Achievement.SOLID_ROUND in lowest)

        // Zeroing everything but Chance is its own achievement: Chance is the one box that cannot be zeroed.
        assertTrue(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in unlocked(finishedGame(player(total = 30))))
        assertFalse(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in unlocked(finishedGame(player(total = 35, overrides = mapOf(ScoreCategory.FIVES to 5)))))

        // Spotless and How Do You Play This Game are mutually exclusive.
        val spotless = unlocked(finishedGame(player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 })))
        assertTrue(Achievement.SPOTLESS in spotless)
        assertFalse(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in spotless)

        // Low Rolls is a range but Extreme Low Rolls is exactly 5.
        val nineteen = unlocked(finishedGame(player(total = 19)))
        assertTrue(Achievement.LOW_ROLLS in nineteen)
        assertFalse(Achievement.ROCK_BOTTOM in nineteen)
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

        assertTrue(Achievement.SOLID_ROUND in unlocked(finishedGame(player(total = 200))), "200 should unlock Solid Round")
        assertTrue(Achievement.SOLID_ROUND in unlocked(finishedGame(player(total = 250))), "250 is over 200, so it should unlock Solid Round")
        assertTrue(Achievement.SHARPSHOOTER in unlocked(finishedGame(player(total = 300))), "300 should unlock Sharpshooter")

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
        assertTrue(Achievement.LOWER_CLASS in update)
        assertFalse(Achievement.UPPER_CLASS in update)

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
        assertFalse(Achievement.LOWER_CLASS in unlockedMidGame(midGamePlayer(withFiveX)))
    }

    @Test
    fun `how the game went - Personal Best - Comeback Kid - Jaws Of Victory - Zero To Hero - Impatient and Naturally Gifted`() {
        val state = finishedGame(player(total = 250))
        assertTrue(Achievement.NEW_PERSONAL_BEST in unlocked(state, GameAchievementContext(previousBestScore = 240)))
        assertFalse(Achievement.NEW_PERSONAL_BEST in unlocked(state, GameAchievementContext(previousBestScore = 260)))
        assertFalse(Achievement.NEW_PERSONAL_BEST in unlocked(state, GameAchievementContext(previousBestScore = null)))

        // Comeback Kid needs both the trailing position and the win; Jaws Of Victory both the lead and the loss.
        val won = finishedGame(player(total = 200), bot(total = 190))
        val lost = finishedGame(player(total = 180), bot(total = 190))
        val trailed = GameAchievementContext(trailedIntoFinalRound = true)
        val led = GameAchievementContext(ledIntoFinalRound = true)
        assertTrue(Achievement.COMEBACK_KID in unlocked(won, trailed))
        assertFalse(Achievement.COMEBACK_KID in unlocked(lost, trailed))
        assertFalse(Achievement.COMEBACK_KID in unlocked(won))
        assertTrue(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY in unlocked(lost, led))
        assertFalse(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY in unlocked(won, led))
        assertFalse(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY in unlocked(lost))

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
        assertTrue(Achievement.DICE_DEITY in handSet.newlyUnlocked)
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
    fun `the achievements list - unique ids - one unbroken run per category and Easter Eggs hidden until earned`() {
        // The ids are the storage and Play Games keys.
        val ids = Achievement.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // Each is its constant's name in lower case, so storage, code and the title all use one name.
        Achievement.entries.forEach { assertEquals(it.name.lowercase(), it.id, it.name) }

        // Declaration order is display order, so a new entry appended to the end of the enum instead of filed into its
        // theme would silently split that theme across the list.
        assertEquals(AchievementCategory.entries.toList(), Achievement.entries.map { it.category }.distinct())

        // The Easter Eggs are hidden achievements (description "???") and none gates Completionist.
        val easterEggs = listOf(
            Achievement.ARE_YOU_A_DEVELOPER,
            Achievement.BIG_FAN,
            Achievement.EGGCELLENT_DISCOVERY,
            Achievement.GREENFINGERS,
            Achievement.LUCK_OF_THE_IRISH,
            Achievement.NOT_THOSE_DICE,
            Achievement.ROLLING_THE_MIDNIGHT_DICE,
            Achievement.SHAKEN_NOT_TAPPED,
            Achievement.THE_MAGICIANS_SECRET,
            Achievement.THE_SOLUTION,
        )
        assertEquals(easterEggs, Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS })
        easterEggs.forEach {
            assertEquals(AchievementVisibility.HIDDEN, it.visibility)
            assertFalse(it in Achievement.COMPLETION_REQUIREMENTS, "$it gates Completionist")
        }
        // How Do You Do, Fellow CPUs? counts toward Completionist - Completionist does not count toward itself.
        assertTrue(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS in Achievement.COMPLETION_REQUIREMENTS)
        assertFalse(Achievement.COMPLETIONIST in Achievement.COMPLETION_REQUIREMENTS)

        // Miscellaneous and Easter Eggs are the only categories with hidden visibility (title visible, description "???").
        Achievement.entries.forEach { achievement ->
            val where = "${achievement.name}: category=${achievement.category}, visibility=${achievement.visibility}"
            val isHiddenCategory = achievement.category == AchievementCategory.MISCELLANEOUS || achievement.category == AchievementCategory.EASTER_EGGS
            assertEquals(isHiddenCategory, achievement.visibility == AchievementVisibility.HIDDEN, where)
        }
    }

    @Test
    fun `the achievements list - the score ladder - the first-roll feats and the Game Modes are each in order`() {
        assertEquals(
            listOf(
                Achievement.NEW_PERSONAL_BEST,
                Achievement.TON,
                Achievement.SOLID_ROUND,
                Achievement.SHARPSHOOTER,
                Achievement.HIGH_ROLLER,
                Achievement.DICE_DEITY,
            ),
            Achievement.entries.filter { it.category == AchievementCategory.SCORING },
        )

        // The first-roll feats are listed together - least unlikely first - adjacent, not merely in order.
        val misc = Achievement.entries.filter { it.category == AchievementCategory.MISCELLANEOUS }
        assertEquals(1, misc.indexOf(Achievement.STRAIGHT_AWAY) - misc.indexOf(Achievement.HOUSE_CALL))

        // The Game Modes achievements are ordered and count towards Completionist.
        val gameModes = Achievement.entries.filter { it.category == AchievementCategory.GAME_MODES }
        assertEquals(
            listOf(
                Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES,
                Achievement.TRICOLOURFUL,
                Achievement.TRICOLOUR_ME_IMPRESSED,
                Achievement.GONE_WITH_THE_WIND,
                Achievement.THIRD_TIMES_THE_CHARM,
                Achievement.HOLD_EM,
                Achievement.LUCKY_SEVEN,
                Achievement.NAT_20,
                Achievement.CRITICAL_SUCCESS,
                Achievement.CONTRACT_FULFILLED,
                Achievement.RIGHT_ON_TARGET,
                Achievement.QUICK_ON_THE_DRAW,
                Achievement.SIX_OF_THE_BEST,
                Achievement.RAINY_DAY_FUND,
                Achievement.BAD_OMEN,
                Achievement.GRAND_TOUR,
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
        val alreadyUnlocked = evaluate(finishedGame(player()), before = AchievementsState(unlockedAt = mapOf(Achievement.PRACTICE_MAKES_PERFECT to 1L)))
        assertFalse(Achievement.PRACTICE_MAKES_PERFECT in alreadyUnlocked.newlyUnlocked)
        assertTrue(Achievement.IMPATIENT in alreadyUnlocked.newlyUnlocked)

        // Dice rolled accumulate towards Well Rolled.
        val rolled = GameAchievementContext(diceRolledByPlayerOne = 25)
        assertEquals(25, evaluate(finishedGame(player()), rolled).counters[AchievementCounter.DICE_ROLLED])
        assertEquals(65, evaluate(finishedGame(player()), rolled, AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to 40))).counters[AchievementCounter.DICE_ROLLED])

        // Completionist unlocks once everything it waits on is done.
        val everythingElse = Achievement.COMPLETION_REQUIREMENTS.filterNot { it == Achievement.PRACTICE_MAKES_PERFECT }.associateWith { 1L }
        val last = evaluate(finishedGame(player()), before = AchievementsState(unlockedAt = everythingElse, counters = mapOf(AchievementCounter.GAMES_PLAYED to 100)))
        assertTrue(Achievement.PRACTICE_MAKES_PERFECT in last.newlyUnlocked)
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
        val now = AchievementEngine.unlockNow(setOf(Achievement.STRAIGHT_AWAY), AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 3)), NOW)
        assertEquals(listOf(Achievement.STRAIGHT_AWAY), now.newlyUnlocked)
        assertEquals(3, now.counters[AchievementCounter.GAMES_PLAYED])
        assertTrue(now.progressed.isEmpty())
        assertEquals(mapOf(Achievement.STRAIGHT_AWAY to NOW), now.unlockedAt())
    }

    @Test
    fun `progress is announced every step of a streak - but only at the milestones of a total`() {
        // A win streak announces progress every single step. ON_A_ROLL is at 2 of 3 too, so it also reports - but only
        // ones still locked do.
        val streak = evaluate(finishedGame(player(total = 200), bot(total = 100)), before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 1)))
        assertEquals(2, streak.progressed.single { it.achievement == Achievement.UNTOUCHABLE }.current)
        assertTrue(streak.progressed.any { it.achievement == Achievement.ON_A_ROLL })

        // A cumulative total only announces progress at milestones: 1 -> 2 of 100 isn't worth interrupting anyone for,
        // 24 -> 25 of 100 crosses the first quarter.
        fun gamesProgressed(before: Int) = evaluate(finishedGame(player()), before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to before)))
            .progressed.any { it.achievement == Achievement.CENTURION }
        assertFalse(gamesProgressed(1))
        assertTrue(gamesProgressed(24))

        // Well Rolled announces progress every 1,000 dice - not every quarter of its 10,000 target: 990 -> 995 doesn't
        // cross a thousand, 999 -> 1,000 does, nowhere near the default cadence's 2,500-dice first quarter.
        fun diceProgressed(before: Int, rolled: Int) = evaluate(
            finishedGame(player()),
            context = GameAchievementContext(diceRolledByPlayerOne = rolled),
            before = AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to before)),
        ).progressed.any { it.achievement == Achievement.WELL_ROLLED }
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

        // Hall Of Famer announces progress every 5 wins - not every quarter of its 25 target:
        // 4 -> 5 crosses a multiple of 5, 3 -> 4 does not.
        fun winsProgressed(before: Int) = evaluate(
            finishedGame(player(total = 200), bot(total = 100)),
            before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_WON to before)),
        ).progressed.any { it.achievement == Achievement.HALL_OF_FAMER }
        assertFalse(winsProgressed(before = 3))
        assertTrue(winsProgressed(before = 4))

        // An achievement being unlocked right now does not also report progress.
        val unlocking = evaluate(finishedGame(player()), before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 9)))
        assertTrue(Achievement.GETTING_COMFORTABLE in unlocking.newlyUnlocked)
        assertFalse(unlocking.progressed.any { it.achievement == Achievement.GETTING_COMFORTABLE })
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
        // Only a game that goes on the Leaderboard: one in a mode off it, or with a modifier, is never shown there.
        assertTrue(Achievement.TALLY in unlocked(finishedGame(player(total = 42, gameMode = GameMode.TRICOLOUR)), context(allButOne)))
        assertFalse(Achievement.TALLY in unlocked(finishedGame(player(total = 42, gameMode = GameMode.HIT_LIST)), context(allButOne)))
        assertFalse(Achievement.TALLY in unlocked(finishedGame(player(total = 42, gameMode = GameMode.QUICKFIRE)), context(allButOne)))
        assertFalse(Achievement.TALLY in unlocked(finishedGame(player(total = 42)).copy(turnTimer = TurnTimer.SECONDS_30), context(allButOne)))
        assertFalse(
            evaluate(finishedGame(player(total = 16, gameMode = GameMode.HIT_LIST)), context((5..15).toSet())).progressed.any { it.achievement == Achievement.TALLY },
            "no progress banner for a score that never reaches the board",
        )

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

    @Test
    fun `Full Set - a 5x of every number - collected across games - a banner for each new number`() {
        fun numbers(vararg values: Int) = GameAchievementContext(fiveOfAKindNumbers = values.toSet())
        fun flags(vararg values: Int) = values.fold(0) { flags, value -> flags or (1 shl (value - 1)) }

        // Each game's numbers join those already stored: 1, 2 and 6 stored, plus 2 and 4 this game, is four of six.
        val partial = evaluate(
            finishedGame(player()),
            numbers(2, 4),
            AchievementsState(counters = mapOf(AchievementCounter.FIVE_X_NUMBERS to flags(1, 2, 6))),
        )
        assertEquals(flags(1, 2, 4, 6), partial.counters[AchievementCounter.FIVE_X_NUMBERS])
        assertFalse(Achievement.FULL_SET in partial.newlyUnlocked)
        assertEquals(AchievementProgress(Achievement.FULL_SET, 3, 4), partial.progressed.single { it.achievement == Achievement.FULL_SET })
        assertEquals(4, AchievementEngine.progressOf(Achievement.FULL_SET, partial.counters, LeaderboardTotals()))

        // A number already stored is no progress, and no banner.
        val repeat = evaluate(finishedGame(player()), numbers(6), AchievementsState(counters = mapOf(AchievementCounter.FIVE_X_NUMBERS to flags(6))))
        assertEquals(flags(6), repeat.counters[AchievementCounter.FIVE_X_NUMBERS])
        assertFalse(repeat.progressed.any { it.achievement == Achievement.FULL_SET })

        // The sixth number completes the set.
        val complete = evaluate(
            finishedGame(player()),
            numbers(3),
            AchievementsState(counters = mapOf(AchievementCounter.FIVE_X_NUMBERS to flags(1, 2, 4, 5, 6))),
        )
        assertTrue(Achievement.FULL_SET in complete.newlyUnlocked)
        assertTrue(Achievement.FULL_SET in Achievement.COMPLETION_REQUIREMENTS)
    }

    @Test
    fun `Grand Tour - a game finished in every mode - measured against player 1's recorded games`() {
        val allButHitList = (GameMode.entries - GameMode.HIT_LIST).toSet()
        fun context(modes: Set<GameMode>) = GameAchievementContext(previousLeaderboard = LeaderboardTotals(modesPlayed = modes))

        // The board is read before this game's rows go in, so the engine adds this game's own mode itself.
        assertFalse(Achievement.GRAND_TOUR in unlocked(finishedGame(player()), context(allButHitList)))
        assertTrue(Achievement.GRAND_TOUR in unlocked(finishedGame(player(gameMode = GameMode.HIT_LIST)), context(allButHitList)))
        // Solo or not, with modifiers or not: a finished game in the mode is all it takes.
        val modified = finishedGame(player(gameMode = GameMode.HIT_LIST)).copy(turnTimer = TurnTimer.SECONDS_30, extendedScores = true)
        assertTrue(Achievement.GRAND_TOUR in unlocked(modified, context(allButHitList)))

        // A banner for every new mode, none for one already played.
        fun progressed(before: Set<GameMode>, mode: GameMode) =
            evaluate(finishedGame(player(gameMode = mode)), context(before)).progressed.singleOrNull { it.achievement == Achievement.GRAND_TOUR }
        assertEquals(AchievementProgress(Achievement.GRAND_TOUR, 1, 2), progressed(setOf(GameMode.STANDARD), GameMode.STUD))
        assertEquals(null, progressed(setOf(GameMode.STANDARD), GameMode.STANDARD))

        assertEquals(7, Achievement.GRAND_TOUR.target)
        assertEquals(3, AchievementEngine.progressOf(Achievement.GRAND_TOUR, emptyMap(), LeaderboardTotals(modesPlayed = setOf(GameMode.STANDARD, GameMode.D20, GameMode.STUD))))
    }

    // ---- Game start and mid-game --------------------------------------------------------------------
    // Gathering GameStartContext's booleans needs an async SettingsRepository read only GameViewModel can do (see
    // checkGameStartAchievements), but deciding what they earn is this pure engine's job, same as
    // evaluate/evaluateInProgress for the rest of a game.

    @Test
    fun `Hands Off - 200 or more in Standard with no die held - not 199 - not after a hold - not in another mode or with a modifier`() {
        fun earned(game: GameState) = Achievement.HANDS_OFF in unlocked(game)
        assertTrue(earned(finishedGame(player(total = 200))))
        assertFalse(earned(finishedGame(player(total = 199))))
        assertFalse(earned(finishedGame(player(total = 250).copy(diceHeld = 1))))
        // Only player 1's holds count: the CPU holding its dice is no matter.
        assertTrue(earned(finishedGame(player(total = 250), bot(total = 100).copy(diceHeld = 30))))
        assertFalse(earned(finishedGame(player(total = 250, gameMode = GameMode.TRICOLOUR))))
        assertFalse(earned(finishedGame(player(total = 250)).copy(turnTimer = TurnTimer.SECONDS_60)))
        assertFalse(earned(finishedGame(player(total = 250)).copy(extendedScores = true)))
        // Player 1's own score: a 200 by the CPU beside a lower one of theirs is no help.
        assertFalse(earned(finishedGame(player(total = 150), bot(total = 260))))
    }

    @Test
    fun `game start - Rolling The Midnight Dice from midnight up to 6am - not at 6 - not on a resumed game`() {
        fun earnedAt(hour: Int?) =
            Achievement.ROLLING_THE_MIDNIGHT_DICE in AchievementEngine.evaluateAtGameStart(GameStartContext(startedAtLocalHour = hour), AchievementsState(), NOW).newlyUnlocked
        for (hour in 0..5) assertTrue(earnedAt(hour), "a game started at hour $hour")
        for (hour in listOf(6, 12, 23)) assertFalse(earnedAt(hour), "a game started at hour $hour")
        // A resumed game has no start hour.
        assertFalse(earnedAt(null))
    }

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
        // Starting any non-Standard mode unlocks Rules - and Standard does not.
        assertTrue(atStart(GameStartContext(gameMode = GameMode.STANDARD)).isEmpty)
        assertEquals(listOf(Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES), atStart(GameStartContext(gameMode = GameMode.TRICOLOUR)).newlyUnlocked)
        assertEquals(listOf(Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES), atStart(GameStartContext(gameMode = GameMode.QUICKFIRE)).newlyUnlocked)

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
        assertTrue(Achievement.SIX_APPEAL in unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.SIXES to 30))))
        assertFalse(Achievement.SIX_APPEAL in unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.ONES to 1)), midGamePlayer(mapOf(ScoreCategory.SIXES to 30), type = PlayerType.AI)))

        // 30 + 50 + 200 bonus = 280 - already well past the 200 rung, but leaving this game now records nothing on the
        // leaderboard, so SOLID_ROUND must not have fired off a total that's about to disappear.
        val banked = unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.CHANCE to 30, ScoreCategory.FIVE_OF_A_KIND to 50), fiveOfAKindBonusCount = 2))
        assertTrue(Achievement.FIVE_X in banked)
        assertTrue(Achievement.ENCORE in banked)
        assertTrue(Achievement.HAT_TRICK in banked)
        assertTrue(Achievement.TAKING_A_CHANCE in banked)
        assertFalse(Achievement.SOLID_ROUND in banked, "a score threshold must wait for the game to actually finish")

        // An almost-empty card is not "Spotless", "Cold Dice" or a solo win - those need an ending.
        val almostEmpty = unlockedMidGame(midGamePlayer(mapOf(ScoreCategory.ONES to 3)))
        assertFalse(Achievement.SPOTLESS in almostEmpty)
        assertFalse(Achievement.COLD_DICE in almostEmpty)
        assertFalse(Achievement.LOW_ROLLS in almostEmpty)
        assertFalse(Achievement.PRACTICE_MAKES_PERFECT in almostEmpty)

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
    private fun greenfingersAfter(rolls: Int, mode: GameMode = GameMode.STANDARD, vararg others: PlayerState, growingFlowerpot: Boolean = true): Boolean {
        val me = midGamePlayer(emptyMap(), gameMode = mode).copy(rollCount = rolls)
        val players = arrayOf(if (mode == GameMode.QUICKFIRE) me.copy(disabledCategories = QUICKFIRE_OFF) else me, *others)
        return Achievement.GREENFINGERS in unlockedMidGame(*players, growingFlowerpotInPlay = growingFlowerpot)
    }

    @Test
    fun `a full bloom earns Greenfingers only on the Flowerpot that grows its plant`() {
        assertTrue(greenfingersAfter(39))
        assertFalse(greenfingersAfter(39, growingFlowerpot = false))
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

    @Test
    fun `winning a non-Standard game with every modifier on unlocks Everything Everywhere All At Once`() {
        fun table(
            mode: GameMode = GameMode.TRICOLOUR,
            humanTotal: Int = 300,
            timer: TurnTimer = TurnTimer.SECONDS_30,
            rolls: RollModifiers = RollModifiers(rollsPerTurn = 2, storedRolls = true),
            extended: Boolean = true,
            unlucky: UnluckyDice? = UnluckyDice(),
            withBot: Boolean = true,
        ): GameState {
            val players = listOfNotNull(player(total = humanTotal, gameMode = mode), if (withBot) bot(total = 200, gameMode = mode) else null)
            return finishedGame(*players.toTypedArray()).copy(turnTimer = timer, rollModifiers = rolls, extendedScores = extended, unluckyDice = unlucky)
        }

        fun earned(state: GameState) = Achievement.EVERYTHING_EVERYWHERE_ALL_AT_ONCE in unlocked(state)
        assertTrue(earned(table()))
        assertFalse(earned(table(mode = GameMode.STANDARD)), "Standard")
        assertFalse(earned(table(humanTotal = 100)), "a loss")
        assertFalse(earned(table(withBot = false)), "solo")
        assertFalse(earned(table(timer = TurnTimer.NONE)), "no timer")
        assertFalse(earned(table(rolls = RollModifiers(storedRolls = true))), "no Number of Rolls")
        assertFalse(earned(table(rolls = RollModifiers(rollsPerTurn = 2))), "no Stored Rolls")
        assertFalse(earned(table(extended = false)), "no Extended Scores")
        assertFalse(earned(table(unlucky = null)), "no Unlucky Dice")
    }

    // ---- Game modes ------------------------------------------------------------------------------

    @Test
    fun `each mode's win achievement needs a win in that mode against an opponent`() {
        fun table(mode: GameMode, humanTotal: Int, withBot: Boolean = true) =
            if (withBot) finishedGame(player(total = humanTotal, gameMode = mode), bot(total = 200, gameMode = mode)) else finishedGame(player(total = humanTotal, gameMode = mode))

        for ((mode, achievement) in listOf(GameMode.TRICOLOUR to Achievement.TRICOLOURFUL, GameMode.QUICKFIRE to Achievement.QUICK_ON_THE_DRAW, GameMode.STUD to Achievement.HOLD_EM)) {
            assertTrue(achievement in unlocked(table(mode, 300)), "$achievement for a win")
            assertFalse(achievement in unlocked(table(mode, 100)), "$achievement for a loss")
            // Same rule as First Victory and the win counter: a solo game has nobody to beat.
            assertFalse(achievement in unlocked(table(mode, 300, withBot = false)), "$achievement solo")
            assertFalse(achievement in unlocked(table(GameMode.STANDARD, 300)), "$achievement for a Standard win")
        }

        val bot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 }
        val strongBot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 30 }
        assertTrue(Achievement.GONE_WITH_THE_WIND in unlocked(finishedGame(thirdWindNoZeroes(), bot)))
        assertFalse(Achievement.GONE_WITH_THE_WIND in unlocked(finishedGame(thirdWindNoZeroes(), strongBot)))
        assertFalse(Achievement.GONE_WITH_THE_WIND in unlocked(finishedGame(thirdWindNoZeroes())))
        assertFalse(Achievement.GONE_WITH_THE_WIND in unlocked(finishedGame(player(total = 250), bot(total = 100))))

        fun hitListWin(state: GameState) = Achievement.CONTRACT_FULFILLED in unlocked(state)
        assertTrue(hitListWin(finishedGame(hitListPlayer(total = 200), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))))
        assertFalse(hitListWin(finishedGame(hitListPlayer(total = 100), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))))
        assertFalse(hitListWin(finishedGame(hitListPlayer(total = 200))))
        assertFalse(hitListWin(finishedGame(player(total = 200), bot(total = 150))))
    }

    @Test
    fun `Tricolour - all four colour boxes scored is Tricolour Me Impressed and How Do You Play This Game zeroes them too`() {
        // It unlocks mid-game, the moment the fourth goes in.
        val threeOfFour = mapOf(ScoreCategory.REDS to 40, ScoreCategory.YELLOWS to 40, ScoreCategory.BLUES to 40)
        assertFalse(Achievement.TRICOLOUR_ME_IMPRESSED in unlockedMidGame(midGamePlayer(threeOfFour, gameMode = GameMode.TRICOLOUR)))
        assertTrue(Achievement.TRICOLOUR_ME_IMPRESSED in unlockedMidGame(midGamePlayer(threeOfFour + (ScoreCategory.COLOURED_HOUSE to 25), gameMode = GameMode.TRICOLOUR)))
        // A zero in any colour box doesn't count.
        val zeroYellow = mapOf(ScoreCategory.REDS to 40, ScoreCategory.YELLOWS to 0, ScoreCategory.BLUES to 40, ScoreCategory.COLOURED_HOUSE to 25)
        assertFalse(Achievement.TRICOLOUR_ME_IMPRESSED in unlockedMidGame(midGamePlayer(zeroYellow, gameMode = GameMode.TRICOLOUR)))

        assertFalse(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in unlocked(finishedGame(player(total = 45, overrides = mapOf(ScoreCategory.REDS to 40), gameMode = GameMode.TRICOLOUR))))
        assertTrue(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in unlocked(finishedGame(player(total = 5, gameMode = GameMode.TRICOLOUR))))
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

        assertTrue(Achievement.SIX_OF_THE_BEST in unlocked(game(GameMode.QUICKFIRE, 150)))
        assertFalse(Achievement.SIX_OF_THE_BEST in unlocked(game(GameMode.QUICKFIRE, 149)))
        assertFalse(Achievement.SIX_OF_THE_BEST in unlocked(game(GameMode.STANDARD, 200)))
        assertFalse(Achievement.SIX_OF_THE_BEST in unlocked(game(GameMode.QUICKFIRE, 200).copy(rollModifiers = RollModifiers(rollsPerTurn = 5))))
        assertFalse(Achievement.SIX_OF_THE_BEST in unlocked(game(GameMode.QUICKFIRE, 200).copy(extendedScores = true)))
        val lost = quickfireGame(quickfirePlayer(total = 160), quickfirePlayer(total = 200, name = "Bot", type = PlayerType.AI))
        assertTrue(Achievement.SIX_OF_THE_BEST in unlocked(lost))
    }

    @Test
    fun `a scaled Quickfire upper bonus doesn't unlock Bonus Round mid-game - a Standard one does`() {
        // The other full-card feats Quickfire guards are in the guarded table below.
        val onlySixes = GameMode.QUICKFIRE.categories.filter { it != ScoreCategory.SIXES && it.section == ScoreSection.UPPER }.toSet() + ScoreCategory.FIVE_OF_A_KIND
        val bonus = midGamePlayer(mapOf(ScoreCategory.SIXES to 18), gameMode = GameMode.QUICKFIRE).copy(disabledCategories = onlySixes)
        assertEquals(35, bonus.upperSectionBonus)
        assertFalse(Achievement.BONUS_ROUND in unlockedMidGame(bonus))
        assertTrue(Achievement.BONUS_ROUND in unlockedMidGame(midGamePlayer(PlayerState.UPPER_CATEGORIES.associateWith { 3 * (PlayerState.UPPER_CATEGORIES.indexOf(it) + 1) })))
    }

    // ---- The turn timer ------------------------------------------------------------------------------

    @Test
    fun `a turn timed out is a zero for nothing - so the low and zeroed cards and Impatient need a game without one`() {
        // Five on the card, every box but Chance a zero: all of these, played out by hand.
        val rockBottom = finishedGame(player(total = 5))
        val lowAndZeroed = listOf(
            Achievement.HOW_DO_YOU_PLAY_THIS_GAME, Achievement.ROCK_BOTTOM, Achievement.LOW_ROLLS, Achievement.COLD_DICE,
            Achievement.SCRATCHED, Achievement.IMPATIENT,
        )
        val byHand = unlocked(rockBottom)
        for (achievement in lowAndZeroed) assertTrue(achievement in byHand, "$achievement by hand")

        // One timeout, a zero in the 5x box, and none of them - not even Scratched 5x, whose only zero the timer took.
        val timedOut = unlocked(rockBottom, GameAchievementContext(playerOneTimeouts = 1, playerOneTimedOutZeroes = listOf(ScoreCategory.FIVE_OF_A_KIND)))
        for (achievement in lowAndZeroed) assertFalse(achievement in timedOut, "$achievement with a turn timed out")
        // A timeout that zeroed some other box leaves the 5x zero player 1's own.
        assertTrue(Achievement.SCRATCHED in unlocked(rockBottom, GameAchievementContext(playerOneTimeouts = 1, playerOneTimedOutZeroes = listOf(ScoreCategory.ONES))))
        // Nor is a winner's Naturally Gifted (never rolling twice) earned by never rolling at all.
        val won = finishedGame(player(total = 200), bot(total = 150))
        assertTrue(Achievement.NATURALLY_GIFTED in unlocked(won))
        assertFalse(Achievement.NATURALLY_GIFTED in unlocked(won, GameAchievementContext(playerOneTimeouts = 1)))
    }

    @Test
    fun `Zero To Hero counts only the zeroes player 1 took - and Scratched 5x mid-game only one the timer didn't score`() {
        val threeZeroes = player(total = 200, overrides = mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to 0, ScoreCategory.SIXES to 30))
        val won = finishedGame(threeZeroes, bot(total = 100))
        // The player() card zeroes every box it isn't told to fill - so count them, then hand all but two to the timer.
        val zeroes = threeZeroes.allScores.count { it == 0 }
        fun timedOut(count: Int) = GameAchievementContext(playerOneTimeouts = count, playerOneTimedOutZeroes = List(count) { ScoreCategory.ONES })
        assertTrue(Achievement.ZERO_TO_HERO in unlocked(won, timedOut(zeroes - 3)))
        assertFalse(Achievement.ZERO_TO_HERO in unlocked(won, timedOut(zeroes - 2)))

        val scratched = midGamePlayer(mapOf(ScoreCategory.FIVE_OF_A_KIND to 0))
        assertTrue(Achievement.SCRATCHED in unlockedMidGame(scratched))
        val byTimer = AchievementEngine.evaluateInProgress(inProgress(scratched), AchievementsState(), NOW, listOf(ScoreCategory.FIVE_OF_A_KIND)).newlyUnlocked
        assertFalse(Achievement.SCRATCHED in byTimer)
    }

    // ---- Modifiers ----------------------------------------------------------------------------------

    private fun modifiedGame(vararg players: PlayerState) = finishedGame(*players.map { it.copy(rollsModified = true) }.toTypedArray())
        .copy(rollModifiers = RollModifiers(storedRolls = true))

    private fun extendedGame(vararg players: PlayerState) =
        finishedGame(*players.map { it.copy(extendedScores = true, scorecard = it.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) }) }.toTypedArray())
            .copy(extendedScores = true)

    @Test
    fun `a single roll chosen with Number of Rolls isn't Impatient - nor Naturally Gifted for a win - but Quickfire's three rolls still are`() {
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
    fun `How Do You Play This Game needs Extended Scores' boxes zeroed too`() {
        val standardZeroes = player(total = 5)
        val allZeroes = standardZeroes.copy(extendedScores = true, scorecard = standardZeroes.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) })
        val withEvens = allZeroes.copy(scorecard = allZeroes.scorecard + (ScoreCategory.EVENS to listOf(12)))
        assertFalse(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in unlocked(finishedGame(withEvens).copy(extendedScores = true)))
        assertTrue(Achievement.HOW_DO_YOU_PLAY_THIS_GAME in unlocked(finishedGame(allZeroes).copy(extendedScores = true)))
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
        assertTrue(Achievement.THIRD_TIMES_THE_CHARM in thirdWind)
        assertFalse(Achievement.SPOTLESS in thirdWind)

        val oneZero = thirdWindPlayer { category, slot -> if (category == ScoreCategory.LARGE_STRAIGHT && slot == 2) 0 else 10 }
        assertFalse(Achievement.THIRD_TIMES_THE_CHARM in unlocked(finishedGame(oneZero)))

        val standard = unlocked(finishedGame(player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 })))
        assertTrue(Achievement.SPOTLESS in standard)
        assertFalse(Achievement.THIRD_TIMES_THE_CHARM in standard)
    }

    @Test
    fun `Third Wind's 5x box and Sixes count per slot`() {
        // Three 50s in its 5x box are a Hat Trick with no bonus chip.
        val hatTrick = evaluate(finishedGame(thirdWindPlayer { category, _ -> if (category == ScoreCategory.FIVE_OF_A_KIND) 50 else 10 }))
        assertEquals(3, hatTrick.counters[AchievementCounter.SCORED_5X])
        assertTrue(Achievement.ENCORE in hatTrick.newlyUnlocked)
        assertTrue(Achievement.HAT_TRICK in hatTrick.newlyUnlocked)

        // Six Appeal needs a 30 in one Sixes slot - not three slots adding up to it.
        assertTrue(Achievement.SIX_APPEAL in unlocked(finishedGame(thirdWindPlayer { category, slot -> if (category == ScoreCategory.SIXES && slot == 1) 30 else 6 })))
        assertFalse(Achievement.SIX_APPEAL in unlocked(finishedGame(thirdWindPlayer { category, slot -> if (category == ScoreCategory.SIXES) listOf(12, 12, 6)[slot] else 6 })))
    }

    // ---- Stud: the best five of seven dice score ------------------------------------------------------

    @Test
    fun `Stud's 5x don't add to Total 5x`() {
        val bonuses = player(total = 400, fiveOfAKindBonusCount = 2, gameMode = GameMode.STUD)
        val stud = evaluate(finishedGame(bonuses))
        assertEquals(0, stud.counters[AchievementCounter.SCORED_5X])

        val standard = evaluate(finishedGame(player(total = 400, fiveOfAKindBonusCount = 2)))
        assertEquals(2, standard.counters[AchievementCounter.SCORED_5X])
        assertTrue(Achievement.ENCORE in standard.newlyUnlocked)
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
        fun earned(scores: Map<ScoreCategory, Int>) = Achievement.RIGHT_ON_TARGET in unlockedMidGame(hitListPlayer(scores = scores))

        assertTrue(earned(mapOf(fiveNumbers to target.exactPoints)))
        assertFalse(earned(mapOf(fiveNumbers to target.points)))
        assertFalse(earned(mapOf(withAnyPlace to anyTarget.exactPoints)))
        // The Alibi never takes an exact hit's points, whatever it holds.
        assertFalse(earned(mapOf(ScoreCategory.ALIBI to target.points)))
    }

    // ---- D20 --------------------------------------------------------------------------------------------

    /** A finished D20 card: [firstHalf] and [secondHalf] in the first box of each half, every other box a zero. */
    private fun d20Player(
        name: String = "Player 1",
        type: PlayerType = PlayerType.HUMAN,
        firstHalf: Int = 100,
        secondHalf: Int = 300,
        scores: Map<ScoreCategory, Int> = emptyMap(),
    ): PlayerState {
        val filled = GameMode.D20.categories.associateWith { 0 } + (ScoreCategory.D20_1 to firstHalf) + (ScoreCategory.D20_11 to secondHalf) + scores
        return PlayerState(name = name, type = type, gameMode = GameMode.D20, scorecard = oneScoreEach(filled))
    }

    @Test
    fun `Critical Success unlocks mid-game once both D20 halves have earned their bonus - not one half - not with extra rolls - not on another card`() {
        fun earned(player: PlayerState) = Achievement.CRITICAL_SUCCESS in unlockedMidGame(player)
        val bothScored = mapOf(ScoreCategory.D20_1 to 138, ScoreCategory.D20_11 to 388)

        assertTrue(earned(midGamePlayer(bothScored, gameMode = GameMode.D20)))
        // Scored across several boxes, not just one.
        assertTrue(earned(midGamePlayer(mapOf(ScoreCategory.D20_4 to 100, ScoreCategory.D20_10 to 38, ScoreCategory.D20_12 to 200, ScoreCategory.D20_20 to 188), gameMode = GameMode.D20)))
        assertFalse(earned(midGamePlayer(mapOf(ScoreCategory.D20_1 to 138, ScoreCategory.D20_11 to 387), gameMode = GameMode.D20)))
        assertFalse(earned(midGamePlayer(mapOf(ScoreCategory.D20_1 to 137, ScoreCategory.D20_11 to 388), gameMode = GameMode.D20)))
        assertFalse(earned(midGamePlayer(mapOf(ScoreCategory.D20_1 to 138), gameMode = GameMode.D20)))
        // A wildcard counts towards its half: 100 + 38 and 350 + 38 are enough, a point less in either is not.
        val withWildcards = mapOf(ScoreCategory.D20_1 to 100, ScoreCategory.D20_WILD_LOW to 38, ScoreCategory.D20_11 to 350, ScoreCategory.D20_WILD_HIGH to 38)
        assertTrue(earned(midGamePlayer(withWildcards, gameMode = GameMode.D20)))
        assertFalse(earned(midGamePlayer(withWildcards + (ScoreCategory.D20_WILD_LOW to 37), gameMode = GameMode.D20)))
        assertFalse(earned(midGamePlayer(withWildcards + (ScoreCategory.D20_WILD_HIGH to 37), gameMode = GameMode.D20)))
        assertFalse(earned(midGamePlayer(bothScored, gameMode = GameMode.D20).copy(rollsModified = true)))
        assertFalse(earned(midGamePlayer(emptyMap(), gameMode = GameMode.STANDARD)))
        // Only a human earns it.
        assertFalse(earned(midGamePlayer(bothScored, type = PlayerType.AI, gameMode = GameMode.D20)))
    }

    @Test
    fun `Nat 20 needs a win in D20 against an opponent`() {
        fun d20Win(state: GameState) = Achievement.NAT_20 in unlocked(state)
        val bot = d20Player(name = "Bot", type = PlayerType.AI, firstHalf = 50, secondHalf = 100)
        assertTrue(d20Win(finishedGame(d20Player(firstHalf = 100, secondHalf = 300), bot)))
        assertFalse(d20Win(finishedGame(d20Player(firstHalf = 10, secondHalf = 20), bot)))
        assertFalse(d20Win(finishedGame(d20Player())))
        assertFalse(d20Win(finishedGame(player(total = 200), bot(total = 150))))
    }

    // ---- What each mode and modifier guards ------------------------------------------------------------

    /** A card in a mode or with a modifier, what it [unlocked], and the feats it [mustNotEarn] - see .claude/GAME_MODES_AUDIT.md. */
    private class Guarded(val card: String, val unlocked: Collection<Achievement>, val mustNotEarn: List<Achievement>)

    @Test
    fun `each mode and modifier earns none of the feats it guards - though a Standard card like it earns every one`() {
        val ladder = listOf(Achievement.SOLID_ROUND, Achievement.SHARPSHOOTER, Achievement.HIGH_ROLLER, Achievement.DICE_DEITY)
        val sections = listOf(Achievement.UPPER_CLASS, Achievement.LOWER_CLASS)
        val noZero = GameMode.STANDARD.categories.associateWith { 10 }
        val strong = player(total = 520, overrides = GameMode.STANDARD.categories.associateWith { 30 })
        val clean = player(total = 250, overrides = noZero + (ScoreCategory.SIXES to 30))
        val threeZeroes = mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to 0, ScoreCategory.SIXES to 30)
        val previousBest = GameAchievementContext(previousBestScore = 100)
        // Best-of-seven hands that would be feats on a Standard card.
        val feats = mapOf(
            ScoreCategory.FIVE_OF_A_KIND to 50, ScoreCategory.SMALL_STRAIGHT to 30, ScoreCategory.LARGE_STRAIGHT to 40, ScoreCategory.CHANCE to 30,
            ScoreCategory.ONES to 5, ScoreCategory.TWOS to 10, ScoreCategory.THREES to 15, ScoreCategory.FOURS to 20, ScoreCategory.FIVES to 25, ScoreCategory.SIXES to 30,
            ScoreCategory.THREE_OF_A_KIND to 30, ScoreCategory.FOUR_OF_A_KIND to 30, ScoreCategory.FULL_HOUSE to 25,
        )
        val studFeats = listOf(
            Achievement.FIVE_X, Achievement.STRAIGHT_TALKER, Achievement.TAKING_A_CHANCE, Achievement.SIX_APPEAL, Achievement.BONUS_ROUND,
            Achievement.UPPER_CLASS, Achievement.LOWER_CLASS, Achievement.SOLID_ROUND, Achievement.SHARPSHOOTER,
        )

        // The Standard cards the guarded ones are measured against: between them, every guarded feat.
        val earnedInStandard = listOf(
            unlocked(finishedGame(strong)),
            unlocked(finishedGame(clean)),
            unlocked(finishedGame(player(total = 90))),
            unlocked(finishedGame(player(total = 15))),
            unlocked(finishedGame(player(total = 5))),
            unlocked(finishedGame(player(total = 200, overrides = threeZeroes), bot(total = 100))),
            unlocked(finishedGame(player(total = 400)), previousBest),
            unlocked(finishedGame(player(total = 340, overrides = feats))),
            unlocked(finishedGame(player(total = 400, fiveOfAKindBonusCount = 2, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50)))),
        ).flatten().toSet()

        val quickfireOn = GameMode.QUICKFIRE.categories - QUICKFIRE_OFF
        val quickfireUpper = quickfirePlayer(
            overrides = mapOf(ScoreCategory.SIXES to 30, ScoreCategory.FIVES to 25, ScoreCategory.FOURS to 20, ScoreCategory.THREES to 15),
            off = setOf(ScoreCategory.FIVE_OF_A_KIND, ScoreCategory.ONES, ScoreCategory.TWOS, ScoreCategory.FULL_HOUSE, ScoreCategory.SMALL_STRAIGHT, ScoreCategory.LARGE_STRAIGHT),
        )
        val quickfireLower = quickfirePlayer(
            total = 200,
            overrides = mapOf(ScoreCategory.THREE_OF_A_KIND to 30, ScoreCategory.FOUR_OF_A_KIND to 30, ScoreCategory.LARGE_STRAIGHT to 40, ScoreCategory.SMALL_STRAIGHT to 30),
        )
        val thirdWindBot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 }
        val hitListBot = hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150)
        val d20Bot = d20Player(name = "Bot", type = PlayerType.AI, firstHalf = 50, secondHalf = 100)

        val guarded = listOf(
            // Quickfire: six boxes, a ceiling of 210.
            Guarded("Quickfire with no zero", unlocked(quickfireGame(quickfirePlayer(total = 100, overrides = quickfireOn.associateWith { 10 }))), listOf(Achievement.SPOTLESS)),
            Guarded("Quickfire on 90", unlocked(quickfireGame(quickfirePlayer(total = 90))), listOf(Achievement.COLD_DICE)),
            Guarded("Quickfire on 15", unlocked(quickfireGame(quickfirePlayer(total = 15))), listOf(Achievement.COLD_DICE, Achievement.LOW_ROLLS)),
            Guarded("Quickfire on 5", unlocked(quickfireGame(quickfirePlayer(total = 5))), listOf(Achievement.COLD_DICE, Achievement.LOW_ROLLS, Achievement.ROCK_BOTTOM)),
            Guarded("Quickfire zeroed but Chance", unlocked(quickfireGame(quickfirePlayer(total = 20))), listOf(Achievement.HOW_DO_YOU_PLAY_THIS_GAME)),
            Guarded("Quickfire on 210", unlocked(quickfireGame(quickfirePlayer(total = 210))), listOf(Achievement.SOLID_ROUND)),
            Guarded("Quickfire's upper boxes on 90", unlocked(quickfireGame(quickfireUpper, off = quickfireUpper.disabledCategories)), listOf(Achievement.UPPER_CLASS)),
            Guarded("Quickfire's lower boxes on 130", unlocked(quickfireGame(quickfireLower)), listOf(Achievement.LOWER_CLASS)),
            // Roll modifiers, Extended Scores and Unlucky Dice.
            Guarded("roll modifiers - a strong card", unlocked(modifiedGame(strong)), ladder + sections),
            Guarded("roll modifiers - a clean card", unlocked(modifiedGame(clean)), listOf(Achievement.SPOTLESS, Achievement.BONUS_ROUND)),
            Guarded("Extended Scores - a strong card", unlocked(extendedGame(strong)), ladder),
            Guarded("Extended Scores - three zeroes in a win", unlocked(extendedGame(player(total = 200, overrides = threeZeroes), bot(total = 100))), listOf(Achievement.ZERO_TO_HERO)),
            Guarded(
                "Unlucky Dice - three zeroes in a win",
                unlocked(finishedGame(player(total = 200, overrides = threeZeroes), bot(total = 100)).copy(unluckyDice = UnluckyDice())),
                listOf(Achievement.ZERO_TO_HERO),
            ),
            // Third Wind: every box three times.
            Guarded("Third Wind - 30 in every slot", unlocked(finishedGame(thirdWindPlayer { _, _ -> 30 })), ladder + sections),
            Guarded("Third Wind - no zero", unlocked(finishedGame(thirdWindNoZeroes()), previousBest), listOf(Achievement.SPOTLESS, Achievement.NEW_PERSONAL_BEST)),
            Guarded(
                "Third Wind - three zeroes in a win",
                unlocked(finishedGame(thirdWindPlayer { category, _ -> if (category == ScoreCategory.LARGE_STRAIGHT) 0 else 10 }, thirdWindBot)),
                listOf(Achievement.ZERO_TO_HERO),
            ),
            // Stud: the best five of seven dice.
            Guarded("Stud - best-of-seven feats", unlocked(finishedGame(player(total = 340, overrides = feats, gameMode = GameMode.STUD))), studFeats),
            Guarded("Stud - best-of-seven feats mid-game", unlockedMidGame(midGamePlayer(feats, gameMode = GameMode.STUD)), studFeats),
            Guarded("Stud - no zero", unlocked(finishedGame(player(total = 130, overrides = GameMode.STUD.categories.associateWith { 10 }, gameMode = GameMode.STUD))), listOf(Achievement.SPOTLESS)),
            Guarded(
                "Stud - three zeroes in a win",
                unlocked(finishedGame(player(total = 200, overrides = threeZeroes, gameMode = GameMode.STUD), bot(total = 100))),
                listOf(Achievement.ZERO_TO_HERO),
            ),
            Guarded("Stud on 400", unlocked(finishedGame(player(total = 400, gameMode = GameMode.STUD)), previousBest), listOf(Achievement.NEW_PERSONAL_BEST)),
            Guarded(
                "Stud - two 5x bonuses",
                unlocked(finishedGame(player(total = 400, fiveOfAKindBonusCount = 2, gameMode = GameMode.STUD))),
                listOf(Achievement.FIVE_X, Achievement.ENCORE, Achievement.HAT_TRICK),
            ),
            // Hit List: misses and partial hits are its usual run.
            Guarded("Hit List on 500", unlocked(finishedGame(hitListPlayer(total = 500))), listOf(Achievement.SOLID_ROUND, Achievement.DICE_DEITY)),
            Guarded("Hit List on 60", unlocked(finishedGame(hitListPlayer(total = 60))), listOf(Achievement.COLD_DICE)),
            Guarded("Hit List on 15", unlocked(finishedGame(hitListPlayer(total = 15))), listOf(Achievement.LOW_ROLLS)),
            Guarded("Hit List - a win with zeroes", unlocked(finishedGame(hitListPlayer(total = 200), hitListBot)), listOf(Achievement.ZERO_TO_HERO)),
            Guarded("Hit List - no zero", unlocked(finishedGame(hitListPlayer(scores = GameMode.HIT_LIST.categories.associateWith { 5 }))), listOf(Achievement.SPOTLESS)),
            // D20: its totals are a different measure. Both halves in: 138 + 388 + 150 = 676.
            Guarded("D20 on 676", unlocked(finishedGame(d20Player(firstHalf = 138, secondHalf = 388))), listOf(Achievement.SOLID_ROUND, Achievement.DICE_DEITY)),
            Guarded("D20 on 30", unlocked(finishedGame(d20Player(firstHalf = 10, secondHalf = 20))), listOf(Achievement.COLD_DICE, Achievement.LOW_ROLLS)),
            Guarded("D20 - a win with zeroes", unlocked(finishedGame(d20Player(firstHalf = 100, secondHalf = 300), d20Bot)), listOf(Achievement.ZERO_TO_HERO)),
            Guarded("D20 - no zero", unlocked(finishedGame(d20Player(scores = GameMode.D20.categories.associateWith { 5 }))), listOf(Achievement.SPOTLESS)),
        )

        val failures = guarded.flatMap { row ->
            row.mustNotEarn.mapNotNull { achievement ->
                when {
                    achievement !in earnedInStandard -> "$achievement: no Standard card here earns it"
                    achievement in row.unlocked -> "$achievement: earned by ${row.card}"
                    else -> null
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object HitListCard {
        /** One Hit List game's targets, drawn the way a real game draws them. */
        val HIT_LIST = GameMode.HIT_LIST.drawHitList(Random(7))
    }
}
