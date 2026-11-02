package app.flatstock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {

    @Test
    fun splitEvenly() {
        assertEquals(mapOf("a" to 300L, "b" to 300L, "c" to 300L), splitEqually(900, listOf("a", "b", "c")))
    }

    @Test
    fun leftoverCentsGoToFirstIdsAndAddUp() {
        val shares = splitEqually(1000, listOf("c", "a", "b"))
        assertEquals(mapOf("a" to 334L, "b" to 333L, "c" to 333L), shares)
        assertEquals(1000L, shares.values.sum())
    }

    @Test
    fun splitIgnoresDuplicatesAndHandlesEmpty() {
        assertEquals(mapOf("a" to 250L, "b" to 250L), splitEqually(500, listOf("a", "b", "a")))
        assertEquals(emptyMap<String, Long>(), splitEqually(500, emptyList()))
        assertEquals(mapOf("a" to 0L, "b" to 0L), splitEqually(0, listOf("a", "b")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAmountRejected() {
        splitEqually(-1, listOf("a"))
    }

    @Test
    fun payerNotSharingIsOwedEverything() {
        // Anna buys oil for Ben and Cem only.
        val b = balances(listOf(Expense("anna", 600, listOf("ben", "cem"))), emptyList())
        assertEquals(mapOf("anna" to 600L, "ben" to -300L, "cem" to -300L), b)
    }

    @Test
    fun balancesNetAcrossPurchases() {
        val b = balances(
            listOf(
                Expense("anna", 900, listOf("anna", "ben", "cem")), // 3.00 each
                Expense("ben", 600, listOf("anna", "ben", "cem")), // 2.00 each
                Expense("cem", 300, listOf("anna", "ben")), // 1.50 each
            ),
            emptyList(),
        )
        assertEquals(mapOf("anna" to 250L, "ben" to -50L, "cem" to -200L), b)
        assertEquals(0L, b.values.sum())
    }

    @Test
    fun paymentsReduceDebts() {
        val b = balances(
            listOf(Expense("anna", 900, listOf("anna", "ben", "cem"))),
            listOf(Payment("ben", "anna", 300)),
        )
        assertEquals(mapOf("anna" to 300L, "ben" to 0L, "cem" to -300L), b)
    }

    @Test
    fun zeroAndInvalidEntriesIgnored() {
        val b = balances(
            listOf(Expense("anna", 0, listOf("anna", "ben")), Expense("ben", 500, emptyList())),
            listOf(Payment("ben", "ben", 100), Payment("ben", "anna", 0)),
        )
        assertTrue(b.isEmpty())
    }

    @Test
    fun oddCentsStillSumToZero() {
        val b = balances(
            listOf(Expense("a", 1001, listOf("a", "b", "c")), Expense("b", 7, listOf("a", "b", "c"))),
            emptyList(),
        )
        assertEquals(0L, b.values.sum())
    }

    @Test
    fun settleSimple() {
        assertEquals(
            listOf(Transfer("cem", "anna", 200), Transfer("ben", "anna", 50)),
            settle(mapOf("anna" to 250L, "ben" to -50L, "cem" to -200L)),
        )
    }

    @Test
    fun settleSeveralCreditorsClearsEveryone() {
        val balances = mapOf("a" to 500L, "b" to 300L, "c" to -600L, "d" to -200L)
        val transfers = settle(balances)
        assertTrue(transfers.size <= balances.size - 1)
        val after = balances.toMutableMap()
        transfers.forEach {
            after[it.from] = after.getValue(it.from) + it.amountCents
            after[it.to] = after.getValue(it.to) - it.amountCents
        }
        assertTrue(after.values.all { it == 0L })
    }

    @Test
    fun settleNothingWhenEven() {
        assertTrue(settle(mapOf("a" to 0L, "b" to 0L)).isEmpty())
        assertTrue(settle(emptyMap()).isEmpty())
    }

    @Test
    fun recordingSuggestedTransfersSettlesUp() {
        val expenses = listOf(
            Expense("anna", 1299, listOf("anna", "ben", "cem", "dana")),
            Expense("dana", 455, listOf("ben", "dana")),
        )
        val payments = settle(balances(expenses, emptyList())).map { Payment(it.from, it.to, it.amountCents) }
        assertTrue(balances(expenses, payments).values.all { it == 0L })
    }

    @Test
    fun parseCentsAcceptsCommonFormats() {
        assertEquals(349L, parseCents("3,49"))
        assertEquals(349L, parseCents("3.49"))
        assertEquals(349L, parseCents("€ 3.49"))
        assertEquals(300L, parseCents("3"))
        assertEquals(350L, parseCents("3,5"))
        assertEquals(50L, parseCents(",50"))
        assertEquals(1999L, parseCents("19.99"))
    }

    @Test
    fun parseCentsRejectsBadInput() {
        assertNull(parseCents(""))
        assertNull(parseCents("abc"))
        assertNull(parseCents("0"))
        assertNull(parseCents("-2"))
        assertNull(parseCents("1.234"))
        assertNull(parseCents("1.2.3"))
    }
}
