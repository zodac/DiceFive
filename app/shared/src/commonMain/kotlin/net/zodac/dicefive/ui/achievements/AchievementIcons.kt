package net.zodac.dicefive.ui.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.EventRepeat
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
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.ic_cowboy_hat
import net.zodac.dicefive.resources.ic_stairs
import net.zodac.dicefive.resources.ic_time_machine_car
import net.zodac.dicefive.ui.theme.AchievementHeartRed
import net.zodac.dicefive.ui.theme.IrishGreenSwatch
import net.zodac.dicefive.ui.theme.IrishOrangeSwatch
import net.zodac.dicefive.ui.theme.IrishWhiteSwatch
import net.zodac.dicefive.ui.theme.MartiniGlassSwatch
import net.zodac.dicefive.ui.theme.MartiniLiquidSwatch
import net.zodac.dicefive.ui.theme.MartiniOliveHighlightSwatch
import net.zodac.dicefive.ui.theme.MartiniOliveSwatch
import net.zodac.dicefive.ui.theme.MartiniPickSwatch
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
        Achievement.I_ROBOT -> Icons.Filled.SmartToy
        Achievement.NATURALLY_GIFTED -> Icons.Filled.Spa

        // ---- Game modes ---------------------------------------------------------------------------
        // A wedge-shaped sports car trailing fire - a nod to the film the title quotes, drawn from
        // scratch rather than traced from it.
        Achievement.NON_STANDARD_MODE -> vectorResource(Res.drawable.ic_time_machine_car)
        // Three lights, for three colours.
        Achievement.TRICOLOUR_WIN -> Icons.Filled.Traffic
        Achievement.TRICOLOUR_ALL_COLOURS -> Icons.Filled.ColorLens

        // ---- Misfortune -------------------------------------------------------------------------
        Achievement.SCRATCHED_5X -> Icons.Filled.Cancel
        Achievement.DICE_HATE_ME -> Icons.Filled.SentimentVeryDissatisfied
        Achievement.ALMOST_FAMOUS -> Icons.Filled.HeartBroken
        Achievement.SINGULARITY -> Icons.Filled.Android
        Achievement.PIPPED_TO_THE_POST -> Icons.Filled.Timer
        Achievement.JAWS_OF_VICTORY -> Icons.Filled.PriorityHigh
        Achievement.SCORE_UNDER_100 -> Icons.Filled.AcUnit
        Achievement.LOW_ROLLS -> Icons.AutoMirrored.Filled.TrendingDown
        Achievement.OUT_OF_TIME -> Icons.Filled.TimerOff

        // ---- Miscellaneous (hidden) ---------------------------------------------------------------
        Achievement.I_DID_IT_MY_WAY -> Icons.Filled.Palette
        Achievement.COMMITMENT_ISSUES -> Icons.Filled.SwapHoriz
        Achievement.DECISIONS_DECISIONS -> Icons.AutoMirrored.Filled.Help
        Achievement.TIME_TO_LET_IT_GO -> Icons.Filled.HourglassEmpty
        Achievement.TIME_WASTING -> Icons.Filled.HourglassFull
        // The same glyph UndoButton.kt uses for the real undo control, not its Redo mirror image -
        // this achievement is about undoing, not redoing.
        Achievement.UNDO_DIFFERENT_CATEGORY -> Icons.AutoMirrored.Filled.Undo
        Achievement.NOT_THOSE_DICE -> Icons.Filled.TouchApp
        Achievement.NO_MORE_ROLLS -> Icons.Filled.Block
        Achievement.IMPATIENT -> Icons.Filled.FlashOn
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
        Achievement.TALLY -> Icons.Filled.Checklist
        Achievement.BOOKKEEPER -> Icons.AutoMirrored.Filled.MenuBook
        Achievement.REGISTRAR -> Icons.AutoMirrored.Filled.LibraryBooks
        Achievement.AUDITOR -> Icons.AutoMirrored.Filled.FactCheck
        Achievement.ARCHIVIST -> Icons.Filled.Archive
        Achievement.HISTORIAN -> Icons.Filled.AutoStories
        Achievement.COMPLETIONIST -> Icons.Filled.Verified

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
    }

/**
 * [tint] as given, except for the fixed-colour Easter Eggs icons, where it's overridden instead:
 * [Achievement.BIG_FAN]'s heart is always [AchievementHeartRed], and [Achievement.LUCK_OF_THE_IRISH]
 * gets [Color.Unspecified], which tells [androidx.compose.material3.Icon] to skip its colour
 * filter entirely and show the flag vector's own three fills. Every Easter Egg means a specific
 * colour, not whatever container happens to hold it - see .claude/UI.md.
 */
fun Achievement.iconTintOrUnspecified(tint: Color): Color = when (this) {
    Achievement.BIG_FAN -> AchievementHeartRed
    Achievement.LUCK_OF_THE_IRISH -> Color.Unspecified
    Achievement.SHAKEN_NOT_TAPPED -> Color.Unspecified
    else -> tint
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

/** Shown in place of [icon] for every achievement until it's unlocked - a mystery, not a spoiler. */
val LOCKED_ACHIEVEMENT_ICON: ImageVector = Icons.Filled.QuestionMark
