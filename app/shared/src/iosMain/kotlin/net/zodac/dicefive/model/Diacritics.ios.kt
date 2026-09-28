package net.zodac.dicefive.model

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.stringByFoldingWithOptions

/** Foundation's own diacritic folding - the same comparison iOS search uses for "diacritic-insensitive". */
@Suppress("CAST_NEVER_SUCCEEDS") // Kotlin String and NSString are toll-free bridged on Apple platforms.
internal actual fun stripDiacritics(text: String): String =
    (text as NSString).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, locale = null)
