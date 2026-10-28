package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.app.Breadcrumbs
import net.zodac.dicefive.model.ScoreCategory

/** A game's moves are left as breadcrumbs, for a crash report to say what led up to it - ids and numbers, never names. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameBreadcrumbsTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starting - rolling - holding - releasing - scoring and undoing each leave a breadcrumb`() = runTest {
        val viewModel = gameViewModel(random = Random(1), aiDispatcher = testDispatcher)
        viewModel.setupForm.setPlayerCount(1)
        viewModel.startGame()
        viewModel.rollDice()
        viewModel.toggleHold(2)
        viewModel.toggleHold(2)
        viewModel.commitScore(ScoreCategory.CHANCE)
        viewModel.undo()

        assertEquals(
            listOf("Started standard: HUMAN", "Rolled, player 1", "Held die 3", "Released die 3", "Scored CHANCE, player 1", "Undid"),
            Breadcrumbs.recent.takeLast(6),
        )
    }
}
