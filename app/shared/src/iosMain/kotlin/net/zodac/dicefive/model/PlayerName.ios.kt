package net.zodac.dicefive.model

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

/** Foundation's canonical composed form - the same as NFC. */
@Suppress("CAST_NEVER_SUCCEEDS") // Kotlin String and NSString are toll-free bridged on Apple platforms.
internal actual fun normalizeNfc(text: String): String = (text as NSString).precomposedStringWithCanonicalMapping
