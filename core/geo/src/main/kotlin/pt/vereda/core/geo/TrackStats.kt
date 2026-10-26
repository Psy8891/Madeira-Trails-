package pt.vereda.core.geo

/** A recorded fix. Each pause starts a new [segment]; distance never bridges segments. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double?,
    val timeMs: Long,
    val segment: Int = 0,
)

data class TrackStats(
    val distanceM: Double,
    val ascentM: Double,
    val descentM: Double,
    val minEleM: Double?,
    val maxEleM: Double?,
    /** Time spent inside segments, i.e. excluding pauses. */
    val movingTimeMs: Long,
    /** First fix to last fix. */
    val totalTimeMs: Long,
)

object TrackStatsCalculator {

    /** Changes smaller than this are treated as GPS/terrain noise (spec: 3 m hysteresis). */
    const val DEFAULT_CLIMB_THRESHOLD_M = 3.0

    fun compute(points: List<TrackPoint>, climbThresholdM: Double = DEFAULT_CLIMB_THRESHOLD_M): TrackStats {
        if (points.isEmpty()) return TrackStats(0.0, 0.0, 0.0, null, null, 0, 0)

        var distance = 0.0
        var moving = 0L
        for ((a, b) in points.zipWithNext()) {
            if (a.segment == b.segment) {
                distance += Geo.distanceM(a.lat, a.lon, b.lat, b.lon)
                moving += (b.timeMs - a.timeMs).coerceAtLeast(0)
            }
        }

        var ascent = 0.0
        var descent = 0.0
        var reference: Double? = null
        for (ele in points.mapNotNull { it.ele }) {
            val ref = reference
            if (ref == null) {
                reference = ele
                continue
            }
            val diff = ele - ref
            if (diff >= climbThresholdM) {
                ascent += diff
                reference = ele
            } else if (diff <= -climbThresholdM) {
                descent -= diff
                reference = ele
            }
        }

        val elevations = points.mapNotNull { it.ele }
        return TrackStats(
            distanceM = distance,
            ascentM = ascent,
            descentM = descent,
            minEleM = elevations.minOrNull(),
            maxEleM = elevations.maxOrNull(),
            movingTimeMs = moving,
            totalTimeMs = (points.last().timeMs - points.first().timeMs).coerceAtLeast(0),
        )
    }
}
