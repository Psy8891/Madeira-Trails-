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

    // Named as in OpenStreetMap, where Madeira's peaks often lack "Pico".
    private val places = listOf(
        Place("Grande", PlaceKind.PEAK, GeoPoint(32.737, -16.988), 1654),
        Place("Ruivo", PlaceKind.PEAK, GeoPoint(32.7588, -16.9426), 1862),
        Place("Pico do Arieiro", PlaceKind.PEAK, GeoPoint(32.7355, -16.9289), 1818),
        Place("Achada Grande", PlaceKind.PEAK, GeoPoint(32.782, -17.188), 1194),
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
        assertEquals("Grande", result.places.first().name)
    }

    @Test
    fun `peaks are ordered by match then height`() {
        val result = CatalogSearch.search(trails, places, "pico")
        assertEquals(listOf("Ruivo", "Pico do Arieiro", "Grande", "Achada Grande"), result.places.map { it.name })
    }

    @Test
    fun `peaks named without Pico are found with it`() {
        assertEquals(listOf("Grande", "Achada Grande"), CatalogSearch.search(trails, places, "pico grande").places.map { it.name })
        assertEquals(listOf("Ruivo"), CatalogSearch.search(trails, places, "Pico Ruivo").places.map { it.name })
    }

    @Test
    fun `a fuller recorded name is shown`() {
        assertEquals("Pico Grande", Place("Grande", PlaceKind.PEAK, GeoPoint(32.7, -16.9), 1654, listOf("Pico Grande")).displayName)
        assertEquals("Grande", Place("Grande", PlaceKind.PEAK, GeoPoint(32.7, -16.9), 1654, listOf("Big one")).displayName)
        assertEquals("Ruivo", places[1].displayName)
    }

    @Test
    fun `named points of interest are found, unnamed and town ones are not`() {
        val pois = places + listOf(
            Place("Miradouro do Pico Grande", PlaceKind.VIEWPOINT, GeoPoint(32.73, -16.98), null),
            Place("Parque Pico Grande", PlaceKind.PARKING, GeoPoint(32.73, -16.98), null),
            Place(null, PlaceKind.WATERFALL, GeoPoint(32.73, -16.98), null),
            Place("Cascata Grande", PlaceKind.WATERFALL, GeoPoint(32.73, -16.98), null),
        )
        val found = CatalogSearch.search(trails, pois, "pico grande").places.map { it.name }
        // Peaks first, then other places with the same match.
        assertEquals(listOf("Grande", "Achada Grande", "Miradouro do Pico Grande"), found.take(3))
        assertTrue("Parque Pico Grande" !in found)
        assertEquals(listOf("Cascata Grande"), CatalogSearch.search(trails, pois, "cascata").places.map { it.name })
    }

    @Test
    fun `trails are found by their area`() {
        val withArea = TrailEntry(
            Trail("osm-w9", TrailSource.COMMUNITY, null, "Vereda do Pico", null, null, null, Island.MADEIRA, area = "Santo António"),
            700, null, listOf(line),
        )
        assertEquals(listOf("osm-w9"), CatalogSearch.search(listOf(withArea), places, "santo antonio").trails.map { it.id })
    }

    @Test
    fun `codes still work`() {
        assertEquals("pr-6", CatalogSearch.search(trails, places, "pr6").trails.first().id)
    }
}
