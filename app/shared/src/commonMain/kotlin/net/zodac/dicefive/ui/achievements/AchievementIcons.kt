package net.zodac.dicefive.ui.achievements

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Filter7
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Filter3
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.Looks6
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NoMeetingRoom
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.ic_cowboy_hat
import net.zodac.dicefive.resources.ic_stairs
import net.zodac.dicefive.resources.ic_time_machine_car
import net.zodac.dicefive.ui.game.style.CUP_VIEW_SQUASH
import net.zodac.dicefive.ui.game.style.pipLayout
import net.zodac.dicefive.ui.theme.AchievementHeartRed
import net.zodac.dicefive.ui.theme.AchievementTrophyGold
import net.zodac.dicefive.ui.theme.DicePipColor
import net.zodac.dicefive.ui.theme.FlowerpotLeaf
import net.zodac.dicefive.ui.theme.FlowerpotLeafDark
import net.zodac.dicefive.ui.theme.FlowerpotStem
import net.zodac.dicefive.ui.theme.IrishGreenSwatch
import net.zodac.dicefive.ui.theme.IrishOrangeSwatch
import net.zodac.dicefive.ui.theme.IrishWhiteSwatch
import net.zodac.dicefive.ui.theme.IvoryDiceBottom
import net.zodac.dicefive.ui.theme.IvoryDiceTop
import net.zodac.dicefive.ui.theme.MagicianHatDark
import net.zodac.dicefive.ui.theme.MagicianHatInside
import net.zodac.dicefive.ui.theme.MagicianHatLight
import net.zodac.dicefive.ui.theme.MagicianHatMid
import net.zodac.dicefive.ui.theme.MagicianHatRibbon
import net.zodac.dicefive.ui.theme.ManuscriptInk
import net.zodac.dicefive.ui.theme.ManuscriptPaper
import net.zodac.dicefive.ui.theme.ManuscriptPaperEdge
import net.zodac.dicefive.ui.theme.MartiniGlassSwatch
import net.zodac.dicefive.ui.theme.MartiniLiquidSwatch
import net.zodac.dicefive.ui.theme.MartiniOliveHighlightSwatch
import net.zodac.dicefive.ui.theme.MartiniOliveSwatch
import net.zodac.dicefive.ui.theme.MartiniPickSwatch
import net.zodac.dicefive.ui.theme.RabbitEye
import net.zodac.dicefive.ui.theme.RabbitFur
import net.zodac.dicefive.ui.theme.RabbitFurShade
import net.zodac.dicefive.ui.theme.RabbitPink
import net.zodac.dicefive.ui.theme.SunflowerDisc
import net.zodac.dicefive.ui.theme.SunflowerPetal
import net.zodac.dicefive.ui.theme.SunflowerPetalShade
import net.zodac.dicefive.ui.theme.SunflowerSeed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.jetbrains.compose.resources.vectorResource

/**
 * The glyph an unlocked [Achievement] shows in [AchievementsScreen]. Every achievement gets its
 * own icon rather than a single stock trophy - almost all stock Material glyphs (a couple, where
 * nothing in Material fit, borrow a bespoke drawable from `ui/game/CategoryIcon.kt` or a small
 * one drawn just for this), picked to hint at what the achievement is actually about.
 * [AchievementsScreen] falls back to [LOCKED_ACHIEVEMENT_ICON] until an achievement is unlocked,
 * so none of this is visible - or a spoiler - beforehand.
 *
 * A `when` over the enum (not a field on [Achievement] itself) so the model stays a plain data
 * catalogue with no Compose dependency, matching how `CategoryIcon.kt` maps [net.zodac.dicefive
 * .model.ScoreCategory] to a glyph rather than the enum carrying one. `@Composable` only because
 * a couple of branches load a drawable resource via [vectorResource] - every other branch is a
 * plain constant, unaffected by being read from a composable context.
 */
