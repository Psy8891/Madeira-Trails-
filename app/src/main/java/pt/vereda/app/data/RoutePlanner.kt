package pt.vereda.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.route.NetworkReader
import pt.vereda.core.route.RouteOptions
import pt.vereda.core.route.RouteOutcome
import pt.vereda.core.route.Router

/** The points of a route being planned (spec: Phase 0.7). */
data class PlanPoints(
    val start: GeoPoint? = null,
    val stops: List<GeoPoint> = emptyList(),
    val end: GeoPoint? = null,
) {
    val isEmpty: Boolean get() = start == null && stops.isEmpty() && end == null
    val all: List<GeoPoint> get() = listOfNotNull(start) + stops + listOfNotNull(end)
}

sealed interface PlanResult {
    data object Waiting : PlanResult // not enough points yet
    data object Working : PlanResult
    data class Done(val outcome: RouteOutcome) : PlanResult
    data class Failed(val message: String) : PlanResult // the network could not be read
}

/**
 * Plans routes on the walking network bundled in the APK (assets/network.bin), on the phone and
 * offline. Lives for the whole app so a plan survives switching tabs.
 */
class RoutePlanner(private val context: Context, private val scope: CoroutineScope) {

    private val _points = MutableStateFlow(PlanPoints())
    val points: StateFlow<PlanPoints> = _points.asStateFlow()

    private val _result = MutableStateFlow<PlanResult>(PlanResult.Waiting)
    val result: StateFlow<PlanResult> = _result.asStateFlow()

    private var router: Router? = null
    private val loadLock = Mutex()
    private var job: Job? = null

    fun setStart(p: GeoPoint) = update { it.copy(start = p) }
    fun setEnd(p: GeoPoint) = update { it.copy(end = p) }
    fun addStop(p: GeoPoint) = update { it.copy(stops = it.stops + p) }
    fun removeStop(index: Int) = update { it.copy(stops = it.stops.filterIndexed { i, _ -> i != index }) }
    fun reverse() = update { PlanPoints(start = it.end, stops = it.stops.reversed(), end = it.start) }
    fun clear() = update { PlanPoints() }

    private fun update(change: (PlanPoints) -> PlanPoints) {
        _points.value = change(_points.value)
        recompute()
    }

    private fun recompute() {
        job?.cancel()
        val points = _points.value
        if (points.start == null || points.end == null) {
            _result.value = PlanResult.Waiting
            return
        }
        _result.value = PlanResult.Working
        job = scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching { loadRouter().route(points.all, RouteOptions(avoidDifficult = true)) }
            }
            if (_points.value != points) return@launch // changed while planning
            _result.value = outcome.fold(
                onSuccess = { PlanResult.Done(it) },
                onFailure = { PlanResult.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    private suspend fun loadRouter(): Router = loadLock.withLock {
        router ?: withContext(Dispatchers.IO) {
            Router(NetworkReader.read(context.assets.open(NETWORK_ASSET).use { it.readBytes() }))
        }.also { router = it }
    }

    companion object {
        const val NETWORK_ASSET = "network.bin"
    }
}
