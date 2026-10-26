package pt.vereda.core.geo

import kotlin.math.cos
import kotlin.math.sqrt

/** Where a position sits relative to a route: how far off it, and how far along it. */
data class RouteSnap(val distanceM: Double, val progressM: Double)

/**
 * A route prepared for nearest-point queries. Distances use a local flat projection around the
 * query point, which is accurate to centimetres at trail scale.
 *
 * Linear scan over segments; add the 100 m grid index from the spec if routes get long enough to
 * need it.
 */
class Route(points: List<GeoPoint>) {

    private val points: List<GeoPoint> = points.toList()
    private val cumulativeM: DoubleArray

    init {
        require(this.points.size >= 2) { "A route needs at least two points" }
        cumulativeM = DoubleArray(this.points.size)
        for (i in 1 until this.points.size) {
            cumulativeM[i] = cumulativeM[i - 1] + Geo.distanceM(this.points[i - 1], this.points[i])
        }
    }

    val lengthM: Double get() = cumulativeM.last()

    fun snap(position: GeoPoint): RouteSnap {
        val kx = Geo.METRES_PER_DEGREE * cos(Math.toRadians(position.lat))
        val ky = Geo.METRES_PER_DEGREE
        var bestDistance = Double.MAX_VALUE
        var bestProgress = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.lon - position.lon) * kx
            val ay = (a.lat - position.lat) * ky
            val dx = (b.lon - a.lon) * kx
            val dy = (b.lat - a.lat) * ky
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val cx = ax + t * dx
            val cy = ay + t * dy
            val distance = sqrt(cx * cx + cy * cy)
            if (distance < bestDistance) {
                bestDistance = distance
                bestProgress = cumulativeM[i] + t * (cumulativeM[i + 1] - cumulativeM[i])
            }
        }
        return RouteSnap(bestDistance, bestProgress)
    }
}

enum class RouteState { ON_ROUTE, OFF_ROUTE }

data class RouteUpdate(
    val state: RouteState,
    /** True on the fix where the state flipped: alert (or clear the alert) once, here. */
    val changed: Boolean,
    /** Null when the fix was too old to use. */
    val snap: RouteSnap?,
)

/**
 * Off-route alerts with hysteresis (spec: Tracking and navigation engine):
 * off when more than [offThresholdM] away for [offDurationMs]; back on below [backThresholdM];
 * fixes older than [maxFixAgeMs] (tunnels, deep valleys) never change the state.
 */
class OffRouteDetector(
    private val route: Route,
    private val offThresholdM: Double = 40.0,
    private val backThresholdM: Double = 25.0,
    private val offDurationMs: Long = 30_000,
    private val maxFixAgeMs: Long = 15_000,
) {
    var state: RouteState = RouteState.ON_ROUTE
        private set

    private var offSinceMs: Long? = null

    fun onFix(position: GeoPoint, fixTimeMs: Long, nowMs: Long): RouteUpdate {
        if (nowMs - fixTimeMs > maxFixAgeMs) return RouteUpdate(state, changed = false, snap = null)
        val snap = route.snap(position)
        val before = state
        when (state) {
            RouteState.ON_ROUTE -> {
                if (snap.distanceM > offThresholdM) {
                    val since = offSinceMs ?: fixTimeMs.also { offSinceMs = it }
                    if (fixTimeMs - since >= offDurationMs) state = RouteState.OFF_ROUTE
                } else {
                    offSinceMs = null
                }
            }
            RouteState.OFF_ROUTE -> {
                if (snap.distanceM < backThresholdM) {
                    state = RouteState.ON_ROUTE
                    offSinceMs = null
                }
            }
        }
        return RouteUpdate(state, changed = state != before, snap = snap)
    }
}
