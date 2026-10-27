package pt.vereda.core.geo

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise and sunset (NOAA solar calculator, accurate to about a minute at Madeira's latitude),
 * for "daylight left" (spec: Safety). Uses the standard 0.833° below the horizon.
 */
object Sun {
    data class Day(val sunrise: Instant, val sunset: Instant)

    /** Sunrise and sunset on [date] (UTC calendar day) at a point, or null in polar day or night. */
    fun day(date: LocalDate, lat: Double, lon: Double): Day? {
        val noon = solarNoonMinutes(date, lon)
        val decl = declination(date, noon)
        val latR = Math.toRadians(lat)
        val zenith = Math.toRadians(90.833)
        val cosH = (cos(zenith) / (cos(latR) * cos(decl))) - tan(latR) * tan(decl)
        if (cosH < -1 || cosH > 1) return null
        val halfDayMinutes = Math.toDegrees(acos(cosH)) * 4.0
        val midnight = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        fun at(minutes: Double) = midnight.plusMillis((minutes * 60_000).toLong())
        return Day(at(noon - halfDayMinutes), at(noon + halfDayMinutes))
    }

    /** Daylight left at [now], or zero after sunset (and before sunrise). */
    fun daylightLeft(now: Instant, lat: Double, lon: Double): Duration {
        val date = now.atOffset(ZoneOffset.UTC).toLocalDate()
        val day = day(date, lat, lon) ?: return Duration.ZERO
        return if (now.isBefore(day.sunrise) || !now.isBefore(day.sunset)) Duration.ZERO else Duration.between(now, day.sunset)
    }

    private fun julianCentury(date: LocalDate, minutes: Double): Double {
        val jd = date.toEpochDay() + 2440587.5 + minutes / 1440.0
        return (jd - 2451545.0) / 36525.0
    }

    private fun meanLongitude(t: Double) = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
    private fun meanAnomaly(t: Double) = 357.52911 + t * (35999.05029 - 0.0001537 * t)
    private fun eccentricity(t: Double) = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun obliquity(t: Double): Double {
        val seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        val mean = 23.0 + (26.0 + seconds / 60.0) / 60.0
        return mean + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * t))
    }

    private fun declination(date: LocalDate, minutes: Double): Double {
        val t = julianCentury(date, minutes)
        val m = Math.toRadians(meanAnomaly(t))
        val center = sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
        val trueLong = meanLongitude(t) + center
        val apparent = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * t))
        return asin(sin(Math.toRadians(obliquity(t))) * sin(Math.toRadians(apparent)))
    }

    /** Equation of time in minutes. */
    private fun equationOfTime(t: Double): Double {
        val eps = Math.toRadians(obliquity(t))
        val l0 = Math.toRadians(meanLongitude(t))
        val e = eccentricity(t)
        val m = Math.toRadians(meanAnomaly(t))
        var y = tan(eps / 2)
        y *= y
        val eq = y * sin(2 * l0) - 2 * e * sin(m) + 4 * e * y * sin(m) * cos(2 * l0) -
            0.5 * y * y * sin(4 * l0) - 1.25 * e * e * sin(2 * m)
        return Math.toDegrees(eq) * 4.0
    }

    /** Solar noon in minutes after UTC midnight, refined once. */
    private fun solarNoonMinutes(date: LocalDate, lon: Double): Double {
        var noon = 720.0 - 4.0 * lon
        repeat(2) { noon = 720.0 - 4.0 * lon - equationOfTime(julianCentury(date, noon)) }
        return noon
    }
}
