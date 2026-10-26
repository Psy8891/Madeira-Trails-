package pt.vereda.core.maps

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
     * The style JSON with the three package files, given as absolute paths on the phone.
     * MapLibre reads them as `pmtiles://file:///…`.
     */
    fun json(basePath: String, contoursPath: String, demPath: String): String {
        fun url(path: String): String {
            require(path.startsWith("/")) { "Expected an absolute path: $path" }
            return "file://" + path.replace("\\", "/").replace("\"", "%22")
        }
        return template
            .replace("{{BASE}}", url(basePath))
            .replace("{{CONTOURS}}", url(contoursPath))
            .replace("{{DEM}}", url(demPath))
    }
}
