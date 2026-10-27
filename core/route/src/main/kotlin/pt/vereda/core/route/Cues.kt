package pt.vereda.core.route

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** How to go on at a cue (spec: Phase 1.5, junction cues). */
enum class Turn { STRAIGHT, KEEP_LEFT, KEEP_RIGHT, LEFT, RIGHT, SHARP_LEFT, SHARP_RIGHT, TURN_AROUND, ARRIVE }

/** One instruction along the route, [alongM] from its start. [name] is the way on, when known. */
data class Cue(val alongM: Double, val point: GeoPoint, val turn: Turn, val name: String?)

/** A line with cumulative distances, for points and bearings at a distance along it. */
class Polyline(val points: List<GeoPoint>) {
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distanceM(points[i - 1], points[i])
    }
    val lengthM: Double get() = if (points.isEmpty()) 0.0 else cumulative.last()

    /** Index of the segment containing [along] (0 until size - 1). */
    fun segmentAt(along: Double): Int {
        if (points.size < 2) return 0
        var lo = 0
        var hi = points.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (cumulative[mid] <= along) lo = mid else hi = mid
        }
        return lo
    }

    fun pointAt(along: Double): GeoPoint {
        if (points.size < 2) return points.first()
        val a = along.coerceIn(0.0, lengthM)
        val i = segmentAt(a)
        val span = cumulative[i + 1] - cumulative[i]
        val t = if (span == 0.0) 0.0 else (a - cumulative[i]) / span
        val p = points[i]
        val q = points[i + 1]
        return GeoPoint(p.lat + (q.lat - p.lat) * t, p.lon + (q.lon - p.lon) * t)
    }
}

object Bearings {
    /** Initial bearing from [a] to [b], degrees clockwise from north, 0–360. */
    fun of(a: GeoPoint, b: GeoPoint): Double {
        val f1 = Math.toRadians(a.lat)
        val f2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Signed difference [to] − [from] in (−180, 180]: positive turns right. */
    fun diff(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180) d -= 360.0
        if (d <= -180) d += 360.0
        return d
    }
}

object Cues {
    /** How far along a way bearings are measured, to smooth out the last wiggle before a node. */
    const val LOOK_M = 20.0
    const val TURN_DEG = 35.0
    const val KEEP_MAX_DEG = 60.0
    const val TURN_MAX_DEG = 135.0
    const val U_TURN_DEG = 165.0
    const val AMBIGUOUS_DEG = 40.0

    /**
     * Cues where the path splits and the way on isn't obvious (spec: Phase 1.5). [turnArounds] are
     * distances where a there-and-back turns; the end gets ARRIVE.
     */
    fun of(network: WalkingNetwork, shape: List<GeoPoint>, junctions: List<RouteJunction>, turnArounds: List<Double> = emptyList()): List<Cue> {
        val line = Polyline(shape)
        if (shape.size < 2) return emptyList()
        val cues = ArrayList<Cue>()
        for (j in junctions) {
            if (j.alongM < 1.0 || j.alongM > line.lengthM - 1.0) continue
            var degree = 0
            network.forEachEdge(j.node) { degree++ }
            if (degree < 3) continue
            val node = network.node(j.node)
            val inBearing = Bearings.of(line.pointAt(j.alongM - LOOK_M), node)
            val outBearing = Bearings.of(node, line.pointAt(j.alongM + LOOK_M))
            val turn = Bearings.diff(inBearing, outBearing)
            val others = ArrayList<Double>()
            network.forEachEdge(j.node) { e ->
                if (e != j.inEdge && e != j.outEdge) others += Bearings.of(node, along(network, e, j.node, LOOK_M))
            }
            val ambiguous = others.filter { abs(Bearings.diff(outBearing, it)) < AMBIGUOUS_DEG }
            val inName = network.name(j.inEdge)
            val outName = network.name(j.outEdge)
            val renamed = outName != null && outName != inName
            if (abs(turn) <= TURN_DEG && ambiguous.isEmpty() && !renamed) continue
            val kind = when {
                abs(turn) <= TURN_DEG && ambiguous.isNotEmpty() ->
                    // Keep to the side away from the nearest other branch.
                    if (Bearings.diff(outBearing, ambiguous.minBy { abs(Bearings.diff(outBearing, it)) }) > 0) Turn.KEEP_LEFT else Turn.KEEP_RIGHT
                abs(turn) <= TURN_DEG -> Turn.STRAIGHT
                abs(turn) <= KEEP_MAX_DEG -> if (turn > 0) Turn.KEEP_RIGHT else Turn.KEEP_LEFT
                abs(turn) <= TURN_MAX_DEG -> if (turn > 0) Turn.RIGHT else Turn.LEFT
                abs(turn) < U_TURN_DEG -> if (turn > 0) Turn.SHARP_RIGHT else Turn.SHARP_LEFT
                else -> Turn.TURN_AROUND
            }
            cues += Cue(j.alongM, node, kind, outName)
        }
        turnArounds.forEach { cues += Cue(it, line.pointAt(it), Turn.TURN_AROUND, null) }
        cues += Cue(line.lengthM, shape.last(), Turn.ARRIVE, null)
        return cues.sortedBy { it.alongM }
    }

    /** The point [metres] along edge [e] going away from [node]. */
    private fun along(network: WalkingNetwork, e: Int, node: Int, metres: Double): GeoPoint {
        val shape = network.geometry(e).let { if (network.from(e) == node) it else it.reversed() }
        return Polyline(shape).pointAt(metres)
    }
}
