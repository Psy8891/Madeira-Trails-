package pt.vereda.core.catalog

import org.junit.jupiter.api.Assumptions.assumeTrue
import pt.vereda.core.model.TrailSource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the real catalog the APK ships (written by data/scripts/build_catalog.py), so a format
 * change in the Python job can't silently break the app.
 */
class BundledCatalogTest {

    private val file = File("../../app/src/main/assets/catalog.json")

    private fun catalog(): Catalog {
        assumeTrue(file.exists(), "No bundled catalog yet")
        return CatalogParser.parse(file.readText())
    }

    @Test
    fun `every official trail has a readable line`() {
        val official = catalog().trails.filter { it.trail.source == TrailSource.OFFICIAL }
        assertEquals(42, official.size)
        val missing = official.filterNot { it.hasLine }.map { it.trail.code }
        assertTrue(missing.size <= 3, "Official trails without a line: $missing")
    }

    @Test
    fun `lines are in Madeira or Porto Santo`() {
        catalog().trails.forEach { entry ->
            val b = entry.bounds ?: return@forEach
            assertTrue(b.south > 32.55 && b.north < 33.2 && b.west > -17.4 && b.east < -16.2, "${entry.id} is outside: $b")
        }
    }

    @Test
    fun `the trails people ask about are findable`() {
        val c = catalog()
        val pico = CatalogSearch.search(c.trails, c.places, "boca da corrida - pico grande")
        assertTrue(pico.trails.any { it.trail.source == TrailSource.COMMUNITY && "Pico Grande" in it.trail.name })
        assertTrue(pico.places.isNotEmpty())
        assertEquals("pr-6", CatalogSearch.search(c.trails, c.places, "25 fontes").trails.first().id)
    }

    @Test
    fun `the map GeoJSON for the whole catalog is built quickly`() {
        val c = catalog()
        val start = System.nanoTime()
        val json = TrailGeoJson.lines(c.trails) { LineStyle.UNKNOWN }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(json.length > 10_000)
        assertTrue(ms < 2_000, "took $ms ms")
    }
}
