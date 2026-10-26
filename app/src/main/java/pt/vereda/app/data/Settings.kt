package pt.vereda.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.vereda.core.catalog.DirectionsTarget
import pt.vereda.core.maps.TopoStyle

/** What the layers sheet shows (spec: Phase 1, Layers). */
data class MapLayerSettings(
    val hillshade: Boolean = true,
    /** 0–1, the hillshade exaggeration. */
    val hillshadeStrength: Float = TopoStyle.HILLSHADE_DEFAULT,
    val contours: Boolean = true,
    val communityTrails: Boolean = true,
    val places: Boolean = true,
)

/** Small choices kept on the phone (spec: Phases 0.8 and 1). */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _directions = MutableStateFlow(DirectionsTarget.fromKey(prefs.getString(KEY_DIRECTIONS, null)))
    val directions: StateFlow<DirectionsTarget> = _directions.asStateFlow()

    fun setDirections(target: DirectionsTarget) {
        _directions.value = target
        prefs.edit().putString(KEY_DIRECTIONS, target.key).apply()
    }

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    /** Download map packages only on Wi-Fi (or another unmetered network). */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        _wifiOnly.value = value
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    private val _layers = MutableStateFlow(
        MapLayerSettings(
            hillshade = prefs.getBoolean(KEY_HILLSHADE, true),
            hillshadeStrength = prefs.getFloat(KEY_HILLSHADE_STRENGTH, TopoStyle.HILLSHADE_DEFAULT),
            contours = prefs.getBoolean(KEY_CONTOURS, true),
            communityTrails = prefs.getBoolean(KEY_COMMUNITY, true),
            places = prefs.getBoolean(KEY_PLACES, true),
        ),
    )
    val layers: StateFlow<MapLayerSettings> = _layers.asStateFlow()

    fun setLayers(value: MapLayerSettings) {
        _layers.value = value
        prefs.edit()
            .putBoolean(KEY_HILLSHADE, value.hillshade)
            .putFloat(KEY_HILLSHADE_STRENGTH, value.hillshadeStrength.coerceIn(0f, 1f))
            .putBoolean(KEY_CONTOURS, value.contours)
            .putBoolean(KEY_COMMUNITY, value.communityTrails)
            .putBoolean(KEY_PLACES, value.places)
            .apply()
    }

    private val _disclaimerAccepted = MutableStateFlow(prefs.getBoolean(KEY_DISCLAIMER, false))
    /** The first-launch safety notice was accepted (spec: Safety). */
    val disclaimerAccepted: StateFlow<Boolean> = _disclaimerAccepted.asStateFlow()

    fun acceptDisclaimer() {
        _disclaimerAccepted.value = true
        prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply()
    }

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_v1"
        const val KEY_DIRECTIONS = "directions"
        const val KEY_WIFI_ONLY = "maps_wifi_only"
        const val KEY_HILLSHADE = "layer_hillshade"
        const val KEY_HILLSHADE_STRENGTH = "layer_hillshade_strength"
        const val KEY_CONTOURS = "layer_contours"
        const val KEY_COMMUNITY = "layer_community"
        const val KEY_PLACES = "layer_places"
    }
}
