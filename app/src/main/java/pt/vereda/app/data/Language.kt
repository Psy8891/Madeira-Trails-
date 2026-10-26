package pt.vereda.app.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import pt.vereda.core.model.AppLanguage
import java.util.Locale

/**
 * The app's own language (spec: Phase 0.9). Android 13+ keeps it as the per-app language, so it
 * also shows in the phone's App languages settings; older phones keep it in the app's settings.
 */
object Language {
    private const val PREFS = "settings"
    private const val KEY = "language"

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AppLanguage.fromTags(context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        } else {
            AppLanguage.fromTags(prefs(context).getString(KEY, null))
        }

    /** Switch and redraw the screen in the new language. */
    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android recreates the activity itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        } else {
            prefs(activity).edit().putString(KEY, language.tag).commit()
            activity.recreate()
        }
    }

    /** For Android 8–12: the activity's context in the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = AppLanguage.fromTags(prefs(base).getString(KEY, null))
        val locale = if (language == AppLanguage.SYSTEM) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Locale.forLanguageTag(language.tag)
        }
        Locale.setDefault(locale)
        if (language == AppLanguage.SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
