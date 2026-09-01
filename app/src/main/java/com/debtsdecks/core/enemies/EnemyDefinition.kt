package com.debtsdecks.core.enemies

import kotlinx.serialization.Serializable

@Serializable
data class EnemyDefinition(
    val id: String,
    val name: String,
    val hp: Int,
    val intentPattern: List<IntentStep>,
    val rewards: EnemyRewards,
    val tier: EnemyTier = EnemyTier.NORMAL,
    val tags: Set<String> = emptySet(),
    /** Per-act HP/damage multipliers, applied by [EnemyInstance] for the act the combat runs in.
     *  Data-driven (see assets/enemies/all.json); empty = no per-act scaling. */
    val actModifiers: List<ActModifier> = emptyList()
)

/**
 * Per-act HP/damage scaling for a single act/district. [act] is 1-based (1 = slaughterhouse,
 * 2 = casino, 3 = boardroom). Both multipliers are applied together so HP and damage scale as a
 * unit — scaling HP alone would make fights *easier* by granting more block-turns (the
 * HP-Matters invariant in the enemy-scaling spec).
 */
@Serializable
data class ActModifier(
    val act: Int,
    val hpMultiplier: Double,
    val damageMultiplier: Double
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

/**
 * What an enemy announces it will do next, and everything the player needs to read it.
 *
 * [l10nKey] and [iconName] are declared here, on the constant, rather than derived in a `when`
 * elsewhere: both are telegraph assets that fail silently when missing (a missing icon draws
 * nothing, a missing Spanish key falls back to English), so the only enforcement available is to
 * make the enum itself refuse to compile without them. `IntentTypeCoverageTest` then checks that
 * what each constant declares actually exists on disk.
 *
 * [iconName] is the asset stem under `assets/art/`, without the `.png` extension.
 */
enum class IntentType(val l10nKey: String, val iconName: String) {
    ATTACK("intent.attack", "intent_attack"),
    BUFF("intent.buff", "intent_buff"),
    DEBUFF("intent.debuff", "intent_debuff"),
    MULTI_ATTACK("intent.multi_attack", "intent_multi"),
    LEVY("intent.levy", "intent_levy"),
    /** Forces a debt payment or penalty: adds Debt when the player is already in debt, else deals HP
     *  damage. The effect is engine-owned (mirrors LEVY) so it routes through the Debt cap/Execution
     *  check; [EnemyAI] only advances the pattern. */
    FORECLOSE("intent.foreclose", "intent_foreclose"),
    /** Reduces incoming damage for the turn by gaining Block (engine-independent, applied in
     *  [EnemyAI]). */
    HEDGE("intent.hedge", "intent_hedge")
}

enum class EnemyTier { NORMAL, ELITE, BOSS }