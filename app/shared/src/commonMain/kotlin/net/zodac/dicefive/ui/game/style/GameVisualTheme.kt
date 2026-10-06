package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * One swappable piece of table art in one colour - a [DiceStyle], [DiceCupStyle], [TableBackground],
 * [DiceMat] or [ScoreFrame]. [id] is what the player's pick is saved as, so it must never change once shipped.
 */
interface TableArt {
    val id: String
}

/**
 * Renders a single die face. Implementations are swapped wholesale via
 * [GameVisualTheme.diceStyle] - e.g. a future "user picked a dice skin"
 * setting would just provide a different [DiceStyle] instance.
 */
interface DiceStyle : TableArt {
    /**
     * Whether this style draws its own tumble mid-roll (the D20, a solid it can turn itself) rather
     * than being rolled as a cube of its own faces by the tray. See [LocalDieTumbleMillis].
     */
    val tumblesItself: Boolean get() = false

    /**
     * Whether this die comes to rest standing straight up (the Egg) rather than lying at its own
     * angle on the mat, as a cube does - the tray then lands it unturned.
     */
    val standsUpright: Boolean get() = false

    /**
     * For a style with loose pupils on its faces that the die's movement throws about (googly eyes),
     * how far each can roll from its socket's centre, as a fraction of the die's size - the tray then
     * keeps each die a [DieMotion] to move them, handed down as [LocalDieMotion]. Null for a style
     * with nothing loose, which needs no such tracking at all.
     */
    val pupilTravel: Float? get() = null

    /**
     * The outline this die casts its ground shadow with when lying on the mat - its own shape, not a
     * generic one. [dieIndex] is which die it is and [tumbleMillis] how long it's been tumbling
     * (null at rest - see [LocalDieTumbleMillis]), for a die whose outline changes with either (the
     * D20). A rounded square by default, like [BeveledDie].
     */
    /**
     * The colour of the die's material itself - what shows along the rounded edge between two faces
     * as [TossedCube] tumbles it. Its swatch, for a style that has one.
     */
    val bodyColor: Color get() = (this as? Swatched)?.swatch ?: Color.LightGray

    fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(cornerPercent)

    /**
     * How far along each diagonal of the die's square the chains of a die locked by Unlucky Dice reach, 1 being
     * nearly corner to corner. A die whose outline doesn't fill its square (the egg, the D20) takes less, so the
     * chains end inside it instead of hanging over its edge.
     */
    val lockedChainReach: Float get() = 1f

    /**
     * How rounded the die's corners are, as a percentage of its size - the one figure its face, its
     * shadow and its tumble ([TossedCube]) all round to, so a square-cornered die doesn't tumble
     * rounded and then snap square as it lands.
     */
    val cornerPercent: Int get() = BEVELED_DIE_CORNER_PERCENT

    /**
     * The face this style's art shows above [value] on the die, for a style drawn as a solid with
     * more than one face in view (the Cube), or null when only [value] shows. A toss lands with this
     * face on top, so the tossed die settles into exactly what [Die] then draws.
     */
    fun topFace(value: Int): Int? = null

    /**
     * The die tumbling mid-toss, [roll] quarter-turns along its way through [ring] - see
     * [TossedCube], which by default rolls this style's own [Die] faces as a cube. A style whose
     * faces already draw a whole solid (the Cube) draws its tumble itself instead, since rolling
     * those as cards would show every card's painted sides at once.
     */
    @Composable
    fun TossedDie(roll: Float, finalTurns: Int, ring: List<Int>, modifier: Modifier) {
        TossedCube(roll = roll, finalTurns = finalTurns, ring = ring, body = bodyColor, cornerPercent = cornerPercent, modifier = modifier) { value, faceModifier ->
            Die(value = value, held = false, modifier = faceModifier)
        }
    }

    /**
     * This style's shape and finish in [palette]'s colours instead of its own - how a die whose
     * colour is part of the roll (Tricolour) is drawn, so the player's style still shows while the
     * colour still reads. Its pips, numbers or digits always take [DieColourPalette.pip], picked to
     * read on that colour, never the style's own, which could vanish against it (the blue D20's gold
     * numbers on a yellow die), and its held ring [DieColourPalette.heldRing], for the same reason.
     */
    fun recoloured(palette: DieColourPalette): DiceStyle