val Achievement.icon: ImageVector
    @Composable
    get() = when (this) {
        // ---- Milestones ---------------------------------------------------------------------
        Achievement.THE_JOURNEY_BEGINS -> Icons.Filled.RocketLaunch
        Achievement.SOLO_GAME -> Icons.Filled.Person
        Achievement.FULL_TABLE -> Icons.Filled.Groups
        Achievement.CONTINUED_GAME -> Icons.Filled.Restore
        Achievement.REPLAY_AFTER_LOSS -> Icons.Filled.Replay
        Achievement.GAMES_10 -> Icons.Filled.Numbers
        Achievement.GAMES_50 -> Icons.Filled.EventRepeat
        Achievement.GAMES_100 -> Icons.Filled.MilitaryTech
        Achievement.WINS_25 -> Icons.Filled.EmojiEvents
        Achievement.DICE_10000 -> Icons.Filled.Casino
        Achievement.STREAK_3 -> Icons.Filled.Whatshot
        Achievement.STREAK_10 -> Icons.Filled.LocalFireDepartment
        Achievement.PROFESSIONAL_ROLLER -> Icons.Filled.WorkspacePremium

        // ---- Dice feats -----------------------------------------------------------------------
        Achievement.UPPER_BONUS -> Icons.Filled.Star
        Achievement.FIRST_5X -> Icons.Filled.NewReleases
        Achievement.NO_ZEROES -> Icons.Filled.CheckCircle
        // Same custom shape as Small/Large Straight's own tile icon (CategoryIcon.kt) - see
        // ic_stairs.xml for why that's a bespoke drawable rather than Icons.Filled/Outlined.Stairs.
        Achievement.BOTH_STRAIGHTS -> vectorResource(Res.drawable.ic_stairs)
        Achievement.UPPER_84 -> Icons.Filled.ArrowUpward
        Achievement.LOWER_150 -> Icons.Filled.ArrowDownward
        Achievement.ENCORE_5X -> Icons.Filled.Repeat
        Achievement.TOTAL_5X_10 -> Icons.Filled.Grade
        Achievement.HAT_TRICK_5X -> Icons.Filled.Looks3
        Achievement.LOADED_DICE -> Icons.Filled.Balance
        Achievement.TWICE_IN_A_LIFETIME -> Icons.Filled.SyncAlt
        Achievement.NATURAL_5X -> Icons.Filled.AutoAwesome
        Achievement.I_CAN_COUNT -> Icons.Filled.FormatListNumbered
        Achievement.CUNNING_STRATEGY -> Icons.Filled.Psychology
        Achievement.EXACT_CHANGE -> Icons.Filled.Calculate

        // ---- Scoring ----------------------------------------------------------------------------
        Achievement.PERSONAL_BEST -> Icons.AutoMirrored.Filled.TrendingUp
        Achievement.TON -> Icons.Filled.Filter1
        Achievement.SCORE_200 -> Icons.Filled.Speed
        Achievement.SCORE_300 -> Icons.Filled.GpsFixed
        Achievement.SCORE_400 -> Icons.Filled.KeyboardDoubleArrowUp
        Achievement.SCORE_500 -> Icons.Filled.Bolt

        // ---- Winning ----------------------------------------------------------------------------
        Achievement.FIRST_WIN -> Icons.Filled.Flag
        Achievement.WIN_BY_100 -> Icons.Filled.Landslide
        Achievement.WIN_BY_5 -> Icons.Filled.PhotoCamera
        // A gavel - the ruling that settled an otherwise-equal score.
        Achievement.TIE_BREAK -> Icons.Filled.Gavel
        // No cowboy-with-twin-pistols glyph in Material (nor any hat at all) for the "quick draw"
        // read of a last-round comeback, so this is the requested fallback: a plain cowboy hat.
        Achievement.COMEBACK -> vectorResource(Res.drawable.ic_cowboy_hat)
        Achievement.BEAT_THREE_AI -> Icons.Filled.EmojiPeople
        Achievement.NATURAL_INTELLIGENCE -> Icons.Filled.SmartToy
        Achievement.NATURALLY_GIFTED -> Icons.Filled.Spa

        // ---- Game modes ---------------------------------------------------------------------------
        // A wedge-shaped sports car trailing fire - a nod to the film the title quotes, drawn from
        // scratch rather than traced from it.
        Achievement.NON_STANDARD_MODE -> vectorResource(Res.drawable.ic_time_machine_car)
        // Three lights, for three colours.
        Achievement.TRICOLOUR_WIN -> Icons.Filled.Traffic
        Achievement.TRICOLOUR_ALL_COLOURS -> Icons.Filled.ColorLens
        // Fast-forward rather than Speed or a bolt - those are Solid Round's and Dice Deity's.
        Achievement.QUICKFIRE_WIN -> Icons.Filled.FastForward
        // A bullseye, for hitting the mark with so few boxes to do it in.
        Achievement.QUICKFIRE_SCORE -> Icons.Filled.Adjust
        // A hand of cards, for the poker game the mode is named after.
        Achievement.STUD_WIN -> Icons.Filled.Style
        Achievement.STUD_LUCKY_SEVEN -> Icons.Filled.Filter7
        Achievement.THIRD_WIND_WIN -> Icons.Filled.Cyclone
        Achievement.THIRD_WIND_NO_ZEROES -> Icons.Filled.Air
        // A list with every job ticked off - the contract seen through.
        Achievement.HIT_LIST_WIN -> Icons.Filled.AssignmentTurnedIn
        // Drawn for this app: Material's bullseyes have no arrow in them.
        Achievement.HIT_LIST_RIGHT_ON_TARGET -> BULLSEYE_ARROW_ICON

        // ---- Misfortune -------------------------------------------------------------------------
        Achievement.SCRATCHED_5X -> Icons.Filled.Cancel
        Achievement.DICE_HATE_ME -> Icons.Filled.SentimentVeryDissatisfied
        Achievement.ALMOST_FAMOUS -> Icons.Filled.HeartBroken
        Achievement.I_ROBOT -> Icons.Filled.Android
        Achievement.PIPPED_TO_THE_POST -> Icons.Filled.Timer
        Achievement.JAWS_OF_VICTORY -> Icons.Filled.PriorityHigh
        Achievement.SCORE_UNDER_100 -> Icons.Filled.AcUnit
        Achievement.LOW_ROLLS -> Icons.AutoMirrored.Filled.TrendingDown
        // Drawn for this app: the bonus's 63, struck through.
        Achievement.PROBABILITY_NEVER_HEARD_OF_HER -> STRUCK_THROUGH_63_ICON

        // ---- Miscellaneous (hidden) ---------------------------------------------------------------
        Achievement.I_DID_IT_MY_WAY -> Icons.Filled.Palette
        Achievement.COMMITMENT_ISSUES -> Icons.Filled.SwapHoriz
        Achievement.DECISIONS_DECISIONS -> Icons.AutoMirrored.Filled.Help
        Achievement.TIME_TO_LET_IT_GO -> Icons.Filled.HourglassEmpty
        Achievement.TIME_WASTING -> Icons.Filled.HourglassFull
        // The same glyph UndoButton.kt uses for the real undo control, not its Redo mirror image -
        // this achievement is about undoing, not redoing.
        Achievement.UNDO_DIFFERENT_CATEGORY -> Icons.AutoMirrored.Filled.Undo
        Achievement.NO_MORE_ROLLS -> Icons.Filled.Block
        Achievement.IMPATIENT -> Icons.Filled.FlashOn
        // The timer shuffling your scores into place for you.
        Achievement.LUCK_OF_THE_DRAW -> Icons.Filled.Shuffle
        Achievement.FRESH_COAT_OF_PAINT -> Icons.Filled.FormatPaint
        Achievement.EMPTY_HOUSE -> Icons.Filled.NoMeetingRoom
        Achievement.FULLER_HOUSE -> Icons.Filled.House
        Achievement.FIRST_ROLL_FULL_HOUSE -> Icons.Filled.HomeWork
        Achievement.FIRST_ROLL_LARGE_STRAIGHT -> Icons.Filled.Route
        Achievement.FIRST_ROLL_5X -> Icons.Filled.Celebration
        Achievement.SIXES_30 -> Icons.Filled.Looks6
        Achievement.CHANCE_30 -> Icons.AutoMirrored.Filled.HelpOutline
        Achievement.DEJA_VU -> Icons.Filled.History
        Achievement.PRODUCT_PLACEMENT -> Icons.Filled.Storefront
        Achievement.POINTLESS_ROLL -> Icons.Filled.RemoveCircle
        Achievement.NICE -> Icons.Filled.ThumbUp
        Achievement.ZERO_TO_HERO -> Icons.Filled.Upgrade
        Achievement.WASTED_5X -> Icons.Filled.DeleteForever
        Achievement.WHY_DID_YOU_DO_THAT -> Icons.Filled.PriorityHigh
        Achievement.ALL_ZEROES -> Icons.Filled.Quiz
        Achievement.EXTREME_LOW_ROLLS -> Icons.Filled.KeyboardDoubleArrowDown
        Achievement.WHO_MADE_THIS -> Icons.Filled.Groups

        // ---- Collection ---------------------------------------------------------------------------
        // The six score ranges, lowest to highest, count up a die's faces: one pip more for each.
        Achievement.TALLY -> dieFaceIcon(1)
        Achievement.BOOKKEEPER -> dieFaceIcon(2)
        Achievement.REGISTRAR -> dieFaceIcon(3)
        Achievement.AUDITOR -> dieFaceIcon(4)
        Achievement.ARCHIVIST -> dieFaceIcon(5)
        Achievement.HISTORIAN -> dieFaceIcon(6)
        Achievement.COMPLETIONIST -> Icons.Filled.EmojiEvents

        // ---- Easter Eggs ------------------------------------------------------------------------
        // Every icon in this category is a fixed colour rather than the ambient tint - see
        // [iconTintOrUnspecified] and .claude/UI.md's "The achievements list" section.
        Achievement.BIG_FAN -> Icons.Filled.Favorite
        // A real tricolour flag, not a single-colour Material glyph - this one's whole point is
        // its own three fixed colours, so it's built with real fills rather than borrowed from
        // Icons.Filled.
        Achievement.LUCK_OF_THE_IRISH -> rememberIrishFlagIcon()
        // A martini, not a Material glyph - like the Irish flag above, its whole point is its own
        // fixed colours (glass, liquid, olive), not a single ambient tint.
        Achievement.SHAKEN_NOT_TAPPED -> rememberMartiniIcon()
        // The Top Hat cup with its rabbit peeking out, drawn the way the player found it.
        Achievement.MAGICIANS_SECRET -> rememberMagicianIcon()
        // The menu logo's own fan - the default ivory dice, 2-4-5-3-6 - that it's earned by tapping.
        Achievement.NOT_THOSE_DICE -> rememberDiceFanIcon()
        // The sunflower the Flowerpot grew, in the pot's own colours.
        Achievement.GREENFINGERS -> rememberSunflowerIcon()
        // A sheaf of white pages, the top one written on - the paper the solution was published in.
        Achievement.THE_SOLUTION -> rememberManuscriptIcon()
    }

