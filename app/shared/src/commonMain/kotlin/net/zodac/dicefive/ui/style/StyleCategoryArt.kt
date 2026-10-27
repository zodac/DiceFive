package net.zodac.dicefive.ui.style

import net.zodac.dicefive.game.style.StyleCategory

/** This category's art: its registry's groups, each drawn. */
val StyleCategory.catalog: StyleCatalog<*>
    get() = when (this) {
        StyleCategory.DICE -> DiceStyles
        StyleCategory.DICE_CUP -> DiceCupStyles
        StyleCategory.MAT -> DiceMats
        StyleCategory.BACKGROUND -> TableBackgrounds
        StyleCategory.FRAME -> ScoreFrames
    }
