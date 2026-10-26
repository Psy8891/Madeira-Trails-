package pt.vereda.core.route

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** What a way is, as written by data/scripts/build_network.py (spec: Phase 0.7). */
enum class WayKind { PATH, FOOTWAY, STEPS, TRACK, PEDESTRIAN, MINOR_ROAD, MAJOR_ROAD }

object WayFlags {
    const val NAMED = 1
    const val ROUTE = 2
    const val DIFFICULT = 4
    const val TUNNEL = 8
    const val LEVADA = 16
    const val CAR = 32
}

/**
 * The walking network of Madeira and Porto Santo, cut at junctions, in flat arrays so the whole
 * island fits in a few MB and A* runs without allocating per edge.
 */
class WalkingNetwork internal constructor(
    val names: List<String>,
    private val nodeLat: DoubleArray,
    private val nodeLon: DoubleArray,
    private val edgeFrom: IntArray,
    private val edgeTo: IntArray,
    private val edgeLength: FloatArray,
    private val edgeKind: ByteArray,
    private val edgeFlags: ByteArray,
    private val edgeName: IntArray,
    private val edgeOfficial: IntArray,
    private val innerStart: IntArray,
    private val innerLat: DoubleArray,
    private val innerLon: DoubleArray,
) {
    val nodeCount: Int get() = nodeLat.size
    val edgeCount: Int get() = edgeFrom.size

    // Adjacency in CSR form: the edges at node n are adjEdge[adjStart[n] until adjStart[n + 1]].
    private val adjStart = IntArray(nodeCount + 1)
    private val adjEdge = IntArray(edgeCount * 2)

    init {
        for (e in 0 until edgeCount) {
            adjStart[edgeFrom[e] + 1]++
            adjStart[edgeTo[e] + 1]++
        }
        for (n in 0 until nodeCount) adjStart[n + 1] += adjStart[n]
        val fill = adjStart.copyOf()
        for (e in 0 until edgeCount) {
            adjEdge[fill[edgeFrom[e]]++] = e
            adjEdge[fill[edgeTo[e]]++] = e
        }
    }

    fun nodeLat(n: Int) = nodeLat[n]
    fun nodeLon(n: Int) = nodeLon[n]
    fun node(n: Int) = GeoPoint(nodeLat[n], nodeLon[n])

    fun from(e: Int) = edgeFrom[e]
    fun to(e: Int) = edgeTo[e]
    fun lengthM(e: Int): Double = edgeLength[e].toDouble()
    fun kind(e: Int): WayKind = WayKind.entries.getOrElse(edgeKind[e].toInt()) { WayKind.PATH }
    fun flags(e: Int): Int = edgeFlags[e].toInt() and 0xFF
    fun has(e: Int, flag: Int): Boolean = flags(e) and flag != 0
    fun name(e: Int): String? = names.getOrNull(edgeName[e])
    fun officialCode(e: Int): String? = names.getOrNull(edgeOfficial[e])

    /** Edges touching node [n]. */
    inline fun forEachEdge(n: Int, action: (Int) -> Unit) {
        for (i in adjacencyStart(n) until adjacencyStart(n + 1)) action(adjacencyEdge(i))
    }

    @PublishedApi internal fun adjacencyStart(n: Int) = adjStart[n]
    @PublishedApi internal fun adjacencyEdge(i: Int) = adjEdge[i]

    fun other(e: Int, n: Int): Int = if (edgeFrom[e] == n) edgeTo[e] else edgeFrom[e]

    /** The edge's shape from `from` to `to`, end nodes included. */
    fun geometry(e: Int): List<GeoPoint> = buildList {
        add(node(edgeFrom[e]))
        for (i in innerStart[e] until innerStart[e + 1]) add(GeoPoint(innerLat[i], innerLon[i]))
        add(node(edgeTo[e]))
    }

    // --- Snapping -------------------------------------------------------------------------------

    private val grid: Map<Long, IntArray> by lazy { buildGrid() }

    private fun cellOf(lat: Double, lon: Double): Long {
        val y = floor(lat / CELL_DEG).toLong()
        val x = floor(lon / CELL_DEG).toLong()
        return (y shl 32) xor (x and 0xFFFFFFFFL)
    }

    private fun buildGrid(): Map<Long, IntArray> {
        val cells = HashMap<Long, MutableList<Int>>()
        for (e in 0 until edgeCount) {
            val keys = HashSet<Long>()
            val shape = geometry(e)
            for ((a, b) in shape.zipWithNext()) {
                // Segments are short (a junction every few hundred metres), so their ends and middle
                // cover every cell they touch at 500 m cells.
                keys += cellOf(a.lat, a.lon)
                keys += cellOf(b.lat, b.lon)
                keys += cellOf((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
                val steps = (Geo.distanceM(a, b) / (CELL_M / 2)).toInt()
                for (i in 1 until steps) {
                    val t = i.toDouble() / steps
                    keys += cellOf(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                }
            }
            for (k in keys) cells.getOrPut(k) { ArrayList() }.add(e)
        }
        return cells.mapValues { it.value.toIntArray() }
    }

    /** The nearest point on the network within [maxM], or null. */
    fun snap(p: GeoPoint, maxM: Double = SNAP_M): Snap? {
        val rings = (maxM / (CELL_M * 0.85)).toInt() + 1
        val y0 = floor(p.lat / CELL_DEG).toLong()
        val x0 = floor(p.lon / CELL_DEG).toLong()
        val kx = Geo.METRES_PER_DEGREE * cos(Math.toRadians(p.lat))
        val ky = Geo.METRES_PER_DEGREE
        var best: Snap? = null
        val seen = HashSet<Int>()
        for (dy in -rings..rings) for (dx in -rings..rings) {
            val key = ((y0 + dy) shl 32) xor ((x0 + dx) and 0xFFFFFFFFL)
            val edges = grid[key] ?: continue
            for (e in edges) {
                if (!seen.add(e)) continue
                val shape = geometry(e)
                var walked = 0.0
                for (i in 0 until shape.size - 1) {
                    val a = shape[i]
                    val b = shape[i + 1]
                    val ax = (a.lon - p.lon) * kx
                    val ay = (a.lat - p.lat) * ky
                    val bx = (b.lon - p.lon) * kx
                    val by = (b.lat - p.lat) * ky
                    val dx2 = bx - ax
                    val dy2 = by - ay
                    val seg = dx2 * dx2 + dy2 * dy2
                    val t = if (seg == 0.0) 0.0 else ((-ax * dx2 - ay * dy2) / seg).coerceIn(0.0, 1.0)
                    val cx = ax + t * dx2
                    val cy = ay + t * dy2
                    val d = sqrt(cx * cx + cy * cy)
                    val segLength = sqrt(seg)
                    if (d <= maxM && (best == null || d < best.distanceM)) {
                        best = Snap(
                            edge = e,
                            point = GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t),
                            distanceM = d,
                            fractionAlong = 0.0, // set below once the shape length is known
                            alongShapeM = walked + t * segLength,
                        )
                    }
                    walked += segLength
                }
                val b = best
                if (b != null && b.edge == e && walked > 0) best = b.copy(fractionAlong = (b.alongShapeM / walked).coerceIn(0.0, 1.0))
            }
        }
        return best
    }

    companion object {
        const val SNAP_M = 500.0
        private const val CELL_DEG = 0.005
        private const val CELL_M = 470.0 // the smaller side of a 0.005° cell at Madeira's latitude
    }
}

/** A point on the network: on [edge], [fractionAlong] of the way from its `from` node. */
data class Snap(
    val edge: Int,
    val point: GeoPoint,
    val distanceM: Double,
    val fractionAlong: Double,
    internal val alongShapeM: Double,
)
