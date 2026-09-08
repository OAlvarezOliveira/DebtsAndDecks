package com.debtsdecks.core.model

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.Archetype
import kotlinx.serialization.Serializable

data class CombatState(
    val player: PlayerState,
    val enemies: List<EnemyState>,
    val currentTurn: TurnPhase,
    val energy: Int,
    val maxEnergy: Int,
    val hand: List<CardInstance>,
    val drawPileCount: Int,
    val discardPileCount: Int,
    val exhaustPileCount: Int,
    val log: List<CombatLogEntry>,
    val turnNumber: Int = 1,
    val debt: Int = 0,
    val gold: Int = 0,
    /** FV.E1 "En Mora" arrears lock: true while the debt >= [com.debtsdecks.core.combat.DebtConfig.ARREARS_THRESHOLD]
     *  charge is armed for this combat (see [CombatEngine.getState]). */
    val inArrears: Boolean = false,
    /** One-shot charge: true once the arrears lock has armed at least once this combat, even
     *  after [inArrears] clears (debt == 0) — it never re-arms within the same combat. */
    val arrearsUsedThisCombat: Boolean = false,
    /** Per-archetype synergy tier (0..3) computed from deck composition at combat start. Read-only
     *  for the HUD and the resolver; defaults to empty so pre-WU1 snapshot construction still works. */
    val archetypeTiers: Map<Archetype, Int> = emptyMap()
)

enum class TurnPhase {
    PLAYER_DRAW,
    PLAYER_ACTION,
    ENEMY_ACTION,
    TURN_END,
    COMBAT_END
}

@Serializable
data class CombatLogEntry(
    val message: String,
    val turn: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(message: String, turn: Int): CombatLogEntry = CombatLogEntry(message, turn)
    }
}