package pt.vereda.core.catalog

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.model.Island
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrailPositionTest {

    // A straight 2 km line running north.
    private val south = GeoPoint(32.70, -17.00)
    private val north = GeoPoint(32.70 + 2000 / Geo.METRES_PER_DEGREE, -17.00)
    private val line = listOf(south, north)

    private fun trail(id: String = "osm-w1", ends: List<TrailEnd> = emptyList(), lines: List<List<GeoPoint>> = listOf(line)) =
        TrailEntry(Trail(id, TrailSource.COMMUNITY, null, "Test", null, null, null, Island.MADEIRA), 2000, null, lines, ends = ends)

    private fun northOf(p: GeoPoint, m: Double) = Geo.offsetNorth(p, m)

    @Test
    fun `on the trail counts from the line start when there is no catalog start`() {
        val position = TrailPositions.of(trail(), Geo.offsetEast(northOf(south, 500.0), 10.0))
        assertIs<TrailPosition.OnTrail>(position)
        assertEquals(500.0, position.walkedM, 1.0)
        assertEquals(1500.0, position.toEndM, 1.0)
        assertEquals(10.0, position.offM, 0.5)
    }

    @Test
    fun `progress follows the catalog start even when the line is stored the other way`() {
        val ends = listOf(TrailEnd(north, RoadAccess(0)), TrailEnd(south, RoadAccess(1200)))
        val position = TrailPositions.of(trail(ends = ends), northOf(south, 500.0))
        assertIs<TrailPosition.OnTrail>(position)
        assertEquals(1500.0, position.walkedM, 1.0)
        assertEquals(500.0, position.toEndM, 1.0)
    }

    @Test
    fun `off the trail gives the distance to it and to the start`() {
        val here = Geo.offsetEast(northOf(south, 1000.0), 450.0)
        val position = TrailPositions.of(trail(), here)
        assertIs<TrailPosition.OffTrail>(position)
        assertEquals(450.0, position.toTrailM, 1.0)
        assertTrue(position.toStartM!! > 1000.0)
    }

    @Test
    fun `just inside 40 m is on the trail, just outside is off`() {
        val middle = northOf(south, 1000.0)
        assertIs<TrailPosition.OnTrail>(TrailPositions.of(trail(), Geo.offsetEast(middle, 39.0)))
        assertIs<TrailPosition.OffTrail>(TrailPositions.of(trail(), Geo.offsetEast(middle, 41.0)))
    }

    @Test
    fun `the nearest of several lines is used`() {
        val far = listOf(GeoPoint(32.80, -17.10), GeoPoint(32.81, -17.10))
        val position = TrailPositions.of(trail(lines = listOf(far, line)), northOf(south, 200.0))
        assertIs<TrailPosition.OnTrail>(position)
        assertEquals(200.0, position.walkedM, 1.0)
    }

    @Test
    fun `a trail without a line has no position`() {
        assertNull(TrailPositions.of(trail(lines = emptyList()), south))
    }

    @Test
    fun `trails near a peak are found nearest first`() {
        val peak = Geo.offsetEast(northOf(south, 1000.0), 100.0)
        val close = trail("close")
        val other = trail("other", lines = listOf(listOf(Geo.offsetEast(south, 140.0), Geo.offsetEast(north, 140.0))))
        val away = trail("away", lines = listOf(listOf(GeoPoint(32.9, -16.9), GeoPoint(32.91, -16.9))))
        assertEquals(listOf("other", "close"), TrailPositions.near(listOf(close, other, away), peak).map { it.id })
        assertEquals(emptyList(), TrailPositions.near(listOf(close), peak, radiusM = 50.0))
    }

    @Test
    fun `start and end points come from the catalog ends or the line`() {
        assertEquals(south, trail().startPoint)
        assertEquals(north, trail().endPoint)
        val loop = trail(ends = listOf(TrailEnd(south, RoadAccess(0))))
        assertEquals(south, loop.startPoint)
        assertNull(loop.endPoint)
        val closedLine = trail(lines = listOf(listOf(south, north, Geo.offsetEast(south, 20.0))))
        assertNull(closedLine.endPoint)
    }
}
