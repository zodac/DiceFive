package net.zodac.dicefive.ui.achievements

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.ic_cowboy_hat
import net.zodac.dicefive.resources.ic_stairs
import net.zodac.dicefive.resources.ic_time_machine_car
import net.zodac.dicefive.ui.icons.Symbols
import net.zodac.dicefive.ui.style.CUP_VIEW_SQUASH
import net.zodac.dicefive.ui.style.pipLayout
import net.zodac.dicefive.ui.theme.AchievementDeveloperCyan
import net.zodac.dicefive.ui.theme.AchievementHeartRed
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
import org.jetbrains.compose.resources.vectorResource

/**
 * The glyph an unlocked [Achievement] shows in [AchievementsScreen]. Every achievement gets its
 * own icon rather than a single stock trophy - mostly Material Symbols ([Symbols]; where nothing
 * there fit, a bespoke drawable from `ui/game/CategoryIcon.kt` or one drawn just for this, in
 * `DrawnAchievementIcons.kt`), picked to hint at what the achievement is actually about.
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
        Achievement.THE_JOURNEY_BEGINS -> Symbols.RocketLaunch
        Achievement.PRACTICE_MAKES_PERFECT -> Symbols.Person
        Achievement.FULL_TABLE -> Symbols.Groups
        Achievement.LETS_FINISH_THIS -> Symbols.History
        Achievement.ONE_MORE_TIME -> Symbols.Replay
        Achievement.GETTING_COMFORTABLE -> Symbols.Numbers
        Achievement.REGULAR -> Symbols.EventRepeat
        Achievement.CENTURION -> Symbols.MilitaryTech
        Achievement.HALL_OF_FAMER -> Symbols.EmojiEvents
        Achievement.WELL_ROLLED -> Symbols.Casino
        Achievement.ON_A_ROLL -> Symbols.ModeHeat
        // A crown for ten wins in a row: a flame would be too close to On A Roll's.
        Achievement.UNTOUCHABLE -> Symbols.Crown
        Achievement.PROFESSIONAL_ROLLER -> Symbols.WorkspacePremium

        // ---- Dice feats -----------------------------------------------------------------------
        Achievement.BONUS_ROUND -> Symbols.Star
        // Drawn for this app: the 5x box's own mark, cut out of a burst.
        Achievement.FIVE_X -> FIVE_X_BURST_ICON
        Achievement.SPOTLESS -> Symbols.CheckCircle
        // Same custom shape as Small/Large Straight's own tile icon (CategoryIcon.kt) - see
        // ic_stairs.xml for why that's a bespoke drawable rather than Material's Stairs.
        Achievement.STRAIGHT_TALKER -> vectorResource(Res.drawable.ic_stairs)
        Achievement.UPPER_CLASS -> Symbols.ArrowUpward
        Achievement.LOWER_CLASS -> Symbols.ArrowDownward
        Achievement.ENCORE -> Symbols.Repeat
        Achievement.DICE_WHISPERER -> Symbols.RecordVoiceOver
        Achievement.HAT_TRICK -> Symbols.Looks3
        // Drawn for this app: every die face, 1 to 6 - the set of numbers it collects.
        Achievement.FULL_SET -> DIE_FACE_SET_ICON
        // Stacked copies of the same thing, the dice coming up the same again.
        Achievement.DEJA_VU -> Symbols.FilterNone
        Achievement.TWICE_IN_A_LIFETIME -> Symbols.SyncAlt
        Achievement.NATURAL_5X -> Symbols.AutoAwesome
        // Drawn for this app: a number line, 1 2 3, as counting is first taught.
        Achievement.I_CAN_COUNT -> NUMBER_LINE_ICON
        Achievement.A_CUNNING_STRATEGY -> Symbols.Psychology
        Achievement.EXACT_CHANGE -> Symbols.Calculate

        // ---- Scoring ----------------------------------------------------------------------------
        Achievement.NEW_PERSONAL_BEST -> Symbols.TrendingUp
        // Drawn for this app: a kettlebell marked 100.
        Achievement.TON -> KETTLEBELL_100_ICON
        Achievement.SOLID_ROUND -> Symbols.Speed
        Achievement.SHARPSHOOTER -> Symbols.MyLocation
        Achievement.HIGH_ROLLER -> Symbols.KeyboardDoubleArrowUp
        Achievement.DICE_DEITY -> Symbols.Bolt

        // ---- Winning ----------------------------------------------------------------------------
        Achievement.FIRST_VICTORY -> Symbols.Flag
        Achievement.LANDSLIDE -> Symbols.Landslide
        Achievement.PHOTO_FINISH -> Symbols.PhotoCamera
        // A gavel - the ruling that settled an otherwise-equal score.
        Achievement.TIE_BREAK -> Symbols.Gavel
        // No cowboy-with-twin-pistols glyph in Material (nor any hat at all) for the "quick draw"
        // read of a last-round comeback, so this is the requested fallback: a plain cowboy hat.
        Achievement.COMEBACK_KID -> vectorResource(Res.drawable.ic_cowboy_hat)
        Achievement.LAST_HUMAN_STANDING -> Symbols.EmojiPeople
        Achievement.HOW_DO_YOU_DO_FELLOW_CPUS -> Symbols.Memory
        Achievement.SLOW_START -> Symbols.SlowMotionVideo
        // A sprinter out in front.
        Achievement.FRONT_RUNNER -> Symbols.Sprint
        // Drawn for this app: a googly eye, its pupil off to one side, in its own white and black.
        Achievement.EVERYTHING_EVERYWHERE_ALL_AT_ONCE -> GOOGLY_EYE_ICON

        // ---- Game modes ---------------------------------------------------------------------------
        // A wedge-shaped sports car trailing fire - a nod to the film the title quotes, drawn from
        // scratch rather than traced from it.
        Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES -> vectorResource(Res.drawable.ic_time_machine_car)
        // Drawn for this app, in fixed colours: a traffic light lit in the mode's red, yellow and blue.
        Achievement.TRICOLOURFUL -> TRICOLOUR_TRAFFIC_LIGHT_ICON
        // The Tricolour leaderboard's own three dice, in the mode's colours.
        Achievement.TRICOLOUR_ME_IMPRESSED -> TRICOLOUR_DICE_ICON
        // Fast-forward rather than Speed or a bolt - those are Solid Round's and Dice Deity's.
        Achievement.QUICK_ON_THE_DRAW -> Symbols.FastForward
        // A clock at speed, for a big score in the shortest game.
        Achievement.SIX_OF_THE_BEST -> Symbols.AvgPace
        // A hand of cards, for the poker game the mode is named after.
        Achievement.HOLD_EM -> Symbols.Style
        Achievement.LUCKY_SEVEN -> Symbols.Filter7
        Achievement.GONE_WITH_THE_WIND -> Symbols.Cyclone
        // A grey alien's head, for the close encounter of the Chance box.
        Achievement.CHANCE_ENCOUNTER -> ALIEN_HEAD_ICON
        // A hexagon, the outline of a twenty-sided die seen face on, with a 20 in it; and a star for each of the two bonuses.
        Achievement.NAT_20 -> D20_NAT_20_ICON
        Achievement.CRITICAL_SUCCESS -> Symbols.Stars
        // A list with every job ticked off - the contract seen through.
        Achievement.CONTRACT_FULFILLED -> Symbols.AssignmentTurnedIn
        Achievement.RIGHT_ON_TARGET -> Symbols.Target
        // A piggy bank, for rolls put by for a rainy day.
        Achievement.RAINY_DAY_FUND -> Symbols.Savings
        // A skull, for dice cursed into chains.
        Achievement.BAD_OMEN -> Symbols.Skull
        // A compass, for finding the way round every mode there is.
        Achievement.GRAND_TOUR -> Symbols.Explore

        // ---- Misfortune -------------------------------------------------------------------------
        Achievement.SCRATCHED -> Symbols.Cancel
        Achievement.THE_DICE_HATE_ME -> Symbols.SentimentDissatisfied
        Achievement.ALMOST_FAMOUS -> Symbols.HeartBroken
        Achievement.I_ROBOT -> Symbols.Android
        Achievement.PIPPED_TO_THE_POST -> Symbols.Timer
        // Drawn for this app: a trophy cracked in two - the win that broke at the last.
        Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY -> CRACKED_TROPHY_ICON
        Achievement.COLD_DICE -> Symbols.AcUnit
        Achievement.LOW_ROLLS -> Symbols.TrendingDown
        // Drawn for this app: the bonus's 63, struck through.
        Achievement.PROBABILITY_NEVER_HEARD_OF_HER -> STRUCK_THROUGH_63_ICON

        // ---- Miscellaneous (hidden) ---------------------------------------------------------------
        // Drawn for this app: a ring with a gem on it.
        Achievement.COMMITMENT_ISSUES -> WEDDING_RING_ICON
        Achievement.DECISIONS_DECISIONS -> Symbols.Help
        // Waving goodbye to the die that was held twice and never used.
        Achievement.TIME_TO_LET_IT_GO -> Symbols.WavingHand
        Achievement.TIME_WASTING -> Symbols.HourglassEmpty
        // The same glyph UndoButton.kt uses for the real undo control, not its Redo mirror image -
        // this achievement is about undoing, not redoing.
        Achievement.I_DIDNT_MEAN_THAT -> Symbols.Undo
        Achievement.NO_MORE_ROLLS -> Symbols.Block
        // A clock with speed lines, rather than a bolt - that's Dice Deity's.
        Achievement.IMPATIENT -> Symbols.Acute
        // The timer shuffling your scores into place for you.
        Achievement.FRESH_COAT_OF_PAINT -> Symbols.FormatPaint
        // A keypad: any number, not one in particular - Getting Comfortable has the number sign.
        Achievement.MY_FAVOURITE_NUMBER -> Symbols.Dialpad
        Achievement.HOUSE_CALL -> Symbols.HomeWork
        // Open stairs, a straight - not Straight Talker's (and the scorecard's) own stairs.
        Achievement.STRAIGHT_AWAY -> Symbols.Stairs2
        Achievement.SIX_APPEAL -> Symbols.Looks6
        Achievement.TAKING_A_CHANCE -> Symbols.HelpOutline
        Achievement.PRODUCT_PLACEMENT -> Symbols.Storefront
        Achievement.WHAT_WAS_THE_POINT_OF_THAT -> Symbols.SentimentNeutral
        Achievement.NICE -> Symbols.ThumbUp
        Achievement.ZERO_TO_HERO -> Symbols.Upgrade
        Achievement.WASTED_FORTUNE -> Symbols.DeleteForever
        Achievement.SIZE_ISNT_EVERYTHING -> Symbols.Straighten
        Achievement.HOW_DO_YOU_PLAY_THIS_GAME -> Symbols.Quiz
        Achievement.ROCK_BOTTOM -> Symbols.Anchor
        Achievement.WHO_MADE_THIS -> Symbols.Info

        // ---- Collection ---------------------------------------------------------------------------
        Achievement.FULL_HOUSES -> Symbols.OtherHouses
        // The six score ranges, lowest to highest, count up a die's faces: one pip more for each.
        Achievement.TALLY -> dieFaceIcon(1)
        Achievement.BOOKKEEPER -> dieFaceIcon(2)
        Achievement.REGISTRAR -> dieFaceIcon(3)
        Achievement.AUDITOR -> dieFaceIcon(4)
        Achievement.ARCHIVIST -> dieFaceIcon(5)
        Achievement.HISTORIAN -> dieFaceIcon(6)
        // Drawn for this app: the trophy in crystal, for the last of them all.
        Achievement.COMPLETIONIST -> CRYSTAL_TROPHY_ICON

        // ---- Easter Eggs ------------------------------------------------------------------------
        // Every icon in this category is a fixed colour rather than the ambient tint - see
        // [iconTintOrUnspecified] and .claude/UI.md's "The achievements list" section.
        // Android's own developer-mode glyph, in the Wireframe dice's cyan.
        Achievement.ARE_YOU_A_DEVELOPER -> Symbols.DeveloperMode
        Achievement.BIG_FAN -> Symbols.Favorite
        // A real tricolour flag, not a single-colour Material glyph - this one's whole point is
        // its own three fixed colours, so it's built with real fills rather than borrowed from
        // Material Symbols.
        Achievement.LUCK_OF_THE_IRISH -> rememberIrishFlagIcon()
        // A martini, not a Material glyph - like the Irish flag above, its whole point is its own
        // fixed colours (glass, liquid, olive), not a single ambient tint.
        Achievement.SHAKEN_NOT_TAPPED -> rememberMartiniIcon()
        // The Top Hat cup with its rabbit peeking out, drawn the way the player found it.
        Achievement.THE_MAGICIANS_SECRET -> rememberMagicianIcon()
        // The menu logo's own fan - the default ivory dice, 2-4-5-3-6 - that it's earned by tapping.
        Achievement.NOT_THOSE_DICE -> rememberDiceFanIcon()
        // A crescent moon and two stars, for the small hours it was earned in.
        Achievement.ROLLING_THE_MIDNIGHT_DICE -> rememberMidnightIcon()
        // The sunflower the Flowerpot grew, in the pot's own colours.
        Achievement.GREENFINGERS -> rememberSunflowerIcon()
        // A fried egg - the golden egg's yolk in its white - as found in the pan.
        Achievement.EGGCELLENT_DISCOVERY -> rememberFriedEggIcon()
        // A sheaf of white pages, the top one written on - the paper the solution was published in.
        Achievement.THE_SOLUTION -> rememberManuscriptIcon()
    }

/**
 * The star badge on the corner of an achievement's icon square (row and unlock banner) when earning it also
 * unlocks a style ([unlocksStyle][net.zodac.dicefive.ui.style.unlocksStyle]) - a cue that there's
 * something extra waiting on the Styles screen. Drawn over the icon's corner rather than beside the text, so it takes
 * no text width and can't make a title or description wrap. Decorative: what it says is spoken by the
 * row's action ("Show which style this unlocks") and the banner's announcement ("Unlocks a style").
 */
