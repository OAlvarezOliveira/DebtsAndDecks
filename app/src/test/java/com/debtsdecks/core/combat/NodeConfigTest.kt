package com.debtsdecks.core.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * C7 between-fight-node economy (R4 / T1.2): escalation math and the locked cost table from design
 * obs 1497 rev2. Concrete values, no tautologies.
 */
class NodeConfigTest {

    @Test
    fun `escalated cost follows the 1pt5 power floor table`() {
        val expected = listOf(
            (8 * 1.0).toInt(),        // node 1
            (8 * 1.5).toInt(),        // node 2
            (8 * 2.25).toInt(),       // node 3
            (8 * 3.375).toInt(),      // node 4
            (8 * 5.0625).toInt(),     // node 5
            (8 * 7.59375).toInt()     // node 6
        )
        for ((i, exp) in expected.withIndex()) {
            assertEquals(exp, NodeConfig.escalatedCost(8, i + 1), "node ${i + 1} buy cost")
        }
    }

    @Test
    fun `buy cost table matches the design`() {
        val buys = (1L..7L).map { NodeConfig.escalatedCost(NodeConfig.BUY_BASE, it.toInt()).toLong() }
        assertEquals(listOf(8L, 12L, 18L, 27L, 40L, 60L, 91L), buys)
    }

    @Test
    fun `remove cost table matches the design`() {
        val removes = (1..7).map { NodeConfig.escalatedCost(NodeConfig.REMOVE_BASE, it).toLong() }
        assertEquals(listOf(10L, 15L, 22L, 33L, 50L, 75L, 113L), removes)
    }

    @Test
    fun `repay fee table matches the design`() {
        val fees = (1..7).map { NodeConfig.escalatedCost(NodeConfig.REPAY_FEE_BASE, it).toLong() }
        assertEquals(listOf(3L, 4L, 6L, 10L, 15L, 22L, 34L), fees)
    }

    @Test
    fun `loan amounts match the design`() {
        val golds = (1..7).map { NodeConfig.escalatedCost(NodeConfig.LOAN_GOLD_BASE, it).toLong() }
        val debts = (1..7).map { NodeConfig.escalatedCost(NodeConfig.LOAN_DEBT_BASE, it).toLong() }
        assertEquals(listOf(12L, 18L, 27L, 40L, 60L, 91L, 136L), golds)
        assertEquals(listOf(8L, 12L, 18L, 27L, 40L, 60L, 91L), debts)
    }

    @Test
    fun `heal and offer sizes are positive`() {
        assertEquals(8, NodeConfig.HEAL_AMOUNT)
        assertEquals(3, NodeConfig.SHOP_OFFER_SIZE)
        assertEquals(3, NodeConfig.REMOVE_OFFER_SIZE)
    }
}