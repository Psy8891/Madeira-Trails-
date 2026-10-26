@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package pt.vereda.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
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
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
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
import pt.vereda.app.ui.theme.VeredaColors
import pt.vereda.core.catalog.LineStyle
import pt.vereda.core.catalog.Place
import pt.vereda.core.catalog.StatusSnapshot
import pt.vereda.core.catalog.TrailEntry
import pt.vereda.core.catalog.TrailGeoJson
import pt.vereda.core.catalog.TrailPositions
import pt.vereda.core.geo.GeoPoint
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
private const val FOLLOW_ZOOM = 15.0
private const val LOCATION_BLUE = 0xFF1673B5.toInt()

private class LoadedMap(val map: MapLibreMap, val style: Style)

/** The locate button cycles through these (spec: Map, Controls). */
private enum class LocateMode { FREE, FOLLOW, COMPASS }

@Composable
fun MapScreen(
    selection: MapSelection?,
    onSelect: (MapSelection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VeredaApplication
    val repository = app.repository
    val tracker = app.location
    val catalog by repository.catalog.collectAsStateWithLifecycle()
    val imports by repository.imports.collectAsStateWithLifecycle()
    val statusLoad by repository.status.collectAsStateWithLifecycle()
    val location by tracker.location.collectAsStateWithLifecycle()
    val snapshot = statusLoad.snapshot
    val scope = rememberCoroutineScope()

    val entries = remember(catalog, imports) { imports + catalog?.trails.orEmpty() }
    val places = catalog?.places.orEmpty()
    val selectedEntry = (selection?.target as? MapTarget.TrailTarget)?.let { t -> entries.firstOrNull { it.id == t.id } }
    val selectedPlace = (selection?.target as? MapTarget.PlaceTarget)?.place
    val here = location?.let { GeoPoint(it.latitude, it.longitude) }

    var loaded by remember { mutableStateOf<LoadedMap?>(null) }
    var locateMode by remember { mutableStateOf(LocateMode.FREE) }
    var hasPermission by remember { mutableStateOf(tracker.hasPermission()) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentPlaces by rememberUpdatedState(places)

    val mapView = rememberMapViewWithLifecycle(
        onReady = { map, style ->
            MapLayers.install(context, style)
            map.addOnMapClickListener { point ->
                val screen = map.projection.toScreenLocation(point)
                val touch = RectF(screen.x - 24f, screen.y - 24f, screen.x + 24f, screen.y + 24f)
                val place = map.queryRenderedFeatures(touch, *MapLayers.TAPPABLE_PLACES)
                    .firstNotNullOfOrNull { f -> if (f.hasProperty("id")) currentPlaces.getOrNull(f.getNumberProperty("id").toInt()) else null }
                val trailId = if (place != null) null else map.queryRenderedFeatures(touch, *MapLayers.TAPPABLE_TRAILS)
                    .firstNotNullOfOrNull { f -> if (f.hasProperty("id")) f.getStringProperty("id") else null }
                currentOnSelect(
                    when {
                        place != null -> MapSelection(MapTarget.PlaceTarget(place), moveCamera = false)
                        trailId != null -> MapSelection(MapTarget.TrailTarget(trailId), moveCamera = false)
                        else -> null
                    },
                )
                true
            }
            map.uiSettings.isCompassEnabled = true
            loaded = LoadedMap(map, style)
        },
    )

    // GPS only while the map is on screen; never in the background (spec: Permissions).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, hasPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (hasPermission) tracker.start()
                Lifecycle.Event.ON_PAUSE -> tracker.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (hasPermission && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tracker.start()
        onDispose {
            lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }

    val messageLocationOff = stringResource(R.string.location_off)
    val messageDenied = stringResource(R.string.location_denied)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        hasPermission = granted.values.any { it } || tracker.hasPermission()
        if (hasPermission) {
            tracker.start()
            locateMode = LocateMode.FOLLOW
            if (!tracker.isLocationOn()) Toast.makeText(context, messageLocationOff, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, messageDenied, Toast.LENGTH_LONG).show()
        }
    }

    // The blue dot: MapLibre draws it from the fixes our tracker gets.
    LaunchedEffect(loaded, hasPermission) {
        val target = loaded ?: return@LaunchedEffect
        if (hasPermission) enableLocationComponent(context, target) { locateMode = LocateMode.FREE }
    }
    LaunchedEffect(loaded, location) {
        val target = loaded ?: return@LaunchedEffect
        val fix: Location = location ?: return@LaunchedEffect
        val component = target.map.locationComponent
        if (component.isLocationComponentActivated) component.forceLocationUpdate(fix)
    }
    LaunchedEffect(loaded, locateMode, hasPermission) {
        val target = loaded ?: return@LaunchedEffect
        val component = target.map.locationComponent
        if (!hasPermission || !component.isLocationComponentActivated) return@LaunchedEffect
        when (locateMode) {
            LocateMode.FREE -> component.cameraMode = CameraMode.NONE
            LocateMode.FOLLOW -> {
                component.cameraMode = CameraMode.TRACKING
                if (target.map.cameraPosition.zoom < 13) component.zoomWhileTracking(FOLLOW_ZOOM)
            }
            LocateMode.COMPASS -> component.cameraMode = CameraMode.TRACKING_COMPASS
        }
    }

    // All trail lines, coloured by source and (for official trails) fresh status only.
    LaunchedEffect(loaded, entries, snapshot) {
        val target = loaded ?: return@LaunchedEffect
        val now = TrailRepository.now()
        val json = withContext(Dispatchers.Default) {
            TrailGeoJson.lines(entries) { LineStyle.of(it, snapshot.stateOf(it.id, now)) }
        }
        MapLayers.setGeoJson(target.style, MapLayers.TRAILS_SOURCE, json)
    }

    // Peaks and other points of interest.
    LaunchedEffect(loaded, places) {
        val target = loaded ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { TrailGeoJson.places(places) }
        MapLayers.setGeoJson(target.style, MapLayers.PLACES_SOURCE, json)
    }

    // The picked trail or place: highlight it, mark its start, end and the walk from the road,
    // and, when picked from a list, fly to it.
    val density = LocalDensity.current
    LaunchedEffect(loaded, selection, selectedEntry) {
        val target = loaded ?: return@LaunchedEffect
        MapLayers.setGeoJson(
            target.style,
            MapLayers.SELECTED_SOURCE,
            selectedEntry?.let { TrailGeoJson.lines(listOf(it)) { LineStyle.UNKNOWN } } ?: MapLayers.EMPTY,
        )
        val markers = when {
            selectedEntry != null -> TrailGeoJson.markers(selectedEntry.startPoint, selectedEntry.endPoint, selectedEntry.startAccess)
            selectedPlace != null -> TrailGeoJson.markers(null, null, selectedPlace.access, selectedPlace.point)
            else -> MapLayers.EMPTY
        }
        MapLayers.setGeoJson(target.style, MapLayers.MARKERS_SOURCE, markers)
        if (selection?.moveCamera != true) return@LaunchedEffect
        locateMode = LocateMode.FREE
        val side = with(density) { 48.dp.roundToPx() }
        val top = with(density) { 96.dp.roundToPx() }
        val bottom = with(density) { 340.dp.roundToPx() }
        // Include the walk from the road, so the start of a trail like Pico Alto is on screen.
        val points = buildList {
            selectedEntry?.lines?.forEach { addAll(it) }
            selectedEntry?.startAccess?.line?.let(::addAll)
            selectedPlace?.let { add(it.point) }
            selectedPlace?.access?.line?.let(::addAll)
        }
        val bounds = pt.vereda.core.catalog.Bounds.of(points)
        when {
            bounds == null -> Unit
            bounds.north - bounds.south < 1e-4 && bounds.east - bounds.west < 1e-4 -> target.map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(bounds.center.lat, bounds.center.lon), PLACE_ZOOM),
                900,
            )
            else -> target.map.easeCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                    side, top, side, bottom,
                ),
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

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LocateButton(
                mode = locateMode,
                onClick = {
                    when {
                        !hasPermission -> permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                        !tracker.isLocationOn() -> Toast.makeText(context, messageLocationOff, Toast.LENGTH_LONG).show()
                        else -> locateMode = when (locateMode) {
                            LocateMode.FREE -> LocateMode.FOLLOW
                            LocateMode.FOLLOW -> LocateMode.COMPASS
                            LocateMode.COMPASS -> LocateMode.FREE
                        }
                    }
                },
            )
            val cardModifier = Modifier.fillMaxWidth()
            when {
                selectedEntry != null -> TrailPeekCard(
                    entry = selectedEntry,
                    snapshot = snapshot,
                    here = here,
                    onClose = { onSelect(null) },
                    onDeleteImport = {
                        scope.launch { repository.deleteImport(selectedEntry.id) }
                        onSelect(null)
                    },
                    modifier = cardModifier,
                )
                selectedPlace != null -> PlacePeekCard(
                    place = selectedPlace,
                    trailsHere = remember(selectedPlace, entries) { TrailPositions.near(entries, selectedPlace.point).take(4) },
                    here = here,
                    onTrail = { onSelect(MapSelection(MapTarget.TrailTarget(it.id), moveCamera = true)) },
                    onClose = { onSelect(null) },
                    modifier = cardModifier,
                )
            }
        }
    }
}

