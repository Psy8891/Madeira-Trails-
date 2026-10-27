package pt.vereda.app.ui.map

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.vereda.app.R
import pt.vereda.app.ui.theme.VeredaColors
import pt.vereda.core.geo.Utm
import java.util.Locale
import kotlin.math.roundToInt

/** The red SOS button on the map. */
@Composable
fun SosButton(onClick: () -> Unit) {
    val label = stringResource(R.string.emergency_title)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = VeredaColors.ClosedLine,
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("SOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** Emergency card (spec: Safety): call 112, large coordinates, share the position by SMS. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmergencyCard(location: Location?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PeekSurface(modifier) {
        Text(stringResource(R.string.emergency_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VeredaColors.ClosedText)
        Button(
            onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }
            },
            colors = ButtonDefaults.buttonColors(containerColor = VeredaColors.ClosedLine, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.emergency_call_112), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (location == null) {
            Text(stringResource(R.string.emergency_no_position), style = MaterialTheme.typography.bodyMedium)
        } else {
            val decimal = String.format(Locale.ROOT, "%.5f, %.5f", location.latitude, location.longitude)
            val utm = Utm.fromLatLon(location.latitude, location.longitude).format()
            val accuracy = if (location.hasAccuracy()) location.accuracy.roundToInt() else null
            val ageMin = ((System.currentTimeMillis() - location.time) / 60_000).coerceAtLeast(0)
            Text(stringResource(R.string.emergency_your_position), style = MaterialTheme.typography.titleSmall)
            Text(decimal, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.coordinates_utm, utm), style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    accuracy?.let { stringResource(R.string.emergency_accuracy, it) },
                    if (ageMin >= 2) stringResource(R.string.emergency_age, ageMin.toInt()) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val message = stringResource(
                R.string.emergency_sms,
                decimal,
                accuracy?.toString() ?: "?",
                String.format(Locale.ROOT, "https://maps.google.com/?q=%.5f,%.5f", location.latitude, location.longitude),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", message)
                    try {
                        context.startActivity(sms)
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), null))
                    }
                }) { Text(stringResource(R.string.emergency_share_sms)) }
                OutlinedButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), null))
                }) { Text(stringResource(R.string.emergency_share_other)) }
            }
        }
        Text(stringResource(R.string.emergency_advice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}
