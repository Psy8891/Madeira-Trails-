package pt.vereda.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.vereda.app.R
import pt.vereda.app.ui.theme.VeredaColors
import pt.vereda.core.geo.ElevationProfile
import kotlin.math.max
import kotlin.math.roundToInt

/** "↑ 850 m · ↓ 120 m · 520–1,862 m" and a small filled height graph (spec: Phase 1). */
@Composable
fun HeightSummary(profile: ElevationProfile, modifier: Modifier = Modifier) {
    val up = formatElevation(profile.ascentM.roundToInt())
    val down = formatElevation(profile.descentM.roundToInt())
    val low = formatElevation(profile.minM.roundToInt())
    val high = formatElevation(profile.maxM.roundToInt())
    val description = stringResource(R.string.height_description, up, down, low, high)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(
            text = stringResource(R.string.height_summary, up, down, high),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        HeightGraph(profile, Modifier.fillMaxWidth().height(64.dp).padding(top = 4.dp))
    }
}

@Composable
fun HeightGraph(profile: ElevationProfile, modifier: Modifier = Modifier) {
    val points = profile.graph
    if (points.size < 2) return
    val fill = VeredaColors.LaurelTint
    val line = VeredaColors.Laurel
    val labelStyle = MaterialTheme.typography.labelSmall
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val total = max(points.last().first, 1.0)
            // Leave room so a flat walk doesn't sit on the floor of the graph.
            val low = profile.minM - 10
            val high = max(profile.maxM, low + 50)
            fun x(d: Double) = (d / total * size.width).toFloat()
            fun y(h: Double) = (size.height - (h - low) / (high - low) * size.height).toFloat()
            val outline = Path().apply {
                moveTo(x(points.first().first), y(points.first().second))
                points.drop(1).forEach { (d, h) -> lineTo(x(d), y(h)) }
            }
            val area = Path().apply {
                addPath(outline)
                lineTo(x(points.last().first), size.height)
                lineTo(x(points.first().first), size.height)
                close()
            }
            drawPath(area, fill)
            drawPath(outline, line, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
            drawLine(line.copy(alpha = 0.25f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Text(
            formatElevation(profile.maxM.roundToInt()),
            style = labelStyle,
            color = VeredaColors.InkSecondary,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            formatElevation(profile.minM.roundToInt()),
            style = labelStyle,
            color = VeredaColors.InkSecondary,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