@SuppressLint("MissingPermission") // only called once the permission is granted
private fun enableLocationComponent(context: Context, target: LoadedMap, onTrackingDismissed: () -> Unit) {
    val component = target.map.locationComponent
    if (!component.isLocationComponentActivated) {
        val options = LocationComponentOptions.builder(context)
            .foregroundTintColor(LOCATION_BLUE)
            .bearingTintColor(LOCATION_BLUE)
            .accuracyColor(LOCATION_BLUE)
            .accuracyAlpha(0.15f)
            .build()
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, target.style)
                .useDefaultLocationEngine(false)
                .locationComponentOptions(options)
                .build(),
        )
        component.addOnCameraTrackingChangedListener(
            object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() = onTrackingDismissed()
                override fun onCameraTrackingChanged(currentMode: Int) = Unit
            },
        )
    }
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.COMPASS
}

@Composable
private fun LocateButton(mode: LocateMode, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(
                    when (mode) {
                        LocateMode.FREE -> R.drawable.ic_locate
                        LocateMode.FOLLOW -> R.drawable.ic_locate_on
                        LocateMode.COMPASS -> R.drawable.ic_locate_compass
                    },
                ),
                contentDescription = stringResource(
                    when (mode) {
                        LocateMode.FREE -> R.string.locate_show
                        LocateMode.FOLLOW -> R.string.locate_compass
                        LocateMode.COMPASS -> R.string.locate_stop
                    },
                ),
                tint = androidx.compose.ui.graphics.Color.Unspecified,
            )
        }
    }
}

