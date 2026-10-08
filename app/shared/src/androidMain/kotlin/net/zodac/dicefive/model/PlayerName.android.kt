package net.zodac.dicefive.model

import java.text.Normalizer

internal actual fun normalizeNfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
