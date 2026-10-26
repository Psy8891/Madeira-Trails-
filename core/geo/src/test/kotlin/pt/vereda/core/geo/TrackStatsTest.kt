package pt.vereda.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackStatsTest {

    private fun point(i: Int, ele: Double?, segment: Int = 0) =
        TrackPoint(lat = 32.70, lon = -17.00 + i * 0.0001, ele = ele, timeMs = i * 10_000L, segment = segment)

    @Test
    fun `empty track has zero stats`() {
        val stats = TrackStatsCalculator.compute(emptyList())
        assertEquals(0.0, stats.distanceM)
        assertNull(stats.minEleM)
    }

    @Test
    fun `GPS noise below 3 m is not counted as climb`() {
        val noisy = (0 until 50).map { point(it, if (it % 2 == 0) 100.0 else 101.5) }
        val stats = TrackStatsCalculator.compute(noisy)
        assertEquals(0.0, stats.ascentM)
        assertEquals(0.0, stats.descentM)
    }

    @Test
    fun `steady climb is counted in full`() {
        val climb = (0..30).map { point(it, 100.0 + it) }
        assertEquals(30.0, TrackStatsCalculator.compute(climb).ascentM, 1e-9)
    }

    @Test
    fun `descent ignores the last change under the threshold`() {
        // Up 100 -> 130, then down 1 m at a time to 120: counted steps 127, 124, 121 = 9 m.
        val up = (0..30).map { point(it, 100.0 + it) }
        val down = (1..10).map { point(30 + it, 130.0 - it) }
        val stats = TrackStatsCalculator.compute(up + down)
        assertEquals(30.0, stats.ascentM, 1e-9)
        assertEquals(9.0, stats.descentM, 1e-9)
        assertEquals(100.0, stats.minEleM)
        assertEquals(130.0, stats.maxEleM)
    }

    @Test
    fun `distance and moving time never bridge a pause`() {
        val first = (0..2).map { point(it, null, segment = 0) }
        val second = (10..12).map { point(it, null, segment = 1) }
        val stats = TrackStatsCalculator.compute(first + second)
        val oneLeg = Geo.distanceM(32.70, -17.00, 32.70, -16.9999)
        assertEquals(4 * oneLeg, stats.distanceM, 0.01)
        assertEquals(40_000L, stats.movingTimeMs)
        assertEquals(120_000L, stats.totalTimeMs)
    }
}