/**
 * The star badge on the corner of an achievement's icon square (row and unlock banner) when earning it also
 * unlocks a style ([unlocksStyle][net.zodac.dicefive.ui.game.style.unlocksStyle]) - a cue that there's
 * something extra waiting on the Styles screen. Drawn over the icon's corner rather than beside the text, so it takes
 * no text width and can't make a title or description wrap. Decorative: what it says is spoken by the
 * row's action ("Show which style this unlocks") and the banner's announcement ("Unlocks a style").
 */
@Composable
fun BoxScope.StyleRewardStar(tint: Color) {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.align(Alignment.TopEnd).offset(x = STAR_BADGE_OFFSET, y = -STAR_BADGE_OFFSET).size(STAR_BADGE_SIZE),
    )
}

private val STAR_BADGE_SIZE = 16.dp
private val STAR_BADGE_OFFSET = 6.dp

/**
 * [tint] as given, except for the fixed-colour Easter Eggs icons, where it's overridden instead:
 * [Achievement.BIG_FAN]'s heart is always [AchievementHeartRed], and [Achievement.LUCK_OF_THE_IRISH]
 * gets [Color.Unspecified], which tells [androidx.compose.material3.Icon] to skip its colour
 * filter entirely and show the flag vector's own three fills. Every Easter Egg means a specific
 * colour, not whatever container happens to hold it - see .claude/UI.md.
 */
