package net.zodac.dicefive.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/** The cloud save's trigger for a game left part-way through: each move off the game screen, and nothing else. */
class LeavingRouteTest {

    @Test
    fun `a request that can wait for the game goes through only while the game screen isn't up`() = runTest {
        var route: String? = "game"
        val passed = flow {
            emit(Unit)
            route = "menu"
            emit(Unit)
            route = null
            emit(Unit)
        }.awayFrom("game") { route }
        assertEquals(2, passed.toList().size)
    }

    @Test
    fun `each move off the route emits once - arriving at it - staying on it - and other moves don't`() = runTest {
        val routes = flowOf("menu", "game", "game", "results", "menu", "game", "menu", "settings", null)
        assertEquals(2, routes.leaving("game").toList().size)
    }

    @Test
    fun `never on the route - nothing`() = runTest {
        assertEquals(0, flowOf("menu", "settings", "menu").leaving("game").toList().size)
    }
}
