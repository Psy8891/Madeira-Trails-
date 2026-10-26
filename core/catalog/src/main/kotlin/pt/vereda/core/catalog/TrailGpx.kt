package pt.vereda.core.catalog

import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.geo.GpxDocument
import pt.vereda.core.geo.GpxPoint
import pt.vereda.core.geo.GpxTrack
import pt.vereda.core.geo.GpxWriter

/** GPX 1.1 export of a trail or a planned route (spec: GPX import and export, Phase 0.7). */
object TrailGpx {

    const val OSM_ATTRIBUTION =
        "Line from OpenStreetMap. © OpenStreetMap contributors, ODbL 1.0: https://www.openstreetmap.org/copyright"

    fun write(name: String, lines: List<List<GeoPoint>>, description: String? = OSM_ATTRIBUTION): String =
        GpxWriter.write(
            GpxDocument(
                name = name,
                description = description,
                tracks = listOf(
                    GpxTrack(name, lines.filter { it.size >= 2 }.map { line -> line.map { GpxPoint(it.lat, it.lon, it.ele) } }),
                ),
            ),
        )

    /** A safe file name for sharing: "Vereda do Pico Grande.gpx" → "Vereda_do_Pico_Grande.gpx". */
    fun fileName(name: String): String {
        val cleaned = name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').take(60)
        return (cleaned.ifEmpty { "route" }) + ".gpx"
    }
}
