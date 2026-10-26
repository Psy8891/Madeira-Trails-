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
) {
    val id: String get() = trail.id

    val hasLine: Boolean = lines.any { it.size >= 2 }

    val bounds: Bounds? = Bounds.of(lines.asSequence().flatten().asIterable())
}

enum class PlaceKind { PEAK }

data class Place(val name: String, val kind: PlaceKind, val point: GeoPoint, val eleM: Int?) {
    /** Stable id for navigation and map features. */
    val id: String get() = "place-${point.lat}-${point.lon}"
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
