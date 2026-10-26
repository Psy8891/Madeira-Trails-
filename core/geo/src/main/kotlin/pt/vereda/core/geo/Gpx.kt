package pt.vereda.core.geo

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: Instant? = null,
    val name: String? = null,
)

/** A track (`trk`, with segments) or a route (`rte`, one segment). */
data class GpxTrack(val name: String?, val segments: List<List<GpxPoint>>)

data class GpxDocument(
    val name: String? = null,
    val description: String? = null,
    val waypoints: List<GpxPoint> = emptyList(),
    val routes: List<GpxTrack> = emptyList(),
    val tracks: List<GpxTrack> = emptyList(),
)

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads GPX 1.0 and 1.1 (with or without namespace). DOCTYPEs are refused to block XXE. */
object GpxParser {

    fun parse(xml: String): GpxDocument = parse(xml.byteInputStream())

    fun parse(input: InputStream): GpxDocument {
        val root = try {
            newFactory().newDocumentBuilder().parse(input).documentElement
        } catch (e: Exception) {
            throw GpxParseException("Not a readable GPX file", e)
        }
        if (root.localNameOrName() != "gpx") throw GpxParseException("Root element is not <gpx>")

        val metadata = root.childElements("metadata").firstOrNull()
        return GpxDocument(
            name = metadata?.childText("name") ?: root.childText("name"),
            description = metadata?.childText("desc") ?: root.childText("desc"),
            waypoints = root.childElements("wpt").map { it.toPoint() },
            routes = root.childElements("rte").map { rte ->
                GpxTrack(rte.childText("name"), listOf(rte.childElements("rtept").map { it.toPoint() }))
            },
            tracks = root.childElements("trk").map { trk ->
                GpxTrack(
                    trk.childText("name"),
                    trk.childElements("trkseg").map { seg -> seg.childElements("trkpt").map { it.toPoint() } },
                )
            },
        )
    }

    private fun newFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // Not every platform parser supports every feature (Android's does not); ignore those.
        trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        trySetFeature("http://xml.org/sax/features/external-general-entities", false)
        trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun Element.toPoint(): GpxPoint {
        val lat = getAttribute("lat").toDoubleOrNull()
        val lon = getAttribute("lon").toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            throw GpxParseException("Point with missing or invalid lat/lon")
        }
        return GpxPoint(
            lat = lat,
            lon = lon,
            ele = childText("ele")?.toDoubleOrNull(),
            time = childText("time")?.let(::parseTime),
            name = childText("name"),
        )
    }

    private fun parseTime(text: String): Instant? =
        runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

    private fun Node.localNameOrName(): String = localName ?: nodeName

    private fun Element.childElements(name: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.localNameOrName() == name }
    }

    private fun Element.childText(name: String): String? =
        childElements(name).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}

/** Writes GPX 1.1. */
object GpxWriter {

    fun write(document: GpxDocument, creator: String = "Vereda"): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"").append(escape(creator))
            .append("\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        if (document.name != null || document.description != null) {
            append("  <metadata>\n")
            document.name?.let { append("    <name>").append(escape(it)).append("</name>\n") }
            document.description?.let { append("    <desc>").append(escape(it)).append("</desc>\n") }
            append("  </metadata>\n")
        }
        document.waypoints.forEach { appendPoint("wpt", it, "  ") }
        document.routes.forEach { route ->
            append("  <rte>\n")
            route.name?.let { append("    <name>").append(escape(it)).append("</name>\n") }
            route.segments.flatten().forEach { appendPoint("rtept", it, "    ") }
            append("  </rte>\n")
        }
        document.tracks.forEach { track ->
            append("  <trk>\n")
            track.name?.let { append("    <name>").append(escape(it)).append("</name>\n") }
            track.segments.forEach { segment ->
                append("    <trkseg>\n")
                segment.forEach { appendPoint("trkpt", it, "      ") }
                append("    </trkseg>\n")
            }
            append("  </trk>\n")
        }
        append("</gpx>\n")
    }

    private fun StringBuilder.appendPoint(tag: String, p: GpxPoint, indent: String) {
        append(indent).append('<').append(tag)
            .append(" lat=\"").append(String.format(Locale.ROOT, "%.7f", p.lat))
            .append("\" lon=\"").append(String.format(Locale.ROOT, "%.7f", p.lon)).append('"')
        if (p.ele == null && p.time == null && p.name == null) {
            append("/>\n")
            return
        }
        append(">")
        p.ele?.let { append("<ele>").append(String.format(Locale.ROOT, "%.1f", it)).append("</ele>") }
        p.time?.let { append("<time>").append(it.toString()).append("</time>") }
        p.name?.let { append("<name>").append(escape(it)).append("</name>") }
        append("</").append(tag).append(">\n")
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
