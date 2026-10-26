package pt.vereda.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.vereda.app.R
import pt.vereda.app.data.DownloadState
import pt.vereda.app.data.MapLayerSettings
import pt.vereda.app.ui.maps.DownloadButtons
import pt.vereda.app.ui.maps.OfflineMapState
import pt.vereda.app.ui.maps.formatMegabytes
import pt.vereda.app.ui.theme.VeredaColors
import pt.vereda.core.geo.ScaleBar
import pt.vereda.core.maps.Resume

/** Top of the map: the attribution offline, or the offline map download while online. */
@Composable
fun MapBanner(offline: Boolean, state: OfflineMapState) {
    if (offline) {
        Text(
            text = stringResource(R.string.map_offline_attribution),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 4.dp).widthIn(max = 420.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val download = state.download
        val pkg = state.available
        Text(
            text = when {
                download is DownloadState.Running -> if (download.verifying) stringResource(R.string.maps_verifying)
                    else stringResource(R.string.maps_downloading, Resume.percent(download.doneBytes, download.totalBytes), formatMegabytes(download.totalBytes))
                download is DownloadState.Paused -> stringResource(R.string.maps_paused, Resume.percent(download.doneBytes, download.totalBytes))
                download is DownloadState.WaitingForWifi -> stringResource(R.string.maps_waiting_wifi)
                download is DownloadState.Failed -> stringResource(R.string.maps_failed, download.message)
                pkg != null -> stringResource(R.string.map_online_get_offline)
                else -> stringResource(R.string.map_dev_banner)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
        )
        if (pkg != null) DownloadButtons(state)
    }
}

@Composable
fun LayersButton(onClick: () -> Unit) {
    val label = stringResource(R.string.layers_title)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_layers), contentDescription = null, tint = Color.Unspecified)
        }
    }
}

/** A round distance and a bar of that length (spec: Phase 1, scale bar). */
@Composable
fun ScaleBarView(bar: ScaleBar, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = bar.label }) {
        Text(
            bar.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = VeredaColors.Ink,
            modifier = Modifier.background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(bar.widthDp.dp)
                .height(4.dp)
                .background(VeredaColors.Ink, RoundedCornerShape(1.dp)),
        )
    }
}

/** Layers sheet (spec: Phase 1): terrain shading and contours offline, and what Vereda draws. */
@Composable
fun LayersCard(
    settings: MapLayerSettings,
    offline: Boolean,
    onChange: (MapLayerSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PeekSurface(modifier) {
        Text(stringResource(R.string.layers_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!offline) {
            Text(
                stringResource(R.string.layers_offline_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ToggleRow(stringResource(R.string.layers_hillshade), settings.hillshade, enabled = offline) { onChange(settings.copy(hillshade = it)) }
        if (offline && settings.hillshade) {
            val strengthLabel = stringResource(R.string.layers_hillshade_strength)
            Slider(
                value = settings.hillshadeStrength,
                onValueChange = { onChange(settings.copy(hillshadeStrength = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = strengthLabel },
            )
        }
        ToggleRow(stringResource(R.string.layers_contours), settings.contours, enabled = offline) { onChange(settings.copy(contours = it)) }
        ToggleRow(stringResource(R.string.layers_community), settings.communityTrails) { onChange(settings.copy(communityTrails = it)) }
        ToggleRow(stringResource(R.string.layers_places), settings.places) { onChange(settings.copy(places = it)) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked && enabled, onCheckedChange = null, enabled = enabled)
    }
}
