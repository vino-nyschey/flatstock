package app.flatstock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsTest {
    private val supplies = listOf(
        SupplyState("Salt", Level.LOW, "ben", 200),
        SupplyState("Toilet paper", Level.OUT, "ben", 300),
        SupplyState("Oil", Level.OUT, "me", 300),
        SupplyState("Tabs", Level.FULL, "ben", 400),
        SupplyState("Bin bags", Level.OUT, "ben", 50),
    )

    @Test
    fun alertsOnlyForOthersChangesSinceLastCheck() {
        assertEquals(listOf("Toilet paper", "Salt"), newAlerts(supplies, since = 100, me = "me").map { it.name })
    }

    @Test
    fun levelFollowsPackCount() {
        assertEquals(Level.OUT, levelForCount(0, Level.FULL))
        assertEquals(Level.OUT, levelForCount(0, Level.LOW))
        assertEquals(Level.FULL, levelForCount(12, Level.OUT))
        assertEquals(Level.LOW, levelForCount(1, Level.LOW))
        assertEquals(Level.FULL, levelForCount(3, Level.FULL))
    }

    @Test
    fun summaryListsOutFirstThenLow() {
        assertEquals(listOf("Bin bags", "Oil", "Toilet paper", "Salt"), lowSummary(supplies).map { it.name })
    }
}
