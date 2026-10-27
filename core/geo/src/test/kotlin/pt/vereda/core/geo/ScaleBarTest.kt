package pt.vereda.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaleBarTest {
    @Test
    fun `metres per dp halve with each zoom level`() {
        val z10 = ScaleBar.metresPerDp(10.0, 32.7)
        assertEquals(z10 / 2, ScaleBar.metresPerDp(11.0, 32.7), 1e-9)
        assertEquals(64.3, z10, 0.2) // 40075 km × cos(32.7°) / (512 × 1024)
    }

    @Test
    fun `the bar is a round distance that fits`() {
        listOf(8.0, 10.0, 12.5, 14.0, 16.0, 18.0).forEach { zoom ->
            val bar = ScaleBar.pick(zoom, 32.7, 100f)
            assertTrue(bar.widthDp in 40f..100f, "zoom $zoom gave ${bar.widthDp} dp")
            val digits = bar.metres.toString().trimEnd('0')
            assertTrue(digits in setOf("1", "2", "5"), "zoom $zoom gave ${bar.metres} m")
        }
        assertEquals("5 km", ScaleBar(5000, 80f).label)
        assertEquals("200 m", ScaleBar(200, 80f).label)
    }
}