    @Composable
    fun Die(value: Int, held: Boolean, modifier: Modifier)
}

/**
 * Renders the shaker cup, including its shake/tilt animation.
 *
 * [rolling] should be true for the brief moment a roll is being resolved
 * (drives the shake), [tilted] reflects whether this turn's dice have
 * already been poured out at least once (drives the resting pose).
 */
interface DiceCupStyle : TableArt {
    /** The proportions this cup is drawn in; callers size its canvas to match. */
    val shape: CupShape get() = CupShape.TALL

    @Composable
    fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier)

    /**
     * Whether this cup shows itself off once a turn's rolls are spent: keeping its colour rather than
     * greying like every other, and standing back up after pouring rather than resting tipped over -
     * it can't be rolled either way. Only a Flowerpot whose sunflower has grown with [growth] (the
     * moment the player should see its bloom in full), never one held at a fixed stage.
     */
    fun showsOffWhenSpent(growth: FlowerpotGrowth): Boolean = false

    /**
     * Whether the cup looks different once its dice are poured - tipped over, its doors or lid open.
     * False for one that only plays something as it pours and is then just as it was (the Chicken).
     */
    val changesWhenPoured: Boolean get() = true
}

/**
 * Supplies the background for the scoring area. Deliberately excludes the player header, which
 * always uses the app's base theme background. Independently swappable from [DiceMat], which
 * covers the dice-tray area below it.
 */
interface TableBackground : TableArt {
    val scoreAreaBrush: Brush

    /**
     * An optional pattern drawn over [scoreAreaBrush], behind the scorecard. Most backgrounds are a
     * plain brush and don't need one, so it's a no-op by default.
     */
    fun DrawScope.drawScoreAreaDecoration() {}

    /**
     * Composed by whoever draws [drawScoreAreaDecoration], for a decoration that moves: keeps it
     * running for as long as it's on screen. A no-op for one that doesn't.
     */
    @Composable
    fun Animate() {}
}

/** Supplies the (independently swappable) dice-tray mat, separate from [TableBackground]. */
interface DiceMat : TableArt {
    val diceTrayBrush: Brush

    /** The fill of the five slots along the top of the tray that held dice move into. */
    val slotSocketBrush: Brush

    /** The rim round each held-dice slot - lighter than both the slot and the mat, so it stands out. */
    val slotSocketBorder: Color

    /**
     * An optional decorative overlay drawn on top of [diceTrayBrush], e.g. a pattern or trim along
     * the tray's edge. Most mats don't need one, so it's a no-op by
     * default rather than every implementation repeating an empty override.
     */
    @Composable
    fun DiceTrayDecoration(modifier: Modifier) {}
}

/**
 * Frames the tab of the player whose turn it is, in the row of player tabs above the board - in place
 * of the plain ring it once had (Classic). Drawn in that player's own colour, so one frame serves every
 * player: shades of [drawFrame]'s colour only, never a colour of its own.
 */
interface ScoreFrame : TableArt {
    /**
     * Draws the frame round a tab this DrawScope's size, in the player's [color]. It's drawn behind the
     * tab's name and score, so it can't hide them, and may spread a few dp past the tab's edges into the
     * gaps between tabs, but no further.
     */
    fun DrawScope.drawFrame(color: Color)
}

/** Bundles the pluggable game-table art. Swap any field to re-skin that piece independently. */
data class GameVisualTheme(
    val diceStyle: DiceStyle = DiceStyles.default,
    val diceCupStyle: DiceCupStyle = DiceCupStyles.default,
    val background: TableBackground = TableBackgrounds.default,
    val mat: DiceMat = DiceMats.default,
    val frame: ScoreFrame = ScoreFrames.default,
)

val LocalGameVisualTheme = staticCompositionLocalOf { GameVisualTheme() }

/**
 * Whether Tricolour's dice/scorecard colours are showing as the Irish flag's green/white/orange
 * right now - true only for [net.zodac.dicefive.model.isLuckOfTheIrish]'s exact condition, provided
 * once per game by `GameScreen` from the live [net.zodac.dicefive.model.GameState]. Kept separate
 * from [GameVisualTheme] (a player's own style picks) since this isn't a choice, it's an easter egg
 * tied to this specific game.
 */
val LocalIrishTricolour = staticCompositionLocalOf { false }
