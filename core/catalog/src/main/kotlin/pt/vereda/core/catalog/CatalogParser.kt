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
                area = entry?.area,
                ends = entry?.ends?.let(::ends).orEmpty(),
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
                        area = t.area,
                    ),
                    lengthM = t.lengthM,
                    sacScale = t.sacScale,
                    lines = decodeLines(t.lines),
                    osmIds = t.osm,
                    noteEn = t.noteEn,
                    notePt = t.notePt,
                    area = t.area,
                    ends = ends(t.ends),
                )
            }
            .filter { it.hasLine }

        val places = doc.places.mapNotNull { p ->
            val kind = PlaceKind.of(p.kind) ?: return@mapNotNull null // a kind from a newer catalog
            val name = p.name?.trim()?.takeIf { it.isNotEmpty() }
            if (kind == PlaceKind.PEAK && name == null) return@mapNotNull null
            Place(name, kind, GeoPoint(p.lat, p.lon), p.ele, p.aliases, p.access?.let(::access))
        }

        return Catalog(
            generatedAt = doc.generatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            attribution = doc.attribution,
            trails = official + community,
            places = places,
        )
    }

    private fun ends(ends: List<CatalogEndJson>): List<TrailEnd> =
        ends.map { e -> TrailEnd(GeoPoint(e.lat, e.lon), access(e)) }

    private fun access(a: AccessJson): RoadAccess = RoadAccess(
        approachM = a.approachM,
        roadPoint = a.access?.toPoint(),
        road = a.road,
        via = a.via,
        line = a.line?.let { runCatching { Polyline.decode(it) }.getOrNull() }.orEmpty(),
        parking = a.parking?.toPoint(),
    )

    private fun List<Double>.toPoint(): GeoPoint? = if (size >= 2) GeoPoint(this[0], this[1]) else null

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
    val noteEn: String? = null,
    val notePt: String? = null,
    val area: String? = null,
    val ends: List<CatalogEndJson> = emptyList(),
)

/** Road access as the catalog writes it, for a trail end or a place. */
internal interface AccessJson {
    val approachM: Int?
    val access: List<Double>?
    val road: String?
    val via: List<String>
    val line: String?
    val parking: List<Double>?
}

@Serializable
internal data class CatalogEndJson(
    val lat: Double,
    val lon: Double,
    override val approachM: Int? = null,
    override val access: List<Double>? = null,
    override val road: String? = null,
    override val via: List<String> = emptyList(),
    override val line: String? = null,
    override val parking: List<Double>? = null,
) : AccessJson

@Serializable
internal data class CatalogPlaceAccessJson(
    override val approachM: Int? = null,
    override val access: List<Double>? = null,
    override val road: String? = null,
    override val via: List<String> = emptyList(),
    override val line: String? = null,
    override val parking: List<Double>? = null,
) : AccessJson

@Serializable
internal data class CatalogPlaceJson(
    val name: String? = null,
    val kind: String = "peak",
    val lat: Double,
    val lon: Double,
    val ele: Int? = null,
    val aliases: List<String> = emptyList(),
    val access: CatalogPlaceAccessJson? = null,
)
