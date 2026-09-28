package net.zodac.dicefive.ui.common

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

// NSDateFormatter defaults to the device's locale and time zone, as java.time's does on Android.
private val TIMESTAMP_FORMATTER = NSDateFormatter().apply { dateFormat = "MMM dd, yyyy HH:mm" }

internal actual fun formatTimestamp(epochMillis: Long): String =
    TIMESTAMP_FORMATTER.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
