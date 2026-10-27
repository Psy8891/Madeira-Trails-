package pt.vereda.core.route

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import java.util.PriorityQueue

/** How the route is chosen (spec: Phase 0.7, owner's answers). */
data class RouteOptions(
    /** Use difficult paths (OSM sac_scale demanding and above) only when nothing else is reasonable. */
    val avoidDifficult: Boolean = true,
)

/** One stretch of the route on one way, for the summary. */
data class RoutePiece(val edge: Int, val metres: Double)

data class RouteSummary(
    val distanceM: Double,
    val roadM: Double,
    val difficultM: Double,
    val tunnels: Int,
    /** Official trails used, in walking order ("PR 12"). */
    val officialTrails: List<String>,
    /** The names that make up most of the walk, longest first. */
    val mainNames: List<String>,
) {
    /** Time on the flat at 4 km/h; climbing adds to it (spec: Router). */
    val flatMinutes: Int get() = Math.round(distanceM / WALKING_M_PER_MIN).toInt()

    companion object {
        const val WALKING_M_PER_MIN = 4000.0 / 60.0
    }
}

/** A network node the route passes through, between [inEdge] and [outEdge], [alongM] from the start. */
data class RouteJunction(val node: Int, val inEdge: Int, val outEdge: Int, val alongM: Double)

data class PlannedRoute(
    val points: List<GeoPoint>,
    val summary: RouteSummary,
    val pieces: List<RoutePiece>,
    /** Every junction node passed, in order (spec: Phase 1.5, junction cues). */
    val junctions: List<RouteJunction> = emptyList(),
)

sealed interface RouteOutcome {
    data class Found(val route: PlannedRoute) : RouteOutcome

    /** Point [index] (0 = start) is more than 500 m from any path or road. */
    data class TooFar(val index: Int) : RouteOutcome

    /** No way through between point [index] and the next. */
    data class NoRoute(val index: Int) : RouteOutcome
}

/** A* over the walking network with a hiking profile: trails first, roads where needed. */
class Router(private val network: WalkingNetwork) {

    /** Cost of one metre of edge [e]; 1.0 is the cheapest, which keeps the A* heuristic exact. */
    fun costFactor(e: Int, options: RouteOptions): Double {
        val n = network
        val onTrail = n.has(e, WayFlags.ROUTE) || n.has(e, WayFlags.LEVADA)
        val named = n.has(e, WayFlags.NAMED)
        var factor = when (n.kind(e)) {
            WayKind.PATH, WayKind.FOOTWAY, WayKind.TRACK -> when {
                onTrail -> 1.0
                named -> 1.1
                else -> 1.4
            }
            WayKind.STEPS -> if (onTrail) 1.0 else 1.5
            WayKind.PEDESTRIAN -> 1.3
            WayKind.MINOR_ROAD -> if (onTrail) 1.3 else 1.6
            WayKind.MAJOR_ROAD -> if (onTrail) 2.0 else 3.0
        }
        if (n.has(e, WayFlags.DIFFICULT)) factor *= if (options.avoidDifficult) 4.0 else 1.2
        return factor
    }

    /** Route through [points] (start, stops…, end), leg by leg. */
    fun route(points: List<GeoPoint>, options: RouteOptions = RouteOptions()): RouteOutcome {
        require(points.size >= 2) { "A route needs a start and an end" }
        val snaps = points.mapIndexed { i, p -> network.snap(p) ?: return RouteOutcome.TooFar(i) }
        val shape = ArrayList<GeoPoint>()
        val pieces = ArrayList<RoutePiece>()
        val junctions = ArrayList<RouteJunction>()
        var before = 0.0
        for (i in 0 until snaps.size - 1) {
            val leg = leg(snaps[i], snaps[i + 1], options) ?: return RouteOutcome.NoRoute(i)
            if (shape.isNotEmpty() && leg.points.isNotEmpty() && shape.last() == leg.points.first()) {
                shape.addAll(leg.points.drop(1))
            } else {
                shape.addAll(leg.points)
            }
            pieces.addAll(leg.pieces)
            leg.junctions.forEach { junctions += it.copy(alongM = it.alongM + before) }
            before += leg.pieces.sumOf { it.metres }
        }
        return RouteOutcome.Found(PlannedRoute(shape, summarize(pieces), pieces, junctions))
    }

    private class Leg(val points: List<GeoPoint>, val pieces: List<RoutePiece>, val junctions: List<RouteJunction> = emptyList())

    private fun leg(a: Snap, b: Snap, options: RouteOptions): Leg? {
        val n = network
        val goal = b.point
        val goalEdge = b.edge
        val goalFrom = n.from(goalEdge)
        val goalTo = n.to(goalEdge)
        val goalFactor = costFactor(goalEdge, options)
        val goalLength = n.lengthM(goalEdge)

        // Same way: walking straight along it may be best.
        var bestCost = Double.MAX_VALUE
        var bestEndNode = -1
        if (a.edge == b.edge) {
            bestCost = kotlin.math.abs(a.fractionAlong - b.fractionAlong) * goalLength * goalFactor
        }

        val cost = HashMap<Int, Double>()
        val previousEdge = HashMap<Int, Int>()
        val queue = PriorityQueue<DoubleArray>(compareBy<DoubleArray> { it[0] })
        fun push(node: Int, g: Double, viaEdge: Int) {
            if (g >= (cost[node] ?: Double.MAX_VALUE)) return
            cost[node] = g
            previousEdge[node] = viaEdge
            val h = Geo.distanceM(n.nodeLat(node), n.nodeLon(node), goal.lat, goal.lon)
            queue.add(doubleArrayOf(g + h, node.toDouble(), g))
        }
        val startFactor = costFactor(a.edge, options)
        val startLength = n.lengthM(a.edge)
        push(n.from(a.edge), a.fractionAlong * startLength * startFactor, START)
        push(n.to(a.edge), (1 - a.fractionAlong) * startLength * startFactor, START)

        val settled = HashSet<Int>()
        while (queue.isNotEmpty()) {
            val top = queue.poll()
            if (top[0] >= bestCost) break
            val node = top[1].toInt()
            val g = top[2]
            if (g > (cost[node] ?: Double.MAX_VALUE) || !settled.add(node)) continue
            // Finish on the goal's way from either of its ends.
            if (node == goalFrom || node == goalTo) {
                val rest = (if (node == goalFrom) b.fractionAlong else 1 - b.fractionAlong) * goalLength * goalFactor
                if (g + rest < bestCost) {
                    bestCost = g + rest
                    bestEndNode = node
                }
            }
            n.forEachEdge(node) { e ->
                val next = n.other(e, node)
                if (next !in settled) push(next, g + n.lengthM(e) * costFactor(e, options), e)
            }
        }
        if (bestCost == Double.MAX_VALUE) return null
        if (bestEndNode < 0) return straightAlong(a, b)

        // Walk back from the goal end to the start.
        val edges = ArrayList<Int>()
        var node = bestEndNode
        while (true) {
            val e = previousEdge[node] ?: break
            if (e == START) break
            edges.add(e)
            node = n.other(e, node)
        }
        edges.reverse()
        val startNode = node // the end of the start way the walk left from

        val points = ArrayList<GeoPoint>()
        val pieces = ArrayList<RoutePiece>()
        val junctions = ArrayList<RouteJunction>()
        // From the start point to the end of its way.
        val startTowardsFrom = startNode == n.from(a.edge)
        appendPart(points, pieces, a.edge, a, if (startTowardsFrom) 0.0 else 1.0)
        var walked = pieces.sumOf { it.metres }
        var at = startNode
        var inEdge = a.edge
        for (e in edges) {
            junctions += RouteJunction(at, inEdge, e, walked)
            val forward = n.from(e) == at
            val shape = n.geometry(e).let { if (forward) it else it.reversed() }
            appendPoints(points, shape)
            pieces.add(RoutePiece(e, n.lengthM(e)))
            walked += n.lengthM(e)
            at = n.other(e, at)
            inEdge = e
        }
        if (goalEdge != inEdge) junctions += RouteJunction(at, inEdge, goalEdge, walked)
        // From the end of the goal's way to the goal point.
        appendPartFrom(points, pieces, goalEdge, if (at == goalFrom) 0.0 else 1.0, b)
        return Leg(points, pieces.filter { it.metres > 0.01 }, junctions)
    }

    private fun straightAlong(a: Snap, b: Snap): Leg {
        val points = ArrayList<GeoPoint>()
        val pieces = ArrayList<RoutePiece>()
        appendPart(points, pieces, a.edge, a, b.fractionAlong)
        if (points.isEmpty() || points.last() != b.point) points.add(b.point)
        return Leg(points, pieces)
    }

    /** Points along [edge] from the snapped point [a] to [toFraction] of the way along it. */
    private fun appendPart(points: MutableList<GeoPoint>, pieces: MutableList<RoutePiece>, edge: Int, a: Snap, toFraction: Double) {
        appendPoints(points, partOf(edge, a.fractionAlong, toFraction, startPoint = a.point))
        pieces.add(RoutePiece(edge, kotlin.math.abs(a.fractionAlong - toFraction) * network.lengthM(edge)))
    }

    private fun appendPartFrom(points: MutableList<GeoPoint>, pieces: MutableList<RoutePiece>, edge: Int, fromFraction: Double, b: Snap) {
        appendPoints(points, partOf(edge, fromFraction, b.fractionAlong, endPoint = b.point))
        pieces.add(RoutePiece(edge, kotlin.math.abs(b.fractionAlong - fromFraction) * network.lengthM(edge)))
    }

    /** The shape of [edge] between two fractions of its length, in walking order. */
    private fun partOf(edge: Int, from: Double, to: Double, startPoint: GeoPoint? = null, endPoint: GeoPoint? = null): List<GeoPoint> {
        val shape = network.geometry(edge)
        val cumulative = DoubleArray(shape.size)
        for (i in 1 until shape.size) cumulative[i] = cumulative[i - 1] + Geo.distanceM(shape[i - 1], shape[i])
        val total = cumulative.last()
        fun pointAt(f: Double): GeoPoint {
            if (total == 0.0) return shape.first()
            val target = f * total
            for (i in 1 until shape.size) {
                if (cumulative[i] >= target) {
                    val span = cumulative[i] - cumulative[i - 1]
                    val t = if (span == 0.0) 0.0 else (target - cumulative[i - 1]) / span
                    val p = shape[i - 1]
                    val q = shape[i]
                    return GeoPoint(p.lat + (q.lat - p.lat) * t, p.lon + (q.lon - p.lon) * t)
                }
            }
            return shape.last()
        }
        val result = ArrayList<GeoPoint>()
        result.add(startPoint ?: pointAt(from))
        val lo = minOf(from, to) * total
        val hi = maxOf(from, to) * total
        val inner = (1 until shape.size - 1).filter { cumulative[it] > lo && cumulative[it] < hi }.map { shape[it] }
        result.addAll(if (from <= to) inner else inner.reversed())
        result.add(endPoint ?: pointAt(to))
        return result
    }

    private fun appendPoints(points: MutableList<GeoPoint>, more: List<GeoPoint>) {
        for (p in more) if (points.isEmpty() || points.last() != p) points.add(p)
    }

    private fun summarize(pieces: List<RoutePiece>): RouteSummary {
        val n = network
        var distance = 0.0
        var road = 0.0
        var difficult = 0.0
        var tunnels = 0
        var lastTunnel = false
        val official = LinkedHashMap<String, Double>()
        val byName = HashMap<String, Double>()
        for (piece in pieces) {
            val e = piece.edge
            distance += piece.metres
            val kind = n.kind(e)
            if (kind == WayKind.MINOR_ROAD || kind == WayKind.MAJOR_ROAD) road += piece.metres
            if (n.has(e, WayFlags.DIFFICULT)) difficult += piece.metres
            val tunnel = n.has(e, WayFlags.TUNNEL)
            if (tunnel && !lastTunnel) tunnels++
            lastTunnel = tunnel
            n.officialCode(e)?.let { official[it] = (official[it] ?: 0.0) + piece.metres }
            n.name(e)?.let { byName[it] = (byName[it] ?: 0.0) + piece.metres }
        }
        val mainNames = byName.entries.filter { it.value >= MIN_NAMED_M }.sortedByDescending { it.value }.take(3).map { it.key }
        // Ways shared by several routes carry one code, so ignore brushes with another trail.
        val trails = official.filter { it.value >= MIN_NAMED_M }.keys.toList()
        return RouteSummary(distance, road, difficult, tunnels, trails, mainNames)
    }

    private companion object {
        const val START = -1
        const val MIN_NAMED_M = 150.0
    }
}
