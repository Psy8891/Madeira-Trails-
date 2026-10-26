package pt.vereda.core.geo

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SunTest {
    private fun assertNear(expectedUtc: String, actual: Instant) {
        val expected = Instant.parse(expectedUtc)
        val minutes = abs(Duration.between(expected, actual).toMinutes())
        assertTrue(minutes <= 2, "expected $expected, got $actual")
    }

    // Reference times from the astral library (NOAA method) for Funchal, 32.65 N 16.91 W.
    @Test
    fun `sunrise and sunset in Funchal through the year`() {
        val summer = Sun.day(LocalDate.of(2026, 6, 21), 32.65, -16.91)!!
        assertNear("2026-06-21T06:00:37Z", summer.sunrise)
        assertNear("2026-06-21T20:18:18Z", summer.sunset)
        val winter = Sun.day(LocalDate.of(2026, 12, 21), 32.65, -16.91)!!
        assertNear("2026-12-21T08:05:54Z", winter.sunrise)
        assertNear("2026-12-21T18:05:34Z", winter.sunset)
        val autumn = Sun.day(LocalDate.of(2026, 10, 27), 32.65, -16.91)!!
        assertNear("2026-10-27T18:21:22Z", autumn.sunset)
    }

    @Test
    fun `daylight left counts down to sunset and is zero at night`() {
        val lat = 32.7587
        val lon = -16.9431
        val sunset = Sun.day(LocalDate.of(2026, 3, 20), lat, lon)!!.sunset
        assertNear("2026-03-20T19:19:04Z", sunset)
        val twoHoursBefore = sunset.minus(Duration.ofHours(2))
        assertEquals(120, Sun.daylightLeft(twoHoursBefore, lat, lon).toMinutes())
        assertEquals(Duration.ZERO, Sun.daylightLeft(sunset.plusSeconds(60), lat, lon))
        assertEquals(Duration.ZERO, Sun.daylightLeft(LocalDate.of(2026, 3, 20).atTime(3, 0).toInstant(ZoneOffset.UTC), lat, lon))
    }
}
