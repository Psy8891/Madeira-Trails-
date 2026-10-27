package pt.vereda.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.vereda.app.R
import pt.vereda.app.navigation.ActiveNavigation
import pt.vereda.app.navigation.CueText
import pt.vereda.app.ui.components.HeightGraph
import pt.vereda.app.ui.components.formatLength
import pt.vereda.app.ui.theme.VeredaColors
import pt.vereda.core.geo.ElevationGrid
import pt.vereda.core.geo.ElevationProfiles
import pt.vereda.core.geo.Geo
import pt.vereda.core.geo.HikeTime
import pt.vereda.core.geo.Sun
import pt.vereda.core.route.NavRoute
import pt.vereda.core.route.PlannedRoute
import pt.vereda.core.route.TripKind
import pt.vereda.core.route.Turn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Before starting (spec: Phase 1.5): one way or there and back, then Start. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartCard(
    title: String,
    route: PlannedRoute?,
    problem: String?,
    elevation: ElevationGrid?,
    kind: TripKind,
    onKind: (TripKind) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PeekSurface(modifier) {
        Text(stringResource(R.string.nav_start_title, title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        when {
            problem != null -> Text(stringResource(R.string.nav_cannot_start, problem), style = MaterialTheme.typography.bodyMedium)
            route == null -> Text(stringResource(R.string.nav_preparing), style = MaterialTheme.typography.bodyMedium)
            else -> {
                val loop = Geo.distanceM(route.points.first(), route.points.last()) <= NavRoute.LOOP_M
                val profile = remember(route, elevation) { elevation?.let { ElevationProfiles.of(route.points, it) } }
                if (loop) {
                    Text(stringResource(R.string.nav_loop), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val options = listOf(TripKind.ONE_WAY to R.string.nav_one_way, TripKind.THERE_AND_BACK to R.string.nav_there_and_back)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (option, label) ->
                            SegmentedButton(
                                selected = kind == option,
                                onClick = { onKind(option) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
                val twoWay = !loop && kind == TripKind.THERE_AND_BACK
                val distance = route.summary.distanceM * if (twoWay) 2 else 1
                val climb = when {
                    profile == null -> 0.0
                    twoWay -> profile.ascentM + profile.descentM
                    else -> profile.ascentM
                }
                Text(
                    stringResource(R.string.nav_trip_summary, formatLength(distance.toInt()) ?: "0 m", durationText(HikeTime.minutes(distance, climb))),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!loop) {
                    Text(
                        stringResource(if (twoWay) R.string.nav_there_and_back_note else R.string.nav_one_way_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.nav_start_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (route != null && problem == null) {
                Button(onClick = onStart) { Text(stringResource(R.string.nav_start)) }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** The next instruction at the top of the map while navigating. */
@Composable
fun CueBanner(nav: ActiveNavigation, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s = nav.state
    val cue = s.nextCue
    val (bg, fg, arrow, line1, line2) = when {
        s.arrived -> Banner(VeredaColors.OpenTint, VeredaColors.OpenText, "⚑", stringResource(R.string.cue_arrive), null)
        s.offRoute -> Banner(VeredaColors.ClosedTint, VeredaColors.ClosedText, "!", stringResource(R.string.nav_off_route, CueText.distance(s.offRouteM)), null)
        s.wrongWay -> Banner(VeredaColors.PartialTint, VeredaColors.PartialText, "↩", stringResource(R.string.nav_wrong_way), null)
        cue != null && cue.turn != Turn.ARRIVE && s.toNextCueM != null ->
            Banner(MaterialTheme.colorScheme.surface, VeredaColors.Ink, CueText.arrow(cue.turn), CueText.distance(s.toNextCueM!!), CueText.turn(context, cue))
        else -> Banner(MaterialTheme.colorScheme.surface, VeredaColors.Ink, "↑", stringResource(R.string.nav_follow_line, CueText.distance(s.remainingM)), null)
    }
    Surface(
        modifier = modifier
            .widthIn(max = 460.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = bg,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(arrow, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(line1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
                line2?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = fg) }
            }
        }
    }
}

private data class Banner(val bg: Color, val fg: Color, val arrow: String, val line1: String, val line2: String?)

/**
 * The bottom panel while navigating (spec: Phase 1.5): folded to one line with ▲ to open, or
 * open with the height graph, daylight and Stop, with ▼ to fold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavPanel(
    nav: ActiveNavigation,
    elevation: ElevationGrid?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRecenter: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = nav.state
    val zone = ZoneId.systemDefault()
    val now = remember(s) { Instant.now() }
    val summary = if (s.arrived) {
        val minutes = ((System.currentTimeMillis() - nav.startedAtMs) / 60_000).toInt()
        stringResource(R.string.nav_arrived_line, formatLength(s.progressM.toInt()) ?: "0 m", durationText(minutes))
    } else {
        stringResource(
            R.string.nav_done_left,
            formatLength(s.progressM.toInt()) ?: "0 m",
            formatLength(s.remainingM.toInt()) ?: "0 m",
            durationText(s.minutesLeft),
            now.plusSeconds(s.minutesLeft * 60L).atZone(zone).format(CLOCK),
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (expanded) Text(nav.route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                val label = stringResource(if (expanded) R.string.nav_show_less else R.string.nav_show_more)
                Surface(
                    onClick = onToggle,
                    shape = RoundedCornerShape(12.dp),
                    color = VeredaColors.LaurelTint,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = label },
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(if (expanded) "▼" else "▲", fontSize = 20.sp, color = VeredaColors.Laurel, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (expanded) {
                val profile = remember(nav.route, elevation) { elevation?.let { ElevationProfiles.of(nav.route.points, it) } }
                profile?.let {
                    HeightGraph(it, Modifier.fillMaxWidth().height(64.dp), markerM = s.progressM)
                }
                val fix = s.lastFix
                if (fix != null) {
                    val day = remember(now, fix.lat, fix.lon) { Sun.day(now.atOffset(java.time.ZoneOffset.UTC).toLocalDate(), fix.lat, fix.lon) }
                    val left = remember(now, fix.lat, fix.lon) { Sun.daylightLeft(now, fix.lat, fix.lon).toMinutes().toInt() }
                    if (day != null) {
                        val sunset = day.sunset.atZone(zone).format(CLOCK)
                        val tight = left <= 0 || s.minutesLeft + 30 > left
                        Text(
                            if (left <= 0) stringResource(R.string.safety_after_sunset, sunset) else stringResource(R.string.safety_daylight, sunset, durationText(left)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tight) VeredaColors.PartialText else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (tight) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!s.arrived) {
                        OutlinedButton(onClick = onRecenter) { Text(stringResource(R.string.nav_recenter)) }
                    }
                    Button(
                        onClick = onStop,
                        colors = if (s.arrived) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = VeredaColors.ClosedLine, contentColor = Color.White),
                    ) { Text(stringResource(if (s.arrived) R.string.nav_close else R.string.nav_stop)) }
                }
            }
        }
    }
}
