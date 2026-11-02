package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.model.HitTarget

class GoldPreviewTest {

    private val target = HitTarget(listOf(4, 1, 3, 2, null), points = 20)

    @Test
    fun `any box but a target previews a score above zero in gold`() {
        assertTrue(isGoldPreview(target = null, previewScore = 5))
        assertFalse(isGoldPreview(target = null, previewScore = 0))
        assertFalse(isGoldPreview(target = null, previewScore = null))
    }

    @Test
    fun `a Hit List target previews only an exact hit's doubled score in gold - a hit or a partial one is white`() {
        assertTrue(isGoldPreview(target, previewScore = 40))
        assertFalse(isGoldPreview(target, previewScore = 20))
        assertFalse(isGoldPreview(target, previewScore = 10))
        assertFalse(isGoldPreview(target, previewScore = 0))
    }
}
