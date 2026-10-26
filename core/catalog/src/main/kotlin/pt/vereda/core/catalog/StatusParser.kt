package pt.vereda.core.catalog

import kotlinx.serialization.Serializable
import pt.vereda.core.model.StatusRules
import pt.vereda.core.model.StatusState
import pt.vereda.core.model.TrailStatus
import java.time.Instant

/** The official status of every listed trail, as published by the status job. */
data class StatusSnapshot(
    val generatedAt: Instant?,
    val ok: Boolean,
    val error: String?,
    val statuses: Map<String, TrailStatus>,
) {
    fun status(trailId: String): TrailStatus? = statuses[trailId]

    /** What the UI may show, after the freshness rule. */
    fun stateOf(trailId: String, now: Instant): StatusState = StatusRules.effectiveState(statuses[trailId], now)

    companion object {
        val EMPTY = StatusSnapshot(null, false, null, emptyMap())
    }
}

/**
 * Reads status.json (spec: Data model). Anything unclear is dropped so the trail shows as
 * unknown: an unknown state word, a missing or unreadable check time, or a missing stale flag.
 */
object StatusParser {

    fun parse(text: String): StatusSnapshot {
        val doc = CatalogParser.json.decodeFromString(StatusJson.serializer(), text)
        val statuses = doc.trails.mapNotNull { (id, entry) ->
            val checkedAt = entry.checkedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val state = when (entry.state.lowercase()) {
                "open" -> StatusState.OPEN
                "partial" -> StatusState.PARTIAL
                "closed" -> StatusState.CLOSED
                else -> return@mapNotNull null
            }
            id to TrailStatus(
                trailId = id,
                state = state,
                checkedAt = checkedAt,
                sourceUrl = entry.sourceUrl,
                label = entry.label,
                stale = entry.stale,
            )
        }.toMap()
        return StatusSnapshot(
            generatedAt = doc.generatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            ok = doc.ok,
            error = doc.error,
            statuses = statuses,
        )
    }
}

@Serializable
internal data class StatusJson(
    val schema: Int = 1,
    val generatedAt: String? = null,
    val ok: Boolean = false,
    val error: String? = null,
    val trails: Map<String, StatusEntryJson> = emptyMap(),
)

@Serializable
internal data class StatusEntryJson(
    val code: String? = null,
    val state: String = "unknown",
    val label: String? = null,
    val sourceUrl: String? = null,
    val checkedAt: String? = null,
    // Missing flag counts as stale: never show a trail open on data we can't vouch for.
    val stale: Boolean = true,
)