@Composable
fun BoxScope.StyleRewardStar(tint: Color) {
    Icon(
        imageVector = Symbols.Star,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.align(Alignment.TopEnd).offset(x = STAR_BADGE_OFFSET, y = -STAR_BADGE_OFFSET).size(STAR_BADGE_SIZE),
    )
}

private val STAR_BADGE_SIZE = 16.dp
private val STAR_BADGE_OFFSET = 6.dp

/**
 * [tint] as given, except for the fixed-colour icons, where it's overridden instead:
 * [Achievement.BIG_FAN]'s heart is always [AchievementHeartRed], and [Achievement.LUCK_OF_THE_IRISH]
 * gets [Color.Unspecified], which tells [androidx.compose.material3.Icon] to skip its colour
 * filter entirely and show the flag vector's own three fills. Every Easter Egg means a specific
 * colour, not whatever container happens to hold it - see .claude/UI_SCREENS.md. So do the few
 * others about a colour: Tricolour's red, yellow and blue, the googly eye's white, and the
 * Completionist's crystal.
 */
fun Achievement.iconTintOrUnspecified(tint: Color): Color = when (this) {
    Achievement.EVERYTHING_EVERYWHERE_ALL_AT_ONCE -> Color.Unspecified
    Achievement.TRICOLOURFUL -> Color.Unspecified
    Achievement.TRICOLOUR_ME_IMPRESSED -> Color.Unspecified
    Achievement.COMPLETIONIST -> Color.Unspecified
    Achievement.ARE_YOU_A_DEVELOPER -> AchievementDeveloperCyan
    Achievement.BIG_FAN -> AchievementHeartRed
    Achievement.LUCK_OF_THE_IRISH -> Color.Unspecified
    Achievement.SHAKEN_NOT_TAPPED -> Color.Unspecified
    Achievement.THE_MAGICIANS_SECRET -> Color.Unspecified
    Achievement.NOT_THOSE_DICE -> Color.Unspecified
    Achievement.ROLLING_THE_MIDNIGHT_DICE -> Color.Unspecified
    Achievement.GREENFINGERS -> Color.Unspecified
    Achievement.EGGCELLENT_DISCOVERY -> Color.Unspecified
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
 * [Achievement.THE_MAGICIANS_SECRET]. Drawn on the game art's own grid, with the same measurements as
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
val LOCKED_ACHIEVEMENT_ICON: ImageVector = Symbols.QuestionMark

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
 * corner with [DicePipColor] pips, as [net.zodac.dicefive.ui.style.IvoryDiceStyle] draws
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

/**
 * A fried egg for [Achievement.EGGCELLENT_DISCOVERY]: a lopsided white with a thin grey edge so it
 * reads on a pale row, and a golden yolk with a glint. Fixed colours, like the other Easter Eggs
 * icons - see [iconTintOrUnspecified].
 */
@Composable
private fun rememberFriedEggIcon(): ImageVector = remember {
    ImageVector.Builder(name = "FriedEgg", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = SolidColor(FriedEggWhite), stroke = SolidColor(FriedEggEdge), strokeLineWidth = 0.8f) {
            moveTo(7.5f, 4.2f)
            curveTo(10.2f, 2.2f, 14.4f, 3.2f, 16.6f, 4.6f)
            curveTo(19.4f, 6.2f, 22.2f, 8.4f, 21.6f, 12.6f)
            curveTo(21.0f, 16.8f, 18.6f, 17.4f, 17.2f, 19.0f)
            curveTo(15.4f, 21.2f, 11.6f, 21.8f, 8.6f, 20.2f)
            curveTo(6.0f, 18.8f, 5.8f, 17.2f, 4.0f, 15.2f)
            curveTo(2.2f, 13.2f, 1.8f, 10.0f, 3.4f, 7.6f)
            curveTo(4.4f, 6.0f, 6.2f, 5.2f, 7.5f, 4.2f)
            close()
        }
        path(fill = SolidColor(FriedEggYolk)) { ellipse(12.4f, 11.8f, 4.6f, 4.6f) }
        path(fill = SolidColor(FriedEggGlint)) { ellipse(10.8f, 10.0f, 1.3f, 1.0f) }
    }.build()
}

