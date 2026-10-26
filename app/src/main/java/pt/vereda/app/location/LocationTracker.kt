package pt.vereda.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.vereda.core.geo.FixChoice

/**
 * The phone's position while the map is on screen (spec: Phase 0.6). Uses GPS for accuracy on
 * the trail and the network provider for a quick first fix; never runs in the background.
 */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private var running = false

    private val listener = LocationListenerCompat { fix -> offer(fix) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationOn(): Boolean = LocationManagerCompat.isLocationEnabled(manager)

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun start() {
        if (running || !hasPermission()) return
        running = true
        val request = LocationRequestCompat.Builder(INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!manager.allProviders.contains(provider)) continue
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let(::offer)
            runCatching {
                LocationManagerCompat.requestLocationUpdates(manager, provider, request, listener, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        LocationManagerCompat.removeUpdates(manager, listener)
    }

    private fun offer(fix: Location) {
        val current = _location.value
        if (FixChoice.better(current?.toFix(), fix.toFix())) _location.value = fix
    }

    private fun Location.toFix() = FixChoice.Fix(
        timeMs = time,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
        fromGps = provider == LocationManager.GPS_PROVIDER,
    )

    private companion object {
        const val INTERVAL_MS = 2_000L
        const val MIN_DISTANCE_M = 2f
    }
}
