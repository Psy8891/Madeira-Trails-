package pt.vereda.core.catalog

import pt.vereda.core.geo.GeoPoint
import kotlin.math.pow
import kotlin.math.roundToLong

/** Google encoded polylines (lat, lon order). The catalog uses precision 5 (about 1 m). */
object Polyline {

    fun decode(encoded: String, precision: Int = 5): List<GeoPoint> {
        val factor = 10.0.pow(precision)
        val points = ArrayList<GeoPoint>(encoded.length / 4 + 1)
        val cursor = Cursor(encoded)
        var lat = 0L
        var lon = 0L
        while (cursor.hasMore()) {
            lat += cursor.readValue()
            require(cursor.hasMore()) { "Polyline ends after a latitude" }
            lon += cursor.readValue()
            points += GeoPoint(lat / factor, lon / factor)
        }
        return points
    }

    fun encode(points: List<GeoPoint>, precision: Int = 5): String {
        val factor = 10.0.pow(precision)
        val out = StringBuilder(points.size * 6)
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = (p.lat * factor).roundToLong()
            val lon = (p.lon * factor).roundToLong()
            writeValue(out, lat - prevLat)
            writeValue(out, lon - prevLon)
            prevLat = lat
            prevLon = lon
        }
        return out.toString()
    }

    private fun writeValue(out: StringBuilder, delta: Long) {
        var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (value >= 0x20) {
            out.append(((0x20L or (value and 0x1F)) + 63).toInt().toChar())
            value = value shr 5
        }
        out.append((value + 63).toInt().toChar())
    }

    private class Cursor(private val text: String) {
        private var index = 0

        fun hasMore() = index < text.length

        fun readValue(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(index < text.length) { "Polyline is cut short" }
                val chunk = text[index++].code - 63
                require(chunk in 0..63) { "Bad polyline character at ${index - 1}" }
                result = result or ((chunk and 0x1F).toLong() shl shift)
                shift += 5
                if (chunk < 0x20) break
                require(shift < 64) { "Polyline value too long" }
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
    }
}
