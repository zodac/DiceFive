package net.zodac.dicefive.ui.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Filter3
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.Looks6
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.QuestionMark
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
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import net.zodac.dicefive.model.Achievement

/**
 * The glyph an unlocked [Achievement] shows in [AchievementsScreen]. Every achievement gets its
 * own icon rather than a single stock trophy - stock Material glyphs (this app draws no bespoke
 * per-achievement art, unlike the launcher mark), picked to hint at what the achievement is
 * actually about. [AchievementsScreen] falls back to [LOCKED_ACHIEVEMENT_ICON] until an
 * achievement is unlocked, so none of this is visible - or a spoiler - beforehand.
 *
 * A `when` over the enum (not a field on [Achievement] itself) so the model stays a plain data
 * catalogue with no Compose dependency, matching how `CategoryIcon.kt` maps [net.zodac.dicefive
 * .model.ScoreCategory] to a glyph rather than the enum carrying one.
 */
val Achievement.icon: ImageVector
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
        Achievement.BOTH_STRAIGHTS -> Icons.Filled.Stairs
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
        Achievement.DOUBLE_TON -> Icons.Filled.LooksTwo
        Achievement.SCORE_300 -> Icons.Filled.GpsFixed
        Achievement.TRIPLE_TON -> Icons.Filled.Filter3
        Achievement.SCORE_400 -> Icons.Filled.KeyboardDoubleArrowUp
        Achievement.SCORE_500 -> Icons.Filled.Bolt
        Achievement.CHEATER_CHEATER -> Icons.Filled.VisibilityOff

        // ---- Winning ----------------------------------------------------------------------------
        Achievement.FIRST_WIN -> Icons.Filled.Flag
        Achievement.WIN_BY_100 -> Icons.Filled.North
        Achievement.WIN_BY_5 -> Icons.Filled.PhotoCamera
        Achievement.COMEBACK -> Icons.AutoMirrored.Filled.Undo
        Achievement.BEAT_THREE_AI -> Icons.Filled.EmojiPeople
        Achievement.I_ROBOT -> Icons.Filled.SmartToy
        Achievement.NATURALLY_GIFTED -> Icons.Filled.Spa

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
        Achievement.DECISIONS_DECISIONS -> Icons.Filled.Help
        Achievement.TIME_TO_LET_IT_GO -> Icons.Filled.HourglassEmpty
        Achievement.TIME_WASTING -> Icons.Filled.HourglassFull
        Achievement.UNDO_DIFFERENT_CATEGORY -> Icons.AutoMirrored.Filled.Redo
        Achievement.NOT_THOSE_DICE -> Icons.Filled.TouchApp
        Achievement.NO_MORE_ROLLS -> Icons.Filled.Block
        Achievement.IMPATIENT -> Icons.Filled.FlashOn
        Achievement.BIG_FAN -> Icons.Filled.Favorite
        Achievement.FRESH_COAT_OF_PAINT -> Icons.Filled.FormatPaint
        Achievement.FULLER_HOUSE -> Icons.Filled.House
        Achievement.FIRST_ROLL_FULL_HOUSE -> Icons.Filled.HomeWork
        Achievement.FIRST_ROLL_LARGE_STRAIGHT -> Icons.Filled.Route
        Achievement.FIRST_ROLL_5X -> Icons.Filled.Celebration
        Achievement.SIXES_30 -> Icons.Filled.Looks6
        Achievement.CHANCE_30 -> Icons.Filled.HelpOutline
        Achievement.DEJA_VU -> Icons.Filled.History
        Achievement.PRODUCT_PLACEMENT -> Icons.Filled.Storefront
        Achievement.POINTLESS_ROLL -> Icons.Filled.RemoveCircle
        Achievement.NICE -> Icons.Filled.ThumbUp
        Achievement.ZERO_TO_HERO -> Icons.Filled.Upgrade
        Achievement.WASTED_5X -> Icons.Filled.DeleteForever
        Achievement.WHY_DID_YOU_DO_THAT -> Icons.Filled.PriorityHigh
        Achievement.ALL_ZEROES -> Icons.Filled.Quiz
        Achievement.EXTREME_LOW_ROLLS -> Icons.Filled.KeyboardDoubleArrowDown
        Achievement.WHO_MADE_THIS -> Icons.Filled.Code

        // ---- Collection ---------------------------------------------------------------------------
        Achievement.TALLY -> Icons.Filled.Checklist
        Achievement.BOOKKEEPER -> Icons.Filled.MenuBook
        Achievement.REGISTRAR -> Icons.Filled.LibraryBooks
        Achievement.AUDITOR -> Icons.Filled.FactCheck
        Achievement.ARCHIVIST -> Icons.Filled.Archive
        Achievement.HISTORIAN -> Icons.Filled.AutoStories
        Achievement.COMPLETIONIST -> Icons.Filled.Verified
    }

/** Shown in place of [icon] for every achievement until it's unlocked - a mystery, not a spoiler. */
val LOCKED_ACHIEVEMENT_ICON: ImageVector = Icons.Filled.QuestionMark
