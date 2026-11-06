package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.common.AppLogo
import net.zodac.dicefive.ui.game.GameBoard
import net.zodac.dicefive.ui.game.GameCup
import net.zodac.dicefive.ui.style.CupDice
import net.zodac.dicefive.ui.style.D20WhiteDiceStyle
import net.zodac.dicefive.ui.style.DiceCupStyles
import net.zodac.dicefive.ui.style.DiceStyle
import net.zodac.dicefive.ui.style.DieColourPalette
import net.zodac.dicefive.ui.style.GameVisualTheme
import net.zodac.dicefive.ui.style.HALO_DIE_SLIDE
import net.zodac.dicefive.ui.style.HALO_DIE_TURN
import net.zodac.dicefive.ui.style.IvoryDiceStyle
import net.zodac.dicefive.ui.style.LocalCupDice
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.style.haloDicePile
import net.zodac.dicefive.ui.style.plainDiceStyle

/** How far a die at rest can reach from the cup's centre line, in grid units: the inner edge of the narrowest ring's tube. */
private const val REACH_AT_REST = 16.7f

/** How far a die can reach shaken: the outer edge of that tube, so it never shows past the ring. */
private const val REACH_SHAKEN = 19.5f

/** Long enough for the cup to finish tipping or standing up. */
private const val SETTLE_MILLIS = 1000L

/** A die that only counts how many of it are composed, in place of the player's style. */
private class CountedDice : DiceStyle {
    override val id = "counted"
    var drawn = 0
        private set

    override fun recoloured(palette: DieColourPalette): DiceStyle = this

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        DisposableEffect(Unit) {
            drawn++
            onDispose { drawn-- }
        }
        Box(modifier)
    }
}

/** The RGB Halo cup shows the player's own dice, as many as the next roll throws, until they're poured. */
class RgbHaloDiceTest : UiTest() {

    private val halo = DiceCupStyles.byId("rgb_halo_rainbow")

    @Test
    fun `every count of dice has a heap of that many`() {
        for (count in 0..7) {
            val pile = haloDicePile(count)
            assertEquals(count, pile.spots.size, "The dice in the heap of $count")
            assertEquals(count, pile.spots.distinctBy { it.x to it.y }.size, "Dice in one place in the heap of $count")
        }
    }

    /** How far a square die [size] across, turned [degrees] and [slide] off its spot at [x], reaches from the centre line. */
    private fun reach(x: Float, size: Float, degrees: Float, slide: Float = 0f): Float {
        val radians = degrees * PI.toFloat() / 180f
        return abs(x) + slide + size / 2f * (abs(cos(radians)) + abs(sin(radians)))
    }

    @Test
    fun `a heap stays inside the rings, turned as it rests and at the height of a shake`() {
        for (count in 1..7) {
            val pile = haloDicePile(count)
            for (spot in pile.spots) {
                val atRest = reach(spot.x, pile.size, spot.degrees)
                assertTrue(atRest <= REACH_AT_REST, "A die in the heap of $count reaches $atRest at rest")
                val shaken = reach(spot.x, pile.size, abs(spot.degrees) + HALO_DIE_TURN, HALO_DIE_SLIDE)
                assertTrue(shaken <= REACH_SHAKEN, "A die in the heap of $count reaches $shaken shaken")
            }
        }
    }

    @Test
    fun `the heap's dice are 20 across, and 16 when there are more than five`() {
        for (count in 1..5) assertEquals(20f, haloDicePile(count).size, "The heap of $count")
        for (count in 6..7) assertEquals(16f, haloDicePile(count).size, "The heap of $count")
    }

    @Test
    fun `the dice go as the cup is poured and come back as it stands up`() = uiTest {
        val dice = CountedDice()
        var tilted by mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalCupDice provides CupDice(dice, 5)) {
                halo.Cup(rolling = false, tilted = tilted, modifier = Modifier.size(58.dp, 84.dp))
            }
        }
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(5, dice.drawn, "Standing")
        Snapshot.withMutableSnapshot { tilted = true }
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(0, dice.drawn, "Poured")
        Snapshot.withMutableSnapshot { tilted = false }
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(5, dice.drawn, "Standing again")
    }

    /** A game of [mode] for one player, with its dice as [dice] makes them. */
    private fun game(mode: GameMode, dice: (List<Die>) -> List<Die> = { it }): GameState {
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 0, type = PlayerType.HUMAN, name = "Sam")), mode)
        return state.copy(dice = dice(state.dice))
    }

    private fun List<Die>.holdingFirst(count: Int) = mapIndexed { index, die -> if (index < count) die.copy(isHeld = true, heldSlot = index) else die }

    /** How many dice the cup shows for [state], through the board's own cup on a phone. */
    private fun ComposeUiTest.diceOnTheBoard(state: GameState): Int {
        val style = CountedDice()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices, LocalGameVisualTheme provides GameVisualTheme(diceStyle = style, diceCupStyle = halo)) {
                GameBoard(state = state, rolling = false, canUndo = false, onScoreCategory = {}, onCupTap = {}, onUndo = {}, modifier = Modifier.fillMaxWidth())
            }
        }
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        return style.drawn
    }

    @Test
    fun `a Standard game has five dice in the cup`() = uiTest {
        assertEquals(5, diceOnTheBoard(game(GameMode.STANDARD)))
    }

    @Test
    fun `a Standard game with two held has three in the cup`() = uiTest {
        assertEquals(3, diceOnTheBoard(game(GameMode.STANDARD) { it.holdingFirst(2) }))
    }

    @Test
    fun `a Stud game has seven dice in the cup, though only held dice score`() = uiTest {
        assertEquals(7, diceOnTheBoard(game(GameMode.STUD)))
    }

    @Test
    fun `a Stud game with two held has five in the cup`() = uiTest {
        assertEquals(5, diceOnTheBoard(game(GameMode.STUD) { it.holdingFirst(2) }))
    }

    @Test
    fun `unlucky dice are in the cup, as the next roll throws them again`() = uiTest {
        assertEquals(5, diceOnTheBoard(game(GameMode.STANDARD) { dice -> dice.mapIndexed { index, die -> die.copy(isUnlucky = index < 2) } }))
    }

    @Test
    fun `the cup beside the tray in landscape holds the same dice`() = uiTest {
        val style = CountedDice()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme(diceStyle = style, diceCupStyle = halo)) {
                GameCup(state = game(GameMode.STUD) { it.holdingFirst(3) }, rolling = false, canUndo = false, onCupTap = {}, onUndo = {}, modifier = Modifier.size(200.dp, 104.dp))
            }
        }
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(4, style.drawn)
    }

    @Test
    fun `the menu's logo draws only its own fan of dice, none in the cup`() = uiTest {
        val style = CountedDice()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                AppLogo(diceStyle = style, cupStyle = halo)
            }
        }
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(5, style.drawn)
    }

    @Test
    fun `the cup's dice are the player's own, or a D20 where the game's dice have more faces than their art`() {
        assertSame(IvoryDiceStyle, plainDiceStyle(IvoryDiceStyle, 1..6))
        assertSame(D20WhiteDiceStyle, plainDiceStyle(IvoryDiceStyle, 1..20))
        assertSame(D20WhiteDiceStyle, plainDiceStyle(D20WhiteDiceStyle, 1..20))
    }
}
