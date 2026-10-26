package pt.vereda.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.vereda.app.R
import pt.vereda.app.ui.theme.VeredaColors
import pt.vereda.core.model.StatusState
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSource

/** Trail code with the yellow-over-red PR waymark, e.g. "PR 6". */
@Composable
fun PrBadge(code: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFD8D4C8)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Column {
                Box(Modifier.width(4.dp).height(5.dp).background(VeredaColors.WaymarkYellow))
                Box(Modifier.width(4.dp).height(5.dp).background(VeredaColors.WaymarkRed))
            }
            Text(text = code, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Status pill using the shared status colours (docs/design/README.md). */
@Composable
fun StatusChip(state: StatusState, modifier: Modifier = Modifier) {
    val (line, text, tint, label) = when (state) {
        StatusState.OPEN -> Style(VeredaColors.OpenLine, VeredaColors.OpenText, VeredaColors.OpenTint, R.string.status_open)
        StatusState.PARTIAL -> Style(VeredaColors.PartialLine, VeredaColors.PartialText, VeredaColors.PartialTint, R.string.status_partial)
        StatusState.CLOSED -> Style(VeredaColors.ClosedLine, VeredaColors.ClosedText, VeredaColors.ClosedTint, R.string.status_closed)
        StatusState.UNKNOWN -> Style(VeredaColors.UnknownLine, VeredaColors.UnknownText, VeredaColors.UnknownTint, R.string.status_unknown)
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = tint) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(7.dp).background(line, CircleShape))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = text,
            )
        }
    }
}

private data class Style(val line: Color, val text: Color, val tint: Color, val label: Int)

/** "Community" or "My import" in place of the PR badge for trails that aren't official. */
@Composable
fun SourceChip(source: TrailSource, modifier: Modifier = Modifier) {
    val (color, tint, label) = when (source) {
        TrailSource.OFFICIAL -> return
        TrailSource.COMMUNITY -> Triple(VeredaColors.CommunityLine, VeredaColors.CommunityTint, R.string.source_community)
        TrailSource.IMPORT -> Triple(VeredaColors.ImportLine, VeredaColors.ImportTint, R.string.source_import)
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = tint) {
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** The badge row every trail shows: PR code or source, plus official status. */
@Composable
fun TrailBadges(trail: Trail, state: StatusState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (trail.source == TrailSource.OFFICIAL) {
            trail.code?.let { PrBadge(it) }
            StatusChip(state)
        } else {
            SourceChip(trail.source)
        }
    }
}
