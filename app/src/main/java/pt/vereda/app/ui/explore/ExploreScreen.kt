package pt.vereda.app.ui.explore

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.vereda.app.R
import pt.vereda.app.VeredaApplication
import pt.vereda.app.data.ImportResult
import pt.vereda.app.data.StatusLoad
import pt.vereda.app.data.TrailRepository
import pt.vereda.app.ui.components.TrailBadges
import pt.vereda.app.ui.components.checkedAgo
import pt.vereda.app.ui.components.formatElevation
import pt.vereda.app.ui.components.formatLength
import pt.vereda.app.ui.map.MapTarget
import pt.vereda.core.catalog.CatalogSearch
import pt.vereda.core.catalog.Place
import pt.vereda.core.catalog.TrailEntry
import pt.vereda.core.model.TrailSource
import java.time.Instant

private enum class SourceFilter(val label: Int, val source: TrailSource?) {
    ALL(R.string.filter_all, null),
    OFFICIAL(R.string.filter_official, TrailSource.OFFICIAL),
    COMMUNITY(R.string.filter_community, TrailSource.COMMUNITY),
    IMPORTS(R.string.filter_imports, TrailSource.IMPORT),
}

@Composable
fun ExploreScreen(onOpen: (MapTarget) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = (context.applicationContext as VeredaApplication).repository
    val catalog by repository.catalog.collectAsStateWithLifecycle()
    val imports by repository.imports.collectAsStateWithLifecycle()
    val statusLoad by repository.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SourceFilter.ALL) }

    val allTrails = remember(catalog, imports) { imports + catalog?.trails.orEmpty() }
    val places = catalog?.places.orEmpty()
    val result = remember(allTrails, places, query) { CatalogSearch.search(allTrails, places, query) }
    val trails = remember(result, filter) { result.trails.filter { filter.source == null || it.trail.source == filter.source } }
    val shownPlaces = if (filter == SourceFilter.ALL) result.places.take(MAX_PLACES) else emptyList()
    val now = remember(statusLoad) { TrailRepository.now() }

    val messageImported = stringResource(R.string.import_done)
    val messageNoTrack = stringResource(R.string.import_no_track)
    val messageNotGpx = stringResource(R.string.import_not_gpx)
    val messageTooBig = stringResource(R.string.import_too_big)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = when (val r = repository.importGpx(uri)) {
                is ImportResult.Imported -> {
                    onOpen(MapTarget.TrailTarget(r.entry.id))
                    messageImported
                }
                ImportResult.NoTrack -> messageNoTrack
                ImportResult.NotGpx -> messageNotGpx
                ImportResult.TooBig -> messageTooBig
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.explore_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.import_gpx))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.explore_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label)) })
            }
        }
        StatusLine(statusLoad, now, onRetry = { scope.launch { repository.refreshStatus() } })

        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "count") {
                Text(
                    text = if (catalog == null) stringResource(R.string.explore_loading)
                    else stringResource(R.string.explore_count, trails.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (shownPlaces.isNotEmpty()) {
                item(key = "peaks-title") { SectionTitle(stringResource(R.string.explore_peaks)) }
                items(shownPlaces) { place ->
                    PlaceCard(place, onClick = { onOpen(MapTarget.PlaceTarget(place)) })
                }
                item(key = "trails-title") { SectionTitle(stringResource(R.string.explore_trails)) }
            }
            items(trails, key = { it.id }) { entry ->
                TrailCard(
                    entry = entry,
                    state = statusLoad.snapshot.stateOf(entry.id, now),
                    onClick = { onOpen(MapTarget.TrailTarget(entry.id)) },
                )
            }
        }
    }
}

private const val MAX_PLACES = 5

@Composable
private fun StatusLine(load: StatusLoad, now: Instant, onRetry: () -> Unit) {
    val generatedAt = load.snapshot.generatedAt
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = when {
            load.refreshing && generatedAt == null -> stringResource(R.string.status_loading)
            load.lastError != null && generatedAt == null -> stringResource(R.string.status_failed)
            generatedAt != null -> stringResource(R.string.status_source_line, checkedAgo(generatedAt, now))
            else -> stringResource(R.string.status_loading)
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (load.lastError != null && !load.refreshing) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TrailCard(entry: TrailEntry, state: pt.vereda.core.model.StatusState, onClick: () -> Unit) {
    val trail = entry.trail
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TrailBadges(trail, state)
            Text(
                text = trail.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val details = listOfNotNull(trail.fromTo, trail.municipality, formatLength(entry.lengthM))
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!entry.hasLine) {
                Text(
                    text = stringResource(R.string.trail_no_line_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(place.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.place_peak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(4.dp))
            place.eleM?.let {
                Text(formatElevation(it), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
