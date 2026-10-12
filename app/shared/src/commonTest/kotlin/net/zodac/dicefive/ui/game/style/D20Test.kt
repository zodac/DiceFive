package net.zodac.dicefive.ui.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The D20 dice style's icosahedron is numbered like a real D20, and can show every roll of a D20. */
class D20Test {

    @Test
    fun theD20NumbersOneToTwentyOnceEachWithOppositesSummingTo21AndEveryRollOfOneToTwentyFacingTheViewer() {
        // Every number from one to twenty is on exactly one face.
        assertEquals(20, D20.faces.size)
        assertEquals((1..20).toList(), D20.faces.map { it.number }.sorted())

        // Opposite faces add up to twenty one.
        for (face in D20.faces) {
            val opposite = D20.faces.single { (it.centre + face.centre).let { sum -> sum dot sum } < 0.01f }
            assertEquals(21, face.number + opposite.number, "${face.number} is opposite ${opposite.number}")
        }

        // Every roll of one to twenty (D20 mode's) faces the viewer.
        for (value in 1..20) {
            val view = D20_VIEWS.getValue(value)
            val front = D20.faces.maxBy { view.cameraSpace(it.normal).z }
            assertEquals(value, front.number)
            assertTrue(view.cameraSpace(front.normal).z > 0.99f)
        }
    }

    @Test
    fun d20ModeDrawsAnyOtherStyleAsTheWhiteD20ButKeepsAPickedD20Variant() {
        assertEquals(D20WhiteDiceStyle, d20Like(DiceStyles.default))
        assertEquals("d20_white", d20Like(DiceStyles.default).id)
        val blue = D20DiceStyle("d20_blue", androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.White)
        assertEquals(blue, d20Like(blue))
        assertEquals(D20WhiteDiceStyle, d20Like(D20WhiteDiceStyle))
    }
}
