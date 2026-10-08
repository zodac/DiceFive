package net.zodac.dicefive

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** [TextFitTest]'s screens, drawn in Arabic (right to left, Arabic-Indic digits): no word is split across two lines. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w360dp-h800dp")
class ArabicTextFitTest : TextFitTest() {
    override val fiveModifiersEnabled = "٥ مفعّلة"
}
