package pt.vereda.app.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.vereda.app.R
import pt.vereda.app.ui.components.PrBadge
import pt.vereda.app.ui.components.StatusChip
import pt.vereda.core.model.OfficialTrails
import pt.vereda.core.model.StatusRules
import pt.vereda.core.model.Trail
import pt.vereda.core.model.TrailSearch
import java.time.Instant

@Composable
fun ExploreScreen(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { TrailSearch.search(OfficialTrails.all, query) }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.explore_title),
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.explore_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Text(
            text = stringResource(R.string.explore_count, results.size),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(results, key = { it.id }) { trail -> TrailCard(trail) }
        }
    }
}

@Composable
private fun TrailCard(trail: Trail) {
    // No status feed yet (Phase 0), so every trail is shown as unknown — never as open.
    val state = StatusRules.effectiveState(status = null, now = Instant.now())
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                trail.code?.let { PrBadge(it) }
                StatusChip(state)
            }
            Text(
                text = trail.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = listOfNotNull(trail.municipality, trail.fromTo).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
