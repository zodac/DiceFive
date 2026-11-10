package net.zodac.dicefive.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.app.UnlockedStyle
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles

/** Where a long-pressed styles banner goes: the one category's page its styles are in, or the Styles page. */
class StylesBannerTargetTest {

    private val die = UnlockedStyle(DiceStyles.families[1].name, DiceStyles.noun)
    private val otherDie = UnlockedStyle(DiceStyles.families[2].name, DiceStyles.noun)
    private val mat = UnlockedStyle(DiceMats.families[1].name, DiceMats.noun)

    @Test
    fun `styles all of one category open its page - of several, the Styles page`() {
        assertEquals("styles/dice", Screen.stylesBannerTarget(listOf(die)))
        assertEquals("styles/dice", Screen.stylesBannerTarget(listOf(die, otherDie)))
        assertEquals("styles/mat", Screen.stylesBannerTarget(listOf(mat)))
        assertEquals("styles", Screen.stylesBannerTarget(listOf(die, mat)))
        assertEquals("styles", Screen.stylesBannerTarget(emptyList()))
    }
}
