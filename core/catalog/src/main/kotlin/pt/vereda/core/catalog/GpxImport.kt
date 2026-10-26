package pt.vereda.core.catalog

import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.geo.GpxDocument
import pt.vereda.core.model.Island
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSource
import kotlin.math.roundToInt

/**
 * Turns a GPX file the user imported into a trail. Imports stay on the phone (spec: GPX rule)
 * and are never sent anywhere.
 */
object GpxImport {

    const val ID_PREFIX = "imp-"
    private const val PORTO_SANTO_MIN_LAT = 32.95

    /** The trail for [document], or null when the file holds no track or route with two points. */
    fun toEntry(id: String, document: GpxDocument, fallbackName: String): TrailEntry? {
        val segments = document.tracks.flatMap { it.segments } + document.routes.flatMap { it.segments }
        val lines = segments
            .map { segment -> segment.map { GeoPoint(it.lat, it.lon, it.ele) } }
            .filter { it.size >= 2 }
        if (lines.isEmpty()) return null

        val name = sequenceOf(document.name, document.tracks.firstOrNull()?.name, document.routes.firstOrNull()?.name)
            .mapNotNull { it?.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: fallbackName
        val center = Bounds.of(lines.flatten())!!.center
        return TrailEntry(
            trail = Trail(
                id = id,
                source = TrailSource.IMPORT,
                code = null,
                name = name,
                from = null,
                to = null,
                municipality = null,
                island = if (center.lat > PORTO_SANTO_MIN_LAT) Island.PORTO_SANTO else Island.MADEIRA,
            ),
            lengthM = lines.sumOf { Geo.lengthM(it) }.roundToInt(),
            sacScale = null,
            lines = lines,
        )
    }

    /** File name without the extension, as a fallback trail name ("pico-grande.gpx" → "pico-grande"). */
    fun nameFromFile(fileName: String?): String =
        fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() } ?: "Imported track"
}
