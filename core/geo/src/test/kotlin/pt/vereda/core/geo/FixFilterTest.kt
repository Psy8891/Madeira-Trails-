package pt.vereda.core.geo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixFilterTest {

    private val filter = FixFilter()
    private val start = Fix(lat = 32.70, lon = -16.99, accuracyM = 5.0, timeMs = 0)

    private fun movedNorth(metres: Double, afterMs: Long, accuracy: Double = 5.0): Fix {
        val p = Geo.offsetNorth(GeoPoint(start.lat, start.lon), metres)
        return Fix(p.lat, p.lon, accuracy, start.timeMs + afterMs)
    }

    @Test
    fun `first fix with good accuracy is accepted`() {
        assertTrue(filter.accept(null, start))
    }

    @Test
    fun `poor accuracy is dropped`() {
        assertFalse(filter.accept(null, start.copy(accuracyM = 30.0)))
    }

    @Test
    fun `walking pace is accepted`() {
        assertTrue(filter.accept(start, movedNorth(5.0, afterMs = 2_000)))
    }

    @Test
    fun `a jump faster than 15 km per hour is dropped`() {
        assertFalse(filter.accept(start, movedNorth(50.0, afterMs = 2_000)))
    }

    @Test
    fun `out of order or duplicate timestamps are dropped`() {
        assertFalse(filter.accept(start, movedNorth(1.0, afterMs = 0)))
        assertFalse(filter.accept(start, movedNorth(1.0, afterMs = -1_000)))
    }
}
