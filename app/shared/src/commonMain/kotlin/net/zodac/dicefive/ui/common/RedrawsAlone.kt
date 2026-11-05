package net.zodac.dicefive.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag

/** The tag of a [redrawsAlone] node, for a test to see it - a screen reader finds nothing there. */
internal const val REDRAWS_ALONE_TAG = "redraws_alone"

/**
 * Gives art that redraws on its own clock a layer of its own, for a sibling placed under what it sits behind. A state read
 * while drawing redraws the whole layer it's drawn in, so art drawn straight onto a parent has everything else in that
 * layer (a board's score text, a menu's wordmark) recorded again on each of its frames, unchanged.
 *
 * It must come before the modifier that draws the art.
 */
internal fun Modifier.redrawsAlone(): Modifier = clearAndSetSemantics { testTag = REDRAWS_ALONE_TAG }.graphicsLayer { }
