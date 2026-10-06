package net.zodac.dicefive

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.game.style.DiceCupStyles
import net.zodac.dicefive.ui.game.style.DiceMats
import net.zodac.dicefive.ui.game.style.DiceStyles
import net.zodac.dicefive.ui.game.style.TableBackgrounds
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.styles.StylesViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A Styles category's gallery toggle, and the gallery it opens. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StylesGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val tiles = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    // The saved picks the page shows. A tap doesn't change them here (the view model saves to a store
    // this stands in for), so a test that needs a new pick sets it, as the store would.
    private lateinit var saved: MutableStateFlow<SavedStyles?>

    private fun open() {
        // Everything earned, so every row holds more tiles than fit across.
        val achievements = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
        saved = MutableStateFlow(
            SavedStyles(DiceStyles.default.id, DiceCupStyles.default.id, TableBackgrounds.default.id, DiceMats.default.id, achievements),
        )
        val viewModel = StylesViewModel(savedStyles = saved)
        compose.setContent { DiceFiveTheme { StylesScreen(viewModel = viewModel, onBack = {}) } }
        // Long enough for every row to build every tile.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
    }

    private fun settle() = repeat(30) { compose.mainClock.advanceTimeByFrame() }

    // The dice tiles: those above the Dice Cup card's title.
    private fun diceTiles(): List<SemanticsNode> {
        val cupTitleTop = compose.onNode(isHeading() and hasText("Dice Cup")).fetchSemanticsNode().positionInRoot.y
        return compose.onAllNodes(tiles).fetchSemanticsNodes().filter { it.positionInRoot.y < cupTitleTop }
    }

    @Test
    fun `every category has a gallery switch, off to start with`() {
        open()
        for (title in listOf("Dice", "Dice Cup", "Mat", "Background", "Frame")) {
            val toggle = compose.onNodeWithContentDescription("$title gallery")
            toggle.assertIsOff()
            val node = toggle.fetchSemanticsNode()
            assertEquals(Role.Switch, node.config.getOrNull(SemanticsProperties.Role))
            // Level with its title, so its press highlight (centred on it) is too.
            val heading = compose.onNode(isHeading() and hasText(title)).fetchSemanticsNode().boundsInRoot
            assertEquals("$title: ${node.boundsInRoot} vs $heading", heading.center.y, node.boundsInRoot.center.y, 1.5f)
        }
    }

    @Test
    fun `the gallery shows every tile on screen, and the row gets them back`() {
        open()
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val before = diceTiles()
        // Where a tile really is, not clipped to what shows of it.
        fun SemanticsNode.left() = positionInRoot.x
        fun SemanticsNode.right() = positionInRoot.x + size.width
        assertTrue("the row should run off screen", before.any { it.right() > width })
        val allTiles = compose.onAllNodes(tiles).fetchSemanticsNodes().size

        compose.onNodeWithContentDescription("Dice gallery").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dice gallery").assertIsOn()
        val inGallery = diceTiles()
        assertEquals(before.size, inGallery.size)
        assertTrue(inGallery.all { it.left() >= 0f && it.right() <= width })
        // More than one row of them, and nothing else lost.
        assertTrue(inGallery.map { it.positionInRoot.y }.distinct().size > 1)
        assertEquals(allTiles, compose.onAllNodes(tiles).fetchSemanticsNodes().size)

        compose.onNodeWithContentDescription("Dice gallery").performClick()
        compose.waitForIdle()
        val toggle = compose.onNodeWithContentDescription("Dice gallery").fetchSemanticsNode()
        assertEquals(ToggleableState.Off, toggle.config[SemanticsProperties.ToggleableState])
        assertEquals(before.size, diceTiles().size)
        assertEquals(allTiles, compose.onAllNodes(tiles).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp")
    fun `a narrow phone's galleries fit four dice, cups and backgrounds to a line, and three mats and frames`() {
        open()
        // Frame by frame: the mats' and backgrounds' animated art never lets the page go idle once it's on screen.
        compose.mainClock.autoAdvance = false
        val expected = listOf(Triple("Dice", "Dice Cup", 4), Triple("Dice Cup", "Mat", 4), Triple("Mat", "Background", 3), Triple("Background", "Frame", 4))
        for ((title, next, perLine) in expected) {
            compose.onNodeWithContentDescription("$title gallery").performClick()
            settle()
            val top = compose.onNode(isHeading() and hasText(title)).fetchSemanticsNode().positionInRoot.y
            val bottom = compose.onNode(isHeading() and hasText(next)).fetchSemanticsNode().positionInRoot.y
            val lines = compose.onAllNodes(tiles).fetchSemanticsNodes()
                .filter { it.positionInRoot.y in top..bottom }
                .groupBy { it.positionInRoot.y }
            assertEquals("$title: ${lines.values.map { it.size }}", perLine, lines.values.first().size)
            compose.onNodeWithContentDescription("$title gallery").performClick()
            settle()
        }
        // The last card: everything below its title.
        // Below the fold on this phone: the page swiped up to it first, so the tap lands on its toggle.
        compose.onRoot().performTouchInput { swipeUp(startY = bottom - 100f, endY = top + 100f) }
        settle()
        compose.onNodeWithContentDescription("Frame gallery").performClick()
        settle()
        compose.onNodeWithContentDescription("Frame gallery").assertIsOn()
        val top = compose.onNode(isHeading() and hasText("Frame")).fetchSemanticsNode().positionInRoot.y
        val lines = compose.onAllNodes(tiles).fetchSemanticsNodes().filter { it.positionInRoot.y > top }.groupBy { it.positionInRoot.y }
        assertEquals("Frame: ${lines.values.map { it.size }}", 3, lines.values.first().size)
    }

    @Test
    fun `dragging the page's right-hand margin scrolls it`() {
        open()
        compose.onNodeWithContentDescription("Dice gallery").performClick()
        compose.waitForIdle()
        val heading = isHeading() and hasText("Mat")
        val before = compose.onNode(heading).fetchSemanticsNode().positionInRoot.y
        // Down the page's 20dp margin, beside the cards: a scrollbar's drag, so the page moves further than the finger.
        val drag = with(compose.density) { 100.dp.toPx() }
        compose.onRoot().performTouchInput {
            val x = right - with(compose.density) { 10.dp.toPx() }
            swipe(start = Offset(x, centerY), end = Offset(x, centerY + drag), durationMillis = 300)
        }
        compose.waitForIdle()
        val moved = before - compose.onNode(heading).fetchSemanticsNode().positionInRoot.y
        assertTrue("moved $moved for a drag of $drag", moved > drag)
    }

    @Test
    // Two pixels to a dp, so a pixel's rounding is the only slack: the row used to be a dp or more off.
    @Config(qualifiers = "w376dp-h830dp-xhdpi")
    fun `every category spaces its tiles the same in its row and its gallery`() {
        open()
        compose.mainClock.autoAdvance = false
        val titles = listOf("Dice", "Dice Cup", "Mat", "Background", "Frame")
        // Each category's tiles, left to right in their first line: those below its title and above the next's.
        fun steps(index: Int): List<Float> {
            val top = compose.onNode(isHeading() and hasText(titles[index])).fetchSemanticsNode().positionInRoot.y
            val bottom = titles.getOrNull(index + 1)?.let { compose.onNode(isHeading() and hasText(it)).fetchSemanticsNode().positionInRoot.y } ?: Float.MAX_VALUE
            val inCard = compose.onAllNodes(tiles).fetchSemanticsNodes().filter { it.positionInRoot.y > top && it.positionInRoot.y < bottom }
            val firstLine = inCard.minOf { it.positionInRoot.y }
            val xs = inCard.filter { it.positionInRoot.y == firstLine }.map { it.positionInRoot.x }.sorted()
            return xs.zipWithNext { a, b -> b - a }
        }
        // From the bottom up, so each card's toggle is still on screen when it's tapped.
        for (index in titles.indices.reversed()) {
            val row = steps(index)
            compose.onNodeWithContentDescription("${titles[index]} gallery").performClick()
            settle()
            val gallery = steps(index)
            assertTrue("${titles[index]}: $gallery", gallery.isNotEmpty())
            for (step in row.take(gallery.size) + gallery) {
                assertEquals("${titles[index]}: row $row, gallery $gallery", gallery.first(), step, 1.01f)
            }
            compose.onNodeWithContentDescription("${titles[index]} gallery").performClick()
            settle()
        }
    }

    @Test
    fun `closing a gallery shows its row centred on what was picked in it`() {
        open()
        compose.onNodeWithContentDescription("Dice gallery").performClick()
        compose.waitForIdle()
        // Far along the row from the Classic dice it opened on, so the row has to move to show it.
        val mahjong = tiles and hasContentDescription("Mahjong", substring = true)
        saved.value = saved.value!!.copy(diceStyleId = DiceStyles.families.first { it.name == "Mahjong" }.colours.first().style.id)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dice gallery").performClick()
        compose.waitForIdle()

        val tile = compose.onNode(mahjong).fetchSemanticsNode()
        val centre = tile.positionInRoot.x + tile.size.width / 2f
        val rootCentre = compose.onRoot().fetchSemanticsNode().boundsInRoot.width / 2f
        assertEquals(rootCentre, centre, 2f)
    }
}
