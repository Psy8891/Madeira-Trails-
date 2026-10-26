package pt.vereda.core.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.vereda.core.model.Island
import pt.vereda.core.model.OfficialTrails
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSource
import pt.vereda.core.geo.GeoPoint
import java.time.Instant

/**
 * Reads catalog.json (spec: Data model). Every official trail is always returned, with or
 * without a line, so a gap in OpenStreetMap never hides a trail. Broken lines are skipped
 * one by one rather than failing the whole catalog.
 */
object CatalogParser {

    internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parse(text: String): Catalog {
        val doc = json.decodeFromString(CatalogJson.serializer(), text)
        val fromJson = doc.trails.associateBy { it.id }

        val official = OfficialTrails.all.map { trail ->
            val entry = fromJson[trail.id]
            TrailEntry(
                trail = trail,
                lengthM = entry?.lengthM,
                sacScale = entry?.sacScale,
                lines = entry?.lines?.let(::decodeLines).orEmpty(),
                osmIds = entry?.osm.orEmpty(),
            )
        }
        val officialIds = official.mapTo(HashSet()) { it.id }

        val community = doc.trails
            .filter { it.source == "community" && it.id !in officialIds }
            .map { t ->
                TrailEntry(
                    trail = Trail(
                        id = t.id,
                        source = TrailSource.COMMUNITY,
                        code = null,
                        name = t.name,
                        from = t.from,
                        to = t.to,
                        municipality = null,
                        island = island(t.island),
                        aliases = t.aliases,
                    ),
                    lengthM = t.lengthM,
                    sacScale = t.sacScale,
                    lines = decodeLines(t.lines),
                    osmIds = t.osm,
                )
            }
            .filter { it.hasLine }

        val places = doc.places.mapNotNull { p ->
            if (p.kind != "peak" || p.name.isBlank()) return@mapNotNull null
            Place(p.name, PlaceKind.PEAK, GeoPoint(p.lat, p.lon), p.ele)
        }

        return Catalog(
            generatedAt = doc.generatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            attribution = doc.attribution,
            trails = official + community,
            places = places,
        )
    }

    private fun decodeLines(encoded: List<String>): List<List<GeoPoint>> =
        encoded.mapNotNull { line -> runCatching { Polyline.decode(line) }.getOrNull()?.takeIf { it.size >= 2 } }

    private fun island(value: String?): Island =
        if (value == "porto_santo") Island.PORTO_SANTO else Island.MADEIRA
}

@Serializable
internal data class CatalogJson(
    val schema: Int = 1,
    val generatedAt: String? = null,
    val attribution: String = "",
    val trails: List<CatalogTrailJson> = emptyList(),
    val places: List<CatalogPlaceJson> = emptyList(),
)

@Serializable
internal data class CatalogTrailJson(
    val id: String,
    val source: String = "community",
    val code: String? = null,
    val name: String,
    val from: String? = null,
    val to: String? = null,
    val island: String? = null,
    val lengthM: Int? = null,
    val sacScale: String? = null,
    val aliases: List<String> = emptyList(),
    val osm: List<String> = emptyList(),
    val lines: List<String> = emptyList(),
)

@Serializable
internal data class CatalogPlaceJson(
    val name: String,
    val kind: String = "peak",
    val lat: Double,
    val lon: Double,
    val ele: Int? = null,
)
