package com.debtsdecks.core.combat

import org.junit.jupiter.api.Assertions.assertEquals
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
        // debt=15 is half of BREAK_THRESHOLD(30) -> rate = 0.5 * MAX_GARNISH_RATE(0.75) = 0.375
        assertEquals(37, DebtConfig.garnishAmount(rawGold = 100, debt = 15))
    }

    @Test
    fun `garnishAmount caps at MAX_GARNISH_RATE once debt reaches the break threshold`() {
        assertEquals(75, DebtConfig.garnishAmount(rawGold = 100, debt = DebtConfig.BREAK_THRESHOLD))
        // Debt well beyond the threshold still caps at the same max rate, never exceeding it.
        assertEquals(75, DebtConfig.garnishAmount(rawGold = 100, debt = DebtConfig.BREAK_THRESHOLD * 4))
    }

    @Test
    fun `usuryDamage is zero below the threshold`() {
        // Usury starts at Debt > half of max HP (50 -> threshold 25).
        assertEquals(0, DebtConfig.usuryDamage(debt = 25, maxHp = 50))
        assertEquals(0, DebtConfig.usuryDamage(debt = 0, maxHp = 50))
    }

    @Test
    fun `usuryDamage burns the overflow above the threshold`() {
        // Debt 40 vs maxHp 50 -> threshold 25 -> burns 15 HP.
        assertEquals(15, DebtConfig.usuryDamage(debt = 40, maxHp = 50))
    }

    @Test
    fun `usuryDamage never kills below 1 HP`() {
        assertEquals(49, DebtConfig.usuryDamage(debt = 200, maxHp = 50))
    }
}
