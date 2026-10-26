package pt.vereda.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.vereda.app.R
import pt.vereda.app.data.Language
import pt.vereda.core.model.AppLanguage

/** Me tab: lists arrive in Phase 2; the language switch is here now (spec: Phase 0.9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // The activity is recreated on a change, so reading once per screen is enough.
    val current = remember { Language.current(context) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.me_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.me_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val options = listOf(
            AppLanguage.SYSTEM to R.string.language_system,
            AppLanguage.ENGLISH to R.string.language_english,
            AppLanguage.PORTUGUESE to R.string.language_portuguese,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (language, label) ->
                SegmentedButton(
                    selected = current == language,
                    onClick = { if (language != current && activity != null) Language.set(activity, language) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
        Text(
            stringResource(R.string.language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
