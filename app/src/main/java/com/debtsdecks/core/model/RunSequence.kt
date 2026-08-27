package com.debtsdecks.core.model

import com.debtsdecks.core.enemies.EnemyRewards
import kotlinx.serialization.Serializable

/**
 * C5 run-length-and-encounter-slots: explicit 8-slot run sequence, decoupled from the enemy roster
 * (which is only a catalog). Each slot names an enemy id + the run-authoritative rewards for that
 * fight (slot rewards ALWAYS win over the enemy's built-in rewards; every slot must specify both).
 */
@Serializable
data class EncounterSlot(
    val enemyId: String,
    val rewards: EnemyRewards
)

@Serializable
data class RunSequence(
    val slots: List<EncounterSlot>
)