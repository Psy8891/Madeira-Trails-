package pt.vereda.core.catalog

import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.geo.GpxParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrailGpxTest {

    @Test
    fun `a trail is written as one track that reads back`() {
        val lines = listOf(
            listOf(GeoPoint(32.71, -16.98), GeoPoint(32.72, -16.97)),
            listOf(GeoPoint(32.73, -16.96)), // too short, left out
        )
        val text = TrailGpx.write("Vereda do Pico Grande & co", lines)
        val doc = GpxParser.parse(text)
        assertEquals("Vereda do Pico Grande & co", doc.name)
        assertTrue(doc.description!!.contains("OpenStreetMap"))
        assertEquals(1, doc.tracks.single().segments.size)
        assertEquals(32.72, doc.tracks.single().segments.single()[1].lat, 1e-7)
    }

    @Test
    fun `file names are safe`() {
        assertEquals("Vereda_do_Pico_Grande.gpx", TrailGpx.fileName("Vereda do Pico Grande"))
        assertEquals("PR_6_Levada_das_25_Fontes.gpx", TrailGpx.fileName("PR 6 · Levada das 25 Fontes"))
        assertEquals("route.gpx", TrailGpx.fileName("///"))
    }
}
