package net.zodac.dicefive.model

private const val PHIL_WOODWARD = "phil woodward"

/**
 * True for "Phil Woodward" however it's cased or spaced ("phil  WOODWARD", trailing space) -
 * [Achievement.THE_SOLUTION]'s trigger.
 */
fun isPhilWoodward(name: String): Boolean = name.trim().split(Regex("\\s+")).joinToString(" ").lowercase() == PHIL_WOODWARD