@Composable
private fun TrailPeekCard(
    entry: TrailEntry,
    snapshot: StatusSnapshot,
    here: GeoPoint?,
    onClose: () -> Unit,
    onDeleteImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trail = entry.trail
    val now = TrailRepository.now()
    val status = snapshot.status(entry.id)
    val state = snapshot.stateOf(entry.id, now)
    val uriHandler = LocalUriHandler.current
    val position = remember(entry, here) { here?.let { TrailPositions.of(entry, it) } }

    PeekSurface(modifier) {
        TrailBadges(trail, state)
        Text(trail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val place = trail.fromTo ?: trail.area?.let { stringResource(R.string.near_area, it) }
        val details = listOfNotNull(place, formatLength(entry.lengthM))
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        positionText(position)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = VeredaColors.LocationBlue, fontWeight = FontWeight.SemiBold)
        }
        startText(entry)?.let { MarkerLine(R.string.marker_start, it, VeredaColors.OpenLine) }
        endText(entry)?.let { MarkerLine(R.string.marker_end, it, VeredaColors.Ink) }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            directionsUrl(entry.startAccess?.roadPoint ?: entry.startPoint)?.let { url ->
                OutlinedButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.directions_to_start)) }
            }
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
private fun MarkerLine(label: Int, text: String, colour: androidx.compose.ui.graphics.Color) {
    Text(
        text = buildString {
            append(stringResource(label))
            append(": ")
            append(text)
        },
        style = MaterialTheme.typography.bodySmall,
        color = colour,
    )
}

@Composable
private fun PlacePeekCard(
    place: Place,
    trailsHere: List<TrailEntry>,
    here: GeoPoint?,
    onTrail: (TrailEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    PeekSurface(modifier) {
        Text(
            text = place.displayName ?: kindLabel(place.kind),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = listOfNotNull(kindLabel(place.kind), place.eleM?.let(::formatElevation)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        here?.let { from ->
            val metres = pt.vereda.core.geo.Geo.distanceM(from, place.point).toInt()
            Text(
                text = stringResource(R.string.position_from_place, formatLength(metres) ?: "0 m"),
                style = MaterialTheme.typography.bodyMedium,
                color = VeredaColors.LocationBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
        placeAccessText(place.access)?.let { MarkerLine(R.string.marker_access, it, VeredaColors.Ink) }
        if (trailsHere.isNotEmpty()) {
            Text(stringResource(R.string.trails_here), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            trailsHere.forEach { entry ->
                TextButton(onClick = { onTrail(entry) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                    Text(listOfNotNull(entry.trail.code, entry.trail.name).joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            text = String.format(java.util.Locale.ROOT, "%.5f, %.5f", place.point.lat, place.point.lon),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val access = place.access
            directionsUrl(if (access != null && access.needsWalk) access.roadPoint else place.point)?.let { url ->
                OutlinedButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.directions)) }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
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
        Column(
            Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

/** Google Maps directions to a point (works in the browser when the app isn't installed). */
private fun directionsUrl(point: GeoPoint?): String? = point?.let {
    String.format(java.util.Locale.ROOT, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", it.lat, it.lon)
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