fun Achievement.iconTintOrUnspecified(tint: Color): Color = when (this) {
    Achievement.BIG_FAN -> AchievementHeartRed
    Achievement.COMPLETIONIST -> AchievementTrophyGold
    Achievement.LUCK_OF_THE_IRISH -> Color.Unspecified
    Achievement.SHAKEN_NOT_TAPPED -> Color.Unspecified
    Achievement.MAGICIANS_SECRET -> Color.Unspecified
    Achievement.NOT_THOSE_DICE -> Color.Unspecified
    Achievement.GREENFINGERS -> Color.Unspecified
    Achievement.THE_SOLUTION -> Color.Unspecified
    else -> tint
}

/**
 * A sheaf of white pages fanned down and to the left, the top one written on in black: a heading,
 * then lines of text ending short. For [Achievement.THE_SOLUTION]. Fixed colours throughout, like
 * the other Easter Eggs icons - see [iconTintOrUnspecified]. Each page has a grey edge so the white
 * still reads against a light container.
 */
@Composable
private fun rememberManuscriptIcon(): ImageVector = remember {
    ImageVector.Builder(name = "Manuscript", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        for ((left, top) in listOf(7f to 1.8f, 5.5f to 3.4f, 4f to 5f)) {
            path(
                fill = SolidColor(ManuscriptPaper),
                stroke = SolidColor(ManuscriptPaperEdge),
                strokeLineWidth = 0.6f,
            ) {
                moveTo(left, top)
                lineTo(left + 13f, top)
                lineTo(left + 13f, top + 17f)
                lineTo(left, top + 17f)
                close()
            }
        }
        // Writing on the front page: a heading, then body lines, the last one short.
        path(stroke = SolidColor(ManuscriptInk), strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) {
            moveTo(6.4f, 8.2f)
            lineTo(14.6f, 8.2f)
        }
        path(stroke = SolidColor(ManuscriptInk), strokeLineWidth = 0.6f, strokeLineCap = StrokeCap.Round) {
            for ((y, end) in listOf(11f to 14.6f, 13f to 14.6f, 15f to 14.6f, 17f to 14.6f, 19f to 10.6f)) {
                moveTo(6.4f, y)
                lineTo(end, y)
            }
        }
    }.build()
}

