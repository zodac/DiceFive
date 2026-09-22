package net.zodac.dicefive.game

import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementCounter
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
    fun `Low Rolls is a range but Extreme Low Rolls is exactly 5`() {
        val nineteen = evaluate(finishedGame(player(total = 19)))
        val twenty = evaluate(finishedGame(player(total = 20)))

        assertTrue(Achievement.LOW_ROLLS in nineteen.newlyUnlocked)
        assertFalse(Achievement.EXTREME_LOW_ROLLS in nineteen.newlyUnlocked)
        assertFalse(Achievement.LOW_ROLLS in twenty.newlyUnlocked)
    }

    @Test
    fun `any human at the table can earn a feat - achievements are per device`() {
        val state = finishedGame(
            player(name = "Alice", total = 90),
            player(name = "Bob", total = 320, overrides = mapOf(ScoreCategory.FIVE_OF_A_KIND to 50)),
        )

        val update = evaluate(state)

        assertTrue(Achievement.SCORE_300 in update.newlyUnlocked)
        assertTrue(Achievement.FIRST_5X in update.newlyUnlocked)
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
    fun `an already-unlocked achievement is not unlocked again`() {
        val before = AchievementsState(unlockedAt = mapOf(Achievement.SOLO_GAME to 1L))

        val update = evaluate(finishedGame(player()), before = before)

        assertFalse(Achievement.SOLO_GAME in update.newlyUnlocked)
        assertTrue(Achievement.FIRST_GAME in update.newlyUnlocked)
    }

    @Test
    fun `dice rolled accumulate towards Well Rolled`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.DICE_ROLLED to 40))

        val update = evaluate(finishedGame(player()), context = GameAchievementContext(diceRolledByHumans = 25))

        assertEquals(25, update.counters[AchievementCounter.DICE_ROLLED])
        assertEquals(65, evaluate(finishedGame(player()), GameAchievementContext(diceRolledByHumans = 25), before)
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
    fun `I Robot is excluded from Completionist while it cannot be earned`() {
        assertFalse(Achievement.I_ROBOT in Achievement.COMPLETION_REQUIREMENTS)
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
        val dice = Achievement.entries.filter { it.category == AchievementCategory.DICE }
        val firstRoll = dice.filter { it.id.contains("first_roll") }

        assertEquals(
            listOf(
                Achievement.FIRST_ROLL_FULL_HOUSE,
                Achievement.FIRST_ROLL_LARGE_STRAIGHT,
                Achievement.FIRST_ROLL_5X,
            ),
            firstRoll,
        )
        // Adjacent, not merely in order.
        assertEquals(1, dice.indexOf(Achievement.FIRST_ROLL_LARGE_STRAIGHT) - dice.indexOf(Achievement.FIRST_ROLL_FULL_HOUSE))
        assertEquals(1, dice.indexOf(Achievement.FIRST_ROLL_5X) - dice.indexOf(Achievement.FIRST_ROLL_LARGE_STRAIGHT))
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
            context = GameAchievementContext(previousDistinctScores = allButOne),
        )
        val complete = evaluate(finalGame, context = GameAchievementContext(previousDistinctScores = allButOne))

        assertFalse(Achievement.TALLY in short.newlyUnlocked)
        assertTrue(Achievement.TALLY in complete.newlyUnlocked)
    }

    @Test
    fun `this game's own score counts towards its band`() {
        // The leaderboard read happens before the insert, so the engine has to add it itself.
        val context = GameAchievementContext(previousDistinctScores = (5..49).toSet())

        val update = evaluate(finishedGame(player(total = 50)), context = context)

        assertTrue(Achievement.TALLY in update.newlyUnlocked)
    }

    @Test
    fun `bands only count scores inside their own range`() {
        val context = GameAchievementContext(previousDistinctScores = (5..50).toSet())

        val update = evaluate(finishedGame(player(total = 300)), context = context)

        assertTrue(Achievement.TALLY in update.newlyUnlocked)
        assertFalse(Achievement.BOOKKEEPER in update.newlyUnlocked)
        assertFalse(Achievement.HISTORIAN in update.newlyUnlocked)
    }

    @Test
    fun `band progress is measured against the leaderboard, not a stored counter`() {
        val scores = (5..27).toSet()

        assertEquals(23, AchievementEngine.progressOf(Achievement.TALLY, emptyMap(), scores))
        assertEquals(0, AchievementEngine.progressOf(Achievement.BOOKKEEPER, emptyMap(), scores))
        assertEquals(46, AchievementEngine.progressOf(Achievement.TALLY, emptyMap(), (5..50).toSet()))
    }

    @Test
    fun `a band announces progress at its quarter marks`() {
        // 11 of 46 is under the first quarter; 12 crosses it.
        val context = GameAchievementContext(previousDistinctScores = (5..15).toSet())

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
    fun `a score ladder is listed in ascending order`() {
        val scoring = Achievement.entries.filter { it.category == AchievementCategory.SCORING }

        assertEquals(
            listOf(
                Achievement.PERSONAL_BEST,
                Achievement.SCORE_200,
                Achievement.SCORE_300,
                Achievement.SCORE_400,
                Achievement.SCORE_500,
            ),
            scoring,
        )
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
