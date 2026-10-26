package pt.vereda.core.catalog

import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.model.Island
import pt.vereda.core.model.OfficialTrails
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSearchTest {

    private val line = listOf(GeoPoint(32.71, -16.99), GeoPoint(32.72, -16.98))

    private val trails = OfficialTrails.all.map { TrailEntry(it, null, null, emptyList()) } + listOf(
        TrailEntry(
            Trail("osm-r4444478", TrailSource.COMMUNITY, null, "Vereda do Pico Grande", null, null, null, Island.MADEIRA),
            5100, null, listOf(line),
        ),
        TrailEntry(
            Trail("imp-1", TrailSource.IMPORT, null, "Boca da Corrida to Pico Grande (mine)", null, null, null, Island.MADEIRA),
            6000, null, listOf(line),
        ),
    )

    private val places = listOf(
        Place("Pico Grande", PlaceKind.PEAK, GeoPoint(32.7146, -16.993), 1654),
        Place("Pico Ruivo", PlaceKind.PEAK, GeoPoint(32.7588, -16.9426), 1862),
        Place("Pico do Arieiro", PlaceKind.PEAK, GeoPoint(32.7355, -16.9289), 1818),
    )

    @Test
    fun `empty query lists every trail and no places`() {
        val result = CatalogSearch.search(trails, places, " ")
        assertEquals(trails.size, result.trails.size)
        assertTrue(result.places.isEmpty())
    }

    @Test
    fun `the Pico Grande question finds the trail, the import and the peak`() {
        val result = CatalogSearch.search(trails, places, "boca da corrida - Pico grande")
        // Full match first, then PR 12 (starts at Boca da Corrida, ends at Ribeira Grande), then the
        // community trail that matches two words.
        assertEquals(listOf("imp-1", "pr-12", "osm-r4444478"), result.trails.map { it.id })
        assertEquals(listOf("Pico Grande"), result.places.map { it.name })
    }

    @Test
    fun `peaks are ordered by match then height`() {
        val result = CatalogSearch.search(trails, places, "pico")
        assertEquals(listOf("Pico Ruivo", "Pico do Arieiro", "Pico Grande"), result.places.map { it.name })
    }

    @Test
    fun `codes still work`() {
        assertEquals("pr-6", CatalogSearch.search(trails, places, "pr6").trails.first().id)
    }
}