@Composable
private fun rememberIrishFlagIcon(): ImageVector = remember {
    ImageVector.Builder(name = "IrishFlag", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = SolidColor(IrishGreenSwatch)) {
            moveTo(2f, 3f)
            lineTo(9f, 3f)
            lineTo(9f, 21f)
            lineTo(2f, 21f)
            close()
        }
        path(fill = SolidColor(IrishWhiteSwatch)) {
            moveTo(9f, 3f)
            lineTo(15f, 3f)
            lineTo(15f, 21f)
            lineTo(9f, 21f)
            close()
        }
        path(fill = SolidColor(IrishOrangeSwatch)) {
            moveTo(15f, 3f)
            lineTo(22f, 3f)
            lineTo(22f, 21f)
            lineTo(15f, 21f)
            close()
        }
    }.build()
}

/** A martini glass, liquid and olive-on-a-pick, each its own fixed fill - see
 * [Achievement.SHAKEN_NOT_TAPPED] and [iconTintOrUnspecified]'s doc comment. */
@Composable
private fun rememberMartiniIcon(): ImageVector = remember {
    ImageVector.Builder(name = "Martini", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        // Stem and base.
        path(fill = SolidColor(MartiniGlassSwatch)) {
            moveTo(11.1f, 13.6f)
            lineTo(11.1f, 20f)
            lineTo(8.3f, 20f)
            lineTo(8.3f, 21.4f)
            lineTo(15.7f, 21.4f)
            lineTo(15.7f, 20f)
            lineTo(12.9f, 20f)
            lineTo(12.9f, 13.6f)
            close()
        }
        // Bowl.
        path(fill = SolidColor(MartiniGlassSwatch)) {
            moveTo(3.2f, 3.6f)
            lineTo(20.8f, 3.6f)
            lineTo(12.9f, 13.6f)
            lineTo(11.1f, 13.6f)
            close()
        }
        // The drink itself, inset from the bowl's own outline so a rim of glass shows around it.
        path(fill = SolidColor(MartiniLiquidSwatch)) {
            moveTo(5.9f, 7f)
            lineTo(18.1f, 7f)
            lineTo(12.75f, 13.6f)
            lineTo(11.25f, 13.6f)
            close()
        }
        // Cocktail pick, resting across the rim.
        path(stroke = SolidColor(MartiniPickSwatch), strokeLineWidth = 0.9f, strokeLineCap = StrokeCap.Round) {
            moveTo(14.3f, 4.9f)
            lineTo(19.3f, 1.7f)
        }
        // The olive - an octagon standing in for a circle, same as its highlight below (this
        // builder has no arc primitive, so a many-sided polygon is the plain-line equivalent).
        path(fill = SolidColor(MartiniOliveSwatch)) {
            moveTo(14.90f, 6.35f)
            lineTo(14.446f, 7.446f)
            lineTo(13.35f, 7.90f)
            lineTo(12.254f, 7.446f)
            lineTo(11.80f, 6.35f)
            lineTo(12.254f, 5.254f)
            lineTo(13.35f, 4.80f)
            lineTo(14.446f, 5.254f)
            close()
        }
        path(fill = SolidColor(MartiniOliveHighlightSwatch)) {
            moveTo(13.40f, 5.85f)
            lineTo(13.239f, 6.239f)
            lineTo(12.85f, 6.40f)
            lineTo(12.461f, 6.239f)
            lineTo(12.30f, 5.85f)
            lineTo(12.461f, 5.461f)
            lineTo(12.85f, 5.30f)
            lineTo(13.239f, 5.461f)
            close()
        }
    }.build()
}

