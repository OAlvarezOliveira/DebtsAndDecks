package com.debtsdecks.core.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * FV.E1 "En Mora" arrears lock (Phase 3, task 3.1): [CombatState] must default-construct with
 * both lock flags false, so every pre-existing constructor call site keeps compiling unmodified.
 */
class CombatStateTest {

    @Test
    fun `combat state default-constructs with both arrears lock flags false`() {
        val state = CombatState(
            player = PlayerState(),
            enemies = emptyList(),
            currentTurn = TurnPhase.PLAYER_ACTION,
            energy = 3,
            maxEnergy = 3,
            hand = emptyList(),
            drawPileCount = 0,
            discardPileCount = 0,
            exhaustPileCount = 0,
            log = emptyList()
        )

        assertFalse(state.inArrears, "inArrears must default to false")
        assertFalse(state.arrearsUsedThisCombat, "arrearsUsedThisCombat must default to false")
    }
}
