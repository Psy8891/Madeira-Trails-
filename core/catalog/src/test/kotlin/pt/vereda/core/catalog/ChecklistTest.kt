package pt.vereda.core.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChecklistTest {
    private val base = WalkFacts(
        official = false, levada = false, tunnels = 0, minutes = 90, highestM = 400.0,
        daylightLeftMinutes = 600, warnings = false, offlineMap = true,
    )

    @Test
    fun `a short low walk gets the basics`() {
        assertEquals(
            listOf(ChecklistItem.WEATHER, ChecklistItem.WATER, ChecklistItem.FOOTWEAR, ChecklistItem.PHONE, ChecklistItem.TELL_SOMEONE),
            Checklists.items(base),
        )
    }

    @Test
    fun `official trails need the status and a booking`() {
        val items = Checklists.items(base.copy(official = true))
        assertEquals(listOf(ChecklistItem.STATUS, ChecklistItem.BOOKING), items.take(2))
    }

    @Test
    fun `tunnels and levadas need a torch, peaks need layers`() {
        assertTrue(ChecklistItem.TORCH in Checklists.items(base.copy(tunnels = 2)))
        assertTrue(ChecklistItem.TORCH in Checklists.items(base.copy(levada = true)))
        assertTrue(ChecklistItem.LAYERS in Checklists.items(base.copy(highestM = 1862.0)))
        assertTrue(ChecklistItem.LAYERS in Checklists.items(base.copy(warnings = true)))
        assertFalse(ChecklistItem.LAYERS in Checklists.items(base))
    }

    @Test
    fun `too little daylight says start earlier and take a torch`() {
        val items = Checklists.items(base.copy(minutes = 240, daylightLeftMinutes = 200))
        assertTrue(ChecklistItem.START_EARLIER in items)
        assertTrue(ChecklistItem.TORCH in items)
        assertFalse(ChecklistItem.START_EARLIER in Checklists.items(base.copy(minutes = 240, daylightLeftMinutes = 400)))
    }

    @Test
    fun `no offline map reminds to download it`() {
        assertTrue(ChecklistItem.OFFLINE_MAP in Checklists.items(base.copy(offlineMap = false)))
    }
}
