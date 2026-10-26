package pt.vereda.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.vereda.app.BuildConfig
import pt.vereda.core.catalog.WeatherWarning
import pt.vereda.core.catalog.WeatherWarnings
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

sealed interface WarningsState {
    data object Unknown : WarningsState
    data class Loaded(val warnings: List<WeatherWarning>, val fetchedAt: Instant) : WarningsState
}

/** IPMA weather warnings, fetched live when a card needs them (spec: Safety). */
class Weather(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<WarningsState>(WarningsState.Unknown)
    val state: StateFlow<WarningsState> = _state.asStateFlow()
    @Volatile private var fetching = false

    fun refreshIfOlderThan(maxAge: Duration = Duration.ofMinutes(30)) {
        val current = _state.value
        if (current is WarningsState.Loaded && Duration.between(current.fetchedAt, Instant.now()) < maxAge) return
        if (fetching) return
        fetching = true
        scope.launch(Dispatchers.IO) {
            try {
                val list = WeatherWarnings.parse(fetch(WeatherWarnings.URL))
                _state.value = WarningsState.Loaded(list, Instant.now())
            } catch (_: Exception) {
                // Leave the last good list (or Unknown); the card says warnings couldn't be checked.
            } finally {
                fetching = false
            }
        }
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Vereda/${BuildConfig.VERSION_NAME} (Android hiking app)")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
