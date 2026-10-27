package net.zodac.dicefive.ui.style

import androidx.compose.runtime.Composable
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.game.style.StyleReward
import net.zodac.dicefive.game.style.access
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.styles_reward_hidden_colours
import net.zodac.dicefive.resources.styles_reward_style
import net.zodac.dicefive.ui.common.pluralStringResource
import net.zodac.dicefive.ui.common.stringResource

/** The table art a game is drawn in for these picks: each one, or its default while it's locked. */
val SavedStyles.visualTheme: GameVisualTheme
    get() = GameVisualTheme(
        diceStyle = DiceStyles.unlockedById(diceStyleId, access),
        diceCupStyle = DiceCupStyles.unlockedById(diceCupStyleId, access),
        background = TableBackgrounds.unlockedById(tableBackgroundId, access),
        mat = DiceMats.unlockedById(diceMatId, access),
        frame = ScoreFrames.unlockedById(scoreFrameId, access),
    )

/** What [this] reward is called in a sentence, in the player's language. */
@Composable
fun StyleReward.description(): String {
    val name = stringResource(styleName)
    val noun = stringResource(categoryNoun)
    return if (hiddenColours == 0) {
        stringResource(Res.string.styles_reward_style, name, noun)
    } else {
        pluralStringResource(Res.plurals.styles_reward_hidden_colours, hiddenColours, name, noun)
    }
}
