package pt.vereda.core.geo

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GpxTest {

    @Test
    fun `write then parse returns the same document`() {
        val doc = GpxDocument(
            name = "Levada das 25 Fontes & Risco <PR 6>",
            description = "Route © OpenStreetMap contributors (ODbL)",
            waypoints = listOf(GpxPoint(32.7581, -17.1302, 1274.0, null, "Casa do Rabaçal \"start\"")),
            routes = listOf(
                GpxTrack("Planned", listOf(listOf(GpxPoint(32.75, -17.13), GpxPoint(32.751, -17.131)))),
            ),
            tracks = listOf(
                GpxTrack(
                    name = "Morning walk",
                    segments = listOf(
                        listOf(
                            GpxPoint(32.7581, -17.1302, 1274.0, Instant.parse("2026-10-26T09:00:00Z")),
                            GpxPoint(32.7579, -17.1309, 1268.5, Instant.parse("2026-10-26T09:01:00Z")),
                        ),
                        listOf(GpxPoint(32.7570, -17.1320, 1250.2, Instant.parse("2026-10-26T09:20:00Z"))),
                    ),
                ),
            ),
        )
        assertEquals(doc, GpxParser.parse(GpxWriter.write(doc)))
    }

    @Test
    fun `reads GPX 1_1 with metadata, missing elevation and offset times`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>PR 8</name></metadata>
              <wpt lat="32.7430" lon="-16.6900"><name>Baía d'Abra</name></wpt>
              <trk><name>PR 8</name>
                <trkseg>
                  <trkpt lat="32.7430" lon="-16.6900"><ele>100</ele><time>2026-10-26T10:00:00+01:00</time></trkpt>
                  <trkpt lat="32.7435" lon="-16.6890"/>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        val doc = GpxParser.parse(xml)
        assertEquals("PR 8", doc.name)
        assertEquals("Baía d'Abra", doc.waypoints.single().name)
        val points = doc.tracks.single().segments.single()
        assertEquals(2, points.size)
        assertEquals(100.0, points[0].ele)
        assertEquals(Instant.parse("2026-10-26T09:00:00Z"), points[0].time)
        assertNull(points[1].ele)
        assertNull(points[1].time)
    }

    @Test
    fun `reads GPX 1_0 without a namespace`() {
        val xml = """<gpx version="1.0"><name>Old file</name><rte><rtept lat="32.7" lon="-17.0"/></rte></gpx>"""
        val doc = GpxParser.parse(xml)
        assertEquals("Old file", doc.name)
        assertEquals(1, doc.routes.single().segments.single().size)
    }

    @Test
    fun `rejects files that are not GPX`() {
        assertFailsWith<GpxParseException> { GpxParser.parse("""<kml><Document/></kml>""") }
        assertFailsWith<GpxParseException> { GpxParser.parse("not xml at all") }
    }

    @Test
    fun `rejects points without valid coordinates`() {
        assertFailsWith<GpxParseException> {
            GpxParser.parse("""<gpx><wpt lat="abc" lon="-17.0"/></gpx>""")
        }
        assertFailsWith<GpxParseException> {
            GpxParser.parse("""<gpx><wpt lat="95" lon="-17.0"/></gpx>""")
        }
    }

    @Test
    fun `refuses DOCTYPE so external entities cannot be read`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <gpx><metadata><name>&secret;</name></metadata></gpx>
        """.trimIndent()
        assertFailsWith<GpxParseException> { GpxParser.parse(xml) }
    }
}
