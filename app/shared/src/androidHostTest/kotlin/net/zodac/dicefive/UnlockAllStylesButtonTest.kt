package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.purchases.LocalStore
import net.zodac.dicefive.purchases.Store
import net.zodac.dicefive.purchases.StoreState
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.DiceStyles
import net.zodac.dicefive.ui.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.robolectric.annotation.Config

/**
 * The "Unlock all styles" purchase on the Styles page: a button in its app bar for each thing the store can say, what
 * TalkBack hears of it, and the word that the purchase is done. Its visible words ("Unlock All", "Payment pending") are
 * cleared for TalkBack, so they're checked in renders, not here.
 *
 * Android only: it draws the whole Styles page, which the desktop test harness draws frame by frame.
 */
@Config(qualifiers = "w411dp-h891dp")
class UnlockAllStylesButtonTest : UiTest() {

    private class FakeStore(initial: StoreState) : Store {
        override val state = MutableStateFlow(initial)
        var buys = 0

        override fun buy() {
            buys++
        }
    }

    private class ToastPlatform : PlatformServices by SilentPlatformServices {
        val toasts = mutableListOf<String>()

        override fun showTransientMessage(message: String) {
            toasts += message
        }
    }

    private val showcase = Showcase { compose }

    private val button = hasContentDescription("Unlock all styles") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun show(store: Store, platform: PlatformServices = SilentPlatformServices, ownsAllStyles: Boolean = false, fontScale: Float = 1f) {
        val saved = MutableStateFlow<SavedStyles?>(
            SavedStyles(
                DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, AchievementsState(),
                ownsAllStyles = ownsAllStyles,
            ),
        )
        val viewModel = stylesViewModel(saved)
        showcase.show {
            DiceFiveTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalPlatformServices provides platform,
                    LocalStore provides store,
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    StylesScreen(viewModel = viewModel, onBack = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    fun `for sale - a button says Unlock All - TalkBack hears Unlock all styles - and a tap opens the store's sheet`() = uiTest {
        val store = FakeStore(StoreState.ForSale(price = "£2.99"))
        show(store)
        assertLabelled(true)
        val node = compose.onNode(button)
        node.assertIsEnabled().assertHasClickAction()
        // No state to hear, and a touch target at least 48dp tall (the button draws 40dp of it).
        assertNull(node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertTrue(node.fetchSemanticsNode().touchBoundsInRoot.height >= with(compose.density) { 48.dp.toPx() })
        assertTitleCentredOnOneLine("for sale")
        node.performClick()
        assertEquals(1, store.buys)
    }

    @Test
    fun `until the store has the product the button is there but can't be pressed`() = uiTest {
        for (state in listOf(StoreState.Loading, StoreState.ForSale(price = null))) {
            val store = FakeStore(state)
            show(store)
            compose.onNode(button).assertIsNotEnabled()
            compose.onNode(button).performClick()
            assertEquals(0, store.buys, "$state")
        }
    }

    @Test
    // Wide enough for "Payment pending" beside the title, which a 411dp phone only just is.
    @Config(qualifiers = "w600dp-h900dp")
    fun `a pending payment says so - on the button and to TalkBack - and can't be bought again`() = uiTest {
        show(FakeStore(StoreState.Pending))
        assertLabelled(true)
        val node = compose.onNode(button).assertIsNotEnabled()
        assertEquals("Payment pending", node.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `there's no button once every style is owned - however the store says so - nor where there's no store`() = uiTest {
        for ((state, owned) in listOf(StoreState.Owned to false, StoreState.Unavailable to false, StoreState.ForSale("£2.99") to true)) {
            show(FakeStore(state), ownsAllStyles = owned)
            compose.onNode(button).assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `where the words would move the title it's the mark alone - still named for TalkBack - and the title stays put`() = uiTest {
        for ((fontScale, state) in listOf(2f to StoreState.ForSale("£2.99"), 1.3f to StoreState.ForSale("£2.99"), 1f to StoreState.Pending)) {
            show(FakeStore(state), fontScale = fontScale)
            assertLabelled(false)
            assertTitleCentredOnOneLine("$fontScale $state")
        }
        // With room for its words, they're there, and the title is where it always is.
        show(FakeStore(StoreState.ForSale("£2.99")))
        assertLabelled(true)
        assertTitleCentredOnOneLine("1x")
    }

    @Test
    fun `a purchase completing on the page says All styles unlocked - once - and an owner opening it hears nothing`() = uiTest {
        val platform = ToastPlatform()
        val store = FakeStore(StoreState.ForSale(price = "£2.99"))
        show(store, platform)
        store.state.value = StoreState.Pending
        compose.waitForIdle()
        store.state.value = StoreState.Owned
        compose.waitForIdle()
        store.state.value = StoreState.Owned
        compose.waitForIdle()
        assertEquals(listOf("All styles unlocked"), platform.toasts)

        val alreadyOwned = ToastPlatform()
        val loading = FakeStore(StoreState.Loading)
        show(loading, alreadyOwned)
        loading.state.value = StoreState.Owned
        compose.waitForIdle()
        assertEquals(emptyList(), alreadyOwned.toasts)
    }

    @Test
    @Config(qualifiers = "es")
    fun `in Spanish TalkBack hears Desbloquear todos los estilos`() = uiTest {
        show(FakeStore(StoreState.ForSale(price = "2,99 €")))
        compose.onNode(hasContentDescription("Desbloquear todos los estilos")).assertIsEnabled()
    }

    /**
     * Whether the button shows its words beside its mark - read from its width, as the words themselves are cleared for
     * TalkBack, which hears the spoken name instead: the mark alone is a button's narrowest, 58dp.
     */
    private fun assertLabelled(labelled: Boolean) {
        val width = compose.onNode(button).fetchSemanticsNode().size.width
        val markAlone = with(compose.density) { 72.dp.roundToPx() }
        assertEquals(labelled, width > markAlone, "button width $width px")
    }

    /** The page's title, on one line, centred on the screen as on every other page. */
    private fun assertTitleCentredOnOneLine(case: String) {
        val title = compose.onNode(hasText("Styles") and isHeading()).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        title.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals(1, layouts.single().lineCount, case)
        val screenCentre = compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(abs(title.boundsInRoot.center.x - screenCentre) <= 2f, "$case: the title is off-centre")
    }
}
