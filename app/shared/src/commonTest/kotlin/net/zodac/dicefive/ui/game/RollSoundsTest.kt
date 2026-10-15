package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.platform.SoundEffect

/** Which clips a roll's shake and landing play: [shakeSound] and [landingSounds]. */
class RollSoundsTest {

    private val unheld = List(5) { Die(value = it + 1) }
    private val allHeld = unheld.map { it.copy(isHeld = true) }
    private val someHeld = unheld.mapIndexed { index, die -> die.copy(isHeld = index < 2) }

    @Test
    fun `the plain cup rattles while any die is rolled`() {
        assertEquals(SoundEffect.CUP_SHAKE, shakeSound(unheld, chickenCup = false))
        assertEquals(SoundEffect.CUP_SHAKE, shakeSound(someHeld, chickenCup = false))
    }

    @Test
    fun `the Chicken cup clucks in place of the rattle`() {
        assertEquals(SoundEffect.CUP_SHAKE_CHICKEN, shakeSound(unheld, chickenCup = true))
        assertEquals(SoundEffect.CUP_SHAKE_CHICKEN, shakeSound(someHeld, chickenCup = true))
    }

    @Test
    fun `a cup shaken with every die held sounds empty - the Chicken cup too`() {
        assertEquals(SoundEffect.EMPTY_CUP_SHAKE, shakeSound(allHeld, chickenCup = false))
        assertEquals(SoundEffect.EMPTY_CUP_SHAKE, shakeSound(allHeld, chickenCup = true))
    }

    @Test
    fun `dice thrown land on the mat`() {
        assertEquals(listOf(SoundEffect.MAT_LANDING), landingSounds(unheld, unheld.map { it.copy(value = 6) }))
        assertEquals(listOf(SoundEffect.MAT_LANDING), landingSounds(someHeld, someHeld))
    }

    @Test
    fun `a roll with every die held lands silently`() {
        assertEquals(emptyList(), landingSounds(allHeld, allHeld))
    }

    @Test
    fun `a die newly come up golden lays an egg after the mat`() {
        val rolled = unheld.mapIndexed { index, die -> die.copy(isGolden = index == 3) }
        assertEquals(listOf(SoundEffect.MAT_LANDING, SoundEffect.EGG_LAY), landingSounds(unheld, rolled))
    }

    @Test
    fun `a golden egg held through the roll lays nothing more`() {
        val before = someHeld.mapIndexed { index, die -> die.copy(isGolden = index == 0) }
        assertEquals(listOf(SoundEffect.MAT_LANDING), landingSounds(before, before))
        val allHeldGolden = allHeld.mapIndexed { index, die -> die.copy(isGolden = index == 0) }
        assertEquals(emptyList(), landingSounds(allHeldGolden, allHeldGolden))
    }
}