// The hat's side is foreshortened by the same raised viewing angle as the game's cups.
private const val HAT_ICON_SQUASH = CUP_VIEW_SQUASH

/** A full ellipse centred on ([cx], [cy]). */
private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcTo(rx, ry, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + rx, y1 = cy)
    arcTo(rx, ry, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - rx, y1 = cy)
    close()
}

/**
 * The Top Hat cup (in its black colour) lying tipped over with its rabbit peeking out - see
 * [Achievement.MAGICIANS_SECRET]. Drawn on the game art's own grid, with the same measurements as
 * `TopHatDiceCupStyle` - hat 46 wide and 46 deep, rabbit at full height - then scaled into the icon
 * and tipped the same way the game tips it, so the achievement shows exactly what the player found.
 * Fixed colours throughout, like the other Easter Eggs icons - see [iconTintOrUnspecified].
 */
@Composable
private fun rememberMagicianIcon(): ImageVector = remember {
    val squash = HAT_ICON_SQUASH
    val centre = 38f
    ImageVector.Builder(name = "MagiciansHat", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        // The drawing spans roughly x 2..74, y -27..66 on the game's grid: centre it, scale it into
        // the icon, and tip it over to the left like the cup lies after a roll.
        group(
            rotate = -32f,
            pivotX = centre,
            pivotY = 19.5f,
            scaleX = 0.25f,
            scaleY = 0.25f,
            translationX = 12f - centre,
            // Nudged up a touch: tipped over, the crown's low corner reaches further than the ears.
            translationY = 11.4f - 19.5f,
        ) {
            val shading = { from: Float, to: Float ->
                Brush.horizontalGradient(
                    0f to MagicianHatDark,
                    0.3f to MagicianHatLight,
                    0.58f to MagicianHatMid,
                    1f to MagicianHatDark,
                    startX = from,
                    endX = to,
                )
            }
            // Crown, then the ribbon just below the brim.
            path(fill = shading(14f, 62f)) {
                moveTo(centre - 23f, 13f)
                lineTo(centre - 24f, 57f)
                arcTo(24f, 24f * squash, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = centre + 24f, y1 = 57f)
                lineTo(centre + 23f, 13f)
                close()
            }
            path(fill = SolidColor(MagicianHatRibbon)) {
                moveTo(centre - 23.1f, 20f)
                lineTo(centre - 23.3f, 28f)
                arcTo(23.3f, 23.3f * squash, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = centre + 23.3f, y1 = 28f)
                lineTo(centre + 23.1f, 20f)
                arcTo(23.1f, 23.1f * squash, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = centre - 23.1f, y1 = 20f)
                close()
            }
            // Brim: its edge, its top face, and the opening in it.
            path(fill = SolidColor(MagicianHatDark)) {
                moveTo(centre - 36f, 11f)
                lineTo(centre - 36f, 13f)
                arcTo(36f, 36f * squash, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = centre + 36f, y1 = 13f)
                lineTo(centre + 36f, 11f)
                close()
            }
            path(fill = shading(2f, 74f)) { ellipse(centre, 11f, 36f, 36f * squash) }
            path(fill = SolidColor(MagicianHatInside)) { ellipse(centre, 11f, 23f, 23f * squash) }

            // The rabbit: ears, head and face, then its paws over the front of the rim.
            for ((angle, x) in listOf(-12f to centre - 7f, 12f to centre + 7f)) {
                group(rotate = angle, pivotX = x, pivotY = -4.4f) {
                    path(fill = SolidColor(RabbitFur)) { ellipse(x, -13.5f, 3.5f, 10.5f) }
                    path(fill = SolidColor(RabbitPink)) { ellipse(x, -13.5f, 1.55f, 8.5f) }
                }
            }
            path(fill = Brush.verticalGradient(listOf(RabbitFur, RabbitFurShade), startY = -6.5f, endY = 11.7f)) {
                ellipse(centre, 2.6f, 11.2f, 9.1f)
            }
            for (x in listOf(centre - 4.9f, centre + 4.9f)) {
                path(fill = SolidColor(RabbitEye)) { ellipse(x, 1.2f, 1.7f, 1.7f) }
                path(fill = SolidColor(Color.White)) { ellipse(x - 0.5f, 0.7f, 0.6f, 0.6f) }
            }
            path(fill = SolidColor(RabbitPink)) { ellipse(centre, 6.1f, 1.7f, 1.3f) }
            for (x in listOf(centre - 9.8f, centre + 9.8f)) {
                path(fill = SolidColor(RabbitFur)) { ellipse(x, 18.3f, 3.5f, 2.5f) }
            }
        }
    }.build()
}

