package net.zodac.dicefive

import kotlin.random.Random

/**
 * Dice that land on [values] in order, cycling once they run out - five dice rolled once give exactly the first five.
 * Every draw must be one the roll could make: a value outside the range asked for fails, rather than dealing a 7 to a
 * six-sided die. Only ranged draws are scripted; any other kind of draw fails too.
 */
open class ScriptedDice(private val values: List<Int>) : Random() {
    private var index = 0

    override fun nextBits(bitCount: Int): Int = error("Scripted dice only deal ranged draws")

    override fun nextInt(from: Int, until: Int): Int {
        val value = values[index++ % values.size]
        require(value in from until until) { "Scripted $value can't come from $from until $until" }
        return value
    }
}

/** Dice that always land on [value], so a test can deal itself a known hand. */
class LoadedDice(value: Int) : ScriptedDice(listOf(value))
