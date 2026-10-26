package pt.vereda.core.catalog

import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.model.Trail
import java.time.Instant

/** A lat/lon box. */
data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {

    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    fun union(other: Bounds) = Bounds(
        minOf(south, other.south),
        minOf(west, other.west),
        maxOf(north, other.north),
        maxOf(east, other.east),
    )

    companion object {
        fun of(points: Iterable<GeoPoint>): Bounds? {
            var south = Double.POSITIVE_INFINITY
            var west = Double.POSITIVE_INFINITY
            var north = Double.NEGATIVE_INFINITY
            var east = Double.NEGATIVE_INFINITY
            var any = false
            for (p in points) {
                any = true
                south = minOf(south, p.lat)
                north = maxOf(north, p.lat)
                west = minOf(west, p.lon)
                east = maxOf(east, p.lon)
            }
            return if (any) Bounds(south, west, north, east) else null
        }
    }
}

/** A trail with its line(s), as the app shows it. Official trails may have no line yet. */
data class TrailEntry(
    val trail: Trail,
    val lengthM: Int?,
    val sacScale: String?,
    val lines: List<List<GeoPoint>>,
    val osmIds: List<String> = emptyList(),
    /** Context for the line, e.g. when OSM maps only part of a popular walk. */
    val noteEn: String? = null,
    val notePt: String? = null,
) {
    val id: String get() = trail.id

    /** The note in Portuguese for "pt", English otherwise. */
    fun note(language: String): String? = if (language == "pt") notePt ?: noteEn else noteEn

    val hasLine: Boolean = lines.any { it.size >= 2 }

    val bounds: Bounds? = Bounds.of(lines.asSequence().flatten().asIterable())
}

enum class PlaceKind { PEAK }

data class Place(
    val name: String,
    val kind: PlaceKind,
    val point: GeoPoint,
    val eleM: Int?,
    val aliases: List<String> = emptyList(),
) {
    /** Stable id for navigation and map features. */
    val id: String get() = "place-${point.lat}-${point.lon}"

    /**
     * The name to show. OpenStreetMap often names Madeira's peaks without "Pico" ("Grande");
     * when another recorded name is the full one ("Pico Grande"), that one reads better.
     */
    val displayName: String
        get() = aliases.firstOrNull { alias ->
            alias.length > name.length && alias.endsWith(name, ignoreCase = true)
        } ?: name

    /** Everything search should match: the name, other names, and "Pico <name>" for peaks. */
    val searchNames: List<String>
        get() = buildList {
            add(name)
            addAll(aliases)
            if (kind == PlaceKind.PEAK && !name.trim().startsWith("pico", ignoreCase = true)) add("Pico $name")
        }
}

data class Catalog(
    val generatedAt: Instant?,
    val attribution: String,
    val trails: List<TrailEntry>,
    val places: List<Place>,
) {
    private val byId: Map<String, TrailEntry> = trails.associateBy { it.id }

    fun trail(id: String): TrailEntry? = byId[id]

    companion object {
        val EMPTY = Catalog(null, "", emptyList(), emptyList())
    }
}
