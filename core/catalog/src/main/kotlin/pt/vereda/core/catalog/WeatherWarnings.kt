package pt.vereda.core.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import pt.vereda.core.model.Island
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** IPMA warning levels, mildest first (green = no warning). */
enum class WarningLevel(val key: String) {
    GREEN("green"), YELLOW("yellow"), ORANGE("orange"), RED("red");

    companion object {
        fun of(key: String?): WarningLevel = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: GREEN
    }
}

/** What the warning is about; IPMA names them in Portuguese ("Precipitação"). */
enum class WarningKind { RAIN, WIND, THUNDER, FOG, HOT, COLD, SNOW, SEA, OTHER;
    companion object {
        fun of(name: String?): WarningKind {
            val n = name.orEmpty().lowercase()
            return when {
                "precipita" in n || "rain" in n -> RAIN
                "vento" in n || "wind" in n -> WIND
                "trovoada" in n || "thunder" in n -> THUNDER
                "nevoeiro" in n || "fog" in n -> FOG
                "quente" in n || "hot" in n -> HOT
                "frio" in n || "cold" in n -> COLD
                "neve" in n || "snow" in n -> SNOW
                "agitação" in n || "agitacao" in n || "marítima" in n || "coastal" in n -> SEA
                else -> OTHER
            }
        }
    }
}

/** IPMA's warning areas for the archipelago. */
enum class WarningArea(val code: String) {
    NORTH_COAST("MCN"), SOUTH_COAST("MCS"), MOUNTAINS("MRM"), PORTO_SANTO("MPS");

    companion object {
        fun of(code: String?): WarningArea? = entries.firstOrNull { it.code == code }
        fun of(island: Island): List<WarningArea> =
            if (island == Island.PORTO_SANTO) listOf(PORTO_SANTO) else listOf(MOUNTAINS, NORTH_COAST, SOUTH_COAST)
    }
}

data class WeatherWarning(
    val area: WarningArea,
    val level: WarningLevel,
    val kind: WarningKind,
    /** IPMA's own name for the kind, for anything not mapped. */
    val kindName: String,
    val text: String,
    val start: Instant,
    val end: Instant,
)

/**
 * IPMA weather warnings (https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json,
 * spec: Safety). Fetched live; nothing is shown when the request fails.
 */
object WeatherWarnings {
    const val URL = "https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json"
    private val PORTUGAL: ZoneId = ZoneId.of("Europe/Lisbon") // same clock as Madeira

    @Serializable
    private data class Row(
        val idAreaAviso: String? = null,
        val awarenessLevelID: String? = null,
        val awarenessTypeName: String? = null,
        val text: String? = null,
        val startTime: String? = null,
        val endTime: String? = null,
    )

    fun parse(json: String): List<WeatherWarning> =
        CatalogParser.json.decodeFromString(ListSerializer(Row.serializer()), json).mapNotNull { row ->
            val area = WarningArea.of(row.idAreaAviso) ?: return@mapNotNull null
            val start = time(row.startTime) ?: return@mapNotNull null
            val end = time(row.endTime) ?: return@mapNotNull null
            WeatherWarning(
                area = area,
                level = WarningLevel.of(row.awarenessLevelID),
                kind = WarningKind.of(row.awarenessTypeName),
                kindName = row.awarenessTypeName.orEmpty(),
                text = row.text.orEmpty().trim(),
                start = start,
                end = end,
            )
        }

    /** Yellow or worse, for the island's areas, not yet over; worst and soonest first. */
    fun relevant(warnings: List<WeatherWarning>, island: Island, now: Instant): List<WeatherWarning> {
        val areas = WarningArea.of(island)
        return warnings
            .filter { it.area in areas && it.level > WarningLevel.GREEN && it.end.isAfter(now) }
            .sortedWith(compareByDescending<WeatherWarning> { it.level }.thenBy { it.start }.thenBy { it.area })
    }

    private fun time(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).atZone(PORTUGAL).toInstant() }.getOrNull()
    }
}
