package pt.vereda.app.ui.map

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import pt.vereda.app.R
import pt.vereda.app.VeredaApplication
import pt.vereda.app.data.TrailRepository
import pt.vereda.app.ui.components.TrailBadges
import pt.vereda.app.ui.components.checkedAgo
import pt.vereda.app.ui.components.formatElevation
import pt.vereda.app.ui.components.formatLength
import pt.vereda.core.catalog.LineStyle
import pt.vereda.core.catalog.Place
import pt.vereda.core.catalog.StatusSnapshot
import pt.vereda.core.catalog.TrailEntry
import pt.vereda.core.catalog.TrailGeoJson
import pt.vereda.core.model.StatusState
import pt.vereda.core.model.TrailSource

/**
 * Temporary online base map (OpenFreeMap, OpenStreetMap data) until the offline Vereda Topo
 * packages exist. Swap this for the local `pmtiles://file://…` style then.
 */
private const val DEV_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val MADEIRA_CENTRE = LatLng(32.75, -16.95)
private const val START_ZOOM = 9.4
private const val PLACE_ZOOM = 14.0

private class LoadedMap(val map: MapLibreMap, val style: Style)

@Composable
fun MapScreen(
    selection: MapSelection?,
    onSelect: (MapSelection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as VeredaApplication).repository
    val catalog by repository.catalog.collectAsStateWithLifecycle()
    val imports by repository.imports.collectAsStateWithLifecycle()
    val statusLoad by repository.status.collectAsStateWithLifecycle()
    val snapshot = statusLoad.snapshot
    val scope = rememberCoroutineScope()

    val entries = remember(catalog, imports) { imports + catalog?.trails.orEmpty() }
    val selectedEntry = (selection?.target as? MapTarget.TrailTarget)?.let { t -> entries.firstOrNull { it.id == t.id } }
    val selectedPlace = (selection?.target as? MapTarget.PlaceTarget)?.place

    var loaded by remember { mutableStateOf<LoadedMap?>(null) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val mapView = rememberMapViewWithLifecycle(
        onReady = { map, style ->
            MapLayers.install(style)
            map.addOnMapClickListener { point ->
                val screen = map.projection.toScreenLocation(point)
                val touch = RectF(screen.x - 24f, screen.y - 24f, screen.x + 24f, screen.y + 24f)
                val id = map.queryRenderedFeatures(touch, *MapLayers.TAPPABLE)
                    .firstNotNullOfOrNull { f -> if (f.hasProperty("id")) f.getStringProperty("id") else null }
                currentOnSelect(id?.let { MapSelection(MapTarget.TrailTarget(it), moveCamera = false) })
                true
            }
            loaded = LoadedMap(map, style)
        },
    )

    // All trail lines, coloured by source and (for official trails) fresh status only.
    LaunchedEffect(loaded, entries, snapshot) {
        val target = loaded ?: return@LaunchedEffect
        val now = TrailRepository.now()
        val json = withContext(Dispatchers.Default) {
            TrailGeoJson.lines(entries) { LineStyle.of(it, snapshot.stateOf(it.id, now)) }
        }
        MapLayers.setGeoJson(target.style, MapLayers.TRAILS_SOURCE, json)
    }

    // The picked trail or peak: highlight it and, when picked from a list, fly to it.
    val density = LocalDensity.current
    LaunchedEffect(loaded, selection, selectedEntry) {
        val target = loaded ?: return@LaunchedEffect
        MapLayers.setGeoJson(
            target.style,
            MapLayers.SELECTED_SOURCE,
            selectedEntry?.let { TrailGeoJson.lines(listOf(it)) { LineStyle.UNKNOWN } } ?: MapLayers.EMPTY,
        )
        MapLayers.setGeoJson(
            target.style,
            MapLayers.PLACE_SOURCE,
            selectedPlace?.let { TrailGeoJson.point(it.point, it.displayName) } ?: MapLayers.EMPTY,
        )
        if (selection?.moveCamera != true) return@LaunchedEffect
        val side = with(density) { 48.dp.roundToPx() }
        val top = with(density) { 96.dp.roundToPx() }
        val bottom = with(density) { 300.dp.roundToPx() }
        val bounds = selectedEntry?.bounds
        when {
            bounds != null -> {
                val update = if (bounds.north - bounds.south < 1e-4 && bounds.east - bounds.west < 1e-4) {
                    CameraUpdateFactory.newLatLngZoom(LatLng(bounds.center.lat, bounds.center.lon), PLACE_ZOOM)
                } else {
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                        side, top, side, bottom,
                    )
                }
                target.map.easeCamera(update, 900)
            }
            selectedPlace != null -> target.map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(selectedPlace.point.lat, selectedPlace.point.lon), PLACE_ZOOM),
                900,
            )
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
        ) {
            Text(
                text = stringResource(R.string.map_dev_banner),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val cardModifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp)
        when {
            selectedEntry != null -> TrailPeekCard(
                entry = selectedEntry,
                snapshot = snapshot,
                onClose = { onSelect(null) },
                onDeleteImport = {
                    scope.launch { repository.deleteImport(selectedEntry.id) }
                    onSelect(null)
                },
                modifier = cardModifier,
            )
            selectedPlace != null -> PlacePeekCard(selectedPlace, onClose = { onSelect(null) }, modifier = cardModifier)
        }
    }
}

