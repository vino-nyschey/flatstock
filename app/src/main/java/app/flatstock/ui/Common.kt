package app.flatstock.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.flatstock.R
import app.flatstock.data.EmailNotVerifiedException
import app.flatstock.domain.Level
import app.flatstock.ui.theme.LevelColors
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

fun formatCents(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { currency = Currency.getInstance("EUR") }.format(cents / 100.0)

/** Cents as the user would type them, e.g. "3,49" in German. */
fun centsInput(cents: Long): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(cents / 100.0)

fun formatDate(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun levelColor(level: Level): Color = when (level) {
    Level.FULL -> LevelColors.full
    Level.LOW -> LevelColors.low
    Level.OUT -> LevelColors.out
}

@Composable
fun levelLabel(level: Level): String = stringResource(
    when (level) {
        Level.FULL -> R.string.level_full
        Level.LOW -> R.string.level_low
        Level.OUT -> R.string.level_out
    }
)

@Composable
fun Pill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Runs suspend actions from buttons, with a busy flag and the error message to show. */
class ActionRunner(private val scope: CoroutineScope, private val res: Resources) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    fun run(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: EmailNotVerifiedException) {
                message = res.getString(R.string.error_email_not_verified)
            } catch (e: FirebaseFirestoreException) {
                message = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) res.getString(R.string.error_permission_denied)
                else e.localizedMessage ?: e.toString()
            } catch (e: Exception) {
                message = e.localizedMessage ?: e.toString()
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberActionRunner(): ActionRunner {
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    return remember { ActionRunner(scope, res) }
}

val EMAIL_RE = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
