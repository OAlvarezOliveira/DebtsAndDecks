package com.debtsdecks.core.model

import com.debtsdecks.core.enemies.EnemyRewards
import kotlinx.serialization.Serializable

/**
 * C5 run-length-and-encounter-slots: explicit 8-slot run sequence, decoupled from the enemy roster
 * (which is only a catalog). Each slot names an enemy id + the run-authoritative rewards for that
 * fight (slot rewards ALWAYS win over the enemy's built-in rewards; every slot must specify both).
 *
 * F2 districts adds [EncounterSlot.districtId] and [EncounterSlot.role]: the same 8 slots, re-cut
 * into three named districts that close on a boss seat. Both fields are metadata — nothing in
 * combat, the node economy or the reward tables reads them — so the re-cut is a zero-delta reskin.
 */
@Serializable
data class EncounterSlot(
    val enemyId: String,
    val districtId: String,
    val rewards: EnemyRewards,
    val role: SlotRole = SlotRole.STREET
)

/**
 * Where a slot sits inside its district. [BOSS] marks the district's closing fight; every district
 * has exactly one and it is always its last slot. Deliberately NOT a
 * [com.debtsdecks.core.combat.RunManager.Phase] value: Phase drives four exhaustive `when` blocks
 * and the run's state machine, and a boss seat changes neither.
 */
enum class SlotRole { STREET, BOSS }

@Serializable
data class RunSequence(
    val slots: List<EncounterSlot>
)
