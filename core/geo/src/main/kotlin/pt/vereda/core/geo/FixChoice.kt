package pt.vereda.core.geo

/** Which of two fixes to show: newer wins, except a rough network fix right after a GPS one. */
object FixChoice {
    data class Fix(val timeMs: Long, val accuracyM: Double?, val fromGps: Boolean)

    private const val GPS_PREFERRED_FOR_MS = 15_000L

    fun better(current: Fix?, next: Fix): Boolean {
        if (current == null) return true
        if (next.timeMs < current.timeMs) return false
        val gpsStillFresh = current.fromGps && next.timeMs - current.timeMs < GPS_PREFERRED_FOR_MS
        if (!next.fromGps && gpsStillFresh) {
            val nextAccuracy = next.accuracyM ?: Double.MAX_VALUE
            val currentAccuracy = current.accuracyM ?: Double.MAX_VALUE
            return nextAccuracy < currentAccuracy
        }
        return true
    }
}
