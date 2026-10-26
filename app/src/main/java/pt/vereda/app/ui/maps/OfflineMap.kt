package pt.vereda.app.ui.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vereda.app.R
import pt.vereda.app.VeredaApplication
import pt.vereda.app.data.DownloadState
import pt.vereda.app.data.ManifestState
import pt.vereda.app.data.MapPackages
import pt.vereda.core.maps.MapPackage
import pt.vereda.core.maps.PackageStatus
import pt.vereda.core.maps.Packages
import pt.vereda.core.maps.Resume
import java.util.Locale

/** "85 MB". */
fun formatMegabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.0f MB", bytes / 1_000_000.0)

/** Everything the offline map UI needs, from the app's MapPackages. */
class OfflineMapState(
    val maps: MapPackages,
    val manifest: ManifestState,
    val status: PackageStatus,
    val download: DownloadState,
    val wifiOnly: Boolean,
) {
    val available: MapPackage? get() = (manifest as? ManifestState.Ready)?.manifest?.find(Packages.MADEIRA)
    val installed: Boolean get() = status is PackageStatus.Installed
    val update: MapPackage? get() = (status as? PackageStatus.Installed)?.update
}

@Composable
fun rememberOfflineMapState(): OfflineMapState {
    val app = LocalContext.current.applicationContext as VeredaApplication
    val manifest by app.maps.manifest.collectAsStateWithLifecycle()
    val installed by app.maps.installed.collectAsStateWithLifecycle()
    val download by app.maps.download.collectAsStateWithLifecycle()
    val wifiOnly by app.settings.wifiOnly.collectAsStateWithLifecycle()
    val available = (manifest as? ManifestState.Ready)?.manifest?.find(Packages.MADEIRA)
    val status = remember(installed, available) { Packages.status(installed, available, MapPackages.APP_MAPS_VERSION) }
    return OfflineMapState(app.maps, manifest, status, download, wifiOnly)
}

/** The full card for Me → Offline map (spec: Phase 1, storage screen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfflineMapCard(state: OfflineMapState, onWifiOnly: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.maps_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.maps_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val pkg = state.available
        val status = state.status
        Text(
            text = when {
                status is PackageStatus.Installed && state.update != null ->
                    stringResource(R.string.maps_update_available, status.version, state.update!!.version, formatMegabytes(state.update!!.totalBytes))
                status is PackageStatus.Installed -> stringResource(R.string.maps_installed, status.version)
                pkg != null -> stringResource(R.string.maps_not_installed, formatMegabytes(pkg.totalBytes))
                state.manifest is ManifestState.Loading -> stringResource(R.string.maps_checking)
                else -> stringResource(R.string.maps_unavailable)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        DownloadProgress(state.download)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadButtons(state)
            if (state.installed && state.download !is DownloadState.Running) {
                TextButton(onClick = { state.maps.delete() }) { Text(stringResource(R.string.maps_delete)) }
            }
            if (state.manifest is ManifestState.Failed) {
                TextButton(onClick = { state.maps.refreshManifestAsync() }) { Text(stringResource(R.string.retry)) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = state.wifiOnly, role = Role.Switch, onValueChange = onWifiOnly)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.maps_wifi_only), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.wifiOnly, onCheckedChange = null)
        }
    }
}

@Composable
fun DownloadProgress(download: DownloadState) {
    when (download) {
        is DownloadState.Running -> {
            LinearProgressIndicator(
                progress = { Resume.percent(download.doneBytes, download.totalBytes) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (download.verifying) stringResource(R.string.maps_verifying)
                else stringResource(R.string.maps_downloading, Resume.percent(download.doneBytes, download.totalBytes), formatMegabytes(download.totalBytes)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is DownloadState.Paused -> Text(
            stringResource(R.string.maps_paused, Resume.percent(download.doneBytes, download.totalBytes)),
            style = MaterialTheme.typography.bodySmall,
        )
        DownloadState.WaitingForWifi -> Text(stringResource(R.string.maps_waiting_wifi), style = MaterialTheme.typography.bodySmall)
        is DownloadState.Failed -> Text(
            stringResource(R.string.maps_failed, download.message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        DownloadState.Idle -> Unit
    }
}

/** Download / Update / Pause / Resume, whichever applies. */
@Composable
fun DownloadButtons(state: OfflineMapState) {
    val target = state.update ?: state.available?.takeIf { !state.installed }
    when (state.download) {
        is DownloadState.Running -> OutlinedButton(onClick = { state.maps.pause() }) { Text(stringResource(R.string.maps_pause)) }
        is DownloadState.Paused, is DownloadState.Failed, DownloadState.WaitingForWifi ->
            if (target != null) Button(onClick = { state.maps.download(target) }) { Text(stringResource(R.string.maps_resume)) }
        DownloadState.Idle -> if (target != null) {
            Button(onClick = { state.maps.download(target) }) {
                Text(stringResource(if (state.installed) R.string.maps_update else R.string.maps_download, formatMegabytes(target.totalBytes)))
            }
        }
    }
}
