package net.zodac.dicefive

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.game.TotalsButton
import net.zodac.dicefive.ui.game.TurnTimerBadge
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import net.zodac.dicefive.ui.menu.MenuScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A phone set to Arabic gets the Arabic strings, laid out right to left, with Arabic-Indic digits and grouping, and all of
 * Arabic's plural forms. The first right-to-left language, so these show that machinery working end to end.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w360dp-h800dp")
class ArabicTextTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an Arabic phone gets the Arabic strings - right to left - with Arabic-Indic digits and grouping - and every plural form`() {
        val showcase = Showcase(compose)
        var direction: LayoutDirection? = null
        showcase.show {
            DiceFiveTheme {
                direction = LocalLayoutDirection.current
                MenuScreen(
                    hasInProgressGame = false,
                    onContinue = {}, onNewGame = {}, onScores = {}, onStatistics = {}, onAchievements = {}, onStyles = {}, onRules = {}, onSettings = {},
                )
            }
        }
        assertEquals(LayoutDirection.Rtl, direction)
        listOf("العب", "الإنجازات", "الأنماط", "لوحة المتصدرين", "الإحصائيات", "القواعد", "الإعدادات").forEach { compose.onNodeWithText(it).assertExists() }

        // Thousands are grouped with Arabic-Indic digits.
        showcase.show { DiceFiveTheme { TotalsButton(upperTotal = 70, upperBonus = 35, lowerTotal = 34521) } }
        compose.onNodeWithContentDescription("المجاميع").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("٣٤٬٥٢١").assertExists()

        // The turn timer says the time in the plural form its count needs: 12 is "many" in Arabic (11-99), a singular noun
        // after the number, in Arabic-Indic digits...
        showcase.show { DiceFiveTheme { TurnTimerBadge(secondsRemaining = 12) } }
        compose.onNodeWithContentDescription("الوقت المتبقي: ١٢ ثانية").assertExists()
        // ...and a count in the few form is said with the plural noun.
        showcase.show { DiceFiveTheme { TurnTimerBadge(secondsRemaining = 3) } }
        compose.onNodeWithContentDescription("ينفد الوقت، تبقّت ٥ ثوانٍ أو أقل").assertExists()

        // The leave-game question is in Arabic.
        val confirmation = net.zodac.dicefive.ui.game.LeaveGameConfirmation()
        confirmation.request { }
        showcase.show { DiceFiveTheme { net.zodac.dicefive.ui.game.LeaveGameConfirmationDialog(confirmation) } }
        compose.onNodeWithText("مغادرة اللعبة؟").assertExists()
    }
}
