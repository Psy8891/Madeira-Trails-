package pt.vereda.core.geo

import java.io.IOException
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Heights for Madeira and Porto Santo from the bundled elevation grid (spec: Phase 1,
 * *Elevation grid*), so climb and the height graph work offline without a map package.
 */
class ElevationGrid internal constructor(private val grids: List<Part>) {

    internal class Part(
        val lat0: Double,
        val lon0: Double,
        val step: Double,
        val rows: Int,
        val cols: Int,
        val heights: ShortArray,
    ) {
        fun covers(lat: Double, lon: Double): Boolean {
            val y = (lat - lat0) / step
            val x = (lon - lon0) / step
            return y >= 0 && x >= 0 && y <= rows - 1 && x <= cols - 1
        }

        /** Bilinear between the four cell centres around the point. */
        fun at(lat: Double, lon: Double): Double {
            val y = ((lat - lat0) / step).coerceIn(0.0, (rows - 1).toDouble())
            val x = ((lon - lon0) / step).coerceIn(0.0, (cols - 1).toDouble())
            val r = floor(y).toInt().coerceAtMost(rows - 2).coerceAtLeast(0)
            val c = floor(x).toInt().coerceAtMost(cols - 2).coerceAtLeast(0)
            val fy = if (rows == 1) 0.0 else y - r
            val fx = if (cols == 1) 0.0 else x - c
            fun h(row: Int, col: Int) = heights[row.coerceAtMost(rows - 1) * cols + col.coerceAtMost(cols - 1)].toDouble()
            val south = h(r, c) * (1 - fx) + h(r, c + 1) * fx
            val north = h(r + 1, c) * (1 - fx) + h(r + 1, c + 1) * fx
            return south * (1 - fy) + north * fy
        }
    }

    /** Height in metres above sea level, or null outside the islands' grids. */
    fun heightAt(lat: Double, lon: Double): Double? = grids.firstOrNull { it.covers(lat, lon) }?.at(lat, lon)

    fun heightAt(p: GeoPoint): Double? = heightAt(p.lat, p.lon)
}

/** Reads elevation.bin as written by data/scripts/build_elevation.py. */
object ElevationReader {
    fun read(bytes: ByteArray): ElevationGrid {
        if (bytes.size < 6 || String(bytes, 0, 4, Charsets.US_ASCII) != "VRE1") throw IOException("Not a Vereda elevation file")
        val version = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)
        if (version != 1) throw IOException("Unsupported elevation version $version")
        var pos = 6
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                if (pos >= bytes.size) throw IOException("Elevation file ends early")
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) return result
                shift += 7
                if (shift > 63) throw IOException("Bad varint")
            }
        }
        fun zigzag(): Long {
            val v = varint()
            return (v ushr 1) xor -(v and 1)
        }
        val count = varint().toInt()
        val parts = List(count) {
            val lat0 = zigzag() / 1e6
            val lon0 = zigzag() / 1e6
            val step = varint() / 1e6
            val rows = varint().toInt()
            val cols = varint().toInt()
            if (rows <= 0 || cols <= 0 || step <= 0) throw IOException("Bad grid header")
            val heights = ShortArray(rows * cols)
            var previous = 0L
            for (i in heights.indices) {
                previous += zigzag()
                heights[i] = previous.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            ElevationGrid.Part(lat0, lon0, step, rows, cols, heights)
        }
        return ElevationGrid(parts)
    }
}

/** Heights along a line, for the climb and the small height graph. */
data class ElevationProfile(
    val distanceM: Double,
    val ascentM: Double,
    val descentM: Double,
    val minM: Double,
    val maxM: Double,
    val startM: Double,
    val endM: Double,
    /** (distance along the line, height) pairs for drawing, at most [ElevationProfiles.GRAPH_POINTS]. */
    val graph: List<Pair<Double, Double>>,
) {
    /** Walking time: 4 km/h on the flat plus 1 h per 600 m of climb (spec: *Elevation stats*). */
    val minutes: Int get() = HikeTime.minutes(distanceM, ascentM)
}

object HikeTime {
    const val FLAT_M_PER_MIN = 4000.0 / 60.0
    const val CLIMB_M_PER_MIN = 600.0 / 60.0

    /** Rounded to 5 min under an hour, to 15 min above (never 0). */
    fun minutes(distanceM: Double, ascentM: Double): Int {
        val raw = distanceM / FLAT_M_PER_MIN + ascentM / CLIMB_M_PER_MIN
        val unit = if (raw < 60) 5.0 else 15.0
        return ((raw / unit).roundToInt() * unit).toInt().coerceAtLeast(5)
    }
}

object ElevationProfiles {
    const val SAMPLE_M = 10.0
    const val GRAPH_POINTS = 120
    private const val CLIMB_THRESHOLD_M = 3.0

    /** Samples every 10 m, smooths with a 3-point median and counts climb in steps of 3 m or more. */
    fun of(line: List<GeoPoint>, grid: ElevationGrid): ElevationProfile? {
        if (line.size < 2) return null
        val distances = ArrayList<Double>()
        val heights = ArrayList<Double>()
        var walked = 0.0
        var nextSample = 0.0
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val step = Geo.distanceM(a, b)
            while (nextSample <= walked + step) {
                val t = if (step == 0.0) 0.0 else (nextSample - walked) / step
                val h = grid.heightAt(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                if (h != null) {
                    distances += nextSample
                    heights += h
                }
                nextSample += SAMPLE_M
            }
            walked += step
        }
        grid.heightAt(line.last())?.let {
            if (distances.isEmpty() || distances.last() < walked) {
                distances += walked
                heights += it
            }
        }
        if (heights.size < 2) return null
        val smooth = median3(heights)
        var ascent = 0.0
        var descent = 0.0
        var reference = smooth.first()
        for (h in smooth) {
            val diff = h - reference
            if (diff >= CLIMB_THRESHOLD_M) {
                ascent += diff
                reference = h
            } else if (diff <= -CLIMB_THRESHOLD_M) {
                descent -= diff
                reference = h
            }
        }
        return ElevationProfile(
            distanceM = walked,
            ascentM = ascent,
            descentM = descent,
            minM = smooth.min(),
            maxM = smooth.max(),
            startM = smooth.first(),
            endM = smooth.last(),
            graph = downsample(distances, smooth),
        )
    }

    private fun median3(values: List<Double>): List<Double> = values.indices.map { i ->
        if (i == 0 || i == values.size - 1) values[i] else listOf(values[i - 1], values[i], values[i + 1]).sorted()[1]
    }

    /** At most GRAPH_POINTS points, keeping the highest and lowest of each bucket so peaks survive. */
    private fun downsample(distances: List<Double>, heights: List<Double>): List<Pair<Double, Double>> {
        if (heights.size <= GRAPH_POINTS) return distances.zip(heights)
        val buckets = GRAPH_POINTS / 2
        val out = ArrayList<Pair<Double, Double>>(GRAPH_POINTS + 2)
        out += distances.first() to heights.first()
        for (b in 0 until buckets) {
            val from = 1 + (heights.size - 2) * b / buckets
            val to = 1 + (heights.size - 2) * (b + 1) / buckets
            if (from >= to) continue
            val range = from until to
            val hi = range.maxBy { heights[it] }
            val lo = range.minBy { heights[it] }
            listOf(hi, lo).distinct().sorted().forEach { out += distances[it] to heights[it] }
        }
        out += distances.last() to heights.last()
        return out
    }
}
