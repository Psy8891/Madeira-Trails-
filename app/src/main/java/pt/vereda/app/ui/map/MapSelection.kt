package pt.vereda.app.ui.map

import pt.vereda.core.catalog.Place

/** What the map shows in its card: a trail (by id) or a peak. */
sealed interface MapTarget {
    data class TrailTarget(val id: String) : MapTarget
    data class PlaceTarget(val place: Place) : MapTarget
}

/**
 * The current selection. [moveCamera] is true when it came from a list (zoom to it) and false
 * when the user tapped the line on the map. [nonce] makes picking the same trail again re-zoom.
 */
data class MapSelection(
    val target: MapTarget,
    val moveCamera: Boolean,
    val nonce: Long = System.nanoTime(),
)
