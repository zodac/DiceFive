package net.zodac.dicefive.model

import java.text.Normalizer

/** Nonspacing marks - what an accented letter leaves behind once decomposed. */
private val DIACRITICS_REGEX = "\\p{Mn}+".toRegex()

/** Decomposes to NFD, splitting each accented letter into its base letter plus combining marks,
 * then drops the marks. */
internal actual fun stripDiacritics(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS_REGEX, "")
