package app.flatstock.domain

enum class Level {
    FULL, LOW, OUT;

    companion object {
        fun of(name: String?): Level = entries.firstOrNull { it.name == name } ?: FULL
    }
}

/**
 * Level after the pack count changed: no packs left means out; packs arriving for something that
 * was out make it full again; otherwise the level someone chose by hand stays.
 */
fun levelForCount(count: Long, current: Level): Level = when {
    count <= 0 -> Level.OUT
    current == Level.OUT -> Level.FULL
    else -> current
}

/** The part of a supply the reminders look at. */
data class SupplyState(val name: String, val level: Level, val changedBy: String, val changedAt: Long)

private val outFirst = compareByDescending<SupplyState> { it.level == Level.OUT }.thenBy { it.name.lowercase() }

/**
 * Supplies that someone else marked low or out after [since]; these trigger a "running out" alert.
 * Changes by [me] are skipped: I already know.
 */
fun newAlerts(supplies: List<SupplyState>, since: Long, me: String): List<SupplyState> =
    supplies.filter { it.level != Level.FULL && it.changedAt > since && it.changedBy != me }.sortedWith(outFirst)

/** Everything currently low or out, out first: the daily or weekly summary. */
fun lowSummary(supplies: List<SupplyState>): List<SupplyState> =
    supplies.filter { it.level != Level.FULL }.sortedWith(outFirst)
