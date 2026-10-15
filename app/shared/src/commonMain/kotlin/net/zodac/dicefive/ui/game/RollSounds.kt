package net.zodac.dicefive.ui.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.platform.SoundEffect

/**
 * The clip the cup makes as it shakes [dice], taken as the shake starts: an empty cup when every die is held
 * (nothing goes into it), the hen's clucking for the Chicken cup ([chickenCup]), and the plain rattle otherwise.
 */
internal fun shakeSound(dice: List<Die>, chickenCup: Boolean): SoundEffect = when {
    rollsNoDice(dice) -> SoundEffect.EMPTY_CUP_SHAKE
    chickenCup -> SoundEffect.CUP_SHAKE_CHICKEN
    else -> SoundEffect.CUP_SHAKE
}

/**
 * The clips a roll makes as it lands, from the dice as the shake started ([before]) to those it landed on
 * ([after]): the mat, unless every die was held and nothing was thrown, then the egg being laid when a die
 * came up golden that wasn't golden before (the same test as [net.zodac.dicefive.model.Achievement.EGGCELLENT_DISCOVERY]).
 */
internal fun landingSounds(before: List<Die>, after: List<Die>): List<SoundEffect> = buildList {
    if (!rollsNoDice(before)) add(SoundEffect.MAT_LANDING)
    if (after.indices.any { after[it].isGolden && before.getOrNull(it)?.isGolden != true }) add(SoundEffect.EGG_LAY)
}

private fun rollsNoDice(dice: List<Die>): Boolean = dice.isNotEmpty() && dice.all { it.isHeld }
