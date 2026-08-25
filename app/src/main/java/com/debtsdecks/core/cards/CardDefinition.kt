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
    val selfDamage: Int = 0,
    val poisonApply: Int = 0,
    val thornsGain: Int = 0,
    val regenGain: Int = 0,
    /** Flat Debt reduction this card applies (debt-resource-mechanic). See [CardResolver] for
     *  how ATTACK-type cards (e.g. Collections Call) apply this once per landed hit rather than
     *  once per play. */
    val debtRepay: Int = 0,
    /** Flat Gold granted once per play, regardless of card type or hit count (debt-resource-mechanic). */
    val goldGain: Int = 0,
    /** Flat Debt added directly to the player when the card resolves (`add_debt` tag). This is a
     *  distinct "penalty/loan" source, never halved by the Escrow Shield. */
    val debtAdd: Int = 0,
    /** Flat Credit/energy granted this turn when the card resolves (`gain_credit` tag). */
    val creditGain: Int = 0,
    val hits: Int = 1,
    val targetType: TargetType,
    val description: String,
    val rarity: Rarity,
    val tags: Set<String> = emptySet()
)

enum class CardType { ATTACK, SKILL, POWER }

enum class TargetType { ENEMY, ALL_ENEMIES, SELF, RANDOM_ENEMY }

enum class Rarity { BASIC, COMMON, UNCOMMON, RARE, SPECIAL }