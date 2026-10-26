package pt.vereda.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.vereda.core.geo.ElevationGrid
import pt.vereda.core.geo.ElevationReader

/**
 * The elevation grid bundled in the APK (assets/elevation.bin, spec: Phase 1). Loaded once in the
 * background; null until then, or when the build has no grid yet.
 */
class Terrain(private val context: Context, scope: CoroutineScope) {
    private val _grid = MutableStateFlow<ElevationGrid?>(null)
    val grid: StateFlow<ElevationGrid?> = _grid.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _grid.value = runCatching {
                ElevationReader.read(context.assets.open(ASSET).use { it.readBytes() })
            }.onFailure { Log.w(TAG, "No elevation grid: ${it.message}") }.getOrNull()
        }
    }

    private companion object {
        const val ASSET = "elevation.bin"
        const val TAG = "Terrain"
    }
}
