package pt.vereda.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vereda.app.R
import pt.vereda.app.VeredaApplication
import pt.vereda.app.data.WarningsState
import pt.vereda.app.ui.map.durationText
import pt.vereda.app.ui.theme.VeredaColors
import pt.vereda.core.catalog.ChecklistItem
import pt.vereda.core.catalog.Checklists
import pt.vereda.core.catalog.WalkFacts
import pt.vereda.core.catalog.WarningArea
import pt.vereda.core.catalog.WarningKind
import pt.vereda.core.catalog.WarningLevel
import pt.vereda.core.catalog.WeatherWarning
import pt.vereda.core.catalog.WeatherWarnings
import pt.vereda.core.geo.GeoPoint
import pt.vereda.core.geo.Sun
import pt.vereda.core.model.Island
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What a card knows about the walk, for daylight, warnings and the checklist (spec: Safety). */
data class WalkInfo(
    val key: String,
    val point: GeoPoint,
    val minutes: Int?,
    val highestM: Double?,
    val official: Boolean,
    val levada: Boolean,
    val tunnels: Int?,
)

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** Sunset and daylight left, IPMA warnings and "Before you go" for a trail or a planned route. */
@Composable
fun SafetySection(info: WalkInfo, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as VeredaApplication
    val warningsState by app.weather.state.collectAsStateWithLifecycle()
    val installedMaps by app.maps.installed.collectAsStateWithLifecycle()
    val offlineMap = remember(installedMaps) { app.maps.installedPaths(installedMaps) != null }
    LaunchedEffect(Unit) { app.weather.refreshIfOlderThan() }
    val now = remember(info.key) { Instant.now() }
    val island = if (info.point.lat > 32.95) Island.PORTO_SANTO else Island.MADEIRA
    val zone = ZoneId.systemDefault()

    val day = remember(info.point, now) { Sun.day(now.atOffset(ZoneOffset.UTC).toLocalDate(), info.point.lat, info.point.lon) }
    val daylight = remember(info.point, now) { Sun.daylightLeft(now, info.point.lat, info.point.lon).toMinutes().toInt() }
    val warnings = (warningsState as? WarningsState.Loaded)?.let { WeatherWarnings.relevant(it.warnings, island, now) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Daylight
        if (day != null) {
            val sunset = day.sunset.atZone(zone).format(TIME)
            val text = when {
                daylight <= 0 -> stringResource(R.string.safety_after_sunset, sunset)
                else -> stringResource(R.string.safety_daylight, sunset, durationText(daylight))
            }
            val tight = info.minutes != null && (daylight <= 0 || info.minutes + Checklists.MARGIN_MIN > daylight)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (tight) VeredaColors.PartialText else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (tight) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (tight && daylight > 0 && info.minutes != null) {
                Text(
                    stringResource(R.string.safety_daylight_tight, durationText(info.minutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = VeredaColors.PartialText,
                )
            }
        }

        // IPMA warnings
        when {
            warnings == null -> Text(
                stringResource(R.string.safety_warnings_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            warnings.isEmpty() -> Text(
                stringResource(R.string.safety_warnings_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> warnings.forEach { WarningLine(it, zone) }
        }

        // Before you go
        val facts = WalkFacts(
            official = info.official,
            levada = info.levada,
            tunnels = info.tunnels,
            minutes = info.minutes,
            highestM = info.highestM,
            daylightLeftMinutes = if (day == null) null else daylight,
            warnings = !warnings.isNullOrEmpty(),
            offlineMap = offlineMap,
        )
        Checklist(info.key, Checklists.items(facts))
    }
}

@Composable
private fun WarningLine(w: WeatherWarning, zone: ZoneId) {
    val (bg, fg) = when (w.level) {
        WarningLevel.RED -> VeredaColors.ClosedTint to VeredaColors.ClosedText
        WarningLevel.ORANGE -> Color(0xFFFFE3CC) to Color(0xFF9A3B00)
        else -> VeredaColors.PartialTint to VeredaColors.PartialText
    }
    val text = stringResource(
        R.string.safety_warning_line,
        levelName(w.level),
        kindName(w),
        areaName(w.area),
        w.end.atZone(zone).format(TIME),
    )
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun levelName(level: WarningLevel): String = stringResource(
    when (level) {
        WarningLevel.RED -> R.string.warning_red
        WarningLevel.ORANGE -> R.string.warning_orange
        else -> R.string.warning_yellow
    },
)

@Composable
private fun kindName(w: WeatherWarning): String = when (w.kind) {
    WarningKind.RAIN -> stringResource(R.string.warning_rain)
    WarningKind.WIND -> stringResource(R.string.warning_wind)
    WarningKind.THUNDER -> stringResource(R.string.warning_thunder)
    WarningKind.FOG -> stringResource(R.string.warning_fog)
    WarningKind.HOT -> stringResource(R.string.warning_hot)
    WarningKind.COLD -> stringResource(R.string.warning_cold)
    WarningKind.SNOW -> stringResource(R.string.warning_snow)
    WarningKind.SEA -> stringResource(R.string.warning_sea)
    WarningKind.OTHER -> w.kindName
}

@Composable
private fun areaName(area: WarningArea): String = stringResource(
    when (area) {
        WarningArea.MOUNTAINS -> R.string.area_mountains
        WarningArea.NORTH_COAST -> R.string.area_north
        WarningArea.SOUTH_COAST -> R.string.area_south
        WarningArea.PORTO_SANTO -> R.string.area_porto_santo
    },
)

/** "Before you go": tick-off list, kept while the card is open. */
@Composable
private fun Checklist(key: String, items: List<ChecklistItem>) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val ticked = remember(key) { mutableStateMapOf<ChecklistItem, Boolean>() }
    val done = items.count { ticked[it] == true }
    TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
        Text(
            stringResource(R.string.checklist_title, done, items.size) + if (open) " ▲" else " ▼",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    if (!open) return
    items.forEach { item ->
        val checked = ticked[item] == true
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { ticked[item] = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(checklistText(item), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun checklistText(item: ChecklistItem): String = stringResource(
    when (item) {
        ChecklistItem.STATUS -> R.string.check_status
        ChecklistItem.BOOKING -> R.string.check_booking
        ChecklistItem.WEATHER -> R.string.check_weather
        ChecklistItem.TORCH -> R.string.check_torch
        ChecklistItem.WATER -> R.string.check_water
        ChecklistItem.LAYERS -> R.string.check_layers
        ChecklistItem.FOOTWEAR -> R.string.check_footwear
        ChecklistItem.PHONE -> R.string.check_phone
        ChecklistItem.TELL_SOMEONE -> R.string.check_tell
        ChecklistItem.OFFLINE_MAP -> R.string.check_offline_map
        ChecklistItem.START_EARLIER -> R.string.check_start_earlier
    },
)