/** A pale gold crescent moon with two white stars beside it, each its own fixed fill - see [Achievement.ROLLING_THE_MIDNIGHT_DICE]. */
@Composable
private fun rememberMidnightIcon(): ImageVector = remember {
    ImageVector.Builder(name = "Midnight", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        // The crescent: round the outside of a circle about (11, 12), back along the inside of one about (15, 9).
        addPath(addPathNodes("M10.04,4.06A8,8 0 1,0 18.36,15.14A7,7 0 0,1 10.04,4.06Z"), fill = SolidColor(MidnightIconMoon))
        // Two four-pointed stars, as the Starry mat's.
        for ((cx, cy, r) in listOf(Triple(18.6f, 4.4f, 2.6f), Triple(21f, 10.4f, 1.7f))) {
            val waist = r * 0.3f
            addPath(
                addPathNodes("M$cx,${cy - r}L${cx + waist},${cy - waist}L${cx + r},${cy}L${cx + waist},${cy + waist}L$cx,${cy + r}L${cx - waist},${cy + waist}L${cx - r},${cy}L${cx - waist},${cy - waist}Z"),
                fill = SolidColor(MidnightIconStar),
            )
        }
    }.build()
}

private val MidnightIconMoon = Color(0xFFFFE08A)
private val MidnightIconStar = Color(0xFFFFFFFF)

private val FriedEggWhite = Color(0xFFFFFFFF)
private val FriedEggEdge = Color(0xFFC9C3B6)
private val FriedEggYolk = Color(0xFFFFB800)
private val FriedEggGlint = Color(0xFFFFE9A0)
