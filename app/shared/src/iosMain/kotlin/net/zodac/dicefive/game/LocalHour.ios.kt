package net.zodac.dicefive.game

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970

internal actual fun localHourOfDay(epochMillis: Long): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitHour, fromDate = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)).toInt()
