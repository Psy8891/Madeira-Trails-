package pt.vereda.app.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.route.NavRoute
import pt.vereda.core.route.NavState

/** A walk being followed (spec: Phase 1.5). */
data class ActiveNavigation(
    val route: NavRoute,
    val state: NavState,
    /** Accepted positions so far, for the walked part of the line. */
    val walked: List<GeoPoint>,
    val startedAtMs: Long,
)

/** Shared between the navigation service (which writes) and the map (which reads). */
class NavigationSession {
    private val _active = MutableStateFlow<ActiveNavigation?>(null)
    val active: StateFlow<ActiveNavigation?> = _active.asStateFlow()

    /** The route the service picks up when it starts. */
    @Volatile internal var pending: NavRoute? = null

    fun start(context: Context, route: NavRoute) {
        pending = route
        _active.value = ActiveNavigation(route, initialState(route), emptyList(), System.currentTimeMillis())
        ContextCompat.startForegroundService(context, Intent(context, NavigationService::class.java))
    }

    fun stop(context: Context) {
        val finished = _active.value?.state?.arrived == true
        _active.value = null
        if (!finished) {
            runCatching { context.startService(Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_STOP)) }
        }
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(NavigationService.NOTIFICATION_ID)
    }

    internal fun update(value: ActiveNavigation?) {
        _active.value = value
    }

    private fun initialState(route: NavRoute) = pt.vereda.core.route.Navigator(route).state
}
