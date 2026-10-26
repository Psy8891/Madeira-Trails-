package pt.vereda.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrailSearchTest {

    private val trails = OfficialTrails.all

    private fun codes(query: String) = TrailSearch.search(trails, query).map { it.code }

    @Test
    fun `normalize strips accents and punctuation`() {
        assertEquals("caldeirao verde", TrailSearch.normalize("  Caldeirão   Verde! "))
        assertEquals("baia d abra", TrailSearch.normalize("Baía d'Abra"))
        assertEquals("pr 6.1", TrailSearch.normalize("PR 6.1"))
        assertEquals("poco da neve", TrailSearch.normalize("Poço da Neve"))
    }

    @Test
    fun `empty query returns everything in catalog order`() {
        assertEquals(trails, TrailSearch.search(trails, "   "))
    }

    @Test
    fun `search ignores accents`() {
        assertEquals(listOf("PR 9", "PR 9.1"), codes("caldeirao"))
    }

    @Test
    fun `exact code comes first, then codes that start with the query`() {
        val result = codes("pr6")
        assertEquals("PR 6", result.first())
        assertEquals(
            listOf("PR 6", "PR 6.1", "PR 6.2", "PR 6.3", "PR 6.4", "PR 6.5", "PR 6.6", "PR 6.7", "PR 6.8"),
            result.take(9),
        )
    }

    @Test
    fun `code with spaces or just the number finds the trail`() {
        assertEquals("PR 6.1", codes("PR 6.1").first())
        assertEquals("PR 6.1", codes("6.1").first())
        assertEquals("PR 13.1", codes("pr13.1").first())
    }

    @Test
    fun `a bare number matches both islands`() {
        assertEquals(listOf("PR 2", "PS PR 2"), codes("2").take(2))
    }

    @Test
    fun `name words match in any order`() {
        assertEquals("PR 6", codes("fontes 25").first())
        assertTrue(codes("25 fontes").containsAll(listOf("PR 6", "PR 6.5", "PR 13.1")))
    }

    @Test
    fun `aliases cover other spellings`() {
        assertEquals(listOf("PR 1"), codes("arieiro"))
    }

    @Test
    fun `municipality search finds Porto Santo only`() {
        assertEquals(listOf("PS PR 1", "PS PR 2", "PS PR 3"), codes("porto santo"))
    }

    @Test
    fun `unknown text finds nothing`() {
        assertEquals(emptyList(), codes("levada qwerty asdf"))
        assertEquals(emptyList(), codes("pico qwerty"))
    }

    private fun community(id: String, name: String, aliases: List<String> = emptyList()) =
        Trail(id, TrailSource.COMMUNITY, null, name, null, null, null, Island.MADEIRA, aliases)

    @Test
    fun `long queries also find partial matches with the most words first`() {
        val list = listOf(
            community("osm-1", "Vereda do Pico Grande"),
            community("osm-2", "Caminho da Boca da Corrida ao Pico Grande"),
            community("osm-3", "Vereda do Pico Ruivo"),
        )
        assertEquals(listOf("osm-2", "osm-1"), TrailSearch.search(list, "boca da corrida - pico grande").map { it.id })
    }

    @Test
    fun `partial matches need whole words and at least two of them`() {
        val list = listOf(community("osm-1", "Vereda do Pico Grande"), community("osm-2", "Levada da Corridinha"))
        assertEquals(emptyList(), TrailSearch.search(list, "boca da corrida rabacal").map { it.id })
        assertEquals(emptyList(), TrailSearch.search(list, "pico grande").filter { it.id == "osm-2" })
    }

    @Test
    fun `aliases from the guides are searched`() {
        val list = listOf(community("osm-1", "Vereda do Pico Grande", listOf("Boca da Corrida – Pico Grande")))
        assertEquals(listOf("osm-1"), TrailSearch.search(list, "boca da corrida").map { it.id })
    }

    @Test
    fun `matchText ranks full and partial matches`() {
        assertEquals(2 to 2, TrailSearch.matchText(listOf("Pico Grande"), "pico grande"))
        assertEquals(3 to 2, TrailSearch.matchText(listOf("Pico Grande"), "boca corrida pico grande"))
        assertEquals(null, TrailSearch.matchText(listOf("Pico Ruivo"), "pico grande"))
    }
}
