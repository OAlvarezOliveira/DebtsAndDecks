package com.debtsdecks.gdx.render

import com.badlogic.gdx.math.Rectangle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F2 task 7.4 (spec R2.7): the district title position must derive from the live viewport width
 * through the existing layout helpers, with no fixed 1280-space coordinate.
 *
 * The title is centred on its own width, so `x == (worldWidth - width) / 2` for every width. A wider
 * device therefore pushes the title right instead of pinning it to the old 1280-world left/right
 * anchors. This is the one piece of the render wiring that is cheap and meaningful to assert headlessly
 * — the actual drawing is covered by review, not a pixel test.
 */
class DistrictTitleLayoutTest {

    @Test
    fun `title is horizontally centred, so its x derives from the world width`() {
        val at1280 = CombatLayout.districtTitle(1280f)
        val at2133 = CombatLayout.districtTitle(2133f)

        // Centred on its own width: x == (worldWidth - width) / 2 at every width.
        assertEquals((1280f - at1280.width) / 2f, at1280.x, 1e-3f)
        assertEquals((2133f - at2133.width) / 2f, at2133.x, 1e-3f)
    }

    @Test
    fun `title x moves with the viewport and is never pinned to a 1280 coordinate`() {
        val at1280 = CombatLayout.districtTitle(1280f)
        val at2133 = CombatLayout.districtTitle(2133f)

        // A wider world shifts the centred title right.
        assertTrue(at2133.x > at1280.x, "wider viewport must move the title right")

        // No fixed 1280-space anchor: at a non-1280 width the x is not the 1280-relative value,
        // and the two widths never coincide.
        assertNotEquals(at1280.x, at2133.x)
        assertNotEquals((1280f - at1280.width) / 2f, at2133.x, 1e-3f)
    }
}
