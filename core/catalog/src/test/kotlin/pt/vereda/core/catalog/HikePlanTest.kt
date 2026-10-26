package pt.vereda.core.catalog

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.model.Island
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HikePlanTest {

    // A straight 2 km trail running north from `south`.
    private val south = GeoPoint(32.70, -17.00)
    private val north = Geo.offsetNorth(south, 2000.0)
    private val line = listOf(south, Geo.offsetNorth(south, 500.0), north)
    private val road = Geo.offsetEast(south, -800.0)
    private val walkIn = RoadAccess(approachM = 900, roadPoint = road)
    private val onRoad = RoadAccess(approachM = 0, roadPoint = south)

    private fun trail(ends: List<TrailEnd> = emptyList(), lines: List<List<GeoPoint>> = listOf(line)) =
        TrailEntry(Trail("osm-w1", TrailSource.COMMUNITY, null, "Test", null, null, null, Island.MADEIRA), 2000, null, lines, ends = ends)

    private fun near(expected: GeoPoint, actual: GeoPoint, metres: Double = 1.0) =
        assertTrue(Geo.distanceM(expected, actual) < metres, "$actual is ${Geo.distanceM(expected, actual)} m from $expected")

    @Test
    fun `a trail is walked from the chosen road along its own line to its end`() {
        val plan = assertNotNull(HikePlans.forTrail(trail(), walkIn, here = null))
        near(road, plan.start)
        assertEquals(4, plan.via.size)
        near(south, plan.via[0])
        near(Geo.offsetNorth(south, 500.0), plan.via[1])
        near(Geo.offsetNorth(south, 1000.0), plan.via[2])
        near(Geo.offsetNorth(south, 1500.0), plan.via[3])
        near(north, plan.end)
    }

    @Test
    fun `a trail start on the road needs no extra via point`() {
        val plan = assertNotNull(HikePlans.forTrail(trail(), onRoad, here = null))
        near(south, plan.start)
        assertEquals(3, plan.via.size)
    }

    @Test
    fun `the line is followed from the catalog start even when stored the other way`() {
        val ends = listOf(TrailEnd(north, onRoad), TrailEnd(south, onRoad))
        val plan = assertNotNull(HikePlans.forTrail(trail(ends = ends), null, here = null))
        near(north, plan.start)
        near(Geo.offsetNorth(south, 1500.0), plan.via[0])
        near(Geo.offsetNorth(south, 500.0), plan.via[2])
        near(south, plan.end)
    }

    @Test
    fun `a loop ends where it started`() {
        val loop = listOf(south, Geo.offsetNorth(south, 1000.0), Geo.offsetEast(Geo.offsetNorth(south, 1000.0), 1000.0), south)
        val plan = assertNotNull(HikePlans.forTrail(trail(ends = listOf(TrailEnd(south, onRoad)), lines = listOf(loop)), null, null))
        near(south, plan.end)
    }

    @Test
    fun `the phone near the start becomes the start, far away it does not`() {
        val close = Geo.offsetEast(road, -300.0)
        near(close, assertNotNull(HikePlans.forTrail(trail(), walkIn, close)).start)
        val far = Geo.offsetEast(road, -5000.0)
        near(road, assertNotNull(HikePlans.forTrail(trail(), walkIn, far)).start)
    }

    @Test
    fun `a trail without a line has no plan`() {
        assertNull(HikePlans.forTrail(trail(lines = emptyList()), walkIn, null))
    }

    @Test
    fun `a peak is walked to from the chosen road, or not at all when it is on the road`() {
        val peak = Place("Pico Teste", PlaceKind.PEAK, north, 1600)
        val plan = assertNotNull(HikePlans.forPlace(peak, RoadAccess(1200, road), null))
        near(road, plan.start)
        assertTrue(plan.via.isEmpty())
        near(north, plan.end)
        assertNull(HikePlans.forPlace(peak, RoadAccess(0, north), null))
        assertNull(HikePlans.forPlace(peak, null, null))
        val nearPeak = Geo.offsetEast(north, 400.0)
        near(nearPeak, assertNotNull(HikePlans.forPlace(peak, null, nearPeak)).start)
    }

    @Test
    fun `the toggle is stored by key and falls back to the app`() {
        DirectionsTarget.entries.forEach { assertEquals(it, DirectionsTarget.fromKey(it.key)) }
        assertEquals(DirectionsTarget.IN_APP, DirectionsTarget.fromKey(null))
        assertEquals(DirectionsTarget.IN_APP, DirectionsTarget.fromKey("bogus"))
    }

    @Test
    fun `google maps link points at the destination`() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=32.700000,-17.000000",
            HikePlans.googleMapsUrl(south),
        )
    }
}
