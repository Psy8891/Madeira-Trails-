package pt.vereda.core.route

import pt.vereda.core.geo.ElevationReader
import pt.vereda.core.geo.Fix
import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import java.io.ByteArrayOutputStream
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NavigationTest {

    private val origin = GeoPoint(32.70, -17.00)
    private fun at(eastM: Double, northM: Double) = Geo.offsetNorth(Geo.offsetEast(origin, eastM), northM)

    // A fork at J (0, 500): Levada A goes on north, Vereda B leaves north-east (about 30° right),
    // Caminho C goes west.
    private val build = TestNetwork()
    private val s = build.node(at(0.0, 0.0))
    private val j = build.node(at(0.0, 500.0))
    private val n = build.node(at(0.0, 1000.0))
    private val e = build.node(at(300.0, 1020.0))
    private val w = build.node(at(-500.0, 500.0))

    init {
        build.edge(s, j, WayKind.PATH, WayFlags.ROUTE or WayFlags.LEVADA, name = "Levada A")
        build.edge(j, n, WayKind.PATH, WayFlags.ROUTE or WayFlags.LEVADA, name = "Levada A")
        build.edge(j, e, WayKind.PATH, WayFlags.ROUTE, name = "Vereda B")
        build.edge(j, w, WayKind.PATH, WayFlags.ROUTE, name = "Caminho C")
    }

    private val network = NetworkReader.read(build.bytes())
    private val router = Router(network)

    private fun route(to: GeoPoint): PlannedRoute =
        (router.route(listOf(at(0.0, 0.0), to)) as RouteOutcome.Found).route

    private fun fix(p: GeoPoint, t: Long, accuracy: Double = 5.0) = Fix(p.lat, p.lon, accuracy, t)

    @Test
    fun `the router records the junctions it passes`() {
        val r = route(at(0.0, 1000.0))
        assertEquals(listOf(j), r.junctions.map { it.node })
        assertEquals(500.0, r.junctions.single().alongM, 5.0)
    }

    @Test
    fun `cues say which way at a fork, and nothing where the way on is obvious`() {
        val north = NavRoute.of("A", route(at(0.0, 1000.0)), network, TripKind.ONE_WAY, null).cues
        assertEquals(listOf(Turn.KEEP_LEFT, Turn.ARRIVE), north.map { it.turn })
        assertEquals("Levada A", north.first().name)
        val west = NavRoute.of("C", route(at(-500.0, 500.0)), network, TripKind.ONE_WAY, null).cues
        assertEquals(Turn.LEFT, west.first().turn)
        assertEquals("Caminho C", west.first().name)
        val east = NavRoute.of("B", route(at(300.0, 1020.0)), network, TripKind.ONE_WAY, null).cues
        assertEquals(Turn.KEEP_RIGHT, east.first().turn)
        assertEquals("Vereda B", east.first().name)
    }

    @Test
    fun `there and back doubles the walk, turns at the end and cues the junction both ways`() {
        val trip = NavRoute.of("C", route(at(-500.0, 500.0)), network, TripKind.THERE_AND_BACK, null)
        assertEquals(TripKind.THERE_AND_BACK, trip.kind)
        assertEquals(2000.0, trip.lengthM, 20.0)
        assertEquals(listOf(Turn.LEFT, Turn.TURN_AROUND, Turn.RIGHT, Turn.ARRIVE), trip.cues.map { it.turn })
        assertEquals("Levada A", trip.cues[2].name)
        assertEquals(1500.0, trip.cues[2].alongM, 10.0)
    }

    @Test
    fun `a route that ends where it starts is a loop`() {
        val shape = listOf(at(0.0, 0.0), at(0.0, 500.0), at(500.0, 500.0), at(10.0, 10.0))
        val planned = PlannedRoute(shape, RouteSummary(1700.0, 0.0, 0.0, 0, emptyList(), emptyList()), emptyList())
        assertEquals(TripKind.LOOP, NavRoute.of("L", planned, network, TripKind.THERE_AND_BACK, null).kind)
    }

    @Test
    fun `progress, cue alerts once each, and arrival`() {
        val trip = NavRoute.of("C", route(at(-500.0, 500.0)), network, TripKind.ONE_WAY, null)
        val nav = Navigator(trip)
        var t = 0L
        fun walk(p: GeoPoint): List<NavEvent> { t += 5_000; return nav.onFix(fix(p, t), t) }
        assertTrue(walk(at(0.0, 0.0)).isEmpty())
        walk(at(3.0, 300.0))
        assertEquals(300.0, nav.state.progressM, 5.0)
        assertEquals(700.0, nav.state.remainingM, 10.0)
        assertEquals(200.0, nav.state.toNextCueM!!, 5.0)
        val ahead = walk(at(2.0, 440.0))
        assertIs<NavEvent.CueAhead>(ahead.single())
        assertTrue(walk(at(2.0, 450.0)).isEmpty())
        val now = walk(at(0.0, 490.0))
        assertIs<NavEvent.CueNow>(now.single())
        assertTrue(walk(at(-5.0, 500.0)).isEmpty())
        walk(at(-300.0, 500.0))
        assertFalse(nav.state.arrived)
        assertEquals(listOf<NavEvent>(NavEvent.Arrived), walk(at(-490.0, 502.0)))
        assertTrue(nav.state.arrived)
        assertTrue(walk(at(-495.0, 500.0)).isEmpty())
    }

    @Test
    fun `on a there and back the way home is not mistaken for the way out`() {
        val trip = NavRoute.of("C", route(at(-500.0, 500.0)), network, TripKind.THERE_AND_BACK, null)
        val nav = Navigator(trip)
        var t = 0L
        fun walk(p: GeoPoint): List<NavEvent> { t += 5_000; return nav.onFix(fix(p, t), t) }
        assertTrue(walk(at(0.0, 0.0)).isEmpty()) // the start is also the end: not arrived
        assertEquals(0.0, nav.state.progressM, 5.0)
        // Out along the levada and the path, back the same way.
        (1..10).forEach { walk(at(0.0, it * 50.0)) }
        (1..10).forEach { walk(at(-it * 50.0, 500.0)) }
        assertEquals(1000.0, nav.state.progressM, 10.0)
        (9 downTo 0).forEach { walk(at(-it * 50.0, 500.0)) }
        (9 downTo 4).forEach { walk(at(0.0, it * 50.0)) }
        assertEquals(1800.0, nav.state.progressM, 10.0)
        assertFalse(nav.state.wrongWay)
        (3 downTo 1).forEach { walk(at(0.0, it * 50.0)) }
        assertEquals(listOf<NavEvent>(NavEvent.Arrived), walk(at(0.0, 5.0)))
    }

    @Test
    fun `off route after 20 s more than 40 m away, back below 25 m, old fixes ignored`() {
        val shape = listOf(at(0.0, 0.0), at(0.0, 2000.0))
        val nav = Navigator(NavRoute.of("line", TripKind.ONE_WAY, shape, listOf(Cue(2000.0, shape.last(), Turn.ARRIVE, null))))
        nav.onFix(fix(at(0.0, 100.0), 0), 0)
        assertTrue(nav.onFix(fix(at(60.0, 120.0), 5_000), 5_000).isEmpty())
        assertTrue(nav.onFix(fix(at(60.0, 130.0), 15_000), 15_000).isEmpty())
        val off = nav.onFix(fix(at(60.0, 140.0), 26_000), 26_000)
        assertIs<NavEvent.OffRoute>(off.single())
        assertTrue(nav.state.offRoute)
        assertEquals(100.0, nav.state.progressM, 5.0) // progress waits while off route
        // A stale or poor fix back on the line changes nothing.
        assertTrue(nav.onFix(fix(at(0.0, 150.0), 10_000), 31_000).isEmpty())
        assertTrue(nav.onFix(fix(at(0.0, 150.0), 31_000, accuracy = 80.0), 31_000).isEmpty())
        assertTrue(nav.state.offRoute)
        assertEquals(listOf<NavEvent>(NavEvent.BackOnRoute), nav.onFix(fix(at(10.0, 150.0), 36_000), 36_000))
    }

    @Test
    fun `walking back towards the start is the wrong way`() {
        val shape = listOf(at(0.0, 0.0), at(0.0, 2000.0))
        val nav = Navigator(NavRoute.of("line", TripKind.ONE_WAY, shape, listOf(Cue(2000.0, shape.last(), Turn.ARRIVE, null))))
        var t = 0L
        listOf(0.0, 200.0, 400.0, 500.0, 470.0).forEach { t += 5_000; assertTrue(nav.onFix(fix(at(0.0, it), t), t).isEmpty()) }
        t += 5_000
        assertEquals(listOf<NavEvent>(NavEvent.WrongWay), nav.onFix(fix(at(0.0, 440.0), t), t))
        t += 5_000
        nav.onFix(fix(at(0.0, 495.0), t), t)
        assertFalse(nav.state.wrongWay)
    }

    @Test
    fun `GPS asks less often when far from a junction or standing still`() {
        val cue = Cue(1000.0, origin, Turn.LEFT, null)
        val walking = NavState(nextCue = cue, toNextCueM = 600.0)
        assertEquals(GpsPolicy.NORMAL_MS, GpsPolicy.intervalMs(walking, 0))
        assertEquals(GpsPolicy.CLOSE_MS, GpsPolicy.intervalMs(walking.copy(toNextCueM = 150.0), 0))
        assertEquals(GpsPolicy.CLOSE_MS, GpsPolicy.intervalMs(walking.copy(offRoute = true), 0))
        assertEquals(GpsPolicy.STILL_MS, GpsPolicy.intervalMs(walking.copy(stillSinceMs = 0), 130_000))
        assertEquals(GpsPolicy.NORMAL_MS, GpsPolicy.intervalMs(walking.copy(stillSinceMs = 0), 60_000))
        assertEquals(GpsPolicy.NORMAL_MS, GpsPolicy.intervalMs(NavState(nextCue = Cue(10.0, origin, Turn.ARRIVE, null), toNextCueM = 50.0), 0))
    }

    @Test
    fun `time left counts the climb still ahead`() {
        // A slope rising 1 m per 0.0001° north (about 11 m): 2 km north climbs about 180 m.
        val out = ByteArrayOutputStream()
        fun varint(value: Long) { var v = value; while (v >= 0x80) { out.write(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }; out.write(v.toInt()) }
        fun zigzag(v: Long) = varint((v shl 1) xor (v shr 63))
        out.write("VRE1".toByteArray()); out.write(1); out.write(0)
        varint(1)
        zigzag((32.69 * 1e6).roundToLong()); zigzag((-17.01 * 1e6).roundToLong()); varint(100); varint(400); varint(200)
        var previous = 0L
        for (r in 0 until 400) for (c in 0 until 200) { val h = r.toLong(); zigzag(h - previous); previous = h }
        val grid = ElevationReader.read(out.toByteArray())
        val shape = listOf(at(0.0, 0.0), at(0.0, 2000.0))
        val trip = NavRoute.of("up", TripKind.ONE_WAY, shape, listOf(Cue(2000.0, shape.last(), Turn.ARRIVE, null)), grid)
        assertEquals(180.0, trip.climbLeftM(0.0), 10.0)
        assertEquals(90.0, trip.climbLeftM(1000.0), 10.0)
        val nav = Navigator(trip)
        assertEquals(30 + 18, nav.state.minutesLeft, 5) // 2 km at 4 km/h + 180 m at 600 m/h
    }
}