/** Shown in place of [icon] for every achievement until it's unlocked - a mystery, not a spoiler. */
val LOCKED_ACHIEVEMENT_ICON: ImageVector = Icons.Filled.QuestionMark

// The dice fan icon: each die's side, and the gap between neighbours, in the icon's 24-unit
// viewport - as big as five across can be, the end dice's tilted corners just inside its edges, with
// only a sliver between neighbours (their tilts differ, so a wider gap is what shrank them). Their
// faces, tilts and drops are the menu logo's own (AppLogo's LOGO_DICE), its drops scaled from its
// 34dp dice to these.
private const val FAN_DIE = 4.5f
private const val FAN_GAP = 0.05f
private val FAN_DICE = listOf(Triple(2, -20f, 8f), Triple(4, -10f, 2f), Triple(5, 0f, 0f), Triple(3, 10f, 2f), Triple(6, 20f, 8f))

// Where the pips sit and how big, as fractions of the die: BeveledDie pads its face by 6dp of the
// logo's 34dp before laying pips out, each a radius of 0.11 of that padded face.
private const val FAN_PIP_PAD = 6f / 34f
private const val FAN_PIP_RADIUS = 0.11f * (1f - 2f * FAN_PIP_PAD)

// A circle drawn as four cubics: each control point this far along the tangent, in radii.
private const val CIRCLE_CUBIC = 0.5523f

/**
 * The menu logo's fan of five default dice - ivory, [IvoryDiceTop] to [IvoryDiceBottom] corner to
 * corner with [DicePipColor] pips, as [net.zodac.dicefive.ui.game.style.IvoryDiceStyle] draws
 * them - showing 2-4-5-3-6 on the same arc. Fixed colours, like every Easter Egg's icon.
 */
@Composable
private fun rememberDiceFanIcon(): ImageVector = remember {
    ImageVector.Builder(name = "DiceFan", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        val step = FAN_DIE + FAN_GAP
        FAN_DICE.forEachIndexed { i, (value, tilt, dropDp) ->
            val centreX = 12f + (i - 2) * step
            val centreY = 12f + dropDp / 34f * FAN_DIE
            group(
                rotate = tilt,
                pivotX = FAN_DIE / 2f,
                pivotY = FAN_DIE / 2f,
                translationX = centreX - FAN_DIE / 2f,
                translationY = centreY - FAN_DIE / 2f,
            ) {
                val face = Brush.linearGradient(listOf(IvoryDiceTop, IvoryDiceBottom), start = Offset.Zero, end = Offset(FAN_DIE, FAN_DIE))
                path(fill = face, stroke = SolidColor(IvoryDiceBottom.copy(alpha = 0.6f)), strokeLineWidth = 0.15f) {
                    roundedSquare(FAN_DIE, FAN_DIE * 0.22f)
                }
                val inner = FAN_DIE * (1f - 2f * FAN_PIP_PAD)
                path(fill = SolidColor(DicePipColor)) {
                    for (pip in pipLayout(value)) {
                        circle(FAN_DIE * FAN_PIP_PAD + pip.x * inner, FAN_DIE * FAN_PIP_PAD + pip.y * inner, FAN_DIE * FAN_PIP_RADIUS)
                    }
                }
            }
        }
    }.build()
}

