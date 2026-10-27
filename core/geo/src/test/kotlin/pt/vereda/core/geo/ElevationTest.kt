package pt.vereda.core.geo

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Writes elevation.bin like data/scripts/build_elevation.py. */
internal object TestElevation {
    class G(val lat0: Double, val lon0: Double, val step: Double, val rows: Int, val cols: Int, val h: (Int, Int) -> Int)

    fun bytes(vararg grids: G): ByteArray {
        val out = ByteArrayOutputStream()
        fun varint(value: Long) {
            var v = value
            while (v >= 0x80) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }
        fun zigzag(v: Long) = varint((v shl 1) xor (v shr 63))
        out.write("VRE1".toByteArray())
        out.write(1)
        out.write(0)
        varint(grids.size.toLong())
        for (g in grids) {
            zigzag((g.lat0 * 1e6).roundToLong())
            zigzag((g.lon0 * 1e6).roundToLong())
            varint((g.step * 1e6).roundToLong())
            varint(g.rows.toLong())
            varint(g.cols.toLong())
            var previous = 0L
            for (r in 0 until g.rows) for (c in 0 until g.cols) {
                val v = g.h(r, c).toLong()
                zigzag(v - previous)
                previous = v
            }
        }
        return out.toByteArray()
    }
}

class ElevationTest {

    // A slope rising 1 m per cell to the north (cells of 0.001°, about 111 m), 100 rows.
    private val slope = TestElevation.G(32.70, -17.00, 0.001, 100, 50) { r, _ -> 100 + r }
    private val island = TestElevation.G(33.05, -16.35, 0.001, 10, 10) { _, _ -> 300 }
    private val grid = ElevationReader.read(TestElevation.bytes(slope, island))

    @Test
    fun `heights are interpolated between cell centres`() {
        assertEquals(100.0, grid.heightAt(32.70, -17.00)!!, 1e-6)
        assertEquals(110.5, grid.heightAt(32.7105, -16.98)!!, 1e-6)
        assertEquals(300.0, grid.heightAt(33.055, -16.345)!!, 1e-6)
        assertNull(grid.heightAt(40.0, -17.0))
    }

    @Test
    fun `a walk up the slope climbs and comes back down`() {
        val bottom = GeoPoint(32.70, -16.99)
        val top = GeoPoint(32.79, -16.99) // 90 cells north: 90 m higher, about 10 km
        val profile = assertNotNull(ElevationProfiles.of(listOf(bottom, top, bottom), grid))
        assertEquals(90.0, profile.ascentM, 3.0)
        assertEquals(90.0, profile.descentM, 3.0)
        assertEquals(100.0, profile.minM, 1.0)
        assertEquals(190.0, profile.maxM, 1.0)
        assertEquals(100.0, profile.startM, 1.0)
        assertTrue(profile.graph.size <= ElevationProfiles.GRAPH_POINTS + 2, "graph has ${profile.graph.size}")
        assertEquals(190.0, profile.graph.maxOf { it.second }, 1.0) // the top survives downsampling
        assertEquals(profile.distanceM, profile.graph.last().first, 1e-6)
    }

    @Test
    fun `small wobbles below 3 m are not counted as climb`() {
        val bumpy = TestElevation.G(32.70, -17.00, 0.0001, 3, 200) { _, c -> if (c % 2 == 0) 100 else 102 }
        val g = ElevationReader.read(TestElevation.bytes(bumpy))
        val profile = assertNotNull(ElevationProfiles.of(listOf(GeoPoint(32.7001, -16.999), GeoPoint(32.7001, -16.982)), g))
        assertEquals(0.0, profile.ascentM, 1e-9)
    }

    @Test
    fun `hike time adds an hour per 600 m of climb`() {
        assertEquals(60, HikeTime.minutes(4000.0, 0.0))
        assertEquals(120, HikeTime.minutes(4000.0, 600.0))
        assertEquals(15, HikeTime.minutes(1000.0, 0.0))
        assertEquals(5, HikeTime.minutes(10.0, 0.0))
        assertEquals(165, HikeTime.minutes(8000.0, 400.0)) // 120 + 40 = 160 → 165 (15 min steps)
    }

    @Test
    fun `lines off the grid have no profile`() {
        assertNull(ElevationProfiles.of(listOf(GeoPoint(40.0, -10.0), GeoPoint(40.01, -10.0)), grid))
        assertNull(ElevationProfiles.of(listOf(GeoPoint(32.71, -16.99)), grid))
    }

    @Test
    fun `a file that is not an elevation grid is refused`() {
        assertFailsWith<IOException> { ElevationReader.read("hello".toByteArray()) }
        assertFailsWith<IOException> { ElevationReader.read(TestElevation.bytes(slope).copyOf(20)) }
    }
}
