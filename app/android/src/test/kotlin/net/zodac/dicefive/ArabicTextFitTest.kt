package net.zodac.dicefive

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [TextFitTest]'s screens, drawn in Arabic (right to left, Arabic-Indic digits): no word is split across two lines. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "ar-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArabicTextFitTest : TextFitTest() {
    override val fiveModifiersEnabled = "٥ مفعّلة"
}
