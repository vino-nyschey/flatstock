package app.flatstock.domain

/*
 * Splitwise-style bookkeeping, free of Android and Firebase so it can be unit tested.
 * All amounts are whole euro cents, so shares always add up exactly.
 * web/js/ledger.js is a line-by-line copy for the web app; keep both in step.
 */

/** A purchase someone paid for, shared by [sharers]. */
data class Expense(val payer: String, val amountCents: Long, val sharers: Collection<String>)

/** Money handed over to settle up: [from] paid [to]. */
data class Payment(val from: String, val to: String, val amountCents: Long)

/** A suggested "from pays to" to bring balances back to zero. */
data class Transfer(val from: String, val to: String, val amountCents: Long)

/**
 * Splits [amountCents] equally. The leftover cents (at most one each) go to the first sharers in
 * id order, so the result always adds up to the full amount and is the same on every device.
 */
fun splitEqually(amountCents: Long, sharers: Collection<String>): Map<String, Long> {
    require(amountCents >= 0) { "negative amount" }
    val ids = sharers.distinct().sorted()
    if (ids.isEmpty()) return emptyMap()
    val base = amountCents / ids.size
    val rest = amountCents % ids.size
    return ids.mapIndexed { i, id -> id to base + if (i < rest) 1 else 0 }.toMap()
}

/**
 * Net balance per person across all purchases and payments: positive means the others owe them,
 * negative means they owe. The values always sum to zero.
 */
fun balances(expenses: List<Expense>, payments: List<Payment>): Map<String, Long> {
    val net = mutableMapOf<String, Long>()
    fun add(id: String, cents: Long) {
        net[id] = (net[id] ?: 0L) + cents
    }
    for (e in expenses) {
        if (e.amountCents <= 0 || e.sharers.isEmpty()) continue
        add(e.payer, e.amountCents)
        splitEqually(e.amountCents, e.sharers).forEach { (id, share) -> add(id, -share) }
    }
    for (p in payments) {
        if (p.amountCents <= 0 || p.from == p.to) continue
        // Paying back lowers what you owe; receiving lowers what you are owed.
        add(p.from, p.amountCents)
        add(p.to, -p.amountCents)
    }
    return net
}

private val biggestFirst = compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key }

/**
 * Who pays whom to settle everything: the biggest debtor pays the biggest creditor, repeated.
 * Needs at most (people - 1) transfers. Ties are broken by id so every device suggests the same.
 */
fun settle(balances: Map<String, Long>): List<Transfer> {
    val debtors = balances.filterValues { it < 0 }.mapValues { -it.value }.toMutableMap()
    val creditors = balances.filterValues { it > 0 }.toMutableMap()
    val transfers = mutableListOf<Transfer>()
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        val from = debtors.entries.sortedWith(biggestFirst).first().key
        val to = creditors.entries.sortedWith(biggestFirst).first().key
        val amount = minOf(debtors.getValue(from), creditors.getValue(to))
        transfers += Transfer(from, to, amount)
        debtors[from] = debtors.getValue(from) - amount
        creditors[to] = creditors.getValue(to) - amount
        if (debtors[from] == 0L) debtors.remove(from)
        if (creditors[to] == 0L) creditors.remove(to)
    }
    return transfers
}

private val MONEY = Regex("""\d+([.]\d{0,2})?|[.]\d{1,2}""")

/** Accepts "3,49", "3.49", "€ 3.49" or "3"; null when empty, not a number or not positive. */
fun parseCents(text: String): Long? {
    val clean = text.replace("€", "").replace(" ", "").replace(',', '.').trim()
    if (!MONEY.matches(clean)) return null
    return Math.round(clean.toDouble() * 100).takeIf { it > 0 }
}
