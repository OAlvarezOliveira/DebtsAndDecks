package com.debtsdecks.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CardDefinition(
    val id: String,
    val name: String,
    val type: CardType,
    val cost: Int,
    val damage: Int = 0,
    val block: Int = 0,
    val draw: Int = 0,
    val strengthGain: Int = 0,
    val weakApply: Int = 0,
    val vulnerableApply: Int = 0,
    val targetType: TargetType,
    val description: String,
    val rarity: Rarity,
    val tags: Set<String> = emptySet()
)

enum class CardType { ATTACK, SKILL, POWER }

enum class TargetType { ENEMY, ALL_ENEMIES, SELF, RANDOM_ENEMY }

enum class Rarity { BASIC, COMMON, UNCOMMON, RARE, SPECIAL }