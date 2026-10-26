package pt.vereda.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfficialTrailsTest {

    private val trails = OfficialTrails.all

    @Test
    fun `has the 42 classified trails, 39 on Madeira and 3 on Porto Santo`() {
        assertEquals(42, trails.size)
        assertEquals(39, trails.count { it.island == Island.MADEIRA })
        assertEquals(3, trails.count { it.island == Island.PORTO_SANTO })
    }

    @Test
    fun `ids and codes are unique`() {
        assertEquals(trails.size, trails.map { it.id }.toSet().size)
        assertEquals(trails.size, trails.map { it.code }.toSet().size)
    }

    @Test
    fun `every trail is official, has a code and a municipality`() {
        trails.forEach {
            assertEquals(TrailSource.OFFICIAL, it.source, it.name)
            assertNotNull(it.code, it.name)
            assertNotNull(it.municipality, it.name)
        }
    }

    @Test
    fun `ids are derived from codes`() {
        assertEquals("pr-6", Trail.idForCode("PR 6"))
        assertEquals("pr-6.1", Trail.idForCode("PR 6.1"))
        assertEquals("ps-pr-2", Trail.idForCode(" PS PR 2 "))
        assertEquals("Levada das 25 Fontes", OfficialTrails.byId("pr-6")?.name)
        assertNull(OfficialTrails.byId("pr-99"))
    }

    @Test
    fun `from-to text needs both ends`() {
        assertEquals("Rabaçal → 25 Fontes", OfficialTrails.byId("pr-6")?.fromTo)
        assertNull(OfficialTrails.byId("pr-22")?.fromTo)
    }

    @Test
    fun `Porto Santo trails all use the PS PR prefix`() {
        assertTrue(trails.filter { it.island == Island.PORTO_SANTO }.all { it.code!!.startsWith("PS PR") })
    }
}
