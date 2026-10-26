package pt.vereda.core.route

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Writes network.bin like data/scripts/build_network.py, for hand-made test networks. */
internal class TestNetwork {
    private val names = mutableListOf<String>()
    private val nodes = mutableListOf<GeoPoint>()
    private class Edge(val from: Int, val to: Int, val kind: Int, val flags: Int, val name: Int, val official: Int, val inner: List<GeoPoint>)
    private val edges = mutableListOf<Edge>()

    fun node(p: GeoPoint): Int {
        nodes += p
        return nodes.size - 1
    }

    private fun nameId(text: String?): Int {
        if (text == null) return -1
        val i = names.indexOf(text)
        if (i >= 0) return i
        names += text
        return names.size - 1
    }

    fun edge(from: Int, to: Int, kind: WayKind, flags: Int = 0, name: String? = null, official: String? = null, inner: List<GeoPoint> = emptyList()) {
        edges += Edge(from, to, kind.ordinal, flags or (if (name != null) WayFlags.NAMED else 0), nameId(name), nameId(official), inner)
    }

    fun bytes(): ByteArray {
        val out = ByteArrayOutputStream()
        fun varint(value: Int) {
            var v = value.toLong()
            while (v >= 0x80) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }
        fun zigzag(v: Int) = varint((v shl 1) xor (v shr 31))
        fun fixed(p: GeoPoint) = (p.lat * 100_000).roundToInt() to (p.lon * 100_000).roundToInt()
        out.write("VRN1".toByteArray())
        out.write(1)
        out.write(0)
        varint(names.size)
        names.forEach { val b = it.toByteArray(); varint(b.size); out.write(b) }
        varint(nodes.size)
        var prev = 0 to 0
        nodes.forEach { p ->
            val f = fixed(p)
            zigzag(f.first - prev.first)
            zigzag(f.second - prev.second)
            prev = f
        }
        varint(edges.size)
        edges.forEach { e ->
            val shape = listOf(nodes[e.from]) + e.inner + listOf(nodes[e.to])
            varint(e.from)
            varint(e.to)
            varint((Geo.lengthM(shape) * 10).roundToInt())
            out.write(e.kind)
            out.write(e.flags)
            varint(e.name + 1)
            varint(e.official + 1)
            varint(e.inner.size)
            var p = fixed(nodes[e.from])
            e.inner.forEach { q ->
                val f = fixed(q)
                zigzag(f.first - p.first)
                zigzag(f.second - p.second)
                p = f
            }
        }
        return out.toByteArray()
    }
}

class RouterTest {

    private val origin = GeoPoint(32.70, -17.00)
    private fun at(eastM: Double, northM: Double) = Geo.offsetNorth(Geo.offsetEast(origin, eastM), northM)

    // A to C: a road straight east (2 km), a marked trail around the north (3 km), and a difficult
    // named shortcut (about 1.9 km). X-Y is a path nobody can reach.
    private val build = TestNetwork()
    private val a = build.node(at(0.0, 0.0))
    private val b = build.node(at(1000.0, 0.0))
    private val c = build.node(at(2000.0, 0.0))
    private val d = build.node(at(0.0, 500.0))
    private val e = build.node(at(2000.0, 500.0))
    private val f = build.node(at(1000.0, -300.0))
    private val x = build.node(at(6000.0, 0.0))
    private val y = build.node(at(6500.0, 0.0))

    init {
        build.edge(a, b, WayKind.MINOR_ROAD, WayFlags.CAR)
        build.edge(b, c, WayKind.MINOR_ROAD, WayFlags.CAR)
        build.edge(a, d, WayKind.PATH, WayFlags.ROUTE, name = "Vereda Alta", official = "PR 99")
        build.edge(d, e, WayKind.PATH, WayFlags.ROUTE or WayFlags.TUNNEL, name = "Vereda Alta", official = "PR 99", inner = listOf(at(1000.0, 520.0)))
        build.edge(e, c, WayKind.PATH, WayFlags.ROUTE, name = "Vereda Alta", official = "PR 99")
        build.edge(a, f, WayKind.PATH, WayFlags.ROUTE or WayFlags.DIFFICULT, name = "Atalho")
        build.edge(f, c, WayKind.PATH, WayFlags.ROUTE or WayFlags.DIFFICULT, name = "Atalho")
        build.edge(x, y, WayKind.PATH)
    }

