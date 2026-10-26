package pt.vereda.core.catalog

import pt.vereda.core.model.StatusState
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusParserTest {

    private val checked = "2026-10-26T13:56:43Z"
    private val now = Instant.parse(checked).plus(Duration.ofMinutes(20))

    // Shaped like the output of data/scripts/build_status.py.
    private val sample = """
        {"schema":1,"generatedAt":"$checked","ok":true,"source":"https://www.visitmadeira.com/en/",
         "trails":{
          "pr-1":{"code":"PR 1","state":"open","label":"Open","sourceUrl":"https://example.org/pr-1/","checkedAt":"$checked","stale":false},
          "pr-1.3":{"code":"PR 1.3","state":"partial","label":"Restricted","checkedAt":"$checked","stale":false},
          "pr-7":{"code":"PR 7","state":"closed","label":"Closed","checkedAt":"$checked","stale":false},
          "pr-8":{"code":"PR 8","state":"open","label":"Open","checkedAt":"2026-10-24T10:00:00Z","stale":false},
          "pr-9":{"code":"PR 9","state":"open","label":"Open","checkedAt":"$checked","stale":true},
          "pr-10":{"code":"PR 10","state":"open","label":"Open","checkedAt":"$checked"},
          "pr-11":{"code":"PR 11","state":"maybe","checkedAt":"$checked","stale":false},
          "pr-12":{"code":"PR 12","state":"open","stale":false},
          "pr-13":{"code":"PR 13","state":"open","checkedAt":"yesterday","stale":false}
         }}
    """.trimIndent()

    private val snapshot = StatusParser.parse(sample)

    @Test
    fun `fresh entries show their published state`() {
        assertEquals(StatusState.OPEN, snapshot.stateOf("pr-1", now))
        assertEquals(StatusState.PARTIAL, snapshot.stateOf("pr-1.3", now))
        assertEquals(StatusState.CLOSED, snapshot.stateOf("pr-7", now))
        assertEquals("Restricted", snapshot.status("pr-1.3")!!.label)
        assertEquals("https://example.org/pr-1/", snapshot.status("pr-1")!!.sourceUrl)
    }

    @Test
    fun `old, stale or unflagged entries are unknown, never open`() {
        assertEquals(StatusState.UNKNOWN, snapshot.stateOf("pr-8", now)) // two days old
        assertEquals(StatusState.UNKNOWN, snapshot.stateOf("pr-9", now)) // stale
        assertEquals(StatusState.UNKNOWN, snapshot.stateOf("pr-10", now)) // no stale flag
    }

    @Test
    fun `unreadable entries are dropped`() {
        assertNull(snapshot.status("pr-11")) // unknown state word
        assertNull(snapshot.status("pr-12")) // no check time
        assertNull(snapshot.status("pr-13")) // bad check time
        assertEquals(StatusState.UNKNOWN, snapshot.stateOf("pr-11", now))
    }

    @Test
    fun `trails not on the page are unknown`() {
        assertEquals(StatusState.UNKNOWN, snapshot.stateOf("pr-23", now))
    }

    @Test
    fun `failed runs keep their error`() {
        val failed = StatusParser.parse("""{"ok":false,"error":"Download failed: timeout","trails":{}}""")
        assertFalse(failed.ok)
        assertEquals("Download failed: timeout", failed.error)
        assertTrue(failed.statuses.isEmpty())
        assertTrue(snapshot.ok)
    }
}
