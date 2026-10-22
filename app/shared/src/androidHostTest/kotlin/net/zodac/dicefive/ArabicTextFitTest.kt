package net.zodac.dicefive

import org.robolectric.annotation.Config

/** [TextFitTest]'s screens, drawn in Arabic (right to left, Arabic-Indic digits): no word is split across two lines. */
@Config(qualifiers = "ar-w360dp-h800dp")
class ArabicTextFitTest : TextFitTest() {
    override val fiveModifiersEnabled = "٥ مفعّلة"
}
