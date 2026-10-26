package pt.vereda.core.model

import java.text.Normalizer

/**
 * Offline trail search: accent-insensitive ("caldeirao" finds "Caldeirão") and code-aware
 * ("pr6", "PR 6", "6" and "6.1" all work).
 *
 * Ranking: exact code match, then code prefix, then every word found in name, ends, municipality
 * and aliases, then (for queries of three or more real words) partial matches with at least two
 * words found, most words first. So "boca da corrida pico grande" still finds "Vereda do Pico
 * Grande". Ties keep catalog order.
 */
object TrailSearch {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^a-z0-9.]+")
    private val codePrefixes = listOf("pspr", "pr", "gr")
    private val stopWords = setOf("a", "o", "e", "da", "do", "de", "das", "dos", "the", "to", "of")

    /** Lower-case, strip accents, turn punctuation into single spaces. Dots are kept for codes. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase()
            .replace(separators, " ")
            .trim()

    fun search(trails: List<Trail>, query: String): List<Trail> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return trails
        val compactQuery = normalized.replace(" ", "")
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        return trails
            .mapIndexedNotNull { index, trail ->
                rank(trail, compactQuery, tokens)?.let { (rank, found) -> Ranked(rank, -found, index, trail) }
            }
            .sortedWith(compareBy<Ranked>({ it.rank }, { it.foundDesc }, { it.index }))
            .map { it.trail }
    }

    /**
     * Text match used for anything with a name (trails, peaks, places): the rank (2 = every word,
     * 3 = partial) and the number of real words found, or null for no match.
     */
    fun matchText(texts: List<String>, query: String): Pair<Int, Int>? {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return 2 to 0
        return matchTokens(texts.joinToString(" ") { normalize(it) }, tokens)
    }

    private data class Ranked(val rank: Int, val foundDesc: Int, val index: Int, val trail: Trail)

    private fun rank(trail: Trail, compactQuery: String, tokens: List<String>): Pair<Int, Int>? {
        val code = trail.code?.let { normalize(it).replace(" ", "") }
        if (code != null) {
            if (code == compactQuery || codeNumber(code) == compactQuery) return 0 to 0
            if (code.startsWith(compactQuery)) return 1 to 0
        }
        val text = buildList {
            add(trail.name)
            trail.from?.let(::add)
            trail.to?.let(::add)
            trail.municipality?.let(::add)
            addAll(trail.aliases)
        }.joinToString(" ") { normalize(it) }
        return matchTokens(text, tokens)
    }

    private fun matchTokens(text: String, tokens: List<String>): Pair<Int, Int>? {
        val words = tokens.filter { it !in stopWords }
        if (tokens.all { text.contains(it) }) return 2 to words.size
        if (words.size < 3) return null
        val wordStarts = " $text"
        val found = words.count { wordStarts.contains(" $it") }
        return if (found >= 2 && found * 2 >= words.size) 3 to found else null
    }

    /** "pr6.1" → "6.1", "pspr2" → "2". */
    private fun codeNumber(compactCode: String): String {
        val prefix = codePrefixes.firstOrNull { compactCode.startsWith(it) } ?: return compactCode
        return compactCode.removePrefix(prefix)
    }
}
