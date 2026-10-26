package pt.vereda.core.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusRulesTest {

    private val now = Instant.parse("2026-10-26T12:00:00Z")

    private fun status(state: StatusState, age: Duration) =
        TrailStatus(trailId = "pr-6", state = state, checkedAt = now.minus(age))

    @Test
    fun `missing status is unknown`() {
        assertEquals(StatusState.UNKNOWN, StatusRules.effectiveState(null, now))
    }

    @Test
    fun `fresh status is shown as published`() {
        assertEquals(StatusState.OPEN, StatusRules.effectiveState(status(StatusState.OPEN, Duration.ofMinutes(18)), now))
        assertEquals(StatusState.PARTIAL, StatusRules.effectiveState(status(StatusState.PARTIAL, Duration.ofHours(3)), now))
        assertEquals(StatusState.CLOSED, StatusRules.effectiveState(status(StatusState.CLOSED, Duration.ofHours(23)), now))
    }

    @Test
    fun `stale status is unknown even when recent`() {
        val stale = status(StatusState.OPEN, Duration.ofMinutes(5)).copy(stale = true)
        assertEquals(StatusState.UNKNOWN, StatusRules.effectiveState(stale, now))
    }

    @Test
    fun `status exactly 24 hours old is still shown`() {
        assertEquals(StatusState.OPEN, StatusRules.effectiveState(status(StatusState.OPEN, Duration.ofHours(24)), now))
    }

    @Test
    fun `open status older than 24 hours becomes unknown, never open`() {
        val stale = status(StatusState.OPEN, Duration.ofHours(24).plusSeconds(1))
        assertEquals(StatusState.UNKNOWN, StatusRules.effectiveState(stale, now))
    }

    @Test
    fun `status from the future is trusted only within the clock skew`() {
        assertEquals(StatusState.OPEN, StatusRules.effectiveState(status(StatusState.OPEN, Duration.ofMinutes(-4)), now))
        assertEquals(StatusState.UNKNOWN, StatusRules.effectiveState(status(StatusState.OPEN, Duration.ofMinutes(-10)), now))
    }

    @Test
    fun `navigate warns unless an official trail is open`() {
        val official = OfficialTrails.byId("pr-6")!!
        val community = official.copy(id = "osm-r1", source = TrailSource.COMMUNITY, code = null)
        assertFalse(StatusRules.needsWarningBeforeNavigate(official, StatusState.OPEN))
        assertTrue(StatusRules.needsWarningBeforeNavigate(official, StatusState.PARTIAL))
        assertTrue(StatusRules.needsWarningBeforeNavigate(official, StatusState.CLOSED))
        assertTrue(StatusRules.needsWarningBeforeNavigate(official, StatusState.UNKNOWN))
        assertTrue(StatusRules.needsWarningBeforeNavigate(community, StatusState.OPEN))
    }
}
