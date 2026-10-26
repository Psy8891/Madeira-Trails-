package pt.vereda.core.catalog

import pt.vereda.core.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolylineTest {

    private fun assertClose(expected: List<Pair<Double, Double>>, actual: List<GeoPoint>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.first, a.lat, 1e-9)
            assertEquals(e.second, a.lon, 1e-9)
        }
    }

    @Test
    fun `decodes the reference example from the format documentation`() {
        assertClose(
            listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453),
            Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@"),
        )
    }

    @Test
    fun `decodes what the Python catalog job encodes`() {
        // data/scripts/geo_utils.py encode_polyline([(32.71077,-16.98652),(32.71460,-16.99300),(32.70160,-17.01860)])
        assertClose(
            listOf(32.71077 to -16.98652, 32.71460 to -16.99300, 32.70160 to -17.01860),
            Polyline.decode("iysfEvttfB}Vng@fpA~~C"),
        )
    }

    @Test
    fun `encode and decode round trip at 1e-5`() {
        val points = listOf(GeoPoint(32.6488, -16.9086), GeoPoint(32.7356, -16.8870), GeoPoint(33.0812, -16.3341))
        val decoded = Polyline.decode(Polyline.encode(points))
        points.zip(decoded).forEach { (p, d) ->
            assertEquals(p.lat, d.lat, 1e-5)
            assertEquals(p.lon, d.lon, 1e-5)
        }
    }

    @Test
    fun `empty string is an empty line`() {
        assertTrue(Polyline.decode("").isEmpty())
    }

    @Test
    fun `broken strings fail instead of returning a wrong line`() {
        assertFailsWith<IllegalArgumentException> { Polyline.decode("_p~iF") } // latitude only
        assertFailsWith<IllegalArgumentException> { Polyline.decode("_p~iF~ps|") } // cut mid-value
        assertFailsWith<IllegalArgumentException> { Polyline.decode("_p~iF ps|U") } // space is not valid
    }
}
