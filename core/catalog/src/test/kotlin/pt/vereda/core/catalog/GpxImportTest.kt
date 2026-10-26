package pt.vereda.core.catalog

import pt.vereda.core.geo.GpxParser
import pt.vereda.core.model.Island
import pt.vereda.core.model.TrailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpxImportTest {

    private val track = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk><name>Boca da Corrida - Pico Grande</name>
            <trkseg>
              <trkpt lat="32.71077" lon="-16.98652"><ele>1225</ele></trkpt>
              <trkpt lat="32.71200" lon="-16.98800"><ele>1260</ele></trkpt>
            </trkseg>
            <trkseg>
              <trkpt lat="32.71300" lon="-16.99000"/>
              <trkpt lat="32.71460" lon="-16.99300"/>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun `a track becomes a private imported trail`() {
        val entry = GpxImport.toEntry("imp-abc", GpxParser.parse(track), "fallback")!!
        assertEquals(TrailSource.IMPORT, entry.trail.source)
        assertEquals("imp-abc", entry.id)
        assertEquals("Boca da Corrida - Pico Grande", entry.trail.name)
        assertEquals(2, entry.lines.size)
        assertEquals(Island.MADEIRA, entry.trail.island)
        assertTrue(entry.lengthM!! in 350..700, "length was ${entry.lengthM}")
        assertEquals(1225.0, entry.lines[0][0].ele)
    }

    @Test
    fun `a route counts too, and the file name is the fallback name`() {
        val gpx = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <rte><rtept lat="33.07" lon="-16.33"/><rtept lat="33.08" lon="-16.34"/></rte>
            </gpx>
        """.trimIndent()
        val entry = GpxImport.toEntry("imp-2", GpxParser.parse(gpx), GpxImport.nameFromFile("Pico do Facho.gpx"))!!
        assertEquals("Pico do Facho", entry.trail.name)
        assertEquals(Island.PORTO_SANTO, entry.trail.island)
    }

    @Test
    fun `waypoints alone are not a trail`() {
        val gpx = """<gpx version="1.1"><wpt lat="32.7" lon="-16.9"/><trk><trkseg><trkpt lat="32.7" lon="-16.9"/></trkseg></trk></gpx>"""
        assertNull(GpxImport.toEntry("imp-3", GpxParser.parse(gpx), "x"))
    }

    @Test
    fun `file names`() {
        assertEquals("pico-grande", GpxImport.nameFromFile("downloads/pico-grande.gpx"))
        assertEquals("Imported track", GpxImport.nameFromFile(null))
        assertEquals("Imported track", GpxImport.nameFromFile(".gpx"))
    }
}
