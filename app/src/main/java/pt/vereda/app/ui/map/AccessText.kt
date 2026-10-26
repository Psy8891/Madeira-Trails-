package pt.vereda.app.ui.map

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pt.vereda.app.R
import pt.vereda.app.ui.components.formatLength
import pt.vereda.core.catalog.PlaceKind
import pt.vereda.core.catalog.RoadAccess
import pt.vereda.core.catalog.TrailEntry
import pt.vereda.core.catalog.TrailPosition

/** "Start: 2.4 km walk from the ER 228, via Caminho Real da Encumeada · parking" and friends. */
@Composable
fun startText(access: RoadAccess?): String? {
    access ?: return null
    val text = when {
        access.approachM == null -> stringResource(R.string.start_no_road)
        access.onRoad -> access.road?.let { stringResource(R.string.start_on_named_road, it) }
            ?: stringResource(R.string.start_on_road)
        else -> walkText(access, R.string.start_walk_from_named_road, R.string.start_walk_from_road)
    }
    return withParking(text, access)
}

@Composable
fun endText(entry: TrailEntry): String? {
    if (entry.ends.size == 1) return stringResource(R.string.end_loop)
    val access = entry.endAccess ?: return null
    return when {
        access.approachM == null -> stringResource(R.string.end_no_road)
        access.onRoad -> access.road?.let { stringResource(R.string.end_on_named_road, it) }
            ?: stringResource(R.string.end_on_road)
        else -> stringResource(R.string.end_walk_to_road, formatLength(access.approachM).orEmpty())
    }
}

/** One way in, for the list of start options: "Boca da Corrida (usual start): 3.5 km walk …". */
@Composable
fun optionText(access: RoadAccess): String {
    val body = when {
        access.approachM == null -> stringResource(R.string.start_no_road)
        access.onRoad -> access.road?.let { stringResource(R.string.start_on_named_road, it) }
            ?: stringResource(R.string.place_by_road)
        else -> walkText(access, R.string.place_walk_from_named_road, R.string.place_walk_from_road)
    }
    val text = withParking(body, access)
    val name = access.name ?: return text
    return stringResource(if (access.known) R.string.start_option_known else R.string.start_option_named, name, text)
}

/** For a peak or another place: "1.2 km walk from the road, via Vereda da Ilha". */
@Composable
fun placeAccessText(access: RoadAccess?): String? {
    access ?: return null
    val text = when {
        access.approachM == null -> return null
        access.onRoad -> stringResource(R.string.place_by_road)
        else -> walkText(access, R.string.place_walk_from_named_road, R.string.place_walk_from_road)
    }
    return withParking(text, access)
}

@Composable
private fun walkText(access: RoadAccess, @StringRes namedRoad: Int, @StringRes anyRoad: Int): String {
    val distance = formatLength(access.approachM).orEmpty()
    val base = access.road?.let { stringResource(namedRoad, distance, it) } ?: stringResource(anyRoad, distance)
    return if (access.via.isEmpty()) base else stringResource(R.string.access_via, base, access.via.joinToString(", "))
}

@Composable
private fun withParking(text: String, access: RoadAccess): String =
    if (access.parking != null) stringResource(R.string.access_with_parking, text) else text

@Composable
fun positionText(position: TrailPosition?): String? = when (position) {
    null -> null
    is TrailPosition.OnTrail -> stringResource(
        R.string.position_on_trail,
        formatLength(position.walkedM.toInt()) ?: "0 m",
        formatLength(position.toEndM.toInt()) ?: "0 m",
    )
    is TrailPosition.OffTrail -> {
        val toTrail = formatLength(position.toTrailM.toInt()).orEmpty()
        val toStart = position.toStartM?.let { formatLength(it.toInt()) }
        if (toStart != null) stringResource(R.string.position_off_trail_start, toTrail, toStart)
        else stringResource(R.string.position_off_trail, toTrail)
    }
}

/** "45 min", "2 h 10 min". */
@Composable
fun durationText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
    minutes % 60 == 0 -> stringResource(R.string.duration_hours, minutes / 60)
    else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

@Composable
fun tunnelsText(count: Int): String = pluralStringResource(R.plurals.plan_tunnels, count, count)

/** The kind of place, as a word ("Viewpoint", "Miradouro"). */
@Composable
fun kindLabel(kind: PlaceKind): String = stringResource(
    when (kind) {
        PlaceKind.PEAK -> R.string.place_peak
        PlaceKind.VIEWPOINT -> R.string.place_viewpoint
        PlaceKind.WATERFALL -> R.string.place_waterfall
        PlaceKind.SPRING -> R.string.place_spring
        PlaceKind.WATER -> R.string.place_water
        PlaceKind.SHELTER -> R.string.place_shelter
        PlaceKind.HUT -> R.string.place_hut
        PlaceKind.PICNIC -> R.string.place_picnic
        PlaceKind.CAMP -> R.string.place_camp
        PlaceKind.CAVE -> R.string.place_cave
        PlaceKind.TRAILHEAD -> R.string.place_trailhead
        PlaceKind.TOILETS -> R.string.place_toilets
        PlaceKind.PARKING -> R.string.place_parking
        PlaceKind.BUS_STOP -> R.string.place_bus_stop
        PlaceKind.TUNNEL -> R.string.place_tunnel
    },
)
