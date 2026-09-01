package com.debtsdecks.gdx.render

import com.debtsdecks.core.combat.Archetype
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.model.CombatState

/**
 * Read-only view-model the [CombatRenderer] draws for the debt HUD (band bar, archetype label,
 * risk counter).
 *
 * Computed from the immutable [CombatState] snapshot plus the player's dominant archetype — it
 * NEVER mutates game state, so the HUD is fully strippable without changing any combat outcome
 * (debt-hud spec: "HUD Is Read-Only"). The model is pure (no LibGDX dependency) so it can be
 * unit-tested headlessly; the renderer only reads [DebtHudData] and paints pixels.
 */
enum class DebtZone {
    /** debt < [DebtConfig.DEBT_BLEED_FLOOR] — band bar reads SAFE (green). */
    SAFE,
    /** [DebtConfig.DEBT_BLEED_FLOOR] <= debt < [DebtConfig.BREAK_THRESHOLD] — bleed floor crossed. */
    DANGER,
    /** [DebtConfig.BREAK_THRESHOLD] <= debt < [DebtConfig.EXECUTION_THRESHOLD] — execution proximity. */
    PROXIMITY,
    /** debt >= [DebtConfig.EXECUTION_THRESHOLD] — execution line. */
    EXECUTION
}

data class DebtHudData(
    val debt: Int,
    val debtBleedFloor: Int,
    val breakThreshold: Int,
    val bandCap: Int,
    val executionThreshold: Int,
    val dominantArchetype: Archetype,
    val archetypeTier: Int,
    /** Per-turn debt bleed (interest) the player is taking at the current debt level. */
    val debtBleed: Int,
    /** Points of debt headroom before Execution (0 once at/over the threshold). */
    val distanceToExecution: Int,
    val zone: DebtZone
)

object DebtHudModel {

    /**
     * Builds the HUD view-model from [state] (the immutable combat snapshot) and the player's
     * [dominantArchetype] + [archetypeTiers] (read-only deck-derived facts owned by [RunManager] /
     * [CombatState]). No side effects.
     */
    fun compute(
        state: CombatState,
        dominantArchetype: Archetype,
        archetypeTiers: Map<Archetype, Int>
    ): DebtHudData {
        val debt = state.debt
        val debtBleed = (DebtConfig.applyInterest(debt) - debt).coerceAtLeast(0)
        val zone = when {
            debt >= DebtConfig.EXECUTION_THRESHOLD -> DebtZone.EXECUTION
            debt >= DebtConfig.BREAK_THRESHOLD -> DebtZone.PROXIMITY
            debt >= DebtConfig.DEBT_BLEED_FLOOR -> DebtZone.DANGER
            else -> DebtZone.SAFE
        }
        return DebtHudData(
            debt = debt,
            debtBleedFloor = DebtConfig.DEBT_BLEED_FLOOR,
            breakThreshold = DebtConfig.BREAK_THRESHOLD,
            bandCap = DebtConfig.LEVERAGE_PAYOFF_BAND_CAP,
            executionThreshold = DebtConfig.EXECUTION_THRESHOLD,
            dominantArchetype = dominantArchetype,
            archetypeTier = archetypeTiers[dominantArchetype] ?: 0,
            debtBleed = debtBleed,
            distanceToExecution = (DebtConfig.EXECUTION_THRESHOLD - debt).coerceAtLeast(0),
            zone = zone
        )
    }
}
