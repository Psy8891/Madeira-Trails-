package pt.vereda.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.maplibre.android.MapLibre
import pt.vereda.app.data.TrailRepository
import pt.vereda.app.location.LocationTracker

class VeredaApplication : Application() {

    /** App-wide scope for loading data; lives as long as the process. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: TrailRepository
        private set

    /** The phone's position; started and stopped by the map screen. */
    lateinit var location: LocationTracker
        private set

    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialised once before any MapView is created.
        MapLibre.getInstance(this)
        repository = TrailRepository(this, appScope).also { it.start() }
        location = LocationTracker(this)
    }
}
