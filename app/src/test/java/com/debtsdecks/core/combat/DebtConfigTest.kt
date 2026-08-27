package com.debtsdecks.core.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-function tests for the Debt economy formulas. No [CombatEngine] instance needed —
 * these are self-contained math checks on named constants defined in [DebtConfig].
 */
class DebtConfigTest {

    @Test
    fun `applyInterest is a no-op when debt is zero`() {
        assertEquals(0, DebtConfig.applyInterest(0))
    }

    @Test
    fun `applyInterest grows debt by the ceiling of the interest rate`() {
        // 100 * 10% = 10 exactly
        assertEquals(115, DebtConfig.applyInterest(100))
        // 3 * 10% = 0.3 -> ceil = 1
        assertEquals(4, DebtConfig.applyInterest(3))
    }

    @Test
    fun `applyInterest clamps growth at the debt cap`() {
        assertEquals(DebtConfig.INTEREST_CAP, DebtConfig.applyInterest(195))
        assertEquals(DebtConfig.INTEREST_CAP, DebtConfig.applyInterest(DebtConfig.INTEREST_CAP))
    }

    @Test
    fun `garnishAmount is zero when debt is zero`() {
        assertEquals(0, DebtConfig.garnishAmount(rawGold = 100, debt = 0))
    }

    @Test
    fun `garnishAmount ramps up with debt below the break threshold`() {
        // debt=15 is half of BREAK_THRESHOLD(30) -> rate = 0.5 * MAX_GARNISH_RATE(0.6) = 0.30
        assertEquals(30, DebtConfig.garnishAmount(rawGold = 100, debt = 15))
    }

    @Test
    fun `garnishAmount caps at MAX_GARNISH_RATE once debt reaches the break threshold`() {
        assertEquals(60, DebtConfig.garnishAmount(rawGold = 100, debt = DebtConfig.BREAK_THRESHOLD))
        // Debt well beyond the threshold still caps at the same max rate, never exceeding it.
        assertEquals(60, DebtConfig.garnishAmount(rawGold = 100, debt = DebtConfig.BREAK_THRESHOLD * 4))
    }

    @Test
    fun `EXECUTION_THRESHOLD sits above BREAK_THRESHOLD for a playable leverage range`() {
        assertTrue(DebtConfig.EXECUTION_THRESHOLD > DebtConfig.BREAK_THRESHOLD)
        assertEquals(50, DebtConfig.EXECUTION_THRESHOLD)
    }
}
