package pt.vereda.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OffRouteDetectorTest {

    private val start = GeoPoint(32.70, -17.00)
    private val end = GeoPoint(32.70, -16.98)
    private val middle = GeoPoint(32.70, -16.99)
    private val route = Route(listOf(start, middle, end))

    private fun off(metres: Double) = Geo.offsetNorth(middle, metres)

    @Test
    fun `snap gives distance off the route and progress along it`() {
        assertEquals(1871.437, route.lengthM, 0.01)
        val onLine = route.snap(middle)
        assertEquals(0.0, onLine.distanceM, 0.5)
        assertEquals(935.72, onLine.progressM, 1.0)
        assertEquals(50.0, route.snap(off(50.0)).distanceM, 0.5)
    }

    @Test
    fun `a route needs two points`() {
        val error = runCatching { Route(listOf(start)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `alerts only after 30 s more than 40 m away, and clears below 25 m`() {
        val detector = OffRouteDetector(route)
        fun at(seconds: Int, metres: Double) =
            detector.onFix(off(metres), fixTimeMs = seconds * 1000L, nowMs = seconds * 1000L)

        assertEquals(RouteState.ON_ROUTE, at(0, 0.0).state)
        assertEquals(RouteState.ON_ROUTE, at(5, 50.0).state)
        assertEquals(RouteState.ON_ROUTE, at(20, 50.0).state)

        val wentOff = at(35, 50.0)
        assertEquals(RouteState.OFF_ROUTE, wentOff.state)
        assertTrue(wentOff.changed)

        val stillOff = at(40, 30.0)
        assertEquals(RouteState.OFF_ROUTE, stillOff.state)
        assertFalse(stillOff.changed)

        val back = at(45, 10.0)
        assertEquals(RouteState.ON_ROUTE, back.state)
        assertTrue(back.changed)
    }

    @Test
    fun `a short excursion resets the timer`() {
        val detector = OffRouteDetector(route)
        fun at(seconds: Int, metres: Double) =
            detector.onFix(off(metres), fixTimeMs = seconds * 1000L, nowMs = seconds * 1000L)

        at(0, 50.0)
        at(10, 10.0)
        at(15, 50.0)
        assertEquals(RouteState.ON_ROUTE, at(40, 50.0).state)
        assertEquals(RouteState.OFF_ROUTE, at(45, 50.0).state)
    }

    @Test
    fun `stale fixes from a tunnel never trigger an alert`() {
        val detector = OffRouteDetector(route)
        val update = detector.onFix(off(500.0), fixTimeMs = 80_000, nowMs = 100_000)
        assertEquals(RouteState.ON_ROUTE, update.state)
        assertFalse(update.changed)
        assertNull(update.snap)
    }
}
