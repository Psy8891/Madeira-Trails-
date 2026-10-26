package pt.vereda.core.route

import java.io.IOException

/** Reads network.bin (format in docs/SPEC.md, Phase 0.7). */
object NetworkReader {

    private const val SCALE = 100_000.0

    fun read(bytes: ByteArray): WalkingNetwork {
        val r = Cursor(bytes)
        if (bytes.size < 6 || String(bytes, 0, 4, Charsets.US_ASCII) != "VRN1") throw IOException("Not a Vereda network file")
        r.pos = 4
        val version = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)
        if (version != 1) throw IOException("Unsupported network version $version")
        r.pos = 6

        val names = List(r.varint()) {
            val length = r.varint()
            if (r.pos + length > bytes.size) throw IOException("Network file ends early")
            val text = String(bytes, r.pos, length, Charsets.UTF_8)
            r.pos += length
            text
        }

        val nodeCount = r.varint()
        val nodeLat = DoubleArray(nodeCount)
        val nodeLon = DoubleArray(nodeCount)
        val nodeLatInt = IntArray(nodeCount)
        val nodeLonInt = IntArray(nodeCount)
        var lat = 0
        var lon = 0
        for (i in 0 until nodeCount) {
            lat += r.zigzag()
            lon += r.zigzag()
            nodeLatInt[i] = lat
            nodeLonInt[i] = lon
            nodeLat[i] = lat / SCALE
            nodeLon[i] = lon / SCALE
        }

        val edgeCount = r.varint()
        val from = IntArray(edgeCount)
        val to = IntArray(edgeCount)
        val length = FloatArray(edgeCount)
        val kind = ByteArray(edgeCount)
        val flags = ByteArray(edgeCount)
        val name = IntArray(edgeCount)
        val official = IntArray(edgeCount)
        val innerStart = IntArray(edgeCount + 1)
        var innerLat = DoubleArray(edgeCount)
        var innerLon = DoubleArray(edgeCount)
        var inner = 0
        for (e in 0 until edgeCount) {
            from[e] = r.varint()
            to[e] = r.varint()
            if (from[e] !in 0 until nodeCount || to[e] !in 0 until nodeCount) throw IOException("Edge $e has a bad node")
            length[e] = r.varint() / 10f
            if (r.pos + 2 > bytes.size) throw IOException("Network file ends early")
            kind[e] = bytes[r.pos++]
            flags[e] = bytes[r.pos++]
            name[e] = r.varint() - 1
            official[e] = r.varint() - 1
            innerStart[e] = inner
            val count = r.varint()
            if (inner + count > innerLat.size) {
                val size = maxOf(innerLat.size * 2, inner + count)
                innerLat = innerLat.copyOf(size)
                innerLon = innerLon.copyOf(size)
            }
            var pLat = nodeLatInt[from[e]]
            var pLon = nodeLonInt[from[e]]
            repeat(count) {
                pLat += r.zigzag()
                pLon += r.zigzag()
                innerLat[inner] = pLat / SCALE
                innerLon[inner] = pLon / SCALE
                inner++
            }
        }
        innerStart[edgeCount] = inner
        return WalkingNetwork(
            names, nodeLat, nodeLon, from, to, length, kind, flags, name, official,
            innerStart, innerLat.copyOf(inner), innerLon.copyOf(inner),
        )
    }

    private class Cursor(private val bytes: ByteArray) {
        var pos = 0

        fun varint(): Int {
            var shift = 0
            var result = 0L
            while (true) {
                if (pos >= bytes.size) throw IOException("Network file ends early")
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) break
                shift += 7
                if (shift > 35) throw IOException("Bad varint")
            }
            return result.toInt()
        }

        fun zigzag(): Int {
            val v = varint()
            return (v ushr 1) xor -(v and 1)
        }
    }
}
