package pt.vereda.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {

    @Test
    fun `each choice round-trips through its tag`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTags(it.tag)) }
    }

    @Test
    fun `regional and listed tags resolve to their language`() {
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTags("pt-PT"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromTags("pt_BR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-GB,pt"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags(" EN "))
    }

    @Test
    fun `empty or untranslated tags mean the phone setting`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("de-DE"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("fr"))
    }
}
