package pt.vereda.core.geo

import java.util.Locale
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

data class UtmCoordinate(
    val zone: Int,
    val northern: Boolean,
    val easting: Double,
    val northing: Double,
) {
    /** "28N 320991 3614052" — zone, hemisphere, easting and northing in whole metres. */
    fun format(): String = String.format(
        Locale.ROOT,
        "%d%s %d %d",
        zone,
        if (northern) "N" else "S",
        easting.roundToLong(),
        northing.roundToLong(),
    )
}

/**
 * WGS84 latitude/longitude to UTM using the Krüger series to n³ (millimetre accuracy within a
 * zone). Madeira and Porto Santo are in zone 28N.
 */
object Utm {

    private const val A = 6_378_137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500_000.0
    private const val FALSE_NORTHING_SOUTH = 10_000_000.0

    fun fromLatLon(lat: Double, lon: Double): UtmCoordinate {
        require(lat in -80.0..84.0) { "UTM is defined between 80°S and 84°N" }
        require(lon in -180.0..180.0) { "Longitude out of range" }

        val zone = (floor((lon + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
        val centralMeridian = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)

        val n = F / (2 - F)
        val bigA = A / (1 + n) * (1 + n * n / 4 + n * n * n * n / 64)
        val alpha = doubleArrayOf(
            n / 2 - 2.0 / 3 * n * n + 5.0 / 16 * n * n * n,
            13.0 / 48 * n * n - 3.0 / 5 * n * n * n,
            61.0 / 240 * n * n * n,
        )

        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lon) - centralMeridian
        val c = 2 * sqrt(n) / (1 + n)
        val t = sinh(atanh(sin(phi)) - c * atanh(c * sin(phi)))
        val xiPrime = atan(t / cos(lambda))
        val etaPrime = atanh(sin(lambda) / sqrt(1 + t * t))

        var easting = etaPrime
        var northing = xiPrime
        for (j in 1..3) {
            val a = alpha[j - 1]
            easting += a * cos(2 * j * xiPrime) * sinh(2 * j * etaPrime)
            northing += a * sin(2 * j * xiPrime) * cosh(2 * j * etaPrime)
        }
        easting = FALSE_EASTING + K0 * bigA * easting
        northing = K0 * bigA * northing
        val northern = lat >= 0
        if (!northern) northing += FALSE_NORTHING_SOUTH

        return UtmCoordinate(zone, northern, easting, northing)
    }
}
