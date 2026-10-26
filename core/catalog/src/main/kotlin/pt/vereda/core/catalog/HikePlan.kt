package pt.vereda.core.catalog

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint

/** Where *Directions* opens (spec: Phase 0.8). */
enum class DirectionsTarget(val key: String) {
    IN_APP("app"),
    GOOGLE_MAPS("google");

    companion object {
        val DEFAULT = IN_APP
        fun fromKey(key: String?): DirectionsTarget = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * The walk *Directions* shows in the app: from [start], through hidden [via] points that keep the
 * route on the trail's own line, to [end].
 */
data class HikePlan(val start: GeoPoint, val via: List<GeoPoint>, val end: GeoPoint)

object HikePlans {
    /** Closer than this to the start, the walk starts from the phone. */
    const val FROM_HERE_M = 1000.0
    private const val SAME_POINT_M = 30.0
    private val FRACTIONS = listOf(0.25, 0.5, 0.75)

    /** The approach from the chosen start plus the whole trail; null when the trail has no line. */
    fun forTrail(entry: TrailEntry, chosen: RoadAccess?, here: GeoPoint?): HikePlan? {
        val oriented = entry.walkLine ?: return null
        val trailStart = entry.startPoint ?: return null
        val walkStart = chosen?.takeIf { it.needsWalk }?.roadPoint ?: trailStart
        val start = fromHereOr(walkStart, here)
        val via = buildList {
            if (Geo.distanceM(start, trailStart) > SAME_POINT_M) add(trailStart)
            FRACTIONS.forEach { add(pointAlong(oriented, it)) }
        }
        return HikePlan(start, via, entry.endPoint ?: trailStart)
    }

    /** From the chosen start to a peak (or waterfall, viewpoint…); null when it is on the road. */
    fun forPlace(place: Place, chosen: RoadAccess?, here: GeoPoint?): HikePlan? {
        val road = chosen?.takeIf { it.needsWalk }?.roadPoint
        val start = when {
            here != null && Geo.distanceM(here, place.point) <= FROM_HERE_M -> here
            road != null -> fromHereOr(road, here)
            else -> return null
        }
        if (Geo.distanceM(start, place.point) <= SAME_POINT_M) return null
        return HikePlan(start, emptyList(), place.point)
    }

    private fun fromHereOr(start: GeoPoint, here: GeoPoint?): GeoPoint =
        if (here != null && Geo.distanceM(here, start) <= FROM_HERE_M) here else start

    /** The point [fraction] of the way along [line]. */
    fun pointAlong(line: List<GeoPoint>, fraction: Double): GeoPoint {
        val target = Geo.lengthM(line) * fraction
        var walked = 0.0
        for (i in 1 until line.size) {
            val step = Geo.distanceM(line[i - 1], line[i])
            if (walked + step >= target && step > 0) {
                val t = (target - walked) / step
                val a = line[i - 1]
                val b = line[i]
                return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            }
            walked += step
        }
        return line.last()
    }

    /** Google Maps directions to a point (works in the browser when the app isn't installed). */
    fun googleMapsUrl(point: GeoPoint): String =
        String.format(java.util.Locale.ROOT, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", point.lat, point.lon)
}
