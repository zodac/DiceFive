package net.zodac.dicefive.online

import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.Achievement.*
import net.zodac.dicefive.model.AchievementCategory

/**
 * Each achievement's Play Games points, from its difficulty tier: entered by hand in Play Console from the export
 * (`scripts/export-play-console.sh`), so this is their record. Every Easter egg is worth the minimum, and the other
 * 100 share the rest of Play's 2,000 by tier. Published points can't change, so a new achievement needs points freed
 * up before it ships (`.claude/PLAY_GAMES.md`, decision 7).
 */
internal enum class PlayPointsTier(val points: Int) {
    TRIVIAL(5),
    EASY(10),
    MEDIUM(15),
    HARD(25),
    VERY_HARD(45),
    EXTREME(70),

    /** Completionist alone: every other achievement. */
    ULTIMATE(100),
    EASTER_EGG(5),
}

internal val playPointsTiers: Map<Achievement, PlayPointsTier> = buildMap {
    fun tier(tier: PlayPointsTier, vararg achievements: Achievement) = achievements.forEach { put(it, tier) }

    tier(
        PlayPointsTier.TRIVIAL,
        THE_JOURNEY_BEGINS, PRACTICE_MAKES_PERFECT, FULL_TABLE, LETS_FINISH_THIS, ONE_MORE_TIME,
        WHERE_WERE_GOING_WE_DONT_NEED_RULES, FRESH_COAT_OF_PAINT, WHO_MADE_THIS, I_DIDNT_MEAN_THAT, NO_MORE_ROLLS,
        TIME_WASTING, DECISIONS_DECISIONS, WHAT_WAS_THE_POINT_OF_THAT, A_CUNNING_STRATEGY, I_ROBOT, SCRATCHED,
        COLD_DICE, IMPATIENT, FIRST_VICTORY, NEW_PERSONAL_BEST,
    )
    tier(
        PlayPointsTier.EASY,
        GETTING_COMFORTABLE, BONUS_ROUND, FIVE_X, SOLID_ROUND, COMMITMENT_ISSUES, TIME_TO_LET_IT_GO,
        SIZE_ISNT_EVERYTHING, HOUSE_CALL, ON_A_ROLL, WASTED_FORTUNE, TRICOLOURFUL, GONE_WITH_THE_WIND, HOLD_EM, NAT_20,
        CONTRACT_FULFILLED, QUICK_ON_THE_DRAW, STRAIGHT_TALKER, LOW_ROLLS, HOW_DO_YOU_PLAY_THIS_GAME,
    )
    tier(
        PlayPointsTier.MEDIUM,
        REGULAR, SPOTLESS, UPPER_CLASS, SHARPSHOOTER, PHOTO_FINISH, PIPPED_TO_THE_POST, COMEBACK_KID,
        LAST_HUMAN_STANDING, LANDSLIDE, DEFEAT_FROM_THE_JAWS_OF_VICTORY, ALMOST_FAMOUS, THE_DICE_HATE_ME, STRAIGHT_AWAY,
        TON, NICE, SIX_OF_THE_BEST, RAINY_DAY_FUND, PROBABILITY_NEVER_HEARD_OF_HER, ZERO_TO_HERO, HALL_OF_FAMER,
        EMPTY_HOUSE, FULLER_HOUSE, TRICOLOUR_ME_IMPRESSED, CRITICAL_SUCCESS, GRAND_TOUR,
    )
    tier(
        PlayPointsTier.HARD,
        CENTURION, WELL_ROLLED, UNTOUCHABLE, ENCORE, NATURAL_5X, HIGH_ROLLER, TIE_BREAK, HOW_DO_YOU_DO_FELLOW_CPUS,
        NATURALLY_GIFTED, EVERYTHING_EVERYWHERE_ALL_AT_ONCE, THIRD_TIMES_THE_CHARM, RIGHT_ON_TARGET, BAD_OMEN,
        LOWER_CLASS, SIX_APPEAL, TAKING_A_CHANCE, DEJA_VU, HANDS_OFF, DICE_WHISPERER, PRODUCT_PLACEMENT, TALLY,
        BOOKKEEPER,
    )
    tier(
        PlayPointsTier.VERY_HARD,
        DICE_DEITY, HAT_TRICK, FULL_SET, TWICE_IN_A_LIFETIME, I_CAN_COUNT, LUCKY_SEVEN, ROCK_BOTTOM, EXACT_CHANGE,
        REGISTRAR, AUDITOR, ARCHIVIST,
    )
    tier(PlayPointsTier.EXTREME, HISTORIAN, PROFESSIONAL_ROLLER)
    tier(PlayPointsTier.ULTIMATE, COMPLETIONIST)
    Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }.forEach { put(it, PlayPointsTier.EASTER_EGG) }
}
