package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The D20 dice style's icosahedron is numbered like a real D20, and can show every roll a normal die can. */
class D20Test {

    @Test
    fun everyNumberFromOneToTwentyIsOnExactlyOneFace() {
        assertEquals(20, D20.faces.size)
        assertEquals((1..20).toList(), D20.faces.map { it.number }.sorted())
    }

    @Test
    fun oppositeFacesAddUpToTwentyOne() {
        for (face in D20.faces) {
            val opposite = D20.faces.single { (it.centre + face.centre).let { sum -> sum dot sum } < 0.01f }
            assertEquals(21, face.number + opposite.number, "${face.number} is opposite ${opposite.number}")
        }
    }

    @Test
    fun everyRollOfOneToSixFacesTheViewer() {
        for (value in 1..6) {
            val view = D20_VIEWS.getValue(value)
            val front = D20.faces.maxBy { view.cameraSpace(it.normal).z }
            assertEquals(value, front.number)
            assertTrue(view.cameraSpace(front.normal).z > 0.99f)
        }
    }
}
