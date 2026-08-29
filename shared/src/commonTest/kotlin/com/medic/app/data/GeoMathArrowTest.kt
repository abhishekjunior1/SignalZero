package com.medic.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The hospital list hard-coded "↗" for every entry, so a casualty told to head
 * west was shown an arrow pointing north-east. These tests pin the glyph to the
 * bearing, and pin it to the same sectors as the cardinal label so the two can
 * never disagree on screen.
 */
class GeoMathArrowTest {

    @Test
    fun arrowMatchesEachCardinalSector() {
        assertEquals("↑", GeoMath.bearingToArrow(0.0))     // N
        assertEquals("↗", GeoMath.bearingToArrow(45.0))    // NE
        assertEquals("→", GeoMath.bearingToArrow(90.0))    // E
        assertEquals("↘", GeoMath.bearingToArrow(135.0))   // SE
        assertEquals("↓", GeoMath.bearingToArrow(180.0))   // S
        assertEquals("↙", GeoMath.bearingToArrow(225.0))   // SW
        assertEquals("←", GeoMath.bearingToArrow(270.0))   // W
        assertEquals("↖", GeoMath.bearingToArrow(315.0))   // NW
    }

    @Test
    fun arrowWrapsAroundNorth() {
        assertEquals("↑", GeoMath.bearingToArrow(359.9))
        assertEquals("↑", GeoMath.bearingToArrow(10.0))
        // NW spans 292.5..337.5; 340 is already back inside the N sector.
        assertEquals("↖", GeoMath.bearingToArrow(320.0))
        assertEquals("↑", GeoMath.bearingToArrow(340.0))
    }

    @Test
    fun arrowNeverDisagreesWithCardinalLabel() {
        val expected = mapOf(
            "N" to "↑", "NE" to "↗", "E" to "→", "SE" to "↘",
            "S" to "↓", "SW" to "↙", "W" to "←", "NW" to "↖",
        )
        var d = 0.0
        while (d < 360.0) {
            assertEquals(
                expected.getValue(GeoMath.bearingToCardinal(d)),
                GeoMath.bearingToArrow(d),
                "glyph and cardinal disagree at $d degrees",
            )
            d += 0.5
        }
    }
}
