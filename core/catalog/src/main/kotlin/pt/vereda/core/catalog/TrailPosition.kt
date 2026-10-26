package pt.vereda.core.catalog

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.geo.Route

/** Where the phone is relative to a picked trail (spec: Phase 0.6). */
sealed interface TrailPosition {
    /** Within [TrailPositions.ON_TRAIL_M] of the line. Distances follow the line from the start. */
    data class OnTrail(val walkedM: Double, val toEndM: Double, val offM: Double) : TrailPosition

    /** Further away: how far to the nearest point of the line, and straight-line to the start. */
    data class OffTrail(val toTrailM: Double, val toStartM: Double?) : TrailPosition
}

object TrailPositions {
    /** Same threshold as the off-route alert (spec: Tracking and navigation engine). */
    const val ON_TRAIL_M = 40.0
    private const val SAME_POINT_M = 100.0

    fun of(entry: TrailEntry, position: GeoPoint): TrailPosition? {
        val lines = entry.lines.filter { it.size >= 2 }
        if (lines.isEmpty()) return null
        var best: Pair<List<GeoPoint>, Route>? = null
        var bestDistance = Double.MAX_VALUE
        var bestProgress = 0.0
        for (line in lines) {
            val route = Route(line)
            val snap = route.snap(position)
            if (snap.distanceM < bestDistance) {
                bestDistance = snap.distanceM
                bestProgress = snap.progressM
                best = line to route
            }
        }
        val (line, route) = best ?: return null
        val start = entry.startPoint
        if (bestDistance > ON_TRAIL_M) {
            return TrailPosition.OffTrail(bestDistance, start?.let { Geo.distanceM(position, it) })
        }
        // The catalog's start can be either end of the stored line. On a trail made of several
        // lines, the start may belong to another one; then this line keeps its own direction.
        val reversed = start != null && Geo.distanceM(start, line.last()).let { toLast ->
            toLast < Geo.distanceM(start, line.first()) && toLast < SAME_POINT_M
        }
        val walked = if (reversed) route.lengthM - bestProgress else bestProgress
        return TrailPosition.OnTrail(walkedM = walked, toEndM = route.lengthM - walked, offM = bestDistance)
    }

    /** Trails passing within [radiusM] of a point (a peak, a waterfall), nearest first. */
    fun near(entries: List<TrailEntry>, point: GeoPoint, radiusM: Double = 150.0): List<TrailEntry> {
        val margin = radiusM / Geo.METRES_PER_DEGREE * 2
        return entries.asSequence()
            .filter { entry ->
                val b = entry.bounds ?: return@filter false
                point.lat in (b.south - margin)..(b.north + margin) && point.lon in (b.west - margin)..(b.east + margin)
            }
            .mapNotNull { entry ->
                val distance = entry.lines.filter { it.size >= 2 }.minOfOrNull { Route(it).snap(point).distanceM }
                if (distance != null && distance <= radiusM) entry to distance else null
            }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
    }
}
