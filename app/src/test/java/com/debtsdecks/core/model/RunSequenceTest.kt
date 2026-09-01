package com.debtsdecks.core.model

import com.debtsdecks.core.simulation.TestAssetLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * C5 run-length-and-encounter-slots (R1 / R4.1): the 8-slot sequence parses, references only real
 * enemy ids, gives the final boss no card pick, and the rewarded fights (1-7) sum to exactly the
 * GDD target budget of 6-8 picks (design: 8). Concrete values, no tautologies.
 */
class RunSequenceTest {

    private val sequence = TestAssetLoader.loadSequence()
    private val rosterIds = TestAssetLoader.loadEnemies().map { it.id }.toSet()

    @Test
    fun `sequence has exactly eight slots`() {
        assertEquals(8, sequence.slots.size)
    }

    @Test
    fun `every slot enemy id resolves in the roster`() {
        for (slot in sequence.slots) {
            assertTrue(slot.enemyId in rosterIds, "slot enemy ${slot.enemyId} must exist in enemies/all.json")
        }
    }

    @Test
    fun `final boss slot is the collector with no card reward`() {
        val last = sequence.slots.last()
        assertEquals("collector", last.enemyId)
        assertEquals(0, last.rewards.cardChoices)
    }

    @Test
    fun `rewarded slots sum to the WU5 3-choose-1 economy budget`() {
        // WU5 T5.3/T5.4: every NON-BOSS slot offers 3 biased choices, boss (non-final) slots offer 1,
        // the final boss offers 0. Non-boss slots = 0,1,3,4,6 (5 x 3 = 15); boss slots = 2,5 (2 x 1 = 2).
        val picks = sequence.slots.dropLast(1).sumOf { it.rewards.cardChoices }
        assertEquals(17, picks)
    }

    @Test
    fun `gold ramp has the designed values`() {
        assertEquals(listOf(10, 10, 15, 12, 18, 20, 25, 30), sequence.slots.map { it.rewards.gold })
    }

    @Test
    fun `mid-boss stand-in slot is the collector at position seven`() {
        val midBoss = sequence.slots[6]
        assertEquals("collector", midBoss.enemyId)
        // Slot 6 is a STREET (non-boss) node in the boardroom district, so WU5 T5.4 gives it 3 picks.
        assertEquals(3, midBoss.rewards.cardChoices)
    }
}