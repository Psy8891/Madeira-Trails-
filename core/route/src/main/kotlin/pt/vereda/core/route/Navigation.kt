package pt.vereda.core.route

import pt.vereda.core.geo.ElevationGrid
import pt.vereda.core.geo.Fix
import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.geo.HikeTime
import kotlin.math.cos
import kotlin.math.sqrt

/** One way, or out to the end and back the same way (spec: Phase 1.5). */
enum class TripKind { ONE_WAY, THERE_AND_BACK, LOOP }

/** The route being followed: its line, cues and climb still ahead at each point. */
class NavRoute private constructor(
    val name: String,
    val kind: TripKind,
    val line: Polyline,
    val cues: List<Cue>,
    /** Climb from each sample to the end, for "time left"; distances every [SAMPLE_M]. */
    private val climbLeft: DoubleArray?,
) {
    val lengthM: Double get() = line.lengthM
    val points: List<GeoPoint> get() = line.points
    val end: GeoPoint get() = line.points.last()

    fun climbLeftM(progressM: Double): Double {
        val c = climbLeft ?: return 0.0
        val i = (progressM / SAMPLE_M).toInt().coerceIn(0, c.size - 1)
        return c[i]
    }

    companion object {
        const val SAMPLE_M = 20.0
        const val LOOP_M = 60.0

        /** A route from the router, as a trip of [kind]; a loop stays a loop. */
        fun of(name: String, route: PlannedRoute, network: WalkingNetwork, kind: TripKind, elevation: ElevationGrid?): NavRoute {
            val out = route.points
            val loop = out.size >= 2 && Geo.distanceM(out.first(), out.last()) <= LOOP_M
            val actual = if (loop) TripKind.LOOP else kind
            return if (actual == TripKind.THERE_AND_BACK) {
                val outLength = Polyline(out).lengthM
                val shape = out + out.reversed().drop(1)
                val back = route.junctions.reversed().map {
                    RouteJunction(it.node, inEdge = it.outEdge, outEdge = it.inEdge, alongM = 2 * outLength - it.alongM)
                }
                build(name, actual, shape, Cues.of(network, shape, route.junctions + back, listOf(outLength)), elevation)
            } else {
                build(name, actual, out, Cues.of(network, out, route.junctions), elevation)
            }
        }

        /** For tests and routes without a network: a line and its cues. */
        fun of(name: String, kind: TripKind, shape: List<GeoPoint>, cues: List<Cue>, elevation: ElevationGrid? = null): NavRoute =
            build(name, kind, shape, cues, elevation)

        private fun build(name: String, kind: TripKind, shape: List<GeoPoint>, cues: List<Cue>, elevation: ElevationGrid?): NavRoute {
            require(shape.size >= 2) { "A route needs at least two points" }
            val line = Polyline(shape)
            val climb = elevation?.let { grid ->
                val n = (line.lengthM / SAMPLE_M).toInt() + 1
                val heights = DoubleArray(n) { i -> grid.heightAt(line.pointAt(i * SAMPLE_M)) ?: Double.NaN }
                if (heights.all { it.isNaN() }) return@let null
                // Climb from each sample to the end, counting rises of 3 m or more.
                val left = DoubleArray(n)
                var reference = heights.last().takeIf { !it.isNaN() } ?: 0.0
                var total = 0.0
                for (i in n - 1 downTo 0) {
                    val h = heights[i]
                    if (!h.isNaN()) {
                        // Walking forwards from i towards i+1…: a rise is reference − h.
                        val d = reference - h
                        if (d >= 3.0) { total += d; reference = h } else if (d <= -3.0) reference = h
                    }
                    left[i] = total
                }
                left
            }
            return NavRoute(name, kind, line, cues, climb)
        }
    }
}

/** Something to tell the walker once (spec: Phase 1.5, alerts). */
sealed interface NavEvent {
    data class CueAhead(val cue: Cue, val inM: Double) : NavEvent
    data class CueNow(val cue: Cue) : NavEvent
    data class OffRoute(val distanceM: Double) : NavEvent
    data object BackOnRoute : NavEvent
    data object WrongWay : NavEvent
    data object Arrived : NavEvent
}

data class NavState(
    val progressM: Double = 0.0,
    val remainingM: Double = 0.0,
    val offRouteM: Double = 0.0,
    val offRoute: Boolean = false,
    val wrongWay: Boolean = false,
    val nextCue: Cue? = null,
    val toNextCueM: Double? = null,
    /** Time left: 4 km/h plus 1 h per 600 m of the climb still ahead. */
    val minutesLeft: Int = 0,
    val arrived: Boolean = false,
    /** Since when the walker has barely moved (ms timestamp), or null when moving. */
    val stillSinceMs: Long? = null,
    val lastFix: Fix? = null,
)

/**
 * Follows a [NavRoute] fix by fix (spec: Phase 1.5): progress-aware snapping, off-route and
 * wrong-way with hysteresis, cue and arrival alerts once each.
 */
class Navigator(
    val route: NavRoute,
    private val offThresholdM: Double = 40.0,
    private val backThresholdM: Double = 25.0,
    private val offDurationMs: Long = 20_000,
    private val maxFixAgeMs: Long = 15_000,
    private val maxAccuracyM: Double = 30.0,
) {
    var state = NavState(remainingM = route.lengthM, nextCue = route.cues.firstOrNull(), toNextCueM = route.cues.firstOrNull()?.alongM, minutesLeft = HikeTime.minutes(route.lengthM, route.climbLeftM(0.0)))
        private set

    private var progress = 0.0
    private var maxProgress = 0.0
    private var started = false
    private var offSinceMs: Long? = null
    private val announcedAhead = HashSet<Int>()
    private val announcedNow = HashSet<Int>()
    private var stillAnchor: Fix? = null

    fun onFix(fix: Fix, nowMs: Long): List<NavEvent> {
        if (state.arrived) return emptyList()
        val events = ArrayList<NavEvent>()
        val usable = nowMs - fix.timeMs <= maxFixAgeMs && fix.accuracyM <= maxAccuracyM
        val here = GeoPoint(fix.lat, fix.lon)
        val stillSince = stillness(fix)
        if (!usable) {
            state = state.copy(stillSinceMs = stillSince, lastFix = fix)
            return events
        }
        val (distance, along) = snap(here)
        val wasOff = state.offRoute
        var off = wasOff
        if (!wasOff) {
            if (distance > offThresholdM) {
                val since = offSinceMs ?: fix.timeMs.also { offSinceMs = it }
                if (fix.timeMs - since >= offDurationMs) {
                    off = true
                    events += NavEvent.OffRoute(distance)
                }
            } else {
                offSinceMs = null
            }
        } else if (distance < backThresholdM) {
            off = false
            offSinceMs = null
            events += NavEvent.BackOnRoute
        }

        var wrongWay = state.wrongWay
        if (!off) {
            progress = along
            if (!started) { started = true; maxProgress = progress }
            if (progress > maxProgress) maxProgress = progress
            if (!wrongWay && maxProgress - progress > WRONG_WAY_M) {
                wrongWay = true
                events += NavEvent.WrongWay
            } else if (wrongWay && maxProgress - progress < 10.0) {
                wrongWay = false
            }
        }

        // The next cue still ahead.
        val index = route.cues.indexOfFirst { it.alongM > progress - CUE_PASSED_M }
        val cue = route.cues.getOrNull(index)
        val toCue = cue?.let { it.alongM - progress }
        if (cue != null && toCue != null && !off && !wrongWay && cue.turn != Turn.ARRIVE) {
            if (toCue <= CUE_NOW_M && announcedNow.add(index)) {
                announcedAhead.add(index)
                events += NavEvent.CueNow(cue)
            } else if (toCue <= CUE_AHEAD_M && toCue > CUE_NOW_M && announcedAhead.add(index)) {
                events += NavEvent.CueAhead(cue, toCue)
            }
        }

        var arrived = false
        if (progress >= route.lengthM * 0.9 && Geo.distanceM(here, route.end) <= ARRIVE_M) {
            arrived = true
            events += NavEvent.Arrived
        }

        val remaining = (route.lengthM - progress).coerceAtLeast(0.0)
        state = NavState(
            progressM = progress,
            remainingM = remaining,
            offRouteM = distance,
            offRoute = off,
            wrongWay = wrongWay,
            nextCue = cue,
            toNextCueM = toCue?.coerceAtLeast(0.0),
            minutesLeft = if (arrived) 0 else HikeTime.minutes(remaining, route.climbLeftM(progress)),
            arrived = arrived,
            stillSinceMs = stillSince,
            lastFix = fix,
        )
        return events
    }

    /** Barely moved (under 10 m) since the anchor fix: the time standing still began. */
    private fun stillness(fix: Fix): Long? {
        val anchor = stillAnchor
        if (anchor == null || Geo.distanceM(anchor.lat, anchor.lon, fix.lat, fix.lon) > STILL_M) {
            stillAnchor = fix
            return null
        }
        return anchor.timeMs
    }

    private var jumpFixes = 0

    /**
     * Nearest point near the current progress first (spec: Phase 1.5), so the way back of a
     * there-and-back or the end of a loop is never taken for the start. Elsewhere on the route
     * only counts after [JUMP_FIXES] fixes in a row within 25 m of it (starting part-way along).
     */
    private fun snap(p: GeoPoint): Pair<Double, Double> {
        val line = route.line
        val from = if (started) (progress - BEHIND_M).coerceAtLeast(0.0) else 0.0
        val near = nearest(p, from, (if (started) progress else 0.0) + AHEAD_M, behindPenalty = BEHIND_PENALTY)
        if (near.first <= offThresholdM) {
            jumpFixes = 0
            return near
        }
        val anywhere = nearest(p, 0.0, line.lengthM)
        if (anywhere.first < backThresholdM) {
            jumpFixes++
            if (jumpFixes >= JUMP_FIXES) {
                jumpFixes = 0
                maxProgress = anywhere.second
                return anywhere
            }
        } else {
            jumpFixes = 0
        }
        // Off the route near where we were: report the distance, keep the progress.
        return minOf(near.first, anywhere.first) to progress
    }

    /**
     * Nearest point between [from] and [to]. With [behindPenalty], each metre behind the current
     * progress counts as that much extra distance, so where the way out and the way back overlap
     * the way on wins.
     */
    private fun nearest(p: GeoPoint, from: Double, to: Double, behindPenalty: Double = 0.0): Pair<Double, Double> {
        val line = route.line
        val pts = line.points
        val kx = Geo.METRES_PER_DEGREE * cos(Math.toRadians(p.lat))
        val ky = Geo.METRES_PER_DEGREE
        var best = Double.MAX_VALUE
        var bestScore = Double.MAX_VALUE
        var bestAlong = from
        val first = line.segmentAt(from)
        val last = line.segmentAt(to)
        for (i in first..last.coerceAtMost(pts.size - 2)) {
            val a = pts[i]
            val b = pts[i + 1]
            val ax = (a.lon - p.lon) * kx
            val ay = (a.lat - p.lat) * ky
            val dx = (b.lon - a.lon) * kx
            val dy = (b.lat - a.lat) * ky
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx
            val cy = ay + t * dy
            val d = sqrt(cx * cx + cy * cy)
            val along = line.cumulative[i] + t * (line.cumulative[i + 1] - line.cumulative[i])
            val score = d + behindPenalty * (progress - along).coerceAtLeast(0.0)
            if (score < bestScore) {
                bestScore = score
                best = d
                bestAlong = along
            }
        }
        return best to bestAlong
    }

    companion object {
        const val BEHIND_M = 60.0
        const val AHEAD_M = 800.0
        const val CUE_AHEAD_M = 80.0
        const val CUE_NOW_M = 20.0
        const val CUE_PASSED_M = 5.0
        const val ARRIVE_M = 30.0
        const val WRONG_WAY_M = 50.0
        const val STILL_M = 10.0
        const val JUMP_FIXES = 3
        const val BEHIND_PENALTY = 0.5
    }
}

/** How often to ask for a GPS fix (spec: Phase 1.5, adaptive GPS). */
object GpsPolicy {
    const val NORMAL_MS = 5_000L
    const val CLOSE_MS = 2_000L
    const val STILL_MS = 15_000L
    const val STILL_AFTER_MS = 120_000L
    const val CLOSE_CUE_M = 200.0
    const val MIN_DISTANCE_M = 3f

    fun intervalMs(state: NavState, nowMs: Long): Long {
        if (state.arrived) return STILL_MS
        if (state.offRoute || state.wrongWay) return CLOSE_MS
        val still = state.stillSinceMs?.let { nowMs - it >= STILL_AFTER_MS } ?: false
        if (still) return STILL_MS
        val toCue = state.toNextCueM
        val cue = state.nextCue
        if (toCue != null && cue != null && cue.turn != Turn.ARRIVE && toCue <= CLOSE_CUE_M) return CLOSE_MS
        return NORMAL_MS
    }
}
