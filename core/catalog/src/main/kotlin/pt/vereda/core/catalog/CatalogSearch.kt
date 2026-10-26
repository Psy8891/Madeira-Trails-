package pt.vereda.core.catalog

import pt.vereda.core.model.TrailSearch

data class SearchResult(val trails: List<TrailEntry>, val places: List<Place>)

/** Offline search over trails (official, community, imports) and named peaks. */
object CatalogSearch {

    /** An empty query lists every trail and no places (hundreds of peaks would bury the trails). */
    fun search(trails: List<TrailEntry>, places: List<Place>, query: String): SearchResult {
        val byId = trails.associateBy { it.id }
        val foundTrails = TrailSearch.search(trails.map { it.trail }, query).mapNotNull { byId[it.id] }
        if (TrailSearch.normalize(query).isEmpty()) return SearchResult(foundTrails, emptyList())

        val foundPlaces = places
            .mapNotNull { place -> TrailSearch.matchText(listOf(place.name), query)?.let { it to place } }
            .sortedWith(
                compareBy<Pair<Pair<Int, Int>, Place>>(
                    { it.first.first },
                    { -it.first.second },
                    { -(it.second.eleM ?: 0) },
                ),
            )
            .map { it.second }
        return SearchResult(foundTrails, foundPlaces)
    }
}
