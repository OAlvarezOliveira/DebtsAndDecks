package com.debtsdecks.gdx.render

import com.debtsdecks.core.combat.Archetype
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WU6 (debt-hud) focused unit test. The HUD is visual, so the meaningful headless assertion is that
 * the renderer's data-extraction model reads the correct immutable [CombatState] fields and derives
 * the band/zone/archetype/bleed facts the renderer paints — with NO mutation of game state. This is
 * the [DebtHudModel] both [CombatRenderer] consumes, so a failing assertion here means the on-screen
 * HUD would read the wrong value (the debt-hud spec's false-positive trap). The actual pixels are
 * covered by manual device review (see apply-progress), which this test deliberately does not try to
 * replace.
 */
class DebtHudModelTest {

    private fun state(debt: Int) = CombatState(
        player = PlayerState(),
        enemies = emptyList(),
        currentTurn = TurnPhase.PLAYER_ACTION,
        energy = 3,
        maxEnergy = 3,
        hand = emptyList(),
        drawPileCount = 0,
        discardPileCount = 0,
        exhaustPileCount = 0,
        log = emptyList(),
        debt = debt
    )

    private val tiers = mapOf(
        Archetype.LEVERAGE to 2,
        Archetype.LIQUIDITY to 0,
        Archetype.PRESSURE to 1
    )

    @Test
    fun `reads debt and the band and threshold constants straight from CombatState`() {
        val hud = DebtHudModel.compute(state(35), Archetype.LEVERAGE, tiers)
        assertEquals(35, hud.debt)
        assertEquals(DebtConfig.DEBT_BLEED_FLOOR, hud.debtBleedFloor)
        assertEquals(DebtConfig.BREAK_THRESHOLD, hud.breakThreshold)
        assertEquals(DebtConfig.LEVERAGE_PAYOFF_BAND_CAP, hud.bandCap)
        assertEquals(DebtConfig.EXECUTION_THRESHOLD, hud.executionThreshold)
    }

    @Test
    fun `band reflects current debt 35 sits in execution proximity not safe`() {
        assertEquals(DebtZone.PROXIMITY, DebtHudModel.compute(state(35), Archetype.LEVERAGE, tiers).zone)
    }

    @Test
    fun `stale value trap HUD shows the debt passed in after an interest tick`() {
        // debt-hud spec: a mid-combat interest tick must surface the NEW debt, never a cached value.
        val afterTick = DebtHudModel.compute(state(30), Archetype.LEVERAGE, tiers)
        assertEquals(30, afterTick.debt)
        assertEquals(DebtZone.PROXIMITY, afterTick.zone)
    }

    @Test
    fun `zone transitions at the spec boundaries`() {
        assertEquals(DebtZone.SAFE, DebtHudModel.compute(state(15), Archetype.LEVERAGE, tiers).zone)
        assertEquals(DebtZone.DANGER, DebtHudModel.compute(state(25), Archetype.LEVERAGE, tiers).zone)
        assertEquals(DebtZone.PROXIMITY, DebtHudModel.compute(state(45), Archetype.LEVERAGE, tiers).zone)
        assertEquals(DebtZone.EXECUTION, DebtHudModel.compute(state(50), Archetype.LEVERAGE, tiers).zone)
        assertEquals(DebtZone.EXECUTION, DebtHudModel.compute(state(60), Archetype.LEVERAGE, tiers).zone)
    }

    @Test
    fun `risk at moderate debt shows distance to execution debt 35 gives 15`() {
        val hud = DebtHudModel.compute(state(35), Archetype.LEVERAGE, tiers)
        assertEquals(15, hud.distanceToExecution)
    }

    @Test
    fun `risk near execution shows small distance debt 45 gives 5`() {
        assertEquals(5, DebtHudModel.compute(state(45), Archetype.LEVERAGE, tiers).distanceToExecution)
    }

    @Test
    fun `per-turn bleed is the interest delta at the current debt`() {
        // 35 * 0.15 = 5.25 -> ceil 6 -> bleed 6
        assertEquals(6, DebtHudModel.compute(state(35), Archetype.LEVERAGE, tiers).debtBleed)
        // 15 * 0.15 = 2.25 -> ceil 3 -> bleed 3
        assertEquals(3, DebtHudModel.compute(state(15), Archetype.LEVERAGE, tiers).debtBleed)
        // zero debt -> no bleed
        assertEquals(0, DebtHudModel.compute(state(0), Archetype.LEVERAGE, tiers).debtBleed)
    }

    @Test
    fun `active archetype and tier are carried through from the run deck`() {
        val hud = DebtHudModel.compute(state(10), Archetype.PRESSURE, tiers)
        assertEquals(Archetype.PRESSURE, hud.dominantArchetype)
        assertEquals(1, hud.archetypeTier)
    }
}