/**
 * A sunflower in full bloom, the way the Flowerpot cup grows it for [Achievement.GREENFINGERS]: the
 * same two rings of petals round a seeded disc, on a stem with a pair of leaves. Fixed colours
 * throughout, like the other Easter Eggs icons - see [iconTintOrUnspecified].
 */
@Composable
private fun rememberSunflowerIcon(): ImageVector = remember {
    val centreX = 12f
    val centreY = 8.6f
    val discRadius = 3.3f
    val petalReach = 7.6f
    ImageVector.Builder(name = "Sunflower", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(stroke = SolidColor(FlowerpotStem), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
            moveTo(centreX, centreY + 3f)
            quadTo(centreX - 1f, 17f, centreX, 23f)
        }
        path(fill = SolidColor(FlowerpotLeafDark)) { leaf(11.6f, 18.6f, -5.6f, -2.6f, 1.5f) }
        path(fill = SolidColor(FlowerpotLeaf)) { leaf(11.7f, 20.4f, 5.6f, -2.2f, 1.5f) }
        // Two rings of petals, the back one a shade darker and half a petal round.
        for ((offset, colour) in listOf(0.5f to SunflowerPetalShade, 0f to SunflowerPetal)) {
            path(fill = SolidColor(colour)) {
                for (i in 0 until SUNFLOWER_ICON_PETALS) {
                    val angle = 2f * PI.toFloat() * (i + offset) / SUNFLOWER_ICON_PETALS - PI.toFloat() / 2f
                    val (ax, ay) = cos(angle) to sin(angle)
                    val (bx, by) = centreX + ax * discRadius * 0.8f to centreY + ay * discRadius * 0.8f
                    val middle = discRadius + (petalReach - discRadius) * 0.45f
                    val (mx, my) = centreX + ax * middle to centreY + ay * middle
                    val width = 2.2f
                    moveTo(bx, by)
                    quadTo(mx - ay * width, my + ax * width, centreX + ax * petalReach, centreY + ay * petalReach)
                    quadTo(mx + ay * width, my - ax * width, bx, by)
                    close()
                }
            }
        }
        path(fill = SolidColor(SunflowerDisc)) { circle(centreX, centreY, discRadius) }
        path(fill = SolidColor(SunflowerSeed)) {
            for ((ring, count) in listOf(0.35f to 5, 0.7f to 9)) {
                for (i in 0 until count) {
                    val angle = 2f * PI.toFloat() * (i + ring) / count
                    circle(centreX + cos(angle) * discRadius * ring, centreY + sin(angle) * discRadius * ring, 0.32f)
                }
            }
        }
    }.build()
}

private const val SUNFLOWER_ICON_PETALS = 12

/** A leaf from ([x], [y]) out along ([dx], [dy]) to its tip, [halfWidth] either side of its midrib at its widest. */
private fun PathBuilder.leaf(x: Float, y: Float, dx: Float, dy: Float, halfWidth: Float) {
    val length = sqrt(dx * dx + dy * dy)
    val (acrossX, acrossY) = -dy / length * halfWidth * 2f to dx / length * halfWidth * 2f
    val (mx, my) = x + dx * 0.45f to y + dy * 0.45f
    moveTo(x, y)
    quadTo(mx + acrossX, my + acrossY, x + dx, y + dy)
    quadTo(mx - acrossX, my - acrossY, x, y)
    close()
}

/** A [side]-wide square from the origin with corners rounded to [corner]. */
private fun PathBuilder.roundedSquare(side: Float, corner: Float) {
    moveTo(corner, 0f)
    lineTo(side - corner, 0f)
    quadTo(side, 0f, side, corner)
    lineTo(side, side - corner)
    quadTo(side, side, side - corner, side)
    lineTo(corner, side)
    quadTo(0f, side, 0f, side - corner)
    lineTo(0f, corner)
    quadTo(0f, 0f, corner, 0f)
    close()
}

/** A circle of [radius] centred on ([x], [y]). */
private fun PathBuilder.circle(x: Float, y: Float, radius: Float) {
    val k = radius * CIRCLE_CUBIC
    moveTo(x + radius, y)
    curveTo(x + radius, y + k, x + k, y + radius, x, y + radius)
    curveTo(x - k, y + radius, x - radius, y + k, x - radius, y)
    curveTo(x - radius, y - k, x - k, y - radius, x, y - radius)
    curveTo(x + k, y - radius, x + radius, y - k, x + radius, y)
    close()
}
