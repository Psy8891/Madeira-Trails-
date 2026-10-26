package pt.vereda.core.catalog

import pt.vereda.core.model.Island
import pt.vereda.core.model.TrailSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogParserTest {

    // Shaped like the output of data/scripts/build_catalog.py.
    private val sample = """
        {"schema":1,"generatedAt":"2026-10-26T14:00:00Z","attribution":"© OpenStreetMap contributors (ODbL)",
         "trails":[
          {"id":"pr-6","source":"official","code":"PR 6","name":"Levada das 25 Fontes","island":"madeira",
           "lengthM":4300,"osm":["r1"],"lines":["iysfEvttfB}Vng@fpA~~C"]},
          {"id":"pr-7","source":"official","code":"PR 7","name":"Levada do Moinho","island":"madeira",
           "lengthM":null,"osm":[],"lines":[]},
          {"id":"osm-r4444478","source":"community","name":"Vereda do Pico Grande","from":null,"to":null,
           "island":"madeira","lengthM":5100,"sacScale":"demanding_mountain_hiking",
           "aliases":["Boca da Corrida – Pico Grande"],"osm":["r4444478"],"lines":["iysfEvttfB}Vng@","not a line ~"]},
          {"id":"osm-w5","source":"community","name":"Broken","island":"madeira","lengthM":900,"osm":["w5"],"lines":["_p~iF"]},
          {"id":"osm-r9","source":"community","name":"Porto Santo walk","island":"porto_santo","lengthM":800,
           "osm":["r9"],"lines":["iysfEvttfB}Vng@"],"someFutureField":{"x":1}}
         ],
         "places":[{"name":"Pico Grande","kind":"peak","lat":32.7146,"lon":-16.993,"ele":1654},
                   {"name":"Pico sem altitude","kind":"peak","lat":32.7,"lon":-16.9,"ele":null}]}
    """.trimIndent()

    private val catalog = CatalogParser.parse(sample)

    @Test
    fun `every official trail is listed, with or without a line`() {
        val official = catalog.trails.filter { it.trail.source == TrailSource.OFFICIAL }
        assertEquals(42, official.size)
        assertTrue(catalog.trail("pr-6")!!.hasLine)
        assertFalse(catalog.trail("pr-7")!!.hasLine)
        assertFalse(catalog.trail("pr-1")!!.hasLine) // not in the file at all
    }

    @Test
    fun `official trails keep the curated names and ends from the app`() {
        val pr6 = catalog.trail("pr-6")!!
        assertEquals("PR 6", pr6.trail.code)
        assertEquals("Rabaçal", pr6.trail.from)
        assertEquals(4300, pr6.lengthM)
        assertEquals(listOf("r1"), pr6.osmIds)
        assertEquals(3, pr6.lines.single().size)
    }

    @Test
    fun `community trails come after the official ones with aliases and bad lines dropped`() {
        val pico = catalog.trail("osm-r4444478")!!
        assertEquals(TrailSource.COMMUNITY, pico.trail.source)
        assertEquals(listOf("Boca da Corrida – Pico Grande"), pico.trail.aliases)
        assertEquals(1, pico.lines.size) // the broken second line was skipped
        assertEquals("demanding_mountain_hiking", pico.sacScale)
        assertTrue(catalog.trails.indexOf(pico) >= 42)
    }

    @Test
    fun `a community trail with no readable line is left out`() {
        assertNull(catalog.trail("osm-w5"))
    }

    @Test
    fun `island and unknown fields are handled`() {
        assertEquals(Island.PORTO_SANTO, catalog.trail("osm-r9")!!.trail.island)
    }

    @Test
    fun `peaks become places`() {
        assertEquals(listOf("Pico Grande", "Pico sem altitude"), catalog.places.map { it.name })
        assertEquals(1654, catalog.places[0].eleM)
        assertNull(catalog.places[1].eleM)
        assertEquals(Instant.parse("2026-10-26T14:00:00Z"), catalog.generatedAt)
    }

    @Test
    fun `bounds cover all lines of a trail`() {
        val bounds = catalog.trail("pr-6")!!.bounds!!
        assertEquals(32.70160, bounds.south, 1e-9)
        assertEquals(32.71460, bounds.north, 1e-9)
        assertEquals(-17.01860, bounds.west, 1e-9)
        assertEquals(-16.98652, bounds.east, 1e-9)
        assertNull(catalog.trail("pr-7")!!.bounds)
    }

    @Test
    fun `a minimal file still lists the official trails`() {
        val minimal = CatalogParser.parse("""{"trails":[]}""")
        assertEquals(42, minimal.trails.size)
        assertTrue(minimal.places.isEmpty())
    }
}