@Composable
private fun TrailPeekCard(
    entry: TrailEntry,
    snapshot: StatusSnapshot,
    onClose: () -> Unit,
    onDeleteImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trail = entry.trail
    val now = TrailRepository.now()
    val status = snapshot.status(entry.id)
    val state = snapshot.stateOf(entry.id, now)
    val uriHandler = LocalUriHandler.current

    PeekSurface(modifier) {
        TrailBadges(trail, state)
        Text(trail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val details = listOfNotNull(trail.fromTo, formatLength(entry.lengthM))
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        val notes = buildList {
            when (trail.source) {
                TrailSource.OFFICIAL -> {
                    add(
                        when {
                            status == null -> stringResource(R.string.status_none_for_trail)
                            state == StatusState.UNKNOWN -> stringResource(R.string.status_too_old)
                            else -> stringResource(
                                R.string.status_with_source,
                                status.label ?: "",
                                checkedAgo(status.checkedAt, now),
                            )
                        },
                    )
                    add(stringResource(R.string.official_booking))
                }
                TrailSource.COMMUNITY -> add(stringResource(R.string.community_warning))
                TrailSource.IMPORT -> add(stringResource(R.string.import_private))
            }
            entry.note(java.util.Locale.getDefault().language)?.let(::add)
            if (!entry.hasLine) add(stringResource(R.string.trail_no_line))
        }
        notes.forEach { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (trail.source) {
                TrailSource.OFFICIAL -> OutlinedButton(
                    onClick = { uriHandler.openUri(status?.sourceUrl ?: TrailRepository.OFFICIAL_LIST_URL) },
                ) { Text(stringResource(R.string.official_page)) }
                TrailSource.COMMUNITY -> osmUrl(entry.osmIds.firstOrNull())?.let { url ->
                    OutlinedButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.view_on_osm)) }
                }
                TrailSource.IMPORT -> OutlinedButton(onClick = onDeleteImport) { Text(stringResource(R.string.import_delete)) }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun PlacePeekCard(place: Place, onClose: () -> Unit, modifier: Modifier = Modifier) {
    PeekSurface(modifier) {
        Text(place.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = listOfNotNull(stringResource(R.string.place_peak), place.eleM?.let(::formatElevation)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = String.format(java.util.Locale.ROOT, "%.5f, %.5f", place.point.lat, place.point.lon),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun PeekSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** "r4444478" → relation page, "w123" → way page on openstreetmap.org. */
private fun osmUrl(osmId: String?): String? {
    if (osmId == null || osmId.length < 2) return null
    val kind = when (osmId[0]) {
        'r' -> "relation"
        'w' -> "way"
        else -> return null
    }
    return "https://www.openstreetmap.org/$kind/${osmId.substring(1)}"
}

/** A MapView that follows the screen's lifecycle and is torn down when the tab leaves. */
@Composable
private fun rememberMapViewWithLifecycle(onReady: (MapLibreMap, Style) -> Unit): MapView {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onReady)
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(MADEIRA_CENTRE)
                    .zoom(START_ZOOM)
                    .build()
                map.setStyle(Style.Builder().fromUri(DEV_STYLE_URL)) { style -> currentOnReady(map, style) }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    started = false
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    destroyed = true
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                if (resumed) mapView.onPause()
                if (started) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    return mapView
}
