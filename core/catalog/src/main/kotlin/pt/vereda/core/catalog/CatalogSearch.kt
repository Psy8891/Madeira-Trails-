package pt.vereda.core.catalog

import pt.vereda.core.model.TrailSearch

data class SearchResult(val trails: List<TrailEntry>, val places: List<Place>)

/** Offline search over trails (official, community, imports), peaks and other named places. */
object CatalogSearch {

    /** Enough to scroll through; a one-word query like "levada" would otherwise list hundreds. */
    const val MAX_PLACES = 40

    /** An empty query lists every trail and no places (hundreds of peaks would bury the trails). */
    fun search(trails: List<TrailEntry>, places: List<Place>, query: String): SearchResult {
        val byId = trails.associateBy { it.id }
        val foundTrails = TrailSearch.search(trails.map { it.trail }, query).mapNotNull { byId[it.id] }
        if (TrailSearch.normalize(query).isEmpty()) return SearchResult(foundTrails, emptyList())

        val foundPlaces = places
            .asSequence()
            .filter { it.kind.searchable && it.name != null }
            .mapNotNull { place -> TrailSearch.matchText(place.searchNames, query)?.let { it to place } }
            .sortedWith(
                compareBy<Pair<Pair<Int, Int>, Place>>(
                    { it.first.first },
                    { -it.first.second },
                    { if (it.second.kind == PlaceKind.PEAK) 0 else 1 },
                    { -(it.second.eleM ?: 0) },
                ),
            )
            .map { it.second }
            .take(MAX_PLACES)
            .toList()
        return SearchResult(foundTrails, foundPlaces)
    }
}
