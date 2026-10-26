package pt.vereda.core.catalog

import kotlinx.serialization.json.JsonPrimitive
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.model.StatusState
import pt.vereda.core.model.TrailSource

/** How a trail line is drawn (colours from docs/design/README.md). */
enum class LineStyle(val color: String, val dashed: Boolean) {
    OPEN("#2E9E5B", false),
    PARTIAL("#E3A008", false),
    CLOSED("#C62828", true),
    UNKNOWN("#8A8A8A", false),
    COMMUNITY("#5B6B9A", true),
    IMPORT("#E4572E", false),
    ;

    companion object {
        /** The highlight for the trail the user picked ("Active route" token). */
        const val SELECTED_COLOR = "#D81B60"

        fun of(entry: TrailEntry, state: StatusState): LineStyle = when (entry.trail.source) {
            TrailSource.COMMUNITY -> COMMUNITY
            TrailSource.IMPORT -> IMPORT
            TrailSource.OFFICIAL -> when (state) {
                StatusState.OPEN -> OPEN
                StatusState.PARTIAL -> PARTIAL
                StatusState.CLOSED -> CLOSED
                StatusState.UNKNOWN -> UNKNOWN
            }
        }
    }
}

/**
 * GeoJSON for the map. Built by hand with a StringBuilder because the full catalog has
 * hundreds of thousands of coordinates; 5 decimals (about 1 m) is the catalog's precision.
 * Feature properties: id, name, color, dashed.
 */
object TrailGeoJson {

    fun lines(entries: List<TrailEntry>, styleOf: (TrailEntry) -> LineStyle): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true
        for (entry in entries) {
            if (!entry.hasLine) continue
            if (!first) append(',')
            first = false
            val style = styleOf(entry)
            append("{\"type\":\"Feature\",\"properties\":{\"id\":")
            append(JsonPrimitive(entry.id))
            append(",\"name\":")
            append(JsonPrimitive(entry.trail.name))
            append(",\"color\":\"").append(style.color).append('"')
            append(",\"dashed\":").append(style.dashed)
            append("},\"geometry\":{\"type\":\"MultiLineString\",\"coordinates\":[")
            entry.lines.filter { it.size >= 2 }.forEachIndexed { i, line ->
                if (i > 0) append(',')
                append('[')
                line.forEachIndexed { j, p ->
                    if (j > 0) append(',')
                    appendPosition(p)
                }
                append(']')
            }
            append("]}}")
        }
        append("]}")
    }

    /**
     * Points of interest. Properties: id (index into [places]), kind (catalog key), label (name,
     * with the height on a second line for peaks), minZoom, and rank (lower is drawn first when
     * labels collide: peaks by height, then named places).
     */
    fun places(places: List<Place>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        places.forEachIndexed { index, place ->
            if (index > 0) append(',')
            append("{\"type\":\"Feature\",\"properties\":{\"id\":").append(index)
            append(",\"kind\":\"").append(place.kind.key).append('"')
            append(",\"label\":").append(JsonPrimitive(label(place)))
            append(",\"minZoom\":").append(place.kind.minZoom)
            append(",\"rank\":").append(rank(place))
            append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
            appendPosition(place.point)
            append("}}")
        }
        append("]}")
    }

    internal fun label(place: Place): String {
        val name = place.displayName ?: return ""
        val ele = place.eleM
        return if (place.kind == PlaceKind.PEAK && ele != null) "$name\n$ele m" else name
    }

    private fun rank(place: Place): Int = when {
        place.kind == PlaceKind.PEAK -> 3000 - (place.eleM ?: 0).coerceIn(0, 2999)
        place.name != null -> 5000
        else -> 9000
    }

    /**
     * Start and end markers and the walk from the road for a picked trail or place.
     * Properties: role = start | end | approach | road | parking | place.
     */
    fun markers(start: GeoPoint?, end: GeoPoint?, access: RoadAccess?, place: GeoPoint? = null): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true
        fun feature(role: String, geometry: StringBuilder.() -> Unit) {
            if (!first) append(',')
            first = false
            append("{\"type\":\"Feature\",\"properties\":{\"role\":\"").append(role).append("\"},\"geometry\":")
            geometry()
            append('}')
        }
        fun pointFeature(role: String, p: GeoPoint) = feature(role) {
            append("{\"type\":\"Point\",\"coordinates\":")
            appendPosition(p)
            append('}')
        }
        if (access != null && access.needsWalk && access.line.size >= 2) {
            feature("approach") {
                append("{\"type\":\"LineString\",\"coordinates\":[")
                access.line.forEachIndexed { i, p ->
                    if (i > 0) append(',')
                    appendPosition(p)
                }
                append("]}")
            }
        }
        if (access != null && access.needsWalk) access.roadPoint?.let { pointFeature("road", it) }
        access?.parking?.let { pointFeature("parking", it) }
        end?.let { pointFeature("end", it) }
        start?.let { pointFeature("start", it) }
        place?.let { pointFeature("place", it) }
        append("]}")
    }

    fun point(point: GeoPoint, name: String): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"name\":")
        append(JsonPrimitive(name))
        append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
        appendPosition(point)
        append("}}]}")
    }

    private fun StringBuilder.appendPosition(p: GeoPoint) {
        append('[')
        appendFixed5(p.lon)
        append(',')
        appendFixed5(p.lat)
        append(']')
    }
}

/** Same as "%.5f" in Locale.ROOT, without String.format's cost. */
internal fun StringBuilder.appendFixed5(value: Double) {
    val scaled = Math.round(value * 100_000.0)
    val abs = kotlin.math.abs(scaled)
    if (scaled < 0) append('-')
    append(abs / 100_000).append('.')
    val fraction = (abs % 100_000).toString()
    repeat(5 - fraction.length) { append('0') }
    append(fraction)
}
