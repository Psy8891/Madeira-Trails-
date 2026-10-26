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
           "aliases":["Boca da Corrida – Pico Grande"],"noteEn":"Only part of the walk.","notePt":"Só parte do percurso.",
           "osm":["r4444478"],"lines":["iysfEvttfB}Vng@","not a line ~"],
           "area":"Serra de Água",
           "ends":[{"lat":32.7186,"lon":-16.9939,"approachM":2400,"road":"ER 228","access":[32.7085,-16.9727],
                    "parking":[32.7087,-16.9725],"via":["Caminho Real da Encumeada"],"line":"iysfEvttfB}Vng@"},
                   {"lat":32.737,"lon":-16.988,"approachM":null}],
           "starts":[{"approachM":3462,"access":[32.711,-16.9868],"road":"Estrada da Corrida","name":"Boca da Corrida","known":true},
                     {"approachM":3015,"access":[32.7329,-16.9725],"via":["Vereda da Fajã Escura"]}]},
          {"id":"osm-w5","source":"community","name":"Broken","island":"madeira","lengthM":900,"osm":["w5"],"lines":["_p~iF"]},
          {"id":"osm-r9","source":"community","name":"Porto Santo walk","island":"porto_santo","lengthM":800,
           "osm":["r9"],"lines":["iysfEvttfB}Vng@"],"someFutureField":{"x":1}}
         ],
         "places":[{"name":"Pico Grande","kind":"peak","lat":32.7146,"lon":-16.993,"ele":1654,"aliases":["Grande"],
                    "access":{"approachM":4099,"access":[32.73289,-16.972508],"via":["Vereda da Fajã Escura"]},
                    "starts":[{"approachM":4676,"access":[32.711,-16.9868],"name":"Boca da Corrida","known":true},
                              {"approachM":4099,"access":[32.73289,-16.972508]}]},
                   {"name":"Pico sem altitude","kind":"peak","lat":32.7,"lon":-16.9,"ele":null},
                   {"kind":"peak","lat":32.6,"lon":-16.9},
                   {"kind":"parking","lat":32.7087,"lon":-16.9725},
                   {"name":"Miradouro","kind":"viewpoint","lat":32.71,"lon":-16.97},
                   {"name":"Something new","kind":"volcano","lat":32.71,"lon":-16.97}]}
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
        assertEquals("Só parte do percurso.", pico.note("pt"))
        assertEquals("Only part of the walk.", pico.note("en"))
        assertNull(catalog.trail("osm-r9")!!.note("en"))
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
    fun `area and ends with road access are read`() {
        val pico = catalog.trail("osm-r4444478")!!
        assertEquals("Serra de Água", pico.area)
        assertEquals("Serra de Água", pico.trail.area)
        assertEquals(2, pico.ends.size)
        val start = pico.ends[0].access
        assertEquals(2400, start.approachM)
        assertEquals("ER 228", start.road)
        assertEquals(listOf("Caminho Real da Encumeada"), start.via)
        assertEquals(2, start.line.size)
        assertEquals(32.7087, start.parking!!.lat, 1e-9)
        assertTrue(start.needsWalk)
        assertNull(pico.endAccess!!.approachM)
        assertEquals(32.7186, pico.startPoint!!.lat, 1e-9)
        assertEquals(32.737, pico.endPoint!!.lat, 1e-9)
        assertTrue(catalog.trail("pr-6")!!.ends.isEmpty())
    }

    @Test
    fun `start options are read, known trailheads first`() {
        val pico = catalog.trail("osm-r4444478")!!
        assertEquals(listOf("Boca da Corrida", null), pico.starts.map { it.name })
        assertTrue(pico.starts[0].known)
        assertEquals("Boca da Corrida", pico.startAccess!!.name)
        val peak = catalog.places[0]
        assertEquals(listOf(4676, 4099), peak.starts.map { it.approachM })
        // An older catalog without starts still offers its single access.
        assertEquals(listOf(4099), peak.copy(startOptions = emptyList()).starts.map { it.approachM })
        assertTrue(catalog.trail("pr-6")!!.starts.isEmpty())
    }

    @Test
    fun `peaks become places`() {
        assertEquals(PlaceKind.PEAK, catalog.places[0].kind)
        assertEquals(listOf("Pico Grande", "Pico sem altitude"), catalog.places.filter { it.kind == PlaceKind.PEAK }.map { it.name })
        assertEquals(1654, catalog.places[0].eleM)
        assertEquals(listOf("Grande"), catalog.places[0].aliases)
        assertNull(catalog.places[1].eleM)
        assertEquals(4099, catalog.places[0].access!!.approachM)
        assertEquals(listOf("Vereda da Fajã Escura"), catalog.places[0].access!!.via)
    }

    @Test
    fun `points of interest are read, unnamed ones kept and unknown kinds skipped`() {
        val kinds = catalog.places.map { it.kind to it.name }
        assertTrue(PlaceKind.PARKING to null in kinds)
        assertTrue(PlaceKind.VIEWPOINT to "Miradouro" in kinds)
        assertEquals(4, catalog.places.size) // the unnamed peak and the volcano are left out
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