    private val network = NetworkReader.read(build.bytes())
    private val router = Router(network)

    private fun found(outcome: RouteOutcome): PlannedRoute {
        assertIs<RouteOutcome.Found>(outcome, "expected a route, got $outcome")
        return outcome.route
    }

    @Test
    fun `the reader keeps names, flags and shapes`() {
        assertEquals(8, network.nodeCount)
        assertEquals(8, network.edgeCount)
        assertEquals(listOf("Vereda Alta", "PR 99", "Atalho"), network.names)
        assertEquals("Vereda Alta", network.name(3))
        assertEquals("PR 99", network.officialCode(3))
        assertTrue(network.has(3, WayFlags.TUNNEL))
        assertEquals(3, network.geometry(3).size)
        assertEquals(1000.0, network.lengthM(0), 1.0)
        assertEquals(WayKind.MINOR_ROAD, network.kind(0))
    }

    @Test
    fun `marked trails win over a slightly shorter road`() {
        val route = found(router.route(listOf(at(0.0, 0.0), at(2000.0, 0.0))))
        assertEquals(3000.0, route.summary.distanceM, 30.0)
        assertEquals(0.0, route.summary.roadM, 1.0)
        assertEquals(listOf("PR 99"), route.summary.officialTrails)
        assertEquals(listOf("Vereda Alta"), route.summary.mainNames)
        assertEquals(1, route.summary.tunnels)
        assertEquals(45, route.summary.flatMinutes)
        assertEquals(at(0.0, 0.0).lat, route.points.first().lat, 1e-5)
        assertEquals(at(2000.0, 0.0).lon, route.points.last().lon, 1e-5)
    }

    @Test
    fun `difficult paths are used only when not avoided`() {
        val avoided = found(router.route(listOf(at(0.0, 0.0), at(2000.0, 0.0))))
        assertEquals(0.0, avoided.summary.difficultM, 1.0)
        val allowed = found(router.route(listOf(at(0.0, 0.0), at(2000.0, 0.0)), RouteOptions(avoidDifficult = false)))
        assertTrue(allowed.summary.distanceM < 2200, "took ${allowed.summary.distanceM}")
        assertEquals(allowed.summary.distanceM, allowed.summary.difficultM, 1.0)
    }

    @Test
    fun `a stop on the road takes the road`() {
        val route = found(router.route(listOf(at(0.0, 0.0), at(1000.0, 20.0), at(2000.0, 0.0))))
        assertEquals(2000.0, route.summary.distanceM, 30.0)
        assertEquals(2000.0, route.summary.roadM, 30.0)
    }

    @Test
    fun `points between junctions are routed from where they are`() {
        val route = found(router.route(listOf(at(200.0, 10.0), at(600.0, -10.0))))
        assertEquals(400.0, route.summary.distanceM, 5.0)
        val across = found(router.route(listOf(at(300.0, 0.0), at(1800.0, 0.0))))
        assertEquals(1500.0, across.summary.distanceM, 10.0)
    }

    @Test
    fun `points far from any path or cut off are reported`() {
        assertEquals(RouteOutcome.TooFar(1), router.route(listOf(at(0.0, 0.0), at(0.0, 5000.0))))
        assertEquals(RouteOutcome.NoRoute(0), router.route(listOf(at(0.0, 0.0), at(6200.0, 0.0))))
    }

    @Test
    fun `snapping finds the nearest way within 500 m`() {
        val snap = network.snap(at(500.0, 100.0))!!
        assertEquals(0, snap.edge) // the road A-B, 100 m south
        assertEquals(0.5, snap.fractionAlong, 0.01)
        assertEquals(100.0, snap.distanceM, 2.0)
        assertEquals(null, network.snap(at(4000.0, 3000.0)))
    }

    @Test
    fun `a file that is not a network is refused`() {
        assertFailsWith<IOException> { NetworkReader.read("hello world".toByteArray()) }
        assertFailsWith<IOException> { NetworkReader.read(build.bytes().copyOf(30)) }
    }
}
