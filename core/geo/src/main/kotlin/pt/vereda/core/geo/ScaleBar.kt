package pt.vereda.core.geo

import kotlin.math.cos
import kotlin.math.pow

/** The map's scale bar: a round distance that fits in [maxDp] at the current zoom (spec: Phase 1). */
data class ScaleBar(val metres: Int, val widthDp: Float) {
    val label: String get() = if (metres >= 1000) "${metres / 1000} km" else "$metres m"

    companion object {
        private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686
        private val NICE = listOf(1, 2, 5)

        /** MapLibre zoom levels are based on 512-dp tiles. */
        fun metresPerDp(zoom: Double, latitude: Double): Double =
            EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(latitude)) / (512 * 2.0.pow(zoom))

        fun pick(zoom: Double, latitude: Double, maxDp: Float = 100f): ScaleBar {
            val perDp = metresPerDp(zoom, latitude)
            val maxMetres = perDp * maxDp
            var best = 1
            var magnitude = 1
            while (magnitude <= 10_000_000) {
                for (n in NICE) {
                    val m = n * magnitude
                    if (m <= maxMetres) best = m
                }
                magnitude *= 10
            }
            return ScaleBar(best, (best / perDp).toFloat())
        }
    }
}
