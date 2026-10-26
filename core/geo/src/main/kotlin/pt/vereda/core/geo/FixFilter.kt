package pt.vereda.core.geo

/** A raw location fix from the phone. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Double,
    val timeMs: Long,
    val ele: Double? = null,
)

/**
 * Drops fixes a hiker could not have produced (spec: Tracking engine, fix filtering):
 * poor accuracy, out-of-order timestamps, or jumps faster than [maxSpeedKmh].
 */
class FixFilter(
    private val maxAccuracyM: Double = 25.0,
    private val maxSpeedKmh: Double = 15.0,
) {
    fun accept(previous: Fix?, next: Fix): Boolean {
        if (next.accuracyM > maxAccuracyM) return false
        if (previous == null) return true
        val dtMs = next.timeMs - previous.timeMs
        if (dtMs <= 0) return false
        val metres = Geo.distanceM(previous.lat, previous.lon, next.lat, next.lon)
        val kmh = metres / (dtMs / 1000.0) * 3.6
        return kmh <= maxSpeedKmh
    }
}
