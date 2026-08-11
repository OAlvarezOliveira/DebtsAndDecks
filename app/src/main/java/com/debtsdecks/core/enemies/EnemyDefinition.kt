package com.debtsdecks.core.enemies

import kotlinx.serialization.Serializable

@Serializable
data class EnemyDefinition(
    val id: String,
    val name: String,
    val hp: Int,
    val intentPattern: List<IntentStep>,
    val rewards: EnemyRewards
)

@Serializable
data class IntentStep(
    val type: IntentType,
    val damage: Int = 0,
    val param: Int = 0
)

@Serializable
data class EnemyRewards(
    val gold: Int,
    val cardChoices: Int
)

enum class IntentType { ATTACK, BUFF, DEBUFF, MULTI_ATTACK }