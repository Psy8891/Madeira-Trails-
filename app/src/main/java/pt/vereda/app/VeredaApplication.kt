package pt.vereda.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.maplibre.android.MapLibre
import pt.vereda.app.data.MapPackages
import pt.vereda.app.data.NetworkMonitor
import pt.vereda.app.data.RoutePlanner
import pt.vereda.app.data.Settings
import pt.vereda.app.data.Terrain
import pt.vereda.app.data.Weather
import pt.vereda.app.data.TrailRepository
import pt.vereda.app.location.LocationTracker
import pt.vereda.app.navigation.NavigationSession

class VeredaApplication : Application() {

    /** App-wide scope for loading data; lives as long as the process. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: TrailRepository
        private set

    /** The route being planned on the map (Phase 0.7). */
    lateinit var planner: RoutePlanner
        private set

    /** The phone's position; started and stopped by the map screen. */
    lateinit var location: LocationTracker
        private set

    /** The offline map package (Phase 1). */
    lateinit var maps: MapPackages
        private set

    /** Online or not, to choose the online or the bundled overview map. */
    lateinit var network: NetworkMonitor
        private set

    /** The walk being followed (Phase 1.5). */
    val navigation = NavigationSession()

    /** IPMA weather warnings (Phase 4). */
    lateinit var weather: Weather
        private set

    /** Heights for climb and the height graph (bundled elevation grid). */
    lateinit var terrain: Terrain
        private set

    /** Small choices kept on the phone (where Directions opens). */
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialised once before any MapView is created.
        MapLibre.getInstance(this)
        repository = TrailRepository(this, appScope).also { it.start() }
        location = LocationTracker(this)
        planner = RoutePlanner(this, appScope)
        settings = Settings(this)
        terrain = Terrain(this, appScope)
        maps = MapPackages(this, appScope, settings).also { it.start() }
        weather = Weather(appScope)
        network = NetworkMonitor(this)
    }
}
