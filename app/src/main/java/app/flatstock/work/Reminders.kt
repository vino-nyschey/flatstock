package app.flatstock.work

import android.content.Context
import app.flatstock.R
import app.flatstock.data.Supply
import app.flatstock.data.Wg
import app.flatstock.domain.Level
import app.flatstock.domain.SupplyState
import app.flatstock.domain.lowSummary
import app.flatstock.domain.newAlerts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class SummaryMode { OFF, DAILY, WEEKLY }

/**
 * Decides which reminders to show. There is no server: the phone checks the WG's supplies itself,
 * from [ReminderWorker] in the background and from the open app, and remembers what it already told.
 */
object Reminders {
    private const val PREFS = "reminders"
    private const val KEY_LAST_ALERT_CHECK = "lastAlertCheck"
    private const val KEY_LAST_SUMMARY_DAY = "lastSummaryDay"
    private const val KEY_MODE = "summaryMode"
    val SUMMARY_TIME: LocalTime = LocalTime.of(9, 0)
    val WEEKLY_DAY: DayOfWeek = DayOfWeek.SATURDAY

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): SummaryMode =
        SummaryMode.entries.firstOrNull { it.name == prefs(context).getString(KEY_MODE, null) } ?: SummaryMode.DAILY

    fun setMode(context: Context, mode: SummaryMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /** Notifies about supplies that flatmates marked low or out since the last check. */
    fun checkAlerts(context: Context, wg: Wg, supplies: List<Supply>, me: String) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val since = prefs.getLong(KEY_LAST_ALERT_CHECK, 0L)
        prefs.edit().putLong(KEY_LAST_ALERT_CHECK, now).apply()
        // First run after install or sign-in: start fresh instead of announcing old news.
        if (since == 0L) return
        val alerts = newAlerts(supplies.map { it.toState() }, since, me)
        if (alerts.isEmpty()) return
        val title = if (alerts.size == 1) {
            val a = alerts.first()
            context.getString(if (a.level == Level.OUT) R.string.notif_out_one else R.string.notif_low_one, a.name)
        } else {
            context.getString(R.string.notif_alert_many, alerts.size)
        }
        val body = alerts.joinToString("\n") { a ->
            context.getString(
                if (a.level == Level.OUT) R.string.notif_marked_out else R.string.notif_marked_low,
                wg.nameOf(a.changedBy), a.name,
            )
        }
        Notifications.show(context, Notifications.ID_ALERT, Notifications.CHANNEL_ALERTS, title, body)
    }

    /** Once a day (or on Saturdays) after 9:00: what is low or out right now. */
    fun checkSummary(context: Context, wg: Wg, supplies: List<Supply>, today: LocalDate = LocalDate.now(), time: LocalTime = LocalTime.now()) {
        val mode = mode(context)
        if (mode == SummaryMode.OFF || time.isBefore(SUMMARY_TIME)) return
        if (mode == SummaryMode.WEEKLY && today.dayOfWeek != WEEKLY_DAY) return
        val prefs = prefs(context)
        if (prefs.getLong(KEY_LAST_SUMMARY_DAY, -1L) == today.toEpochDay()) return
        prefs.edit().putLong(KEY_LAST_SUMMARY_DAY, today.toEpochDay()).apply()
        val low = lowSummary(supplies.map { it.toState() })
        if (low.isEmpty()) return
        Notifications.show(
            context, Notifications.ID_SUMMARY, Notifications.CHANNEL_SUMMARY,
            context.getString(R.string.notif_summary_title, wg.name, low.size),
            low.joinToString("\n") { "• ${it.name} – ${levelText(context, it)}" },
        )
    }

    private fun levelText(context: Context, s: SupplyState) =
        context.getString(if (s.level == Level.OUT) R.string.level_out else R.string.level_low)

    /** Forget the alert history, e.g. after signing out or switching WG. */
    fun reset(context: Context) {
        prefs(context).edit().remove(KEY_LAST_ALERT_CHECK).apply()
    }
}
