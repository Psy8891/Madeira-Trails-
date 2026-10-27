@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.vereda.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import pt.vereda.core.catalog.DirectionsTarget
import pt.vereda.core.catalog.HikePlans
import pt.vereda.core.maps.TopoStyle
import pt.vereda.core.route.NavRoute
import pt.vereda.core.route.TripKind
import pt.vereda.core.geo.ScaleBar
import pt.vereda.core.geo.ElevationGrid
import pt.vereda.core.geo.ElevationProfiles
import pt.vereda.core.geo.Utm
import pt.vereda.app.ui.components.HeightSummary
import pt.vereda.app.ui.components.SafetySection
import pt.vereda.app.ui.components.WalkInfo
import pt.vereda.app.ui.maps.rememberOfflineMapState
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
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
import pt.vereda.app.data.PlanPoints
import pt.vereda.app.data.PlanResult
import pt.vereda.app.data.TrailRepository
import pt.vereda.app.ui.components.shareGpx
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
import pt.vereda.core.catalog.RoadAccess
import pt.vereda.core.route.RouteOutcome

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

/** Which planner point the next tap on the map sets (the *Plan a route* button). */
private enum class PickRole { START, STOP, END }

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
    val navigation by app.navigation.active.collectAsStateWithLifecycle()
    val navigating = navigation != null
    // While navigating, the service's fixes are the position (the map's own GPS requests stop).
    val navFix = navigation?.state?.lastFix
    val here = if (navFix != null) GeoPoint(navFix.lat, navFix.lon) else location?.let { GeoPoint(it.latitude, it.longitude) }
    val planner = app.planner
    val planPoints by planner.points.collectAsStateWithLifecycle()
    val planResult by planner.result.collectAsStateWithLifecycle()
    val directionsTarget by app.settings.directions.collectAsStateWithLifecycle()
    val elevation by app.terrain.grid.collectAsStateWithLifecycle()
    val screenUriHandler = LocalUriHandler.current

    var loaded by remember { mutableStateOf<LoadedMap?>(null) }
    var pressed by remember { mutableStateOf<GeoPoint?>(null) }
    // Which way in is shown for the picked trail or peak (0 = recommended).
    var startChoice by remember(selection?.target) { mutableIntStateOf(0) }
    var locateMode by remember { mutableStateOf(LocateMode.FREE) }
    // The planner card is open (from the Plan a route button), and which point a tap sets.
    var planning by remember { mutableStateOf(false) }
    var pickRole by remember { mutableStateOf<PickRole?>(null) }
    // Before Start (Phase 1.5): the card asking one way or there and back, and the choice.
    var startSheet by remember { mutableStateOf(false) }
    var startKind by remember { mutableStateOf(TripKind.ONE_WAY) }
    var navExpanded by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(tracker.hasPermission()) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentPlaces by rememberUpdatedState(places)
    val currentPressed by rememberUpdatedState(pressed)
    val currentOnLongPress by rememberUpdatedState<(GeoPoint?) -> Unit>({ pressed = it })
    val currentPickRole by rememberUpdatedState(pickRole)
    val currentNavigating by rememberUpdatedState(navigating)
    val currentOnPick by rememberUpdatedState<(GeoPoint) -> Unit>({ p ->
        when (pickRole) {
            PickRole.START -> planner.setStart(p)
            PickRole.STOP -> planner.addStop(p)
            PickRole.END -> {
                planner.setEnd(p)
                if (planPoints.start == null && here != null) planner.setStart(here)
            }
            null -> Unit
        }
        pickRole = null
    })

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var camera by remember { mutableStateOf(MADEIRA_CENTRE.latitude to START_ZOOM) }
    val mapView = rememberMapViewWithLifecycle(
        onReady = { map ->
            fun track() {
                val position = map.cameraPosition
                position.target?.let { camera = it.latitude to position.zoom }
            }
            map.addOnCameraMoveListener { track() }
            map.addOnCameraIdleListener { track() }
            map.addOnMapLongClickListener { point ->
                if (!currentNavigating) currentOnLongPress(GeoPoint(point.latitude, point.longitude))
                true
            }
            map.addOnMapClickListener { point ->
                if (currentNavigating) return@addOnMapClickListener true
                if (currentPickRole != null) {
                    currentOnPick(GeoPoint(point.latitude, point.longitude))
                    return@addOnMapClickListener true
                }
                if (currentPressed != null) {
                    currentOnLongPress(null)
                    return@addOnMapClickListener true
                }
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
            mapRef = map
        },
    )

    // The base map: the offline Vereda Topo style once the package is on the phone, otherwise the
    // online map (spec: Phase 1). Switching style re-adds Vereda's layers and data.
    val installedMaps by app.maps.installed.collectAsStateWithLifecycle()
    val mapPaths = remember(installedMaps) { app.maps.installedPaths(installedMaps) }
    val offline = mapPaths != null
    val online by app.network.online.collectAsStateWithLifecycle()
    val overviewPath by app.maps.overview.collectAsStateWithLifecycle()
    // Without the package: the online map when connected, else the small overview map in the app.
    val useOverview = mapPaths == null && !online && overviewPath != null
    LaunchedEffect(mapRef, mapPaths, useOverview) {
        val map = mapRef ?: return@LaunchedEffect
        loaded = null
        val topoFonts = MapLayers.Fonts(TopoStyle.FONT_REGULAR, TopoStyle.FONT_BOLD)
        val (builder, fonts) = when {
            mapPaths != null -> {
                val json = withContext(Dispatchers.IO) {
                    TopoStyle.json(mapPaths.getValue("base"), mapPaths.getValue("contours"), mapPaths.getValue("dem"))
                }
                Style.Builder().fromJson(json) to topoFonts
            }
            useOverview -> Style.Builder().fromJson(TopoStyle.json(overviewPath!!, null, null)) to topoFonts
            else -> Style.Builder().fromUri(DEV_STYLE_URL) to MapLayers.Fonts.ONLINE
        }
        map.setStyle(builder) { style ->
            MapLayers.install(context, style, fonts)
            loaded = LoadedMap(map, style)
        }
    }
    val layerSettings by app.settings.layers.collectAsStateWithLifecycle()
    LaunchedEffect(loaded, layerSettings) {
        val target = loaded ?: return@LaunchedEffect
        MapLayers.applyTerrain(target.style, layerSettings.hillshade, layerSettings.hillshadeStrength, layerSettings.contours)
    }

    // GPS only while the map is on screen; never in the background (spec: Permissions).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, hasPermission, navigating) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (hasPermission && !navigating) tracker.start()
                Lifecycle.Event.ON_PAUSE -> tracker.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (navigating) tracker.stop()
        else if (hasPermission && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tracker.start()
        onDispose {
            lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }

    val messageLocationOff = stringResource(R.string.location_off)
    val needLocation = stringResource(R.string.nav_need_location)
    val noLineMessage = stringResource(R.string.trail_no_line)
    // Android 13+: the navigation notification needs permission; guidance works without it.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
    LaunchedEffect(loaded, location, navFix) {
        val target = loaded ?: return@LaunchedEffect
        val fix: Location = navFix?.let {
            Location("vereda").apply {
                latitude = it.lat
                longitude = it.lon
                accuracy = it.accuracyM.toFloat()
                time = it.timeMs
            }
        } ?: location ?: return@LaunchedEffect
        val component = target.map.locationComponent
        if (component.isLocationComponentActivated) component.forceLocationUpdate(fix)
    }

    // The walk being followed: the way ahead in the navigation colour, the walked part grey.
    var drawnProgress by remember { mutableStateOf(-1.0) }
    LaunchedEffect(loaded, navigation) {
        val target = loaded ?: return@LaunchedEffect
        val nav = navigation
        if (nav == null) {
            drawnProgress = -1.0
            MapLayers.setGeoJson(target.style, MapLayers.NAV_SOURCE, MapLayers.EMPTY)
            MapLayers.setGeoJson(target.style, MapLayers.NAV_DONE_SOURCE, MapLayers.EMPTY)
            return@LaunchedEffect
        }
        // Redraw only every 10 m of progress: cheap on the battery with the screen on.
        if (drawnProgress >= 0 && kotlin.math.abs(nav.state.progressM - drawnProgress) < 10 && nav.walked.size > 1) return@LaunchedEffect
        drawnProgress = nav.state.progressM
        val ahead = withContext(Dispatchers.Default) { aheadOf(nav.route, nav.state.progressM) }
        MapLayers.setGeoJson(target.style, MapLayers.NAV_SOURCE, TrailGeoJson.line(ahead))
        MapLayers.setGeoJson(target.style, MapLayers.NAV_DONE_SOURCE, if (nav.walked.size >= 2) TrailGeoJson.line(nav.walked) else MapLayers.EMPTY)
    }
    // Follow with the compass when a walk starts.
    LaunchedEffect(navigating) {
        if (navigating) {
            locateMode = LocateMode.COMPASS
            navExpanded = false
        }
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
    LaunchedEffect(loaded, entries, snapshot, layerSettings.communityTrails) {
        val target = loaded ?: return@LaunchedEffect
        val now = TrailRepository.now()
        val shown = if (layerSettings.communityTrails) entries else entries.filter { it.trail.source != TrailSource.COMMUNITY }
        val json = withContext(Dispatchers.Default) {
            TrailGeoJson.lines(shown) { LineStyle.of(it, snapshot.stateOf(it.id, now)) }
        }
        MapLayers.setGeoJson(target.style, MapLayers.TRAILS_SOURCE, json)
    }

    // Peaks and other points of interest.
    LaunchedEffect(loaded, places, layerSettings.places) {
        val target = loaded ?: return@LaunchedEffect
        val json = if (!layerSettings.places) MapLayers.EMPTY else withContext(Dispatchers.Default) { TrailGeoJson.places(places) }
        MapLayers.setGeoJson(target.style, MapLayers.PLACES_SOURCE, json)
    }

    // The picked trail or place: highlight it, mark its start, end and the walk from the road,
    // and, when picked from a list, fly to it.
    val density = LocalDensity.current
    LaunchedEffect(loaded, selection, selectedEntry, startChoice) {
        val target = loaded ?: return@LaunchedEffect
        MapLayers.setGeoJson(
            target.style,
            MapLayers.SELECTED_SOURCE,
            selectedEntry?.let { TrailGeoJson.lines(listOf(it)) { LineStyle.UNKNOWN } } ?: MapLayers.EMPTY,
        )
        val starts = selectedEntry?.starts ?: selectedPlace?.starts.orEmpty()
        val chosen = starts.getOrNull(startChoice) ?: starts.firstOrNull()
        val others = starts.filter { it != chosen }.mapNotNull { it.roadPoint }
        val markers = when {
            selectedEntry != null -> TrailGeoJson.markers(selectedEntry.startPoint, selectedEntry.endPoint, chosen, otherRoads = others)
            selectedPlace != null -> TrailGeoJson.markers(null, null, chosen ?: selectedPlace.access, selectedPlace.point, others)
            else -> MapLayers.EMPTY
        }
        MapLayers.setGeoJson(target.style, MapLayers.MARKERS_SOURCE, markers)
        if (selection?.moveCamera != true || startChoice != 0) return@LaunchedEffect
        locateMode = LocateMode.FREE
        val side = with(density) { 48.dp.roundToPx() }
        val top = with(density) { 96.dp.roundToPx() }
        val bottom = with(density) { 340.dp.roundToPx() }
        // Include the walk from the road, so the start of a trail like Pico Alto is on screen.
        val points = buildList {
            selectedEntry?.lines?.forEach { addAll(it) }
            starts.forEach { addAll(it.line) }
            selectedPlace?.let { add(it.point) }
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

    // Only the latest pick is shown (issue #2): picking a trail or peak replaces a route opened
    // from Directions, and hides a route the user built until the card is closed.
    LaunchedEffect(selection?.target) {
        if (selection == null) return@LaunchedEffect
        pickRole = null
        if (planner.points.value.title != null) planner.clear()
    }

    // The route being planned: its line and its start, stops and end. Zoom out to it when it
    // doesn't fit on screen.
    val savedMessage = stringResource(R.string.plan_saved)
    val planHidden = selection != null || navigating
    LaunchedEffect(loaded, planPoints, planResult, planHidden) {
        val target = loaded ?: return@LaunchedEffect
        if (planHidden) {
            MapLayers.setGeoJson(target.style, MapLayers.ROUTE_SOURCE, MapLayers.EMPTY)
            MapLayers.setGeoJson(target.style, MapLayers.PLAN_SOURCE, MapLayers.EMPTY)
            return@LaunchedEffect
        }
        val route = ((planResult as? PlanResult.Done)?.outcome as? RouteOutcome.Found)?.route
        MapLayers.setGeoJson(target.style, MapLayers.ROUTE_SOURCE, route?.let { TrailGeoJson.line(it.points) } ?: MapLayers.EMPTY)
        MapLayers.setGeoJson(target.style, MapLayers.PLAN_SOURCE, TrailGeoJson.planPoints(planPoints.start, planPoints.stops, planPoints.end))
        val bounds = route?.let { pt.vereda.core.catalog.Bounds.of(it.points) } ?: return@LaunchedEffect
        val visible = target.map.projection.visibleRegion.latLngBounds
        if (visible.contains(LatLng(bounds.north, bounds.east)) && visible.contains(LatLng(bounds.south, bounds.west))) return@LaunchedEffect
        locateMode = LocateMode.FREE
        val side = with(density) { 48.dp.roundToPx() }
        target.map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                side, with(density) { 96.dp.roundToPx() }, side, with(density) { 380.dp.roundToPx() },
            ),
            900,
        )
    }

    val offlineState = rememberOfflineMapState()
    var showLayers by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = if (navigating) 108.dp else 72.dp, end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SosButton(onClick = { showEmergency = true })
            LayersButton(onClick = { showLayers = !showLayers })
        }
        ScaleBarView(
            bar = remember(camera) { ScaleBar.pick(camera.second, camera.first, 96f) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = if (navigating) 112.dp else 76.dp, start = 14.dp),
        )
        if (navigation != null) {
            CueBanner(navigation!!, Modifier.align(Alignment.TopCenter).padding(12.dp))
        } else Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
        ) {
            val role = pickRole
            if (role == null) {
                MapBanner(offline = offline, overview = useOverview, state = offlineState)
            } else {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            when (role) {
                                PickRole.START -> R.string.plan_pick_start
                                PickRole.STOP -> R.string.plan_pick_stop
                                PickRole.END -> R.string.plan_pick_end
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { pickRole = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val nothingOpen = pressed == null && selection == null && !planning && planPoints.isEmpty && !showLayers && !showEmergency && !navigating
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nothingOpen) PlanButton(onClick = { planning = true })
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
            }
            val cardModifier = Modifier.fillMaxWidth()
            val exportFailed = stringResource(R.string.export_failed)
            fun export(name: String, lines: List<List<GeoPoint>>, osm: Boolean) {
                scope.launch {
                    runCatching { repository.exportGpx(name, lines, osm) }
                        .onSuccess { shareGpx(context, it, name) }
                        .onFailure { Toast.makeText(context, exportFailed, Toast.LENGTH_LONG).show() }
                }
            }
            val pressedPoint = pressed
            when {
                showEmergency -> EmergencyCard(location = navFix?.let { f -> Location("vereda").apply { latitude = f.lat; longitude = f.lon; accuracy = f.accuracyM.toFloat(); time = f.timeMs } } ?: location, onClose = { showEmergency = false }, modifier = cardModifier)
                navigation != null -> NavPanel(
                    nav = navigation!!,
                    elevation = elevation,
                    expanded = navExpanded,
                    onToggle = { navExpanded = !navExpanded },
                    onRecenter = { locateMode = LocateMode.COMPASS },
                    onStop = { app.navigation.stop(context) },
                    modifier = cardModifier,
                )
                startSheet -> {
                    val route = ((planResult as? PlanResult.Done)?.outcome as? RouteOutcome.Found)?.route
                    val problem = when (val r = planResult) {
                        is PlanResult.Failed -> r.message
                        is PlanResult.Done -> if (r.outcome is RouteOutcome.Found) null else stringResource(R.string.plan_no_route)
                        else -> null
                    }
                    val title = planPoints.title ?: stringResource(R.string.plan_title)
                    StartCard(
                        title = title,
                        route = route,
                        problem = problem,
                        elevation = elevation,
                        kind = startKind,
                        onKind = { startKind = it },
                        onStart = {
                            if (!hasPermission) {
                                Toast.makeText(context, needLocation, Toast.LENGTH_LONG).show()
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else if (route != null) {
                                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                scope.launch {
                                    val trip = withContext(Dispatchers.Default) {
                                        NavRoute.of(title, route, planner.network(), startKind, elevation)
                                    }
                                    app.navigation.start(context, trip)
                                    startSheet = false
                                    planning = false
                                    pickRole = null
                                    planner.clear()
                                }
                            }
                        },
                        onCancel = { startSheet = false },
                        modifier = cardModifier,
                    )
                }
                showLayers -> LayersCard(
                    settings = layerSettings,
                    offline = offline,
                    onChange = app.settings::setLayers,
                    onClose = { showLayers = false },
                    modifier = cardModifier,
                )
                pressedPoint != null -> PointCard(
                    point = pressedPoint,
                    heightM = elevation?.heightAt(pressedPoint),
                    hasStart = planPoints.start != null,
                    onStart = {
                        planner.setStart(pressedPoint)
                        pressed = null
                        onSelect(null)
                    },
                    onStop = {
                        planner.addStop(pressedPoint)
                        pressed = null
                        onSelect(null)
                    },
                    onEnd = {
                        planner.setEnd(pressedPoint)
                        if (planPoints.start == null && here != null) planner.setStart(here)
                        pressed = null
                        onSelect(null)
                    },
                    onClose = { pressed = null },
                    modifier = cardModifier,
                )
                selectedEntry != null -> TrailPeekCard(
                    entry = selectedEntry,
                    elevation = elevation,
                    snapshot = snapshot,
                    here = here,
                    startChoice = startChoice,
                    onStartChoice = { startChoice = it },
                    directionsTarget = directionsTarget,
                    onDirectionsTarget = app.settings::setDirections,
                    onStart = {
                        val chosen = selectedEntry.starts.getOrNull(startChoice) ?: selectedEntry.starts.firstOrNull()
                        val plan = HikePlans.forTrail(selectedEntry, chosen, here)
                        if (plan == null) {
                            Toast.makeText(context, noLineMessage, Toast.LENGTH_LONG).show()
                        } else {
                            planner.showHike(plan, listOfNotNull(selectedEntry.trail.code, selectedEntry.trail.name).joinToString(" "))
                            startKind = TripKind.ONE_WAY
                            startSheet = true
                            onSelect(null)
                        }
                    },
                    onDirections = run {
                        val chosen = selectedEntry.starts.getOrNull(startChoice) ?: selectedEntry.starts.firstOrNull()
                        val plan = HikePlans.forTrail(selectedEntry, chosen, here)
                        val road = chosen?.roadPoint ?: selectedEntry.startPoint
                        directionsAction(plan != null, road) {
                            if (plan != null && directionsTarget == DirectionsTarget.IN_APP) {
                                planner.showHike(plan, listOfNotNull(selectedEntry.trail.code, selectedEntry.trail.name).joinToString(" "))
                                onSelect(null)
                            } else if (road != null) {
                                screenUriHandler.openUri(HikePlans.googleMapsUrl(road))
                            }
                        }
                    },
                    onExport = {
                        export(
                            listOfNotNull(selectedEntry.trail.code, selectedEntry.trail.name).joinToString(" "),
                            selectedEntry.lines,
                            selectedEntry.trail.source != TrailSource.IMPORT,
                        )
                    },
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
                    startChoice = startChoice,
                    onStartChoice = { startChoice = it },
                    onTrail = { onSelect(MapSelection(MapTarget.TrailTarget(it.id), moveCamera = true)) },
                    directionsTarget = directionsTarget,
                    onDirectionsTarget = app.settings::setDirections,
                    onDirections = run {
                        val chosen = selectedPlace.starts.getOrNull(startChoice) ?: selectedPlace.starts.firstOrNull()
                        val plan = HikePlans.forPlace(selectedPlace, chosen, here)
                        val kindName = kindLabel(selectedPlace.kind)
                        val road = if (chosen != null && chosen.needsWalk) chosen.roadPoint else selectedPlace.point
                        directionsAction(plan != null, road) {
                            if (plan != null && directionsTarget == DirectionsTarget.IN_APP) {
                                planner.showHike(plan, selectedPlace.displayName ?: kindName)
                                onSelect(null)
                            } else if (road != null) {
                                screenUriHandler.openUri(HikePlans.googleMapsUrl(road))
                            }
                        }
                    },
                    onPlanHere = {
                        planner.setEnd(selectedPlace.point)
                        val chosen = selectedPlace.starts.getOrNull(startChoice)
                        when {
                            here != null -> planner.setStart(here)
                            chosen?.roadPoint != null -> planner.setStart(chosen.roadPoint!!)
                        }
                        onSelect(null)
                    },
                    onClose = { onSelect(null) },
                    modifier = cardModifier,
                )
                planning || !planPoints.isEmpty -> PlannerCard(
                    points = planPoints,
                    elevation = elevation,
                    result = planResult,
                    snapshot = snapshot,
                    here = here,
                    onFromHere = { here?.let(planner::setStart) },
                    onRemoveStop = planner::removeStop,
                    onReverse = planner::reverse,
                    onClear = {
                        planner.clear()
                        planning = false
                        pickRole = null
                    },
                    onPick = { pickRole = it },
                    onSave = { name, points ->
                        scope.launch {
                            repository.saveRoute(name, points)?.let {
                                Toast.makeText(context, savedMessage, Toast.LENGTH_LONG).show()
                                planner.clear()
                                planning = false
                                onSelect(MapSelection(MapTarget.TrailTarget(it.id), moveCamera = false))
                            }
                        }
                    },
                    onExport = { name, points -> export(name, listOf(points), osm = true) },
                    onStart = {
                        startKind = TripKind.ONE_WAY
                        startSheet = true
                    },
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

/** Opens the route planner; long-press on the map does the same without it. */
@Composable
private fun PlanButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_route), contentDescription = null, tint = androidx.compose.ui.graphics.Color.Unspecified)
            Text(stringResource(R.string.plan_button), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TrailPeekCard(
    entry: TrailEntry,
    elevation: ElevationGrid?,
    snapshot: StatusSnapshot,
    here: GeoPoint?,
    startChoice: Int,
    onStartChoice: (Int) -> Unit,
    directionsTarget: DirectionsTarget,
    onDirectionsTarget: (DirectionsTarget) -> Unit,
    onDirections: (() -> Unit)?,
    onStart: () -> Unit,
    onExport: () -> Unit,
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
    val starts = entry.starts
    val chosen = starts.getOrNull(startChoice) ?: starts.firstOrNull()

    PeekSurface(modifier) {
        TrailBadges(trail, state)
        Text(trail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val place = trail.fromTo ?: trail.area?.let { stringResource(R.string.near_area, it) }
        val profile = remember(entry, elevation) { elevation?.let { g -> entry.walkLine?.let { ElevationProfiles.of(it, g) } } }
        val details = listOfNotNull(
            place,
            formatLength(entry.lengthM),
            profile?.let { stringResource(R.string.hike_time, durationText(it.minutes)) },
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        profile?.let { HeightSummary(it) }
        positionText(position)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = VeredaColors.LocationBlue, fontWeight = FontWeight.SemiBold)
        }
        if (starts.size > 1) {
            StartOptions(starts, startChoice, onStartChoice)
        } else {
            startText(chosen)?.let { MarkerLine(R.string.marker_start, it, VeredaColors.OpenLine) }
        }
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
        entry.startPoint?.let { start ->
            SafetySection(
                WalkInfo(
                    key = entry.id,
                    point = start,
                    minutes = profile?.minutes,
                    highestM = profile?.maxM,
                    official = trail.source == TrailSource.OFFICIAL,
                    levada = trail.name.contains("levada", ignoreCase = true),
                    tunnels = null,
                ),
            )
        }
        if (onDirections != null) DirectionsToggle(directionsTarget, onDirectionsTarget)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entry.hasLine) Button(onClick = onStart) { Text(stringResource(R.string.nav_start)) }
            onDirections?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.directions)) } }
            when (trail.source) {
                TrailSource.OFFICIAL -> OutlinedButton(
                    onClick = { uriHandler.openUri(status?.sourceUrl ?: TrailRepository.OFFICIAL_LIST_URL) },
                ) { Text(stringResource(R.string.official_page)) }
                TrailSource.COMMUNITY -> osmUrl(entry.osmIds.firstOrNull())?.let { url ->
                    OutlinedButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.view_on_osm)) }
                }
                TrailSource.IMPORT -> OutlinedButton(onClick = onDeleteImport) { Text(stringResource(R.string.import_delete)) }
            }
            if (entry.hasLine) OutlinedButton(onClick = onExport) { Text(stringResource(R.string.export_gpx)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

/** The ways in to a trail or peak; the chosen one is drawn on the map and used for Directions. */
@Composable
private fun StartOptions(starts: List<RoadAccess>, chosen: Int, onChoose: (Int) -> Unit) {
    Text(stringResource(R.string.start_options), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    starts.forEachIndexed { i, access ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (i == chosen) VeredaColors.LaurelTint else androidx.compose.ui.graphics.Color.Transparent)
                .selectable(selected = i == chosen, onClick = { onChoose(i) })
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = i == chosen, onClick = null)
            Text(optionText(access), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
    startChoice: Int,
    onStartChoice: (Int) -> Unit,
    onTrail: (TrailEntry) -> Unit,
    directionsTarget: DirectionsTarget,
    onDirectionsTarget: (DirectionsTarget) -> Unit,
    onDirections: (() -> Unit)?,
    onPlanHere: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val starts = place.starts
    val chosen = starts.getOrNull(startChoice) ?: starts.firstOrNull()
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
        if (starts.size > 1) {
            StartOptions(starts, startChoice, onStartChoice)
        } else {
            placeAccessText(chosen)?.let { MarkerLine(R.string.marker_access, it, VeredaColors.Ink) }
        }
        if (trailsHere.isNotEmpty()) {
            Text(stringResource(R.string.trails_here), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            trailsHere.forEach { entry ->
                TextButton(onClick = { onTrail(entry) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                    Text(listOfNotNull(entry.trail.code, entry.trail.name).joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        CoordinatesText(place.point, null)
        if (onDirections != null) DirectionsToggle(directionsTarget, onDirectionsTarget)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onDirections?.let { Button(onClick = it) { Text(stringResource(R.string.directions)) } }
            OutlinedButton(onClick = onPlanHere) { Text(stringResource(R.string.plan_route_here)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

/** After a long-press: use the point for the route being planned. */
@Composable
private fun PointCard(
    point: GeoPoint,
    heightM: Double?,
    hasStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEnd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PeekSurface(modifier) {
        Text(stringResource(R.string.point_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        CoordinatesText(point, heightM)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart) { Text(stringResource(R.string.plan_start_here)) }
            if (hasStart) OutlinedButton(onClick = onStop) { Text(stringResource(R.string.plan_stop_here)) }
            Button(onClick = onEnd) { Text(stringResource(R.string.plan_end_here)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

/** Decimal degrees, UTM 28N and the height at a point (spec: Phase 1, long-press coordinates). */
@Composable
private fun CoordinatesText(point: GeoPoint, heightM: Double?) {
    val utm = remember(point) { Utm.fromLatLon(point.lat, point.lon).format() }
    Text(
        text = String.format(java.util.Locale.ROOT, "%.5f, %.5f", point.lat, point.lon),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = listOfNotNull(
            stringResource(R.string.coordinates_utm, utm),
            heightM?.let { stringResource(R.string.coordinates_height, formatElevation(it.roundToInt())) },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The route being planned: what it is like, and what to do with it (spec: Phase 0.7). */
@Composable
private fun PlannerCard(
    points: PlanPoints,
    elevation: ElevationGrid?,
    result: PlanResult,
    snapshot: StatusSnapshot,
    here: GeoPoint?,
    onFromHere: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onReverse: () -> Unit,
    onClear: () -> Unit,
    onPick: (PickRole) -> Unit,
    onSave: (String, List<GeoPoint>) -> Unit,
    onExport: (String, List<GeoPoint>) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val route = ((result as? PlanResult.Done)?.outcome as? RouteOutcome.Found)?.route
    val position = remember(route, here) {
        if (route == null || here == null) null else TrailPositions.of(routeEntry(route.points), here)
    }
    PeekSurface(modifier) {
        Text(points.title ?: stringResource(R.string.plan_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (points.isEmpty) {
            Text(stringResource(R.string.plan_intro), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.plan_long_press_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val message = when {
            points.start == null -> stringResource(R.string.plan_need_start)
            points.end == null -> stringResource(R.string.plan_need_end)
            result is PlanResult.Working -> stringResource(R.string.plan_working)
            result is PlanResult.Failed -> stringResource(R.string.plan_failed, result.message)
            result is PlanResult.Done -> when (val o = result.outcome) {
                is RouteOutcome.TooFar -> when (o.index) {
                    0 -> stringResource(R.string.plan_too_far_start)
                    points.all.size - 1 -> stringResource(R.string.plan_too_far_end)
                    else -> stringResource(R.string.plan_too_far_stop, o.index)
                }
                is RouteOutcome.NoRoute -> stringResource(R.string.plan_no_route)
                is RouteOutcome.Found -> null
            }
            else -> null
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        if (route != null) {
            val summary = route.summary
            val profile = remember(route, elevation) { elevation?.let { ElevationProfiles.of(route.points, it) } }
            Text(
                text = if (profile != null) {
                    stringResource(R.string.plan_summary_climb, formatLength(summary.distanceM.toInt()) ?: "0 m", durationText(profile.minutes))
                } else {
                    stringResource(R.string.plan_summary, formatLength(summary.distanceM.toInt()) ?: "0 m", durationText(summary.flatMinutes))
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            profile?.let { HeightSummary(it) }
            positionText(position)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = VeredaColors.LocationBlue, fontWeight = FontWeight.SemiBold)
            }
            if (summary.mainNames.isNotEmpty()) {
                Text(stringResource(R.string.plan_mostly, summary.mainNames.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
            }
            val now = TrailRepository.now()
            if (summary.officialTrails.isNotEmpty()) {
                Text(stringResource(R.string.plan_official, summary.officialTrails.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                summary.officialTrails.forEach { code ->
                    val id = pt.vereda.core.model.Trail.idForCode(code)
                    val state = snapshot.stateOf(id, now)
                    if (state == StatusState.CLOSED || state == StatusState.PARTIAL) {
                        val label = stringResource(if (state == StatusState.CLOSED) R.string.status_closed else R.string.status_partial)
                        Text(
                            stringResource(R.string.plan_official_warning, code, label.lowercase()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state == StatusState.CLOSED) VeredaColors.ClosedText else VeredaColors.PartialText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(stringResource(R.string.plan_official_booking), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (summary.roadM >= 50) {
                Text(stringResource(R.string.plan_roads, formatLength(summary.roadM.toInt()).orEmpty()), style = MaterialTheme.typography.bodySmall)
            }
            if (summary.difficultM >= 20) {
                Text(
                    stringResource(R.string.plan_difficult, formatLength(summary.difficultM.toInt()).orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = VeredaColors.PartialText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (summary.tunnels > 0) Text(tunnelsText(summary.tunnels), style = MaterialTheme.typography.bodySmall)
            if (profile == null) {
                Text(stringResource(R.string.plan_climb_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            route.points.firstOrNull()?.let { start ->
                SafetySection(
                    WalkInfo(
                        key = "plan-${route.points.size}-${summary.distanceM.toInt()}",
                        point = start,
                        minutes = profile?.minutes ?: summary.flatMinutes,
                        highestM = profile?.maxM,
                        official = summary.officialTrails.isNotEmpty(),
                        levada = summary.mainNames.any { it.contains("levada", ignoreCase = true) },
                        tunnels = summary.tunnels,
                    ),
                )
            }
            Text(stringResource(R.string.community_route_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        points.stops.forEachIndexed { i, _ ->
            TextButton(onClick = { onRemoveStop(i) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.plan_remove_stop, i + 1), style = MaterialTheme.typography.bodySmall)
            }
        }

        val name = route?.let {
            points.title ?: it.summary.mainNames.firstOrNull()?.let { main -> stringResource(R.string.plan_route_name, main) }
                ?: stringResource(R.string.plan_route_name_plain, formatLength(it.summary.distanceM.toInt()).orEmpty())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (route != null && name != null) {
                Button(onClick = onStart) { Text(stringResource(R.string.nav_start)) }
                OutlinedButton(onClick = { onSave(name, route.points) }) { Text(stringResource(R.string.plan_save)) }
                OutlinedButton(onClick = { onExport(name, route.points) }) { Text(stringResource(R.string.export_gpx)) }
            }
            if (here != null && points.start != here) {
                OutlinedButton(onClick = onFromHere) { Text(stringResource(R.string.plan_from_location)) }
            }
            if (points.start != null && points.end != null) {
                OutlinedButton(onClick = onReverse) { Text(stringResource(R.string.plan_reverse)) }
            }
            OutlinedButton(onClick = { onPick(PickRole.START) }) {
                Text(stringResource(if (points.start == null) R.string.plan_pick_start_button else R.string.plan_move_start_button))
            }
            if (points.start != null && points.end != null) {
                OutlinedButton(onClick = { onPick(PickRole.STOP) }) { Text(stringResource(R.string.plan_pick_stop_button)) }
            }
            OutlinedButton(onClick = { onPick(PickRole.END) }) {
                Text(stringResource(if (points.end == null) R.string.plan_pick_end_button else R.string.plan_move_end_button))
            }
            TextButton(onClick = onClear) { Text(stringResource(if (points.isEmpty) R.string.close else R.string.plan_clear)) }
        }
    }
}

/** A planned route as a trail, so "where am I" works the same way on it. */
private fun routeEntry(points: List<GeoPoint>) = TrailEntry(
    trail = pt.vereda.core.model.Trail(
        "plan", TrailSource.IMPORT, null, "", null, null, null, pt.vereda.core.model.Island.MADEIRA,
    ),
    lengthM = null,
    sacScale = null,
    lines = listOf(points),
)

@Composable
internal fun PeekSurface(modifier: Modifier, content: @Composable () -> Unit) {
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

/** What *Directions* does, or null (no button) when neither the app nor Google Maps has a way. */
private fun directionsAction(hasPlan: Boolean, road: GeoPoint?, action: () -> Unit): (() -> Unit)? =
    if (hasPlan || road != null) action else null

/** Where *Directions* opens: one side Vereda, the other Google Maps (spec: Phase 0.8). */
@Composable
private fun DirectionsToggle(target: DirectionsTarget, onChange: (DirectionsTarget) -> Unit) {
    Text(stringResource(R.string.directions_open_in), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val options = listOf(DirectionsTarget.IN_APP to R.string.directions_in_app, DirectionsTarget.GOOGLE_MAPS to R.string.directions_google_maps)
        options.forEachIndexed { i, (option, label) ->
            SegmentedButton(
                selected = target == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(stringResource(label)) }
        }
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
private fun rememberMapViewWithLifecycle(onReady: (MapLibreMap) -> Unit): MapView {
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
                currentOnReady(map)
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

/** The part of the route still ahead of [progressM], for the navigation line. */
private fun aheadOf(route: NavRoute, progressM: Double): List<GeoPoint> {
    val line = route.line
    if (progressM <= 0.0) return line.points
    val i = line.segmentAt(progressM)
    return listOf(line.pointAt(progressM)) + line.points.drop(i + 1)
}
