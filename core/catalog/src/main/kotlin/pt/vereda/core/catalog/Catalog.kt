package pt.vereda.core.catalog

import pt.vereda.core.geo.Geo
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

/**
 * The walk from a trail end (or a peak, a waterfall, …) to the nearest point a car can reach,
 * worked out by the catalog job over the OpenStreetMap path network (spec: Phase 0.6).
 */
data class RoadAccess(
    /** Metres to walk: 0 when already on a road, null when no road was found within 5 km. */
    val approachM: Int?,
    val roadPoint: GeoPoint? = null,
    /** The road's ref ("ER 228") or name, when OSM has one. */
    val road: String? = null,
    /** Named paths the walk uses, from the road towards the trail. */
    val via: List<String> = emptyList(),
    /** The walk, from the road to the trail end. */
    val line: List<GeoPoint> = emptyList(),
    val parking: GeoPoint? = null,
    /** A hand-checked trailhead's name ("Boca da Corrida"), from data/known-starts.json. */
    val name: String? = null,
    val known: Boolean = false,
) {
    val onRoad: Boolean get() = approachM == 0
    val needsWalk: Boolean get() = approachM != null && approachM > 0
}

/** One end of a trail's main line with its road access. The catalog lists the start first. */
data class TrailEnd(val point: GeoPoint, val access: RoadAccess)

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
    /** The nearest village or locality to the middle of the line. */
    val area: String? = null,
    /** Start first; one entry for a loop; empty when the catalog has no access data. */
    val ends: List<TrailEnd> = emptyList(),
    /** Ways in to the start, recommended first (Phase 0.7); empty for older catalogs. */
    val startOptions: List<RoadAccess> = emptyList(),
) {
    val id: String get() = trail.id

    /** The note in Portuguese for "pt", English otherwise. */
    fun note(language: String): String? = if (language == "pt") notePt ?: noteEn else noteEn

    val hasLine: Boolean = lines.any { it.size >= 2 }

    val bounds: Bounds? = Bounds.of(lines.asSequence().flatten().asIterable())

    private val mainLine: List<GeoPoint>? = lines.filter { it.size >= 2 }.maxByOrNull { Geo.lengthM(it) }

    /** Where the walk starts: the catalog's start, or the first point of the main line. */
    val startPoint: GeoPoint? get() = ends.firstOrNull()?.point ?: mainLine?.first()

    /** Where it ends; null for a loop. */
    val endPoint: GeoPoint?
        get() {
            if (ends.size >= 2) return ends[1].point
            if (ends.size == 1) return null
            val line = mainLine ?: return null
            return line.last().takeIf { Geo.distanceM(it, line.first()) > LOOP_M }
        }

    val startAccess: RoadAccess? get() = startOptions.firstOrNull() ?: ends.firstOrNull()?.access

    /** Every way in to show: the catalog's options, or the single access of an older catalog. */
    val starts: List<RoadAccess> get() = startOptions.ifEmpty { listOfNotNull(ends.firstOrNull()?.access) }
    val endAccess: RoadAccess? get() = ends.getOrNull(1)?.access

    companion object {
        const val LOOP_M = 60.0
    }
}

/** Kinds of places in the catalog, with how the app treats them. */
enum class PlaceKind(
    val key: String,
    /** Named ones are offered in search. */
    val searchable: Boolean,
    /** Drawn on the map from this zoom. */
    val minZoom: Double,
) {
    PEAK("peak", true, 10.0),
    VIEWPOINT("viewpoint", true, 12.0),
    WATERFALL("waterfall", true, 12.0),
    SPRING("spring", true, 13.0),
    WATER("water", false, 13.5),
    SHELTER("shelter", true, 13.0),
    HUT("hut", true, 12.0),
    PICNIC("picnic", true, 13.0),
    CAMP("camp", true, 12.0),
    CAVE("cave", true, 13.0),
    TRAILHEAD("trailhead", true, 12.0),
    TOILETS("toilets", false, 14.0),
    PARKING("parking", false, 13.5),
    BUS_STOP("bus_stop", false, 14.0),
    TUNNEL("tunnel", false, 13.0),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun of(key: String): PlaceKind? = byKey[key]
    }
}

data class Place(
    /** Null for unnamed points (most car parks, water taps). */
    val name: String?,
    val kind: PlaceKind,
    val point: GeoPoint,
    val eleM: Int?,
    val aliases: List<String> = emptyList(),
    /** How to reach it from the road (peaks, waterfalls, viewpoints, caves, springs). */
    val access: RoadAccess? = null,
    /** Several ways in, recommended first, for peaks and other places off the road. */
    val startOptions: List<RoadAccess> = emptyList(),
) {
    /** Every way in to show, recommended first. */
    val starts: List<RoadAccess> get() = startOptions.ifEmpty { listOfNotNull(access) }

    /** Stable id for navigation and map features. */
    val id: String get() = "place-${kind.key}-${point.lat}-${point.lon}"

    /**
     * The name to show. Catalogs since Phase 0.6 already give peaks their full name; for older
     * ones, a recorded name that is the full one ("Pico Grande" for "Grande") reads better.
     */
    val displayName: String?
        get() {
            val own = name ?: return null
            return aliases.firstOrNull { alias -> alias.length > own.length && alias.endsWith(own, ignoreCase = true) } ?: own
        }

    /** Everything search should match: the name, other names, and "Pico <name>" for peaks. */
    val searchNames: List<String>
        get() = buildList {
            val own = name ?: return@buildList
            add(own)
            addAll(aliases)
            if (kind == PlaceKind.PEAK && !own.trim().startsWith("pico", ignoreCase = true)) add("Pico $own")
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
