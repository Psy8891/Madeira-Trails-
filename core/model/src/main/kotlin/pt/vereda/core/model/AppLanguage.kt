package pt.vereda.core.model

/** The app's language choice in Me → Language (spec: Phase 0.9). No German or French in v1. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    PORTUGUESE("pt");

    companion object {
        /**
         * The choice for stored language tags ("pt-PT", "en,pt"): the first tag's language, or
         * [SYSTEM] when empty or not one the app is translated into.
         */
        fun fromTags(tags: String?): AppLanguage {
            val first = tags?.split(',')?.firstOrNull()?.trim().orEmpty()
            if (first.isEmpty()) return SYSTEM
            val language = first.split('-', '_').first().lowercase()
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }
    }
}
