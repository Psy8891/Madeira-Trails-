package pt.vereda.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals

class GeoTest {

    @Test
    fun `one thousandth of a degree of latitude is about 111 m`() {
        assertEquals(111.195, Geo.distanceM(0.0, 0.0, 0.001, 0.0), 0.001)
    }

    @Test
    fun `distance along Madeira's latitude matches the reference value`() {
        // Reference computed independently (haversine, R = 6371008.8 m).
        assertEquals(1871.437, Geo.distanceM(32.70, -17.00, 32.70, -16.98), 0.01)
    }

    @Test
    fun `distance is symmetric and zero for the same point`() {
        val a = GeoPoint(32.7357, -16.9287)
        val b = GeoPoint(32.6496, -16.9086)
        assertEquals(Geo.distanceM(a, b), Geo.distanceM(b, a), 1e-9)
        assertEquals(0.0, Geo.distanceM(a, a), 1e-9)
    }

    @Test
    fun `polyline length is the sum of its legs`() {
        val line = listOf(GeoPoint(32.70, -17.00), GeoPoint(32.70, -16.99), GeoPoint(32.70, -16.98))
        assertEquals(1871.437, Geo.lengthM(line), 0.01)
    }

    @Test
    fun `offsets move the point by the requested metres`() {
        val p = GeoPoint(32.70, -16.99)
        assertEquals(50.0, Geo.distanceM(p, Geo.offsetNorth(p, 50.0)), 0.01)
        assertEquals(50.0, Geo.distanceM(p, Geo.offsetEast(p, 50.0)), 0.05)
    }
}
