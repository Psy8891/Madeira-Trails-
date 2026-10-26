package pt.vereda.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.vereda.core.catalog.DirectionsTarget

/** Small choices kept on the phone (spec: Phase 0.8). */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _directions = MutableStateFlow(DirectionsTarget.fromKey(prefs.getString(KEY_DIRECTIONS, null)))
    val directions: StateFlow<DirectionsTarget> = _directions.asStateFlow()

    fun setDirections(target: DirectionsTarget) {
        _directions.value = target
        prefs.edit().putString(KEY_DIRECTIONS, target.key).apply()
    }

    private companion object {
        const val KEY_DIRECTIONS = "directions"
    }
}
