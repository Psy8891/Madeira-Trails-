package pt.vereda.core.model

import java.text.Normalizer

/**
 * Offline trail search: accent-insensitive ("caldeirao" finds "Caldeirão") and code-aware
 * ("pr6", "PR 6", "6" and "6.1" all work).
 *
 * Ranking: exact code match, then code prefix, then text match on name, ends, municipality and
 * aliases. Ties keep catalog order.
 */
object TrailSearch {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^a-z0-9.]+")
    private val codePrefixes = listOf("pspr", "pr", "gr")

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
                rank(trail, compactQuery, tokens)?.let { Ranked(it, index, trail) }
            }
            .sortedWith(compareBy<Ranked>({ it.rank }, { it.index }))
            .map { it.trail }
    }

    private data class Ranked(val rank: Int, val index: Int, val trail: Trail)

    private fun rank(trail: Trail, compactQuery: String, tokens: List<String>): Int? {
        val code = trail.code?.let { normalize(it).replace(" ", "") }
        if (code != null) {
            if (code == compactQuery || codeNumber(code) == compactQuery) return 0
            if (code.startsWith(compactQuery)) return 1
        }
        val text = buildList {
            add(trail.name)
            trail.from?.let(::add)
            trail.to?.let(::add)
            trail.municipality?.let(::add)
            addAll(trail.aliases)
        }.joinToString(" ") { normalize(it) }
        return if (tokens.all { text.contains(it) }) 2 else null
    }

    /** "pr6.1" → "6.1", "pspr2" → "2". */
    private fun codeNumber(compactCode: String): String {
        val prefix = codePrefixes.firstOrNull { compactCode.startsWith(it) } ?: return compactCode
        return compactCode.removePrefix(prefix)
    }
}
