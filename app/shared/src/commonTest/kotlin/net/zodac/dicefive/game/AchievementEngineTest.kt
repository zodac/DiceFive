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

    private fun finishedGame(vararg players: PlayerState) =
        GameState(gameMode = players.first().gameMode, players = players.toList(), isGameOver = true)

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
        assertFalse(Achievement.I_ROBOT in update.newlyUnlocked)
    }

    @Test
    fun `losing to an AI is I Robot and resets the streak`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 4))
        val state = finishedGame(player(total = 120), player(name = "Bot", type = PlayerType.AI, total = 300))

        val update = evaluate(state, before = before)

        assertEquals(0, update.counters[AchievementCounter.WIN_STREAK])
        assertEquals(0, update.counters[AchievementCounter.GAMES_WON])
        assertTrue(Achievement.I_ROBOT in update.newlyUnlocked)
    }

    @Test
    fun `only player 1 earns achievements - another human player's win and score don't count`() {
        // Player 2 (the second player passed in - never player 1) wins big; player 1 barely scores.
        val state = finishedGame(player(name = "P1", total = 60), player(name = "P2", total = 500))

        val update = evaluate(state)

        assertFalse(Achievement.FIRST_WIN in update.newlyUnlocked, "player 2 winning must not earn player 1 a win")
        assertFalse(Achievement.SCORE_300 in update.newlyUnlocked, "player 2's score must not earn player 1 Sharpshooter")
        assertEquals(0, update.counters[AchievementCounter.GAMES_WON])
    }

    @Test
    fun `losing to another human is not I Robot - that achievement is specifically about an AI`() {
        // Player 1 loses, but the winner is a second human, not an AI - no AI at this table at all.
        val state = finishedGame(player(name = "P1", total = 120), player(name = "P2", total = 300))

        val update = evaluate(state)

        assertFalse(Achievement.I_ROBOT in update.newlyUnlocked)
    }

    @Test
    fun `winning margins pick out Landslide and Photo Finish`() {
        val landslide = evaluate(
            finishedGame(player(total = 300), player(name = "Bot", type = PlayerType.AI, total = 150)),
        )
        val photoFinish = evaluate(
            finishedGame(player(total = 201), player(name = "Bot", type = PlayerType.AI, total = 200)),
        )

        assertTrue(Achievement.WIN_BY_100 in landslide.newlyUnlocked)
        assertFalse(Achievement.WIN_BY_5 in landslide.newlyUnlocked)
        assertTrue(Achievement.WIN_BY_5 in photoFinish.newlyUnlocked)
        assertFalse(Achievement.WIN_BY_100 in photoFinish.newlyUnlocked)
    }

    @Test
    fun `Photo Finish is exactly a one-point win - not a close-ish one`() {
        val byTwo = evaluate(
            finishedGame(player(total = 202), player(name = "Bot", type = PlayerType.AI, total = 200)),
        )

        assertFalse(Achievement.WIN_BY_5 in byTwo.newlyUnlocked, "winning by 2 is not a photo finish")
    }

    @Test
    fun `beating one opponent isn't a win if a third player still finished ahead`() {
        // Player 1 outscores the weaker bot, but the strongest one still finished on top overall -
        // "win" means finishing 1st across the whole table, not merely ahead of *someone*.
        val state = finishedGame(
            player(total = 200),
            player(name = "Weak Bot", type = PlayerType.AI, total = 150),
            player(name = "Strong Bot", type = PlayerType.AI, total = 400),
        )

        val update = evaluate(state)

        assertFalse(Achievement.FIRST_WIN in update.newlyUnlocked, "player 1 didn't actually finish 1st, so this must not count as a win")
        assertEquals(0, update.counters[AchievementCounter.GAMES_WON])
    }

    @Test
    fun `a tie at the top counts as a win for the human - but not a one-point win`() {
        val state = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 200))

        val update = evaluate(state)

        assertTrue(Achievement.FIRST_WIN in update.newlyUnlocked)
        // A tie is a 0-point margin, not a 1-point one - Photo Finish is exactly 1, no more no less.
        assertFalse(Achievement.WIN_BY_5 in update.newlyUnlocked)
        assertFalse(Achievement.I_ROBOT in update.newlyUnlocked)
        // Identical scorecards either side - the house rule has nothing to break here, so this is
        // still a true, unbroken tie, not a Tie Break win.
        assertFalse(Achievement.TIE_BREAK in update.newlyUnlocked)
    }

    @Test
    fun `matching the top score with fewer 5x wins Tie Break`() {
        val state = finishedGame(
            player(total = 200, fiveOfAKindBonusCount = 0),
            player(name = "Bot", type = PlayerType.AI, total = 200, fiveOfAKindBonusCount = 1),
        )

        val update = evaluate(state)

        assertTrue(Achievement.FIRST_WIN in update.newlyUnlocked)
        assertTrue(Achievement.TIE_BREAK in update.newlyUnlocked)
    }

    @Test
    fun `matching the top score with more 5x loses the tie-break - no win and no Tie Break`() {
        val state = finishedGame(
            player(total = 200, fiveOfAKindBonusCount = 1),
            player(name = "Bot", type = PlayerType.AI, total = 200, fiveOfAKindBonusCount = 0),
        )

        val update = evaluate(state)

        // Losing the tie-break is losing, full stop - a raw-score tie the house rule then decides
        // against player 1 must not count as a win anywhere, FIRST_WIN included.
        assertFalse(Achievement.FIRST_WIN in update.newlyUnlocked, "the bot won the tie-break, not player 1")
        assertFalse(Achievement.TIE_BREAK in update.newlyUnlocked, "the bot won the tie-break, not player 1")
        assertEquals(0, update.counters[AchievementCounter.GAMES_WON])
    }

    @Test
    fun `Tie Break needs an opponent to tie with - a solo game never unlocks it`() {
        val update = evaluate(finishedGame(player(total = 200, fiveOfAKindBonusCount = 0)))

        assertFalse(Achievement.TIE_BREAK in update.newlyUnlocked)
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
            finishedGame(player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 })),
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
    fun `only player 1's own feats and scorecard earn achievements - not a second human's`() {
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
    fun `losing by exactly one point is Pipped to the Post - losing by more is not`() {
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
    fun `player 1 playing first-roll-only unlocks Impatient - and Naturally Gifted if they also won`() {
        val won = finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 150))
        val lost = finishedGame(player(total = 100), player(name = "Bot", type = PlayerType.AI, total = 150))

        val wonFirstRollOnly = evaluate(won, context = GameAchievementContext(playerOneTookExtraRoll = false))
        val wonWithExtraRolls = evaluate(won, context = GameAchievementContext(playerOneTookExtraRoll = true))
        val lostFirstRollOnly = evaluate(lost, context = GameAchievementContext(playerOneTookExtraRoll = false))

        assertTrue(Achievement.IMPATIENT in wonFirstRollOnly.newlyUnlocked)
        assertTrue(Achievement.NATURALLY_GIFTED in wonFirstRollOnly.newlyUnlocked)
        assertFalse(Achievement.IMPATIENT in wonWithExtraRolls.newlyUnlocked)
        assertFalse(Achievement.NATURALLY_GIFTED in wonWithExtraRolls.newlyUnlocked)
        assertTrue(Achievement.IMPATIENT in lostFirstRollOnly.newlyUnlocked, "first-roll-only but lost - Impatient still applies")
        assertFalse(Achievement.NATURALLY_GIFTED in lostFirstRollOnly.newlyUnlocked, "first-roll-only but lost - not a win")
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
    fun `the Easter Eggs are the only secret achievements - and none gates Completionist`() {
        assertEquals(AchievementVisibility.SECRET, Achievement.BIG_FAN.visibility)
        assertEquals(AchievementVisibility.SECRET, Achievement.LUCK_OF_THE_IRISH.visibility)
        assertEquals(AchievementVisibility.SECRET, Achievement.SHAKEN_NOT_TAPPED.visibility)
        assertFalse(Achievement.BIG_FAN in Achievement.COMPLETION_REQUIREMENTS)
        assertFalse(Achievement.LUCK_OF_THE_IRISH in Achievement.COMPLETION_REQUIREMENTS)
        assertFalse(Achievement.SHAKEN_NOT_TAPPED in Achievement.COMPLETION_REQUIREMENTS)
        assertEquals(AchievementVisibility.SECRET, Achievement.MAGICIANS_SECRET.visibility)
        assertFalse(Achievement.MAGICIANS_SECRET in Achievement.COMPLETION_REQUIREMENTS)
        assertEquals(AchievementVisibility.SECRET, Achievement.NOT_THOSE_DICE.visibility)
        assertFalse(Achievement.NOT_THOSE_DICE in Achievement.COMPLETION_REQUIREMENTS)
        assertEquals(AchievementVisibility.SECRET, Achievement.GREENFINGERS.visibility)
        assertFalse(Achievement.GREENFINGERS in Achievement.COMPLETION_REQUIREMENTS)
        assertEquals(AchievementVisibility.SECRET, Achievement.THE_SOLUTION.visibility)
        assertFalse(Achievement.THE_SOLUTION in Achievement.COMPLETION_REQUIREMENTS)
        // Every other achievement stays at least title-visible from the start - secrecy is the
        // exception, not the rule.
        assertEquals(
            listOf(
                Achievement.BIG_FAN,
                Achievement.GREENFINGERS,
                Achievement.LUCK_OF_THE_IRISH,
                Achievement.NOT_THOSE_DICE,
                Achievement.SHAKEN_NOT_TAPPED,
                Achievement.MAGICIANS_SECRET,
                Achievement.THE_SOLUTION,
            ),
            Achievement.entries.filter { it.visibility == AchievementVisibility.SECRET },
        )
    }

    @Test
    fun `an already-unlocked achievement is not unlocked again`() {
        val before = AchievementsState(unlockedAt = mapOf(Achievement.SOLO_GAME to 1L))

        val update = evaluate(finishedGame(player()), before = before)

        assertFalse(Achievement.SOLO_GAME in update.newlyUnlocked)
        // A different, still-locked achievement the same game also earns, to show the "already
        // unlocked" check is per-achievement, not a blanket freeze on the whole update.
        assertTrue(Achievement.IMPATIENT in update.newlyUnlocked)
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
    fun `Well Rolled announces progress every 1 -000 dice - not every quarter of its 10 -000 target`() {
        val state = finishedGame(player())

        // 990 -> 995 doesn't cross a thousand.
        val quiet = evaluate(
            state,
            context = GameAchievementContext(diceRolledByPlayerOne = 5),
            before = AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to 990)),
        )
        // 999 -> 1,000 does - nowhere near the default cadence's 2,500-dice first quarter.
        val milestone = evaluate(
            state,
            context = GameAchievementContext(diceRolledByPlayerOne = 1),
            before = AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to 999)),
        )

        assertFalse(quiet.progressed.any { it.achievement == Achievement.DICE_10000 })
        assertTrue(milestone.progressed.any { it.achievement == Achievement.DICE_10000 })
    }

    @Test
    fun `Professional Roller announces progress every 1 -000 career points - not every quarter of its 100 -000 target`() {
        // 199 -> 249 doesn't cross a thousand.
        val quiet = evaluate(
            finishedGame(player(total = 50)),
            context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(totalPoints = 199)),
        )
        // 999 -> 1,000 does - nowhere near the default cadence's 25,000-point first quarter.
        val milestone = evaluate(
            finishedGame(player(total = 1)),
            context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(totalPoints = 999)),
        )

        assertFalse(quiet.progressed.any { it.achievement == Achievement.PROFESSIONAL_ROLLER })
        assertTrue(milestone.progressed.any { it.achievement == Achievement.PROFESSIONAL_ROLLER })
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
    fun `force-unlocking Completionist directly once everything else is already unlocked does not double it up`() {
        // The superuser force-unlock path (AchievementsViewModel.forceUnlock) calls unlockNow with
        // exactly the achievement being force-unlocked - here, Completionist itself, in the state a
        // tester forcing it last would realistically be in: every requirement already unlocked.
        val everythingElse = Achievement.COMPLETION_REQUIREMENTS.associateWith { 1L }
        val before = AchievementsState(unlockedAt = everythingElse, counters = emptyMap())

        val update = AchievementEngine.unlockNow(setOf(Achievement.COMPLETIONIST), before, now = 2L)

        assertEquals(1, update.newlyUnlocked.count { it == Achievement.COMPLETIONIST })
    }

    @Test
    fun `I Robot counts toward Completionist - Completionist does not count toward itself`() {
        assertTrue(Achievement.NATURAL_INTELLIGENCE in Achievement.COMPLETION_REQUIREMENTS)
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
    fun `each category is one unbroken run - in category order`() {
        val runs = Achievement.entries.map { it.category }.distinct()

        assertEquals(AchievementCategory.entries.toList(), runs)
    }

    @Test
    fun `the three first-roll feats are listed together - least unlikely first`() {
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
                isMiscellaneous,
                isHidden,
                "${achievement.name}: category=${achievement.category}, visibility=${achievement.visibility}",
            )
        }
    }

    /**
     * Easter Eggs is exclusive with secret visibility, in both directions - see the class doc on
     * [Achievement]. Guards against a new secret achievement being filed under its subject's
     * usual category (which would defeat [AchievementCategory.EASTER_EGGS]'s whole point: its
     * header only ever appearing once something in it is unlocked), or a normal achievement being
     * left in Easter Eggs.
     */
    @Test
    fun `Easter Eggs category and secret visibility are exclusive to each other`() {
        Achievement.entries.forEach { achievement ->
            val isSecretCategory = achievement.category == AchievementCategory.EASTER_EGGS
            val isSecretVisibility = achievement.visibility == AchievementVisibility.SECRET

            assertEquals(
                isSecretCategory,
                isSecretVisibility,
                "${achievement.name}: category=${achievement.category}, visibility=${achievement.visibility}",
            )
        }
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
    fun `band progress is measured against the leaderboard - not a stored counter`() {
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
        bands.forEach { assertEquals(it.scoreBand!!.count(), it.target, it.name) }
        assertEquals(46, Achievement.TALLY.target)
        assertEquals(50, Achievement.BOOKKEEPER.target)
    }

    @Test
    fun `the bands tile the whole 5 to 300 range without gaps or overlap`() {
        val covered = Achievement.entries.mapNotNull { it.scoreBand }.flatten().toSet()

        assertEquals((5..300).toSet(), covered)
    }

    @Test
    fun `Lower Class reads the lower section - and lands mid-game`() {
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
    fun `Lower Class doesn't count the 5x box`() {
        val lower = mapOf(
            ScoreCategory.THREE_OF_A_KIND to 25,
            ScoreCategory.FOUR_OF_A_KIND to 25,
            ScoreCategory.FULL_HOUSE to 25,
            ScoreCategory.SMALL_STRAIGHT to 30,
            ScoreCategory.CHANCE to 35,
            // 140 without the 5x - a scored 5x on top would cross 150 in the raw lower section
            // total, but must not count towards this achievement.
            ScoreCategory.FIVE_OF_A_KIND to 50,
        )
        val state = inProgress(midGamePlayer(lower))

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        assertFalse(Achievement.LOWER_150 in update.newlyUnlocked)
    }

    @Test
    fun `Ton is exactly 100 - and only a finished game can say so`() {
        val exactly = evaluate(finishedGame(player(total = 100)))
        val over = evaluate(finishedGame(player(total = 101)))
        val midGame = AchievementEngine.evaluateInProgress(
            inProgress(midGamePlayer(mapOf(ScoreCategory.CHANCE to 100))),
            AchievementsState(),
            NOW,
        )

        assertTrue(Achievement.TON in exactly.newlyUnlocked)
        assertFalse(Achievement.TON in over.newlyUnlocked)
        assertFalse(Achievement.TON in midGame.newlyUnlocked, "a running total can still climb past 100")
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
    fun `only player 1's score adds to career points - not every human at the table`() {
        // Old score is 400 short of the target on player 1's own points alone (99_400 + 500 =
        // 99_900); adding player 2's 500 as well (the bug this guards against) would have crossed
        // it at 100_400.
        val context = GameAchievementContext(previousLeaderboard = LeaderboardTotals(totalPoints = 99_400))

        val update = evaluate(finishedGame(player(name = "A", total = 500), player(name = "B", total = 500)), context)

        assertFalse(
            Achievement.PROFESSIONAL_ROLLER in update.newlyUnlocked,
            "player 2's score must not count towards player 1's Professional Roller",
        )
    }

    @Test
    fun `a score ladder is listed in ascending order`() {
        val scoring = Achievement.entries.filter { it.category == AchievementCategory.SCORING }

        assertEquals(
            listOf(
                Achievement.PERSONAL_BEST,
                Achievement.TON,
                Achievement.SCORE_200,
                Achievement.SCORE_300,
                Achievement.SCORE_400,
                Achievement.SCORE_500,
            ),
            scoring,
        )
    }

    @Test
    fun `Solid Round and Sharpshooter unlock on exactly 200 or 300 - not only strictly over`() {
        val exactly200 = evaluate(finishedGame(player(total = 200)))
        val over200 = evaluate(finishedGame(player(total = 250)))
        val exactly300 = evaluate(finishedGame(player(total = 300)))

        assertTrue(Achievement.SCORE_200 in exactly200.newlyUnlocked, "200 should unlock Solid Round")
        assertTrue(Achievement.SCORE_200 in over200.newlyUnlocked, "250 is over 200, so it should unlock Solid Round")
        assertTrue(Achievement.SCORE_300 in exactly300.newlyUnlocked, "300 should unlock Sharpshooter")
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
    fun `The Journey Begins unlocks the first time a game starts - never again`() {
        val before = AchievementsState()

        val first = AchievementEngine.evaluateAtGameStart(GameStartContext(), before, NOW)
        val second = AchievementEngine.evaluateAtGameStart(GameStartContext(), AchievementsState(unlockedAt = first.unlockedAt()), NOW)

        assertEquals(listOf(Achievement.THE_JOURNEY_BEGINS), first.newlyUnlocked)
        assertTrue(second.isEmpty)
    }

    @Test
    fun `a non-default style earns Fresh Coat Of Paint at game start`() {
        // Already past The Journey Begins, so it doesn't muddy the assertions below.
        val before = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))

        val allDefault = AchievementEngine.evaluateAtGameStart(GameStartContext(), before, NOW)
        val customStyle = AchievementEngine.evaluateAtGameStart(GameStartContext(playedNonDefaultStyle = true), before, NOW)

        assertTrue(allDefault.isEmpty)
        assertEquals(listOf(Achievement.FRESH_COAT_OF_PAINT), customStyle.newlyUnlocked)
    }

    @Test
    fun `a two-player game with P2 named zodac unlocks Big Fan`() {
        // GameViewModel.checkGameStartAchievements is the one that decides whether this flag is
        // true - it requires a two-player game with a human P2 named exactly "zodac", never P1 and
        // never a 3P/4P game's P2. This engine just trusts the flag it's handed.
        val before = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))

        val withZodac = AchievementEngine.evaluateAtGameStart(GameStartContext(hasHumanPlayerNamedZodac = true), before, NOW)
        val without = AchievementEngine.evaluateAtGameStart(GameStartContext(hasHumanPlayerNamedZodac = false), before, NOW)

        assertEquals(listOf(Achievement.BIG_FAN), withZodac.newlyUnlocked)
        assertTrue(without.isEmpty)
    }

    @Test
    fun `Tricolour with P1 named Ireland unlocks Luck of the Irish`() {
        // GameViewModel.checkGameStartAchievements decides this flag from GameState.isLuckOfTheIrish
        // (see IrishEasterEggTest for the name-matching and player-1/game-mode rules) - this engine
        // just trusts the flag it's handed, same as hasHumanPlayerNamedZodac.
        val before = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))

        val irish = AchievementEngine.evaluateAtGameStart(GameStartContext(hasIrishPlayerOneInTricolour = true), before, NOW)
        val notIrish = AchievementEngine.evaluateAtGameStart(GameStartContext(hasIrishPlayerOneInTricolour = false), before, NOW)

        assertEquals(listOf(Achievement.LUCK_OF_THE_IRISH), irish.newlyUnlocked)
        assertTrue(notIrish.isEmpty)
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
        val before = AchievementsState(
            unlockedAt = mapOf(Achievement.FRESH_COAT_OF_PAINT to 1L),
            counters = mapOf(AchievementCounter.GAMES_PLAYED to 7),
        )

        val update = AchievementEngine.evaluateAtGameStart(GameStartContext(playedNonDefaultStyle = true), before, NOW)

        assertTrue(Achievement.FRESH_COAT_OF_PAINT !in update.newlyUnlocked)
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

    @Test
    fun `a maxed box unlocks mid-game - without waiting for the results screen`() {
        val state = inProgress(midGamePlayer(mapOf(ScoreCategory.SIXES to 30)))

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        assertTrue(Achievement.SIXES_30 in update.newlyUnlocked)
    }

    @Test
    fun `feats already banked in the scorecard unlock mid-game - but a score threshold waits for the actual result`() {
        val state = inProgress(
            // 30 + 50 + 200 bonus = 280 - already well past the 200 rung, but leaving this game
            // now records nothing on the leaderboard, so SCORE_200 must not have fired off a total
            // that's about to disappear.
            midGamePlayer(mapOf(ScoreCategory.CHANCE to 30, ScoreCategory.FIVE_OF_A_KIND to 50), fiveOfAKindBonusCount = 2),
        )

        val update = AchievementEngine.evaluateInProgress(state, AchievementsState(), NOW)

        assertTrue(Achievement.FIRST_5X in update.newlyUnlocked)
        assertTrue(Achievement.ENCORE_5X in update.newlyUnlocked)
        assertTrue(Achievement.HAT_TRICK_5X in update.newlyUnlocked)
        assertTrue(Achievement.CHANCE_30 in update.newlyUnlocked)
        assertFalse(Achievement.SCORE_200 in update.newlyUnlocked, "a score threshold must wait for the game to actually finish")
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
    fun `The Solution needs a solo standard game as Phil Woodward on exactly 255`() {
        fun earned(state: GameState) = Achievement.THE_SOLUTION in evaluate(state).newlyUnlocked

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

    @Test
    fun `Phil Woodward is matched by isPhilWoodward ignoring case and spacing`() {
        assertTrue(net.zodac.dicefive.model.isPhilWoodward("Phil Woodward"))
        assertTrue(net.zodac.dicefive.model.isPhilWoodward(" PHIL\tWoodward "))
        assertFalse(net.zodac.dicefive.model.isPhilWoodward("PhilWoodward"))
    }

    @Test
    fun `mid-game touches no counters - so an undo can't inflate a running total`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 7))
        val state = inProgress(midGamePlayer(mapOf(ScoreCategory.SIXES to 30)))

        val update = AchievementEngine.evaluateInProgress(state, before, NOW)

        assertTrue(update.counters.isEmpty())
        assertTrue(update.progressed.isEmpty())
    }

    @Test
    fun `mid-game earns nothing once the game is over - leaving it to the final pass`() {
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

    // ---- Game modes ------------------------------------------------------------------------------

    @Test
    fun `starting any non-Standard mode unlocks Rules - and Standard does not`() {
        val before = AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 1L))

        val standard = AchievementEngine.evaluateAtGameStart(GameStartContext(gameMode = GameMode.STANDARD), before, NOW)
        val tricolour = AchievementEngine.evaluateAtGameStart(GameStartContext(gameMode = GameMode.TRICOLOUR), before, NOW)
        val quickfire = AchievementEngine.evaluateAtGameStart(GameStartContext(gameMode = GameMode.QUICKFIRE), before, NOW)

        assertTrue(standard.isEmpty)
        assertEquals(listOf(Achievement.NON_STANDARD_MODE), tricolour.newlyUnlocked)
        assertEquals(listOf(Achievement.NON_STANDARD_MODE), quickfire.newlyUnlocked)
    }

    @Test
    fun `winning a multiplayer Tricolour game unlocks Tricolourful`() {
        val win = evaluate(
            finishedGame(
                player(total = 300, gameMode = GameMode.TRICOLOUR),
                player(name = "Bot", type = PlayerType.AI, total = 200, gameMode = GameMode.TRICOLOUR),
            ),
        )
        val loss = evaluate(
            finishedGame(
                player(total = 200, gameMode = GameMode.TRICOLOUR),
                player(name = "Bot", type = PlayerType.AI, total = 300, gameMode = GameMode.TRICOLOUR),
            ),
        )

        assertTrue(Achievement.TRICOLOUR_WIN in win.newlyUnlocked)
        assertFalse(Achievement.TRICOLOUR_WIN in loss.newlyUnlocked)
    }

    @Test
    fun `Tricolourful needs Tricolour mode and an opponent to beat`() {
        val standardWin = evaluate(
            finishedGame(player(total = 300), player(name = "Bot", type = PlayerType.AI, total = 200)),
        )
        // Same rule as First Victory and the win counter: a solo game has nobody to beat.
        val soloTricolour = evaluate(finishedGame(player(total = 300, gameMode = GameMode.TRICOLOUR)))

        assertFalse(Achievement.TRICOLOUR_WIN in standardWin.newlyUnlocked)
        assertFalse(Achievement.TRICOLOUR_WIN in soloTricolour.newlyUnlocked)
    }

    @Test
    fun `scoring all four colour boxes unlocks Tricolour Me Impressed mid-game - the moment the fourth goes in`() {
        val threeOfFour = mapOf(ScoreCategory.REDS to 40, ScoreCategory.YELLOWS to 40, ScoreCategory.BLUES to 40)

        val beforeFourth = AchievementEngine.evaluateInProgress(
            inProgress(midGamePlayer(threeOfFour, gameMode = GameMode.TRICOLOUR)),
            AchievementsState(),
            NOW,
        )
        val afterFourth = AchievementEngine.evaluateInProgress(
            inProgress(midGamePlayer(threeOfFour + (ScoreCategory.COLOURED_HOUSE to 25), gameMode = GameMode.TRICOLOUR)),
            AchievementsState(),
            NOW,
        )

        assertFalse(Achievement.TRICOLOUR_ALL_COLOURS in beforeFourth.newlyUnlocked)
        assertTrue(Achievement.TRICOLOUR_ALL_COLOURS in afterFourth.newlyUnlocked)
    }

    @Test
    fun `a zero in any colour box doesn't count towards Tricolour Me Impressed`() {
        val update = AchievementEngine.evaluateInProgress(
            inProgress(
                midGamePlayer(
                    mapOf(
                        ScoreCategory.REDS to 40,
                        ScoreCategory.YELLOWS to 0,
                        ScoreCategory.BLUES to 40,
                        ScoreCategory.COLOURED_HOUSE to 25,
                    ),
                    gameMode = GameMode.TRICOLOUR,
                ),
            ),
            AchievementsState(),
            NOW,
        )

        assertFalse(Achievement.TRICOLOUR_ALL_COLOURS in update.newlyUnlocked)
    }

    /** Player 1 part-way through a game in [mode], having rolled [rolls] times, as [evaluateInProgress] sees it after a roll. */
    private fun afterRolls(rolls: Int, mode: GameMode = GameMode.STANDARD, vararg others: PlayerState) = AchievementEngine.evaluateInProgress(
        inProgress(midGamePlayer(emptyMap(), gameMode = mode).copy(rollCount = rolls), *others),
        AchievementsState(),
        NOW,
    )

    @Test
    fun `using every roll of every turn unlocks Greenfingers - one roll short does not`() {
        assertTrue(Achievement.GREENFINGERS in afterRolls(39).newlyUnlocked)
        assertFalse(Achievement.GREENFINGERS in afterRolls(38).newlyUnlocked)
        // Tricolour's card is longer, so its sunflower takes every roll of its 17 turns.
        assertTrue(Achievement.GREENFINGERS in afterRolls(51, GameMode.TRICOLOUR).newlyUnlocked)
        assertFalse(Achievement.GREENFINGERS in afterRolls(50, GameMode.TRICOLOUR).newlyUnlocked)
    }

    @Test
    fun `Quickfire's sunflower takes every roll of its six turns - 18 - and one short does not`() {
        fun afterQuickfireRolls(rolls: Int) = AchievementEngine.evaluateInProgress(
            inProgress(midGamePlayer(emptyMap(), gameMode = GameMode.QUICKFIRE).copy(rollCount = rolls, disabledCategories = QUICKFIRE_OFF)),
            AchievementsState(),
            NOW,
        )

        assertTrue(Achievement.GREENFINGERS in afterQuickfireRolls(18).newlyUnlocked)
        assertFalse(Achievement.GREENFINGERS in afterQuickfireRolls(17).newlyUnlocked)
    }

    @Test
    fun `another player's sunflower does not unlock Greenfingers - only player 1 earns achievements`() {
        val playerTwo = midGamePlayer(emptyMap()).copy(name = "Player 2", rollCount = 39)

        assertFalse(Achievement.GREENFINGERS in afterRolls(20, GameMode.STANDARD, playerTwo).newlyUnlocked)
    }

    @Test
    fun `the Game Modes achievements are ordered and count towards Completionist`() {
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

    @Test
    fun `winning a multiplayer Quickfire game unlocks Quick On The Draw - not a loss or a solo game or another mode`() {
        fun game(mode: GameMode, humanTotal: Int, withBot: Boolean = true) = if (withBot) {
            finishedGame(
                player(total = humanTotal, gameMode = mode),
                player(name = "Bot", type = PlayerType.AI, total = 200, gameMode = mode),
            )
        } else {
            finishedGame(player(total = humanTotal, gameMode = mode))
        }

        assertTrue(Achievement.QUICKFIRE_WIN in evaluate(game(GameMode.QUICKFIRE, 300)).newlyUnlocked)
        assertFalse(Achievement.QUICKFIRE_WIN in evaluate(game(GameMode.QUICKFIRE, 100)).newlyUnlocked)
        assertFalse(Achievement.QUICKFIRE_WIN in evaluate(game(GameMode.QUICKFIRE, 300, withBot = false)).newlyUnlocked)
        assertFalse(Achievement.QUICKFIRE_WIN in evaluate(game(GameMode.STANDARD, 300)).newlyUnlocked)
    }

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
    fun `winning a multiplayer game of Stud unlocks Hold Em - and only Stud`() {
        fun game(mode: GameMode, humanTotal: Int, withBot: Boolean = true) = if (withBot) {
            finishedGame(
                player(total = humanTotal, gameMode = mode),
                player(name = "Bot", type = PlayerType.AI, total = 200, gameMode = mode),
            )
        } else {
            finishedGame(player(total = humanTotal, gameMode = mode))
        }

        assertTrue(Achievement.STUD_WIN in evaluate(game(GameMode.STUD, 300)).newlyUnlocked)
        assertFalse(Achievement.STUD_WIN in evaluate(game(GameMode.STUD, 100)).newlyUnlocked)
        assertFalse(Achievement.STUD_WIN in evaluate(game(GameMode.STUD, 300, withBot = false)).newlyUnlocked)
        assertFalse(Achievement.STUD_WIN in evaluate(game(GameMode.STANDARD, 300)).newlyUnlocked)
    }

    @Test
    fun `Impatient and Naturally Gifted aren't handed out when Number of Rolls makes it one roll - Quickfire has three`() {
        fun won(mode: GameMode) = finishedGame(
            player(total = 200, gameMode = mode),
            player(name = "Bot", type = PlayerType.AI, total = 150, gameMode = mode),
        )
        val context = GameAchievementContext(playerOneTookExtraRoll = false)

        val oneRoll = evaluate(won(GameMode.STANDARD).copy(rollModifiers = RollModifiers(rollsPerTurn = 1)), context = context)
        assertFalse(Achievement.IMPATIENT in oneRoll.newlyUnlocked)
        assertFalse(Achievement.NATURALLY_GIFTED in oneRoll.newlyUnlocked)

        val quickfire = evaluate(won(GameMode.QUICKFIRE), context = context)
        assertTrue(Achievement.IMPATIENT in quickfire.newlyUnlocked)
        assertTrue(Achievement.NATURALLY_GIFTED in quickfire.newlyUnlocked)
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

    /** Everything a finished [game] unlocks. */
    private fun unlocked(game: GameState) = evaluate(game).newlyUnlocked

    @Test
    fun `a Quickfire card with no zero doesn't unlock Spotless - a full Standard one does`() {
        val enabled = GameMode.QUICKFIRE.categories - QUICKFIRE_OFF
        val quickfire = quickfirePlayer(total = 100, overrides = enabled.associateWith { 10 })
        val standard = player(total = 100, overrides = GameMode.STANDARD.categories.associateWith { 10 })

        assertFalse(Achievement.NO_ZEROES in unlocked(quickfireGame(quickfire)))
        assertTrue(Achievement.NO_ZEROES in unlocked(finishedGame(standard)))
    }

    @Test
    fun `a low Quickfire total doesn't unlock Cold Dice or Low Rolls or Rock Bottom - a Standard one does`() {
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
    }

    @Test
    fun `zeroing every Quickfire box but Chance doesn't unlock How Do You Play This Game`() {
        assertFalse(Achievement.ALL_ZEROES in unlocked(quickfireGame(quickfirePlayer(total = 20))))
        assertTrue(Achievement.ALL_ZEROES in unlocked(finishedGame(player(total = 20))))
    }

    @Test
    fun `the score ladder isn't climbed in Quickfire however high the total`() {
        val update = unlocked(quickfireGame(quickfirePlayer(total = 210)))

        assertFalse(Achievement.SCORE_200 in update)
        assertTrue(Achievement.SCORE_200 in unlocked(finishedGame(player(total = 210))))
    }

    @Test
    fun `a scaled Quickfire upper bonus doesn't unlock Bonus Round - nor do the section totals unlock Upper or Lower Class`() {
        val onlySixes = GameMode.QUICKFIRE.categories.filter { it != ScoreCategory.SIXES && it.section == ScoreSection.UPPER }.toSet() + ScoreCategory.FIVE_OF_A_KIND
        val bonus = midGamePlayer(mapOf(ScoreCategory.SIXES to 18), gameMode = GameMode.QUICKFIRE).copy(disabledCategories = onlySixes)
        assertEquals(35, bonus.upperSectionBonus)
        assertFalse(Achievement.UPPER_BONUS in AchievementEngine.evaluateInProgress(inProgress(bonus), AchievementsState(), NOW).newlyUnlocked)
        val standard = midGamePlayer(PlayerState.UPPER_CATEGORIES.associateWith { 3 * (PlayerState.UPPER_CATEGORIES.indexOf(it) + 1) })
        assertTrue(Achievement.UPPER_BONUS in AchievementEngine.evaluateInProgress(inProgress(standard), AchievementsState(), NOW).newlyUnlocked)

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

    private fun wonGame(mode: GameMode = GameMode.STANDARD) = finishedGame(
        player(total = 300, gameMode = mode),
        player(name = "Bot", type = PlayerType.AI, total = 200, gameMode = mode),
    )

    private fun luckOfTheDraw(state: GameState, timeouts: Int) =
        Achievement.LUCK_OF_THE_DRAW in evaluate(state, context = GameAchievementContext(playerOneTimeouts = timeouts)).newlyUnlocked

    @Test
    fun `winning with 3 or fewer categories scored yourself unlocks Luck Of The Draw`() {
        // Standard's 13 boxes: 10 timeouts leaves 3 scored by hand.
        assertTrue(luckOfTheDraw(wonGame(), timeouts = 10))
        assertTrue(luckOfTheDraw(wonGame(), timeouts = 13))
        assertFalse(luckOfTheDraw(wonGame(), timeouts = 9))
    }

    @Test
    fun `Luck Of The Draw needs a win against an opponent`() {
        val loss = finishedGame(player(total = 100), player(name = "Bot", type = PlayerType.AI, total = 200))
        val solo = finishedGame(player(total = 300))

        assertFalse(luckOfTheDraw(loss, timeouts = 13))
        assertFalse(luckOfTheDraw(solo, timeouts = 13))
    }

    @Test
    fun `Luck Of The Draw works in every mode - measured against that mode's own card`() {
        // Quickfire's six boxes: 3 timeouts leaves 3 by hand, 2 leaves 4.
        val quickfire = quickfireGame(quickfirePlayer(total = 300), quickfirePlayer(total = 200, name = "Bot", type = PlayerType.AI))
        assertTrue(luckOfTheDraw(quickfire, timeouts = 3))
        assertFalse(luckOfTheDraw(quickfire, timeouts = 2))
        // Tricolour's 17 boxes: 14 timeouts leaves 3 by hand, 13 leaves 4.
        assertTrue(luckOfTheDraw(wonGame(GameMode.TRICOLOUR), timeouts = 14))
        assertFalse(luckOfTheDraw(wonGame(GameMode.TRICOLOUR), timeouts = 13))
    }

    @Test
    fun `How Do You Play This Game in Tricolour needs the colour boxes zeroed too`() {
        val zeroesButRed = evaluate(
            finishedGame(player(total = 45, overrides = mapOf(ScoreCategory.REDS to 40), gameMode = GameMode.TRICOLOUR)),
        )
        val allZeroes = evaluate(finishedGame(player(total = 5, gameMode = GameMode.TRICOLOUR)))

        assertFalse(Achievement.ALL_ZEROES in zeroesButRed.newlyUnlocked)
        assertTrue(Achievement.ALL_ZEROES in allZeroes.newlyUnlocked)
    }

    // ---- Roll modifiers ----------------------------------------------------------------------------

    private fun modifiedGame(vararg players: PlayerState) = finishedGame(*players.map { it.copy(rollsModified = true) }.toTypedArray())
        .copy(rollModifiers = RollModifiers(storedRolls = true))

    @Test
    fun `extra rolls don't hand out the score ladder or the section thresholds`() {
        val strong = player(total = 520, overrides = GameMode.STANDARD.categories.associateWith { 30 })

        val plain = evaluate(finishedGame(strong))
        val modified = evaluate(modifiedGame(strong))

        for (achievement in listOf(Achievement.SCORE_200, Achievement.SCORE_300, Achievement.SCORE_400, Achievement.SCORE_500)) {
            assertTrue(achievement in plain.newlyUnlocked, "$achievement in a plain game")
            assertFalse(achievement in modified.newlyUnlocked, "$achievement with roll modifiers")
        }
        assertTrue(Achievement.UPPER_84 in plain.newlyUnlocked)
        assertFalse(Achievement.UPPER_84 in modified.newlyUnlocked)
        assertTrue(Achievement.LOWER_150 in plain.newlyUnlocked)
        assertFalse(Achievement.LOWER_150 in modified.newlyUnlocked)
    }

    @Test
    fun `extra rolls don't hand out Spotless or Bonus Round`() {
        val clean = player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 } + mapOf(ScoreCategory.SIXES to 30))

        assertTrue(Achievement.NO_ZEROES in evaluate(finishedGame(clean)).newlyUnlocked)
        assertFalse(Achievement.NO_ZEROES in evaluate(modifiedGame(clean)).newlyUnlocked)
        assertTrue(Achievement.UPPER_BONUS in evaluate(finishedGame(clean)).newlyUnlocked)
        assertFalse(Achievement.UPPER_BONUS in evaluate(modifiedGame(clean)).newlyUnlocked)
    }

    @Test
    fun `a single roll chosen with Number of Rolls isn't Impatient`() {
        val game = finishedGame(player()).copy(rollModifiers = RollModifiers(rollsPerTurn = 1))

        assertFalse(Achievement.IMPATIENT in evaluate(game).newlyUnlocked)
        assertTrue(Achievement.IMPATIENT in evaluate(finishedGame(player())).newlyUnlocked)
    }

    // ---- Extended Scores: Two Pair, Evens and Odds ----------------------------------------------

    private fun extendedGame(vararg players: PlayerState) =
        finishedGame(*players.map { it.copy(extendedScores = true, scorecard = it.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) }) }.toTypedArray())
            .copy(extendedScores = true)

    @Test
    fun `Extended Scores doesn't hand out the score ladder`() {
        val strong = player(total = 520, overrides = GameMode.STANDARD.categories.associateWith { 30 })

        val plain = evaluate(finishedGame(strong))
        val extended = evaluate(extendedGame(strong))

        for (achievement in listOf(Achievement.SCORE_200, Achievement.SCORE_300, Achievement.SCORE_400, Achievement.SCORE_500)) {
            assertTrue(achievement in plain.newlyUnlocked, "$achievement in a plain game")
            assertFalse(achievement in extended.newlyUnlocked, "$achievement with Extended Scores")
        }
    }

    @Test
    fun `Extended Scores doesn't hand out Zero To Hero`() {
        val threeZeroes = player(total = 200, overrides = mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to 0, ScoreCategory.SIXES to 30))
        val bot = player(name = "Bot", type = PlayerType.AI, total = 100)

        assertTrue(Achievement.ZERO_TO_HERO in evaluate(finishedGame(threeZeroes, bot)).newlyUnlocked)
        assertFalse(Achievement.ZERO_TO_HERO in evaluate(extendedGame(threeZeroes, bot)).newlyUnlocked)
    }

    @Test
    fun `Unlucky Dice doesn't hand out Zero To Hero`() {
        val threeZeroes = player(total = 200, overrides = mapOf(ScoreCategory.ONES to 0, ScoreCategory.TWOS to 0, ScoreCategory.THREES to 0, ScoreCategory.SIXES to 30))
        val bot = player(name = "Bot", type = PlayerType.AI, total = 100)

        assertFalse(Achievement.ZERO_TO_HERO in evaluate(finishedGame(threeZeroes, bot).copy(unluckyDice = UnluckyDice())).newlyUnlocked)
    }

    @Test
    fun `How Do You Play This Game with Extended Scores needs its boxes zeroed too`() {
        val standardZeroes = player(total = 5)
        val withEvens = standardZeroes.copy(extendedScores = true, scorecard = standardZeroes.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) } + (ScoreCategory.EVENS to listOf(12)))
        val allZeroes = standardZeroes.copy(extendedScores = true, scorecard = standardZeroes.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) })

        assertFalse(Achievement.ALL_ZEROES in evaluate(finishedGame(withEvens).copy(extendedScores = true)).newlyUnlocked)
        assertTrue(Achievement.ALL_ZEROES in evaluate(finishedGame(allZeroes).copy(extendedScores = true)).newlyUnlocked)
    }

    @Test
    fun `Luck Of The Draw counts the Extended Scores turns too`() {
        fun won(extended: Boolean): GameState {
            val me = player(total = 300).let { if (extended) it.copy(extendedScores = true, scorecard = it.scorecard + ScoreCategory.EXTENDED.associateWith { listOf(0) }) else it }
            return finishedGame(me, player(name = "Bot", type = PlayerType.AI, total = 100)).copy(extendedScores = extended)
        }

        // 13 boxes: 10 timeouts leaves 3 by hand. 16 boxes: 13 timeouts does.
        assertTrue(evaluate(won(false), GameAchievementContext(playerOneTimeouts = 10)).newlyUnlocked.contains(Achievement.LUCK_OF_THE_DRAW))
        assertFalse(evaluate(won(true), GameAchievementContext(playerOneTimeouts = 10)).newlyUnlocked.contains(Achievement.LUCK_OF_THE_DRAW))
        assertTrue(evaluate(won(true), GameAchievementContext(playerOneTimeouts = 13)).newlyUnlocked.contains(Achievement.LUCK_OF_THE_DRAW))
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
    fun `Third Time's The Charm is Third Wind's Spotless - and Spotless itself can't be earned there`() {
        val update = evaluate(finishedGame(thirdWindNoZeroes()))

        assertTrue(Achievement.THIRD_WIND_NO_ZEROES in update.newlyUnlocked)
        assertFalse(Achievement.NO_ZEROES in update.newlyUnlocked)
    }

    @Test
    fun `winning a game of Third Wind is Gone With The Wind - not a loss - a solo game or another mode`() {
        val bot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 }
        val strongBot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 30 }

        assertTrue(Achievement.THIRD_WIND_WIN in evaluate(finishedGame(thirdWindNoZeroes(), bot)).newlyUnlocked)
        assertFalse(Achievement.THIRD_WIND_WIN in evaluate(finishedGame(thirdWindNoZeroes(), strongBot)).newlyUnlocked)
        assertFalse(Achievement.THIRD_WIND_WIN in evaluate(finishedGame(thirdWindNoZeroes())).newlyUnlocked)
        assertFalse(
            Achievement.THIRD_WIND_WIN in evaluate(finishedGame(player(total = 250), player(name = "Bot", type = PlayerType.AI, total = 100))).newlyUnlocked,
        )
    }

    @Test
    fun `one zero in any of Third Wind's 39 slots misses Third Time's The Charm`() {
        val oneZero = thirdWindPlayer { category, slot -> if (category == ScoreCategory.LARGE_STRAIGHT && slot == 2) 0 else 10 }

        assertFalse(Achievement.THIRD_WIND_NO_ZEROES in evaluate(finishedGame(oneZero)).newlyUnlocked)
    }

    @Test
    fun `a Standard game without a zero is Spotless - not Third Time's The Charm`() {
        val update = evaluate(finishedGame(player(total = 250, overrides = GameMode.STANDARD.categories.associateWith { 10 })))

        assertTrue(Achievement.NO_ZEROES in update.newlyUnlocked)
        assertFalse(Achievement.THIRD_WIND_NO_ZEROES in update.newlyUnlocked)
    }

    @Test
    fun `Third Wind's tripled totals don't hand out the score or section thresholds`() {
        // 30 in every slot: 540 upper, 870 lower without the 5x box - past every rung in one go.
        val update = evaluate(finishedGame(thirdWindPlayer { _, _ -> 30 }))

        for (achievement in listOf(Achievement.SCORE_200, Achievement.SCORE_300, Achievement.SCORE_400, Achievement.SCORE_500)) {
            assertFalse(achievement in update.newlyUnlocked, "$achievement")
        }
        assertFalse(Achievement.UPPER_84 in update.newlyUnlocked)
        assertFalse(Achievement.LOWER_150 in update.newlyUnlocked)
    }

    @Test
    fun `a Third Wind game is never a New Personal Best - it isn't on the Leaderboard`() {
        val update = evaluate(finishedGame(thirdWindNoZeroes()), context = GameAchievementContext(previousBestScore = 100))

        assertFalse(Achievement.PERSONAL_BEST in update.newlyUnlocked)
    }

    @Test
    fun `winning Third Wind with three zeroes isn't Zero To Hero - over 39 turns that's the usual run of things`() {
        val threeZeroes = thirdWindPlayer { category, _ -> if (category == ScoreCategory.LARGE_STRAIGHT) 0 else 10 }
        val bot = thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 }

        assertFalse(Achievement.ZERO_TO_HERO in evaluate(finishedGame(threeZeroes, bot)).newlyUnlocked)
    }

    @Test
    fun `three 50s in Third Wind's 5x box are a Hat Trick with no bonus chip`() {
        val update = evaluate(finishedGame(thirdWindPlayer { category, _ -> if (category == ScoreCategory.FIVE_OF_A_KIND) 50 else 10 }))

        assertEquals(3, update.counters[AchievementCounter.SCORED_5X])
        assertTrue(Achievement.ENCORE_5X in update.newlyUnlocked)
        assertTrue(Achievement.HAT_TRICK_5X in update.newlyUnlocked)
    }

    @Test
    fun `Six Appeal in Third Wind needs a 30 in one Sixes slot - not three slots adding up to it`() {
        val thirtyInOne = thirdWindPlayer { category, slot -> if (category == ScoreCategory.SIXES && slot == 1) 30 else 6 }
        val thirtyAcrossThree = thirdWindPlayer { category, slot -> if (category == ScoreCategory.SIXES) listOf(12, 12, 6)[slot] else 6 }

        assertTrue(Achievement.SIXES_30 in evaluate(finishedGame(thirtyInOne)).newlyUnlocked)
        assertFalse(Achievement.SIXES_30 in evaluate(finishedGame(thirtyAcrossThree)).newlyUnlocked)
    }

    @Test
    fun `Luck Of The Draw in Third Wind is still 3 or fewer of its 39 turns scored yourself`() {
        val state = finishedGame(thirdWindNoZeroes(), thirdWindPlayer(name = "Bot", type = PlayerType.AI) { _, _ -> 1 })
        fun earned(timeouts: Int) =
            Achievement.LUCK_OF_THE_DRAW in evaluate(state, context = GameAchievementContext(playerOneTimeouts = timeouts)).newlyUnlocked

        assertTrue(earned(36))
        assertFalse(earned(35))
    }

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
        fun earned(scores: Map<ScoreCategory, Int>) = Achievement.HIT_LIST_RIGHT_ON_TARGET in
            AchievementEngine.evaluateInProgress(inProgress(hitListPlayer(scores = scores)), AchievementsState(), NOW).newlyUnlocked

        assertTrue(earned(mapOf(fiveNumbers to target.exactPoints)))
        assertFalse(earned(mapOf(fiveNumbers to target.points)))
        assertFalse(earned(mapOf(withAnyPlace to anyTarget.exactPoints)))
        // The Alibi never takes an exact hit's points, whatever it holds.
        assertFalse(earned(mapOf(ScoreCategory.ALIBI to target.points)))
    }

    @Test
    fun `winning a multiplayer Hit List game unlocks Contract Fulfilled - not a loss or a solo game or another mode`() {
        fun earned(state: GameState) = Achievement.HIT_LIST_WIN in evaluate(state).newlyUnlocked

        assertTrue(earned(finishedGame(hitListPlayer(total = 200), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))))
        assertFalse(earned(finishedGame(hitListPlayer(total = 100), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))))
        assertFalse(earned(finishedGame(hitListPlayer(total = 200))))
        assertFalse(earned(finishedGame(player(total = 200), player(name = "Bot", type = PlayerType.AI, total = 150))))
    }

    @Test
    fun `a Hit List total earns no rung of the score ladder`() {
        val unlocked = evaluate(finishedGame(hitListPlayer(total = 500))).newlyUnlocked

        assertFalse(Achievement.SCORE_200 in unlocked)
        assertFalse(Achievement.SCORE_500 in unlocked)
        assertTrue(Achievement.SCORE_200 in evaluate(finishedGame(player(total = 200))).newlyUnlocked)
    }

    @Test
    fun `a low Hit List total is neither Cold Dice nor Low Rolls`() {
        assertFalse(Achievement.SCORE_UNDER_100 in evaluate(finishedGame(hitListPlayer(total = 60))).newlyUnlocked)
        assertFalse(Achievement.LOW_ROLLS in evaluate(finishedGame(hitListPlayer(total = 15))).newlyUnlocked)
    }

    @Test
    fun `winning Hit List with three zeroes isn't Zero To Hero - misses are the usual run of things there`() {
        // Every box but the first is a zero: far more than three.
        val state = finishedGame(hitListPlayer(total = 200), hitListPlayer(name = "Bot", type = PlayerType.AI, total = 150))

        assertFalse(Achievement.ZERO_TO_HERO in evaluate(state).newlyUnlocked)
    }

    @Test
    fun `a Hit List card without a zero isn't Spotless - partial hits leave few`() {
        val noZeroes = hitListPlayer(scores = GameMode.HIT_LIST.categories.associateWith { 5 })

        assertFalse(Achievement.NO_ZEROES in evaluate(finishedGame(noZeroes)).newlyUnlocked)
        assertTrue(Achievement.NO_ZEROES in evaluate(finishedGame(player(overrides = GameMode.STANDARD.categories.associateWith { 5 }, total = 100))).newlyUnlocked)
    }

    private companion object HitListCard {
        /** One Hit List game's targets, drawn the way a real game draws them. */
        val HIT_LIST = GameMode.HIT_LIST.drawHitList(Random(7))
    }
}
