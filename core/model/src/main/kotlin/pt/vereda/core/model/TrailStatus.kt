package pt.vereda.core.model

import java.time.Duration
import java.time.Instant

enum class StatusState { OPEN, PARTIAL, CLOSED, UNKNOWN }

/** One trail's official status as last read by the status job (spec: status.json). */
data class TrailStatus(
    val trailId: String,
    val state: StatusState,
    val checkedAt: Instant,
    val noteEn: String? = null,
    val notePt: String? = null,
    val sourceUrl: String? = null,
    /** The wording on the official page ("Restricted"). */
    val label: String? = null,
    /** The status job could not read the source on its last run; this is an old value. */
    val stale: Boolean = false,
)

/**
 * Safety rules for showing status. The app must never show a trail as open on old or missing data.
 */
object StatusRules {

    /** Status older than this is shown as unknown. */
    val MAX_AGE: Duration = Duration.ofHours(24)

    /** Tolerated clock difference between the status job and the phone. */
    val CLOCK_SKEW: Duration = Duration.ofMinutes(5)

    /** The state the UI may show for [status] at [now]. */
    fun effectiveState(status: TrailStatus?, now: Instant): StatusState {
        if (status == null || status.stale) return StatusState.UNKNOWN
        if (status.checkedAt.isAfter(now.plus(CLOCK_SKEW))) return StatusState.UNKNOWN
        if (Duration.between(status.checkedAt, now) > MAX_AGE) return StatusState.UNKNOWN
        return status.state
    }

    /** Whether Navigate must show a warning first; closed trails are also blocked by the UI. */
    fun needsWarningBeforeNavigate(trail: Trail, state: StatusState): Boolean =
        trail.source != TrailSource.OFFICIAL || state != StatusState.OPEN
}
