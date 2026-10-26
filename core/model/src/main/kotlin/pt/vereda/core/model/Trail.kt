package pt.vereda.core.model

/** Where a trail's data comes from. Shown on every trail so users know how much to trust it. */
enum class TrailSource {
    /** Classified by IFCN (PR / GR trails): has official status, fee and booking. */
    OFFICIAL,

    /** Mapped by the OpenStreetMap community; not officially maintained. */
    COMMUNITY,

    /** A GPX/KML/GeoJSON file the user imported on this phone. */
    IMPORT,
}

enum class Island { MADEIRA, PORTO_SANTO }

/**
 * A trail in the catalog. Geometry and computed stats arrive later from the catalog pipeline
 * (spec: Data model); this holds what is known without it.
 */
data class Trail(
    val id: String,
    val source: TrailSource,
    val code: String?,
    val name: String,
    val from: String?,
    val to: String?,
    val municipality: String?,
    val island: Island,
    val aliases: List<String> = emptyList(),
) {
    /** "Rabaçal → 25 Fontes", or null when either end is unknown. */
    val fromTo: String? get() = if (from != null && to != null) "$from → $to" else null

    companion object {
        /** "PR 6.1" → "pr-6.1", "PS PR 2" → "ps-pr-2". */
        fun idForCode(code: String): String = code.trim().lowercase().replace(Regex("\\s+"), "-")
    }
}
