package net.zodac.dicefive.game

/** The hour of the day (0-23) on the phone's own clock, in its own time zone, at [epochMillis]. */
internal expect fun localHourOfDay(epochMillis: Long): Int
