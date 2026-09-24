package net.zodac.dicefive.game

import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.AchievementVisibility
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    ): PlayerState {
        val filled = ScoreCategory.entries.associateWith { 0 } + overrides
        val bonusTotal = fiveOfAKindBonusCount * PlayerState.FIVE_OF_A_KIND_BONUS_AMOUNT
        // Whatever the overrides didn't account for is parked in CHANCE to hit the asked-for total.
        val accountedFor = filled.filterKeys { it != ScoreCategory.CHANCE }.values.sum() + bonusTotal
        val scorecard = filled + (ScoreCategory.CHANCE to (total - accountedFor))
        return PlayerState(
            name = name,
            type = type,
            difficulty = difficulty,
            scorecard = scorecard,
            fiveOfAKindBonusCount = fiveOfAKindBonusCount,
        )
    }

    private fun finishedGame(vararg players: PlayerState) =
        GameState(players = players.toList(), isGameOver = true)

    private fun evaluate(
        state: GameState,
        context: GameAchievementContext = GameAchievementContext(),
        before: AchievementsState = AchievementsState(),
    ) = AchievementEngine.evaluate(state, context, before, NOW)

    @Test
    fun `a finished solo game counts as played but neither won nor lost`() {
        val update = evaluate(finishedGame(player(total = 150)))

        assertEquals(1, update.counters[AchievementCounter.GAMES_PLAYED])
        assertEquals(0, update.counters[AchievementCounter.GAMES_WON])
        assertTrue(Achievement.FIRST_GAME in update.newlyUnlocked)
        assertTrue(Achievement.SOLO_GAME in update.newlyUnlocked)
        assertFalse(Achievement.FIRST_WIN in update.newlyUnlocked)
    }

    @Test
    fun `a solo game neither extends nor breaks a win streak`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 2))

        val update = evaluate(finishedGame(player()), before = before)

        assertEquals(2, update.counters[AchievementCounter.WIN_STREAK])
    }

    @Test
    fun `beating an AI wins the game and extends the streak`() {
        val state = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 180))

        val update = evaluate(state)

        assertEquals(1, update.counters[AchievementCounter.GAMES_WON])
        assertEquals(1, update.counters[AchievementCounter.WIN_STREAK])
        assertTrue(Achievement.FIRST_WIN in update.newlyUnlocked)
        assertFalse(Achievement.SINGULARITY in update.newlyUnlocked)
    }

    @Test
    fun `losing to an AI is Singularity and resets the streak`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 4))
        val state = finishedGame(player(total = 120), player(name = "Bot", type = PlayerType.AI, total = 300))

        val update = evaluate(state, before = before)

        assertEquals(0, update.counters[AchievementCounter.WIN_STREAK])
        assertEquals(0, update.counters[AchievementCounter.GAMES_WON])
        assertTrue(Achievement.SINGULARITY in update.newlyUnlocked)
    }

    @Test
    fun `only player 1 earns achievements - another human player's win and score don't count`() {
        // Player 2 (the second player passed in - never player 1) wins big; player 1 barely scores.
        val state = finishedGame(player(name = "P1", total = 60), player(name = "P2", total = 500))

        val update = evaluate(state)

        assertFalse("player 2 winning must not earn player 1 a win", Achievement.FIRST_WIN in update.newlyUnlocked)
        assertFalse("player 2's score must not earn player 1 Sharpshooter", Achievement.SCORE_300 in update.newlyUnlocked)
        assertEquals(0, update.counters[AchievementCounter.GAMES_WON])
    }

    @Test
    fun `losing to another human is not Singularity - that achievement is specifically about an AI`() {
        // Player 1 loses, but the winner is a second human, not an AI - no AI at this table at all.
        val state = finishedGame(player(name = "P1", total = 120), player(name = "P2", total = 300))

        val update = evaluate(state)

        assertFalse(Achievement.SINGULARITY in update.newlyUnlocked)
    }

    @Test
    fun `winning margins pick out Landslide and Photo Finish`() {
        val landslide = evaluate(
            finishedGame(player(total = 300), player(name = "Bot", type = PlayerType.AI, total = 150)),
        )
        val photoFinish = evaluate(
            finishedGame(player(total = 202), player(name = "Bot", type = PlayerType.AI, total = 200)),
        )

        assertTrue(Achievement.WIN_BY_100 in landslide.newlyUnlocked)
        assertFalse(Achievement.WIN_BY_5 in landslide.newlyUnlocked)
        assertTrue(Achievement.WIN_BY_5 in photoFinish.newlyUnlocked)
        assertFalse(Achievement.WIN_BY_100 in photoFinish.newlyUnlocked)
    }

    @Test
    fun `a tie at the top counts as a win for the human`() {
        val state = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 200))

        val update = evaluate(state)

        assertTrue(Achievement.FIRST_WIN in update.newlyUnlocked)
        assertTrue(Achievement.WIN_BY_5 in update.newlyUnlocked)
        assertFalse(Achievement.SINGULARITY in update.newlyUnlocked)
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
        assertFalse(Achievement.I_ROBOT in medium.newlyUnlocked)
        assertTrue(Achievement.I_ROBOT in hard.newlyUnlocked)
        assertTrue(Achievement.FULL_TABLE in hard.newlyUnlocked)
    }

    @Test
    fun `5x counts the box and every bonus chip after it`() {
        val state = finishedGame(
            player(total = 400, fiveOfAKindBonusCount = 2, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50)),
        )

        val update = evaluate(state)

        assertEquals(3, update.counters[AchievementCounter.SCORED_5X])
        assertTrue(Achievement.FIRST_5X in update.newlyUnlocked)
        assertTrue(Achievement.ENCORE_5X in update.newlyUnlocked)
        assertTrue(Achievement.HAT_TRICK_5X in update.newlyUnlocked)
        assertTrue(Achievement.SCORE_400 in update.newlyUnlocked)
    }

    @Test
    fun `a scratched 5x box is its own achievement`() {
        val update = evaluate(finishedGame(player(total = 120, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 0))))

        assertTrue(Achievement.SCRATCHED_5X in update.newlyUnlocked)
        assertFalse(Achievement.FIRST_5X in update.newlyUnlocked)
        assertFalse(Achievement.NO_ZEROES in update.newlyUnlocked)
    }

    @Test
    fun `the lowest score the rules allow earns every bad-game achievement at once`() {
        val update = evaluate(finishedGame(player(total = 5)))

        assertTrue(Achievement.EXTREME_LOW_ROLLS in update.newlyUnlocked)
        assertTrue(Achievement.LOW_ROLLS in update.newlyUnlocked)
        assertTrue(Achievement.SCORE_UNDER_100 in update.newlyUnlocked)
        assertFalse(Achievement.SCORE_200 in update.newlyUnlocked)
    }

    @Test
    fun `zeroing everything but Chance is its own achievement`() {
        // Chance is the one box that cannot be zeroed, so it is the one exception.
        val allZeroed = evaluate(finishedGame(player(total = 30)))
        val oneBoxScored = evaluate(
            finishedGame(player(total = 35, overrides = mapOf(ScoreCategory.FIVES to 5))),
        )

        assertTrue(Achievement.ALL_ZEROES in allZeroed.newlyUnlocked)
        assertFalse(Achievement.ALL_ZEROES in oneBoxScored.newlyUnlocked)
    }

    @Test
    fun `the lowest possible score also zeroes everything but Chance`() {
        val update = evaluate(finishedGame(player(total = 5)))

        assertTrue(Achievement.ALL_ZEROES in update.newlyUnlocked)
        assertTrue(Achievement.EXTREME_LOW_ROLLS in update.newlyUnlocked)
    }

    @Test
    fun `Spotless and How Do You Play This Game are mutually exclusive`() {
        val spotless = evaluate(
            finishedGame(player(total = 250, overrides = ScoreCategory.entries.associateWith { 10 })),
        )

        assertTrue(Achievement.NO_ZEROES in spotless.newlyUnlocked)
        assertFalse(Achievement.ALL_ZEROES in spotless.newlyUnlocked)
    }

    @Test
    fun `Low Rolls is a range but Extreme Low Rolls is exactly 5`() {
        val nineteen = evaluate(finishedGame(player(total = 19)))
        val twenty = evaluate(finishedGame(player(total = 20)))

        assertTrue(Achievement.LOW_ROLLS in nineteen.newlyUnlocked)
        assertFalse(Achievement.EXTREME_LOW_ROLLS in nineteen.newlyUnlocked)
        assertFalse(Achievement.LOW_ROLLS in twenty.newlyUnlocked)
    }

    @Test
    fun `only player 1's own feats and scorecard earn achievements, not a second human's`() {
        val state = finishedGame(
            // Player 1 (first in the list): under 100, no 5x.
            player(name = "Alice", total = 90),
            // Player 2: over 300, a genuine 5x - neither should count towards Alice's achievements.
            player(name = "Bob", total = 320, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50)),
        )

        val update = evaluate(state)

        assertFalse(Achievement.SCORE_300 in update.newlyUnlocked)
        assertFalse(Achievement.FIRST_5X in update.newlyUnlocked)
        assertTrue(Achievement.SCORE_UNDER_100 in update.newlyUnlocked)
    }

    @Test
    fun `New Personal Best only fires when the previous leaderboard best is beaten`() {
        val state = finishedGame(player(total = 250))

        val beaten = evaluate(state, context = GameAchievementContext(previousBestScore = 240))
        val notBeaten = evaluate(state, context = GameAchievementContext(previousBestScore = 260))
        val firstEverGame = evaluate(state, context = GameAchievementContext(previousBestScore = null))

        assertTrue(Achievement.PERSONAL_BEST in beaten.newlyUnlocked)
        assertFalse(Achievement.PERSONAL_BEST in notBeaten.newlyUnlocked)
        assertFalse(Achievement.PERSONAL_BEST in firstEverGame.newlyUnlocked)
    }

    @Test
    fun `Comeback Kid needs both the trailing position and the win`() {
        val won = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 190))
        val lost = finishedGame(player(total = 180), player(name = "Bot", type = PlayerType.AI, total = 190))
        val context = GameAchievementContext(trailedIntoFinalRound = true)

        assertTrue(Achievement.COMEBACK in evaluate(won, context).newlyUnlocked)
        assertFalse(Achievement.COMEBACK in evaluate(lost, context).newlyUnlocked)
        assertFalse(Achievement.COMEBACK in evaluate(won).newlyUnlocked)
    }

    @Test
    fun `Defeat From the Jaws of Victory needs both the lead and the loss`() {
        val lost = finishedGame(player(total = 190), player(name = "Bot", type = PlayerType.AI, total = 200))
        val won = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 190))
        val context = GameAchievementContext(ledIntoFinalRound = true)

        assertTrue(Achievement.JAWS_OF_VICTORY in evaluate(lost, context).newlyUnlocked)
        assertFalse(Achievement.JAWS_OF_VICTORY in evaluate(won, context).newlyUnlocked)
        assertFalse(Achievement.JAWS_OF_VICTORY in evaluate(lost).newlyUnlocked)
    }

    @Test
    fun `losing by exactly one point is Pipped to the Post, losing by more is not`() {
        val byOne = evaluate(finishedGame(player(total = 199), player(name = "Bot", type = PlayerType.AI, total = 200)))
        val byFive = evaluate(finishedGame(player(total = 195), player(name = "Bot", type = PlayerType.AI, total = 200)))

        assertTrue(Achievement.PIPPED_TO_THE_POST in byOne.newlyUnlocked)
        assertFalse(Achievement.PIPPED_TO_THE_POST in byFive.newlyUnlocked)
    }

    @Test
    fun `winning with at least three zeroes on the winning scorecard is Zero to Hero`() {
        // player()'s own defaults are 0 for anything not overridden and not Chance, so every OTHER
        // category needs an explicit non-zero override here - otherwise both scorecards below would
        // already be all zeroes except Chance, and the "only two" case couldn't exist to compare against.
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
        val threeZeroes = evaluate(
            finishedGame(
                player(
                    total = 200,
                    overrides = nonZeroElsewhere + mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to 0),
                ),
                player(name = "Bot", type = PlayerType.AI, total = 150),
            ),
        )
        val onlyTwoZeroes = evaluate(
            finishedGame(
                player(
                    total = 200,
                    overrides = nonZeroElsewhere + mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to 3),
                ),
                player(name = "Bot", type = PlayerType.AI, total = 150),
            ),
        )

        assertTrue(Achievement.ZERO_TO_HERO in threeZeroes.newlyUnlocked)
        assertFalse(Achievement.ZERO_TO_HERO in onlyTwoZeroes.newlyUnlocked)
    }

    @Test
    fun `player 1 playing first-roll-only unlocks Impatient, and Naturally Gifted if they also won`() {
        val won = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 150))
        val lost = finishedGame(player(total = 100), player(name = "Bot", type = PlayerType.AI, total = 150))

        val wonFirstRollOnly = evaluate(won, context = GameAchievementContext(playerOneTookExtraRoll = false))
        val wonWithExtraRolls = evaluate(won, context = GameAchievementContext(playerOneTookExtraRoll = true))
        val lostFirstRollOnly = evaluate(lost, context = GameAchievementContext(playerOneTookExtraRoll = false))

        assertTrue(Achievement.IMPATIENT in wonFirstRollOnly.newlyUnlocked)
        assertTrue(Achievement.NATURALLY_GIFTED in wonFirstRollOnly.newlyUnlocked)
        assertFalse(Achievement.IMPATIENT in wonWithExtraRolls.newlyUnlocked)
        assertFalse(Achievement.NATURALLY_GIFTED in wonWithExtraRolls.newlyUnlocked)
        assertTrue("first-roll-only but lost - Impatient still applies", Achievement.IMPATIENT in lostFirstRollOnly.newlyUnlocked)
        assertFalse("first-roll-only but lost - not a win", Achievement.NATURALLY_GIFTED in lostFirstRollOnly.newlyUnlocked)
    }

    /**
     * Superuser mode used to disqualify a game outright. It no longer does: the cheat is gated on
     * a debug build, so the rule protected nobody, and it made the one tool best placed to test
     * achievements useless for testing them.
     */
    @Test
    fun `a hand-set game still earns its achievements`() {
        val state = finishedGame(player(total = 500, fiveOfAKindBonusCount = 3))

        val update = evaluate(state)

        assertTrue(Achievement.SCORE_500 in update.newlyUnlocked)
        assertEquals(1, update.counters[AchievementCounter.GAMES_PLAYED])
    }

    @Test
    fun `Cheater, Cheater! only unlocks on a literal perfect game`() {
        val perfect = evaluate(finishedGame(player(total = PlayerState.MAX_POSSIBLE_SCORE)))
        val oneShort = evaluate(finishedGame(player(total = PlayerState.MAX_POSSIBLE_SCORE - 1)))
        val greatButNotPerfect = evaluate(finishedGame(player(total = 500)))

        assertTrue(Achievement.CHEATER_CHEATER in perfect.newlyUnlocked)
        assertFalse(Achievement.CHEATER_CHEATER in oneShort.newlyUnlocked)
        assertFalse(Achievement.CHEATER_CHEATER in greatButNotPerfect.newlyUnlocked)
    }

    @Test
    fun `Cheater, Cheater! is secret and does not gate Completionist`() {
        assertEquals(AchievementVisibility.SECRET, Achievement.CHEATER_CHEATER.visibility)
        assertFalse(Achievement.CHEATER_CHEATER in Achievement.COMPLETION_REQUIREMENTS)
        // Every other achievement stays at least title-visible from the start - secrecy is the
        // exception, not the rule.
        assertEquals(
            listOf(Achievement.CHEATER_CHEATER),
            Achievement.entries.filter { it.visibility == AchievementVisibility.SECRET },
        )
    }

    @Test
    fun `an already-unlocked achievement is not unlocked again`() {
        val before = AchievementsState(unlockedAt = mapOf(Achievement.SOLO_GAME to 1L))

        val update = evaluate(finishedGame(player()), before = before)

        assertFalse(Achievement.SOLO_GAME in update.newlyUnlocked)
        assertTrue(Achievement.FIRST_GAME in update.newlyUnlocked)
    }

    @Test
    fun `dice rolled accumulate towards Well Rolled`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to 40))

        val update = evaluate(finishedGame(player()), context = GameAchievementContext(diceRolledByPlayerOne = 25))

        assertEquals(25, update.counters[AchievementCounter.DICE_ROLLED])
        assertEquals(65, evaluate(finishedGame(player()), GameAchievementContext(diceRolledByPlayerOne = 25), before)
            .counters[AchievementCounter.DICE_ROLLED])
    }

    @Test
    fun `a win streak announces progress every single step`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 1))
        val state = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 100))

        val update = evaluate(state, before = before)

        val streakProgress = update.progressed.single { it.achievement == Achievement.STREAK_10 }
        assertEquals(2, streakProgress.current)
        // STREAK_3 is at 2 of 3 too, so it also reports - but only ones still locked do.
        assertTrue(update.progressed.any { it.achievement == Achievement.STREAK_3 })
    }

    @Test
    fun `a cumulative total only announces progress at milestones`() {
        val state = finishedGame(player())

        // 1 -> 2 of 100 is not worth interrupting anyone for.
        val quiet = evaluate(
            state,
            before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 1)),
        )
        // 24 -> 25 of 100 crosses the first quarter.
        val milestone = evaluate(
            state,
            before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 24)),
        )

        assertFalse(quiet.progressed.any { it.achievement == Achievement.GAMES_100 })
        assertTrue(milestone.progressed.any { it.achievement == Achievement.GAMES_100 })
    }

    @Test
    fun `an achievement being unlocked right now does not also report progress`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 9))

        val update = evaluate(finishedGame(player()), before = before)

        assertTrue(Achievement.GAMES_10 in update.newlyUnlocked)
        assertFalse(update.progressed.any { it.achievement == Achievement.GAMES_10 })
    }

    @Test
    fun `Completionist unlocks once everything it waits on is done`() {
        val everythingElse = Achievement.COMPLETION_REQUIREMENTS
            .filterNot { it == Achievement.SOLO_GAME }
            .associateWith { 1L }
        val before = AchievementsState(
            unlockedAt = everythingElse,
            counters = mapOf(AchievementCounter.GAMES_PLAYED to 100),
        )

        val update = evaluate(finishedGame(player()), before = before)

        assertTrue(Achievement.SOLO_GAME in update.newlyUnlocked)
        assertTrue(Achievement.COMPLETIONIST in update.newlyUnlocked)
    }

    @Test
    fun `I Robot counts toward Completionist, Completionist does not count toward itself`() {
        assertTrue(Achievement.I_ROBOT in Achievement.COMPLETION_REQUIREMENTS)
        assertFalse(Achievement.COMPLETIONIST in Achievement.COMPLETION_REQUIREMENTS)
    }

    @Test
    fun `achievement ids are unique - they are the storage and Play Games keys`() {
        val ids = Achievement.entries.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * Declaration order is display order, so a new entry appended to the end of the enum instead
     * of filed into its theme would silently split that theme across the list.
     */
    @Test
    fun `each category is one unbroken run, in category order`() {
        val runs = Achievement.entries.map { it.category }.distinct()

        assertEquals(AchievementCategory.entries.toList(), runs)
    }

    @Test
    fun `the three first-roll feats are listed together, least unlikely first`() {
        val misc = Achievement.entries.filter { it.category == AchievementCategory.MISCELLANEOUS }
        val firstRoll = misc.filter { it.id.contains("first_roll") }

        assertEquals(
            listOf(
                Achievement.FIRST_ROLL_FULL_HOUSE,
                Achievement.FIRST_ROLL_LARGE_STRAIGHT,
                Achievement.FIRST_ROLL_5X,
            ),
            firstRoll,
        )
        // Adjacent, not merely in order.
        assertEquals(1, misc.indexOf(Achievement.FIRST_ROLL_LARGE_STRAIGHT) - misc.indexOf(Achievement.FIRST_ROLL_FULL_HOUSE))
        assertEquals(1, misc.indexOf(Achievement.FIRST_ROLL_5X) - misc.indexOf(Achievement.FIRST_ROLL_LARGE_STRAIGHT))
    }

    /**
     * Miscellaneous is exclusive with hidden visibility, in both directions - see the class doc
     * on [Achievement]. Guards against a new hidden achievement being filed under its subject's
     * usual category, or a normal achievement being left in Miscellaneous.
     */
    @Test
    fun `Miscellaneous category and hidden visibility are exclusive to each other`() {
        Achievement.entries.forEach { achievement ->
            val isMiscellaneous = achievement.category == AchievementCategory.MISCELLANEOUS
            val isHidden = achievement.visibility == AchievementVisibility.HIDDEN

            assertEquals(
                "${achievement.name}: category=${achievement.category}, visibility=${achievement.visibility}",
                isMiscellaneous,
                isHidden,
            )
        }
    }

    /**
     * The one place the trademarked word is allowed to appear in source, because this is what
     * keeps it out of everywhere else - see the ban in CLAUDE.md.
     */
    @Test
    fun `no achievement mentions the trademarked name in anything a player can see`() {
        val visible = Achievement.entries.flatMap { listOf(it.title, it.description) } +
            AchievementCategory.entries.map { it.label }

        assertTrue(visible.none { it.contains("yahtzee", ignoreCase = true) })
    }

    // ---- Score collection -------------------------------------------------------------------

    @Test
    fun `a score band unlocks only once every score in it has been recorded`() {
        val allButOne = ((5..50) - 42).toSet()
        val finalGame = finishedGame(player(total = 42))

        val short = evaluate(
            finishedGame(player(total = 300)),
            context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(distinctScores = allButOne)),
        )
        val complete = evaluate(finalGame, context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(distinctScores = allButOne)))

        assertFalse(Achievement.TALLY in short.newlyUnlocked)
        assertTrue(Achievement.TALLY in complete.newlyUnlocked)
    }

    @Test
    fun `this game's own score counts towards its band`() {
        // The leaderboard read happens before the insert, so the engine has to add it itself.
        val context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(distinctScores = (5..49).toSet()))

        val update = evaluate(finishedGame(player(total = 50)), context = context)

        assertTrue(Achievement.TALLY in update.newlyUnlocked)
    }

    @Test
    fun `bands only count scores inside their own range`() {
        val context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(distinctScores = (5..50).toSet()))

        val update = evaluate(finishedGame(player(total = 300)), context = context)

        assertTrue(Achievement.TALLY in update.newlyUnlocked)
        assertFalse(Achievement.BOOKKEEPER in update.newlyUnlocked)
        assertFalse(Achievement.HISTORIAN in update.newlyUnlocked)
    }

    @Test
    fun `band progress is measured against the leaderboard, not a stored counter`() {
        val scores = (5..27).toSet()

        assertEquals(23, AchievementEngine.progressOf(Achievement.TALLY, emptyMap(), LeaderboardTotals(distinctScores = scores)))
        assertEquals(0, AchievementEngine.progressOf(Achievement.BOOKKEEPER, emptyMap(), LeaderboardTotals(distinctScores = scores)))
        assertEquals(46, AchievementEngine.progressOf(Achievement.TALLY, emptyMap(), LeaderboardTotals(distinctScores = (5..50).toSet())))
    }

    @Test
    fun `a band announces progress at its quarter marks`() {
        // 11 of 46 is under the first quarter; 12 crosses it.
        val context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(distinctScores = (5..15).toSet()))

        val update = evaluate(finishedGame(player(total = 16)), context = context)

        assertTrue(update.progressed.any { it.achievement == Achievement.TALLY && it.current == 12 })
    }

    @Test
    fun `every band's target matches the size of its range`() {
        val bands = Achievement.entries.filter { it.scoreBand != null }

        assertEquals(6, bands.size)
        bands.forEach { assertEquals(it.title, it.scoreBand!!.count(), it.target) }
        assertEquals(46, Achievement.TALLY.target)
        assertEquals(50, Achievement.BOOKKEEPER.target)
    }

    @Test
    fun `the bands tile the whole 5 to 300 range without gaps or overlap`() {
        val covered = Achievement.entries.mapNotNull { it.scoreBand }.flatten().toSet()

        assertEquals((5..300).toSet(), covered)
    }

    @Test
    fun `Lower Class reads the lower section, and lands mid-game`() {
        val lower = mapOf(
            ScoreCategory.THREE_OF_A_KIND to 25,
            ScoreCategory.FOUR_OF_A_KIND to 25,
            ScoreCategory.FULL_HOUSE to 25,
            ScoreCategory.SMALL_STRAIGHT to 30,
            ScoreCategory.LARGE_STRAIGHT to 40,
            ScoreCategory.CHANCE to 25,
        )
        val state = inProgress(midGamePlayer(lower))

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        // 170 in the lower boxes, and nothing later can take it away.
        assertTrue(Achievement.LOWER_150 in update.newlyUnlocked)
        assertFalse(Achievement.UPPER_84 in update.newlyUnlocked)
    }

    @Test
    fun `Ton is exactly 100, and only a finished game can say so`() {
        val exactly = evaluate(finishedGame(player(total = 100)))
        val over = evaluate(finishedGame(player(total = 101)))
        val midGame = AchievementEngine.evaluateInProgress(
            inProgress(midGamePlayer(mapOf(ScoreCategory.CHANCE to 100))),
            AchievementsState(),
            NOW,
        )

        assertTrue(Achievement.TON in exactly.newlyUnlocked)
        assertFalse(Achievement.TON in over.newlyUnlocked)
        assertFalse("a running total can still climb past 100", Achievement.TON in midGame.newlyUnlocked)
    }

    @Test
    fun `Nice is exactly 69`() {
        val exactly = evaluate(finishedGame(player(total = 69)))
        val over = evaluate(finishedGame(player(total = 70)))
        val under = evaluate(finishedGame(player(total = 68)))

        assertTrue(Achievement.NICE in exactly.newlyUnlocked)
        assertFalse(Achievement.NICE in over.newlyUnlocked)
        assertFalse(Achievement.NICE in under.newlyUnlocked)
    }

    @Test
    fun `career points accumulate across every game on the leaderboard`() {
        val nearly = LeaderboardTotals(totalPoints = 99_800)

        val short = evaluate(finishedGame(player(total = 150)), context = GameAchievementContext(previousLeaderboard = nearly))
        val over = evaluate(finishedGame(player(total = 250)), context = GameAchievementContext(previousLeaderboard = nearly))

        assertFalse(Achievement.PROFESSIONAL_ROLLER in short.newlyUnlocked)
        assertTrue(Achievement.PROFESSIONAL_ROLLER in over.newlyUnlocked)
        assertEquals(100_000, AchievementEngine.progressOf(Achievement.PROFESSIONAL_ROLLER, emptyMap(), LeaderboardTotals(totalPoints = 120_000)))
    }

    @Test
    fun `every human at the table adds to career points`() {
        val context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(totalPoints = 99_000))

        val update = evaluate(finishedGame(player(name = "A", total = 500), player(name = "B", total = 500)), context)

        assertTrue(Achievement.PROFESSIONAL_ROLLER in update.newlyUnlocked)
    }

    @Test
    fun `a score ladder is listed in ascending order`() {
        val scoring = Achievement.entries.filter { it.category == AchievementCategory.SCORING }

        assertEquals(
            listOf(
                Achievement.PERSONAL_BEST,
                Achievement.TON,
                Achievement.SCORE_200,
                Achievement.DOUBLE_TON,
                Achievement.SCORE_300,
                Achievement.TRIPLE_TON,
                Achievement.SCORE_400,
                Achievement.SCORE_500,
                Achievement.CHEATER_CHEATER,
            ),
            scoring,
        )
    }

    @Test
    fun `Double Ton and Triple Ton are exact, unlike the or-more rungs next to them`() {
        val exactly200 = evaluate(finishedGame(player(total = 200)))
        val over200 = evaluate(finishedGame(player(total = 250)))
        val exactly300 = evaluate(finishedGame(player(total = 300)))

        assertTrue(Achievement.DOUBLE_TON in exactly200.newlyUnlocked)
        assertTrue("200 or more should still unlock Solid Round", Achievement.SCORE_200 in exactly200.newlyUnlocked)
        assertFalse(Achievement.DOUBLE_TON in over200.newlyUnlocked)
        assertTrue(Achievement.TRIPLE_TON in exactly300.newlyUnlocked)
        assertFalse(Achievement.DOUBLE_TON in exactly300.newlyUnlocked)
    }

    @Test
    fun `Exact Change needs every upper box to hold precisely its own pip count`() {
        val exact = evaluate(
            finishedGame(
                player(
                    total = 21,
                    overrides = mapOf(
                        ScoreCategory.ONES to 1,
                        ScoreCategory.TWOS to 2,
                        ScoreCategory.THREES to 3,
                        ScoreCategory.FOURS to 4,
                        ScoreCategory.FIVES to 5,
                        ScoreCategory.SIXES to 6,
                    ),
                ),
            ),
        )
        // One box over its target - two 2s in Twos instead of one - should not count.
        val oneOff = evaluate(
            finishedGame(
                player(
                    total = 22,
                    overrides = mapOf(
                        ScoreCategory.ONES to 1,
                        ScoreCategory.TWOS to 4,
                        ScoreCategory.THREES to 3,
                        ScoreCategory.FOURS to 4,
                        ScoreCategory.FIVES to 5,
                        ScoreCategory.SIXES to 6,
                    ),
                ),
            ),
        )

        assertTrue(Achievement.EXACT_CHANGE in exact.newlyUnlocked)
        assertFalse(Achievement.EXACT_CHANGE in oneOff.newlyUnlocked)
    }

    // ---- Game start ----------------------------------------------------------------------------
    // Gathering GameStartContext's booleans needs an async SettingsRepository read only
    // GameViewModel can do (see checkGameStartAchievements), but deciding what they earn is this
    // pure engine's job, same as evaluate/evaluateInProgress for the rest of a game.

    @Test
    fun `The Journey Begins unlocks the first time a game starts, never again`() {
        val before = AchievementsState()

        val first = AchievementEngine.evaluateAtGameStart(GameStartContext(), before, NOW)
        val second = AchievementEngine.evaluateAtGameStart(GameStartContext(), AchievementsState(unlockedAt = first.unlockedAt()), NOW)

        assertEquals(listOf(Achievement.THE_JOURNEY_BEGINS), first.newlyUnlocked)
        assertTrue(second.isEmpty)
    }

    @Test
    fun `a non-default style earns its own milestone at game start, per swappable category`() {
        // Already past The Journey Begins, so it doesn't muddy the per-category assertions below.
        val before = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))

        val allDefault = AchievementEngine.evaluateAtGameStart(GameStartContext(), before, NOW)
        val customDice = AchievementEngine.evaluateAtGameStart(GameStartContext(playedNonDefaultDiceStyle = true), before, NOW)
        val customCup = AchievementEngine.evaluateAtGameStart(GameStartContext(playedNonDefaultDiceCupStyle = true), before, NOW)
        val customBackground = AchievementEngine.evaluateAtGameStart(GameStartContext(playedNonDefaultTableBackground = true), before, NOW)

        assertTrue(allDefault.isEmpty)
        assertEquals(listOf(Achievement.STYLE_DICE), customDice.newlyUnlocked)
        assertEquals(listOf(Achievement.STYLE_CUP), customCup.newlyUnlocked)
        assertEquals(listOf(Achievement.STYLE_BACKGROUND), customBackground.newlyUnlocked)
    }

    @Test
    fun `a human P2, P3, or P4 named zodac unlocks Big Fan`() {
        val before = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))

        val withZodac = AchievementEngine.evaluateAtGameStart(GameStartContext(hasHumanPlayerNamedZodac = true), before, NOW)
        val without = AchievementEngine.evaluateAtGameStart(GameStartContext(hasHumanPlayerNamedZodac = false), before, NOW)

        assertEquals(listOf(Achievement.BIG_FAN), withZodac.newlyUnlocked)
        assertTrue(without.isEmpty)
    }

    @Test
    fun `customizing game settings before starting unlocks I Did It My Way`() {
        val before = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))

        val customized = AchievementEngine.evaluateAtGameStart(GameStartContext(customizedGameSettings = true), before, NOW)
        val default = AchievementEngine.evaluateAtGameStart(GameStartContext(customizedGameSettings = false), before, NOW)

        assertEquals(listOf(Achievement.I_DID_IT_MY_WAY), customized.newlyUnlocked)
        assertTrue(default.isEmpty)
    }

    @Test
    fun `game start touches no counters and skips an already-unlocked style`() {
        val before = AchievementsState(unlockedAt = mapOf(Achievement.STYLE_DICE to 1L), counters = mapOf(AchievementCounter.GAMES_PLAYED to 7))

        val update = AchievementEngine.evaluateAtGameStart(GameStartContext(playedNonDefaultDiceStyle = true), before, NOW)

        assertTrue(Achievement.STYLE_DICE !in update.newlyUnlocked)
        assertEquals(7, update.counters[AchievementCounter.GAMES_PLAYED])
    }

    @Test
    fun `unlockNow raises a single mid-game achievement without touching counters`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 3))

        val update = AchievementEngine.unlockNow(setOf(Achievement.FIRST_ROLL_5X), before, NOW)

        assertEquals(listOf(Achievement.FIRST_ROLL_5X), update.newlyUnlocked)
        assertEquals(3, update.counters[AchievementCounter.GAMES_PLAYED])
        assertTrue(update.progressed.isEmpty())
        assertEquals(mapOf(Achievement.FIRST_ROLL_5X to NOW), update.unlockedAt())
    }

    @Test
    fun `a game that is not over yet earns nothing from the end-of-game pass`() {
        val update = evaluate(GameState(players = listOf(player()), isGameOver = false))

        assertTrue(update.isEmpty)
    }

    /** A partially filled card: only the listed categories are scored, the rest still open. */
    private fun midGamePlayer(
        scored: Map<ScoreCategory, Int>,
        fiveOfAKindBonusCount: Int = 0,
        type: PlayerType = PlayerType.HUMAN,
    ) = PlayerState(
        name = "Player 1",
        type = type,
        scorecard = ScoreCategory.entries.associateWith { null } + scored,
        fiveOfAKindBonusCount = fiveOfAKindBonusCount,
    )

    private fun inProgress(vararg players: PlayerState) =
        GameState(players = players.toList(), isGameOver = false)

    @Test
    fun `a maxed box unlocks mid-game, without waiting for the results screen`() {
        val state = inProgress(midGamePlayer(mapOf(ScoreCategory.SIXES to 30)))

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        assertTrue(Achievement.SIXES_30 in update.newlyUnlocked)
    }

    @Test
    fun `a score threshold passed mid-game unlocks immediately - a total only grows`() {
        val state = inProgress(
            midGamePlayer(mapOf(ScoreCategory.CHANCE to 30, ScoreCategory.FIVE_OF_A_KIND to 50), fiveOfAKindBonusCount = 1),
        )

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        assertTrue(Achievement.FIRST_5X in update.newlyUnlocked)
        assertTrue(Achievement.ENCORE_5X in update.newlyUnlocked)
        assertTrue(Achievement.CHANCE_30 in update.newlyUnlocked)
        // 30 + 50 + 100 bonus = 180, so the 200 rung is not reached yet.
        assertFalse(Achievement.SCORE_200 in update.newlyUnlocked)
    }

    @Test
    fun `mid-game never judges what a later turn could still change`() {
        val state = inProgress(midGamePlayer(mapOf(ScoreCategory.ONES to 3)))

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        // An almost-empty card is not "Spotless", "Cold Dice" or a solo win - those need an ending.
        assertFalse(Achievement.NO_ZEROES in update.newlyUnlocked)
        assertFalse(Achievement.SCORE_UNDER_100 in update.newlyUnlocked)
        assertFalse(Achievement.LOW_ROLLS in update.newlyUnlocked)
        assertFalse(Achievement.SOLO_GAME in update.newlyUnlocked)
    }

    @Test
    fun `mid-game touches no counters, so an undo can't inflate a running total`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 7))
        val state = inProgress(midGamePlayer(mapOf(ScoreCategory.SIXES to 30)))

        val update = AchievementEngine.evaluateInProgress(state, before, NOW)

        assertTrue(update.counters.isEmpty())
        assertTrue(update.progressed.isEmpty())
    }

    @Test
    fun `mid-game earns nothing once the game is over, leaving it to the final pass`() {
        val state = finishedGame(player(total = 300))

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        assertTrue(update.isEmpty)
    }

    @Test
    fun `an AI maxing a box earns the device nothing`() {
        val state = inProgress(
            midGamePlayer(mapOf(ScoreCategory.ONES to 1)),
            midGamePlayer(mapOf(ScoreCategory.SIXES to 30), type = PlayerType.AI),
        )

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        assertFalse(Achievement.SIXES_30 in update.newlyUnlocked)
    }
}
