package pt.vereda.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Expected values were computed independently with PROJ (pyproj, EPSG:32628 / EPSG:32734). */
class UtmTest {

    @Test
    fun `Funchal is in zone 28N`() {
        val utm = Utm.fromLatLon(32.6496, -16.9086)
        assertEquals(28, utm.zone)
        assertTrue(utm.northern)
        assertEquals(320991.181, utm.easting, 0.05)
        assertEquals(3614051.546, utm.northing, 0.05)
    }

    @Test
    fun `Pico do Areeiro matches PROJ`() {
        val utm = Utm.fromLatLon(32.7357, -16.9287)
        assertEquals(319279.338, utm.easting, 0.05)
        assertEquals(3623632.477, utm.northing, 0.05)
    }

    @Test
    fun `Porto Santo matches PROJ`() {
        val utm = Utm.fromLatLon(33.07, -16.34)
        assertEquals(28, utm.zone)
        assertEquals(374917.074, utm.easting, 0.05)
        assertEquals(3659845.415, utm.northing, 0.05)
    }

    @Test
    fun `southern hemisphere uses the false northing`() {
        val utm = Utm.fromLatLon(-33.9249, 18.4241)
        assertEquals(34, utm.zone)
        assertFalse(utm.northern)
        assertEquals(261881.599, utm.easting, 0.05)
        assertEquals(6243182.355, utm.northing, 0.05)
    }

    @Test
    fun `format shows zone, hemisphere and whole metres`() {
        assertEquals("28N 320991 3614052", Utm.fromLatLon(32.6496, -16.9086).format())
    }
}
