package pt.vereda.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pt.vereda.app.R
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** "4.3 km" or "850 m", with the phone's decimal separator. */
fun formatLength(metres: Int?): String? = when {
    metres == null || metres <= 0 -> null
    metres < 1000 -> "$metres m"
    else -> String.format(Locale.getDefault(), "%.1f km", metres / 1000.0)
}

/** "1,654 m" with the phone's grouping. */
fun formatElevation(metres: Int): String = String.format(Locale.getDefault(), "%,d m", metres)

/** "Checked 12 min ago", "Checked 3 h ago", "Checked 2 days ago". */
@Composable
fun checkedAgo(checkedAt: Instant, now: Instant): String {
    val age = Duration.between(checkedAt, now).coerceAtLeast(Duration.ZERO)
    return when {
        age.toMinutes() < 1 -> stringResource(R.string.checked_just_now)
        age.toHours() < 1 -> stringResource(R.string.checked_minutes_ago, age.toMinutes().toInt())
        age.toDays() < 1 -> stringResource(R.string.checked_hours_ago, age.toHours().toInt())
        else -> stringResource(R.string.checked_days_ago, age.toDays().toInt())
    }
}
