package pt.vereda.core.maps

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Vereda Topo style (spec: *Map style*) over the offline package: OpenMapTiles base,
 * contours and Terrarium terrain from local PMTiles files, fonts from the APK.
 */
object TopoStyle {
    const val FONT_REGULAR = "NotoSans-Regular"
    const val FONT_BOLD = "NotoSans-Bold"
    const val FONT_ITALIC = "NotoSans-Italic"

    const val HILLSHADE_LAYER = "hillshade"
    val CONTOUR_LAYERS = listOf("contour-minor", "contour-index", "contour-label")

    /** Default hillshade strength (spec: exaggeration 0.4); the layers sheet scales it 0–1. */
    const val HILLSHADE_DEFAULT = 0.4f

    private val template: String by lazy {
        TopoStyle::class.java.getResourceAsStream("/style/vereda-topo.json")!!.bufferedReader().use { it.readText() }
    }

    /**
     * The style JSON with the package files, given as absolute paths on the phone. MapLibre reads
     * them as `pmtiles://file:///…`. Without contours or terrain (the small overview map that
     * ships in the app), their sources and layers are left out.
     */
    fun json(basePath: String, contoursPath: String?, demPath: String?): String {
        fun url(path: String): String {
            require(path.startsWith("/")) { "Expected an absolute path: $path" }
            return "file://" + path.replace("\\", "/").replace("\"", "%22")
        }
        val filled = template
            .replace("{{BASE}}", url(basePath))
            .replace("{{CONTOURS}}", contoursPath?.let(::url) ?: "missing")
            .replace("{{DEM}}", demPath?.let(::url) ?: "missing")
        if (contoursPath != null && demPath != null) return filled
        val drop = buildSet {
            if (contoursPath == null) add("contours")
            if (demPath == null) add("dem")
        }
        val style = Json.parseToJsonElement(filled).jsonObject
        val sources = JsonObject(style.getValue("sources").jsonObject.filterKeys { it !in drop })
        val layers = JsonArray(
            (style.getValue("layers") as JsonArray).filter { layer ->
                layer.jsonObject["source"]?.jsonPrimitive?.content !in drop
            },
        )
        return JsonObject(style + mapOf("sources" to sources, "layers" to layers)).toString()
    }
}
