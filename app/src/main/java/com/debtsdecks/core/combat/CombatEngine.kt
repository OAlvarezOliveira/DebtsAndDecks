package com.debtsdecks.core.combat

import com.badlogic.gdx.utils.I18NBundle
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.resolution.CardResolver
import com.debtsdecks.core.enemies.EnemyAI
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyInstance
import com.debtsdecks.core.model.CombatLogEntry
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.EnemyState
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.TurnPhase
import java.util.ArrayDeque
import kotlin.random.Random

class CombatEngine(
    private val cardRegistry: CardRegistry,
    private val bundle: I18NBundle,
    private val rng: Random = Random(System.currentTimeMillis())
) {
    private var player: PlayerState = PlayerState()
    private var enemies: MutableList<EnemyInstance> = mutableListOf()
    private var drawPile: ArrayDeque<CardInstance> = ArrayDeque()
    private var discardPile: ArrayDeque<CardInstance> = ArrayDeque()
    private var exhaustPile: ArrayDeque<CardInstance> = ArrayDeque()
    private var hand: MutableList<CardInstance> = mutableListOf()
    private var energy: Int = 3
    private var maxEnergy: Int = 3
    private var currentPhase: TurnPhase = TurnPhase.PLAYER_DRAW
    private var turnNumber: Int = 0
    private var log: MutableList<CombatLogEntry> = mutableListOf()

    /** Run-persistent liability, carried in via [startCombat] and grown by a per-encounter interest tick. */
    private var debt: Int = 0

    /** Run-persistent currency, carried in via [startCombat] and spendable through [repayDebt]. */
    private var gold: Int = 0

    /** Per-combat flag (see [activateEscrowShield]) that halves Debt added from a shortfall while active. */
    private var escrowShieldActive: Boolean = false
    private val cardResolver = CardResolver(bundle)
    private var enemyAIs: Map<String, EnemyAI> = emptyMap()

    val HAND_SIZE = 5

    companion object {
        val STARTER_DECK = listOf(
            "strike", "strike", "strike", "strike", "strike",
            "defend", "defend", "defend",
            "bash", "survive"
        )
    }

    /**
     * Begins a new encounter. [startingGold] and [startingDebt] carry Gold/Debt forward from the
     * previous encounter (0 for a fresh run). A single compounding interest tick is applied to
     * [startingDebt] here, at the encounter boundary, per the debt-resource-mechanic design.
     * [startingHp] carries the player's HP forward from the previous encounter (full [PlayerState.maxHp]
     * for a fresh run) as a 100% raw value — no healing, no decay, mirroring the same threading
     * pattern as Gold/Debt.
     */
    fun startCombat(
        enemyDefinitions: List<EnemyDefinition>,
        starterDeck: List<String>,
        startingGold: Int = 0,
        startingDebt: Int = 0,
        startingHp: Int = PlayerState().maxHp
    ) {
        // Create enemies
        enemies = enemyDefinitions.map { EnemyInstance(it) }.toMutableList()
        enemyAIs = enemies.associateBy({ it.id }, { EnemyAI(it, bundle) })

        // Create player
        player = PlayerState(hp = startingHp)

        // Build draw pile from starter deck
        drawPile = ArrayDeque(starterDeck.map { cardId ->
            val def = cardRegistry.getOrThrow(cardId)
            CardInstance(def)
        }.shuffled(rng))

        discardPile.clear()
        exhaustPile.clear()
        hand.clear()
        energy = maxEnergy
        turnNumber = 0
        log.clear()

        gold = startingGold
        debt = DebtConfig.applyInterest(startingDebt)
        escrowShieldActive = false

        // Start first turn
        beginTurn()
    }

    /** Activates the per-combat Escrow Shield: halves (rounded up) any Debt added from a shortfall. */
    fun activateEscrowShield() {
        escrowShieldActive = true
    }

    fun getState(): CombatState {
        return CombatState(
            player = player,
            enemies = enemies.map { EnemyState.fromInstance(it) },
            currentTurn = currentPhase,
            energy = energy,
            maxEnergy = maxEnergy,
            hand = hand.toList(),
            drawPileCount = drawPile.size,
            discardPileCount = discardPile.size,
            exhaustPileCount = exhaustPile.size,
            log = log.toList(),
            turnNumber = turnNumber,
            debt = debt,
            gold = gold
        )
    }

    fun playCard(instanceId: String, targetId: String?): PlayResult {
        if (currentPhase != TurnPhase.PLAYER_ACTION) {
            return PlayResult(false, "Not player action phase")
        }

        val cardIndex = hand.indexOfFirst { it.id == instanceId }
        if (cardIndex == -1) {
            return PlayResult(false, "Card not in hand")
        }

        val card = hand[cardIndex]
        if (!card.isPlayable()) {
            return PlayResult(false, "Card cannot be played")
        }

        if (card.definition.tags.contains("conditional") && hand.size > 1) {
            return PlayResult(false, "Requires an empty hand")
        }

        // Validate target
        val target = when (card.targetType) {
            com.debtsdecks.core.model.TargetType.ENEMY -> {
                if (targetId == null || enemies.none { it.id == targetId }) {
                    return PlayResult(false, "Invalid target")
                }
                targetId
            }
            com.debtsdecks.core.model.TargetType.SELF -> player.hashCode().toString()
            com.debtsdecks.core.model.TargetType.RANDOM_ENEMY -> enemies.randomOrNull(rng)?.id
            com.debtsdecks.core.model.TargetType.ALL_ENEMIES -> "ALL"
            else -> null
        }

        // Pay Credit. X-cost cards ("x_cost" tag) spend all remaining Credit, which becomes
        // the number of times their effect repeats (see CardResolver); by definition they never
        // have a shortfall, since their cost dynamically equals whatever Credit is available.
        val isXCost = card.definition.tags.contains("x_cost")
        val xValue = if (isXCost) energy else 0
        if (isXCost) {
            energy -= xValue
        } else {
            // Cards are always playable: any Credit shortfall converts 1:1 into Debt (halved,
            // rounded up, while the Escrow Shield is active) instead of blocking the play.
            val rawShortfall = card.shortfall(energy)
            energy -= (card.cost - rawShortfall)
            if (rawShortfall > 0) {
                val actualBorrow = if (escrowShieldActive) (rawShortfall + 1) / 2 else rawShortfall
                debt = minOf(debt + actualBorrow, DebtConfig.INTEREST_CAP)
            }
        }

        // Resolve card
        val resolution = cardResolver.resolve(card, target, getState(), xValue)

        // Apply effects
        applyEffects(resolution.effects, card)

        if (player.isDead()) {
            endCombat(victory = false)
            return PlayResult(true, "Defeat!")
        }

        // Log
        log.addAll(resolution.logEntries)

        // Move card to discard or exhaust
        hand.removeAt(cardIndex)
        if (resolution.effects.any { it is CardResolver.Effect.ExhaustSelf }) {
            exhaustPile.add(card)
            card.exhausted = true
        } else {
            discardPile.add(card)
        }

        // Check win condition
        if (enemies.all { it.isDead() }) {
            endCombat(victory = true)
            return PlayResult(true, "Victory!")
        }

        return PlayResult(true, "Card played")
    }

    /**
     * Dedicated, always-available action to pay down Debt: it does not cost Credit and does not
     * end the turn. [RepayMode.GOLD] spends `min(gold, debt)` 1:1; [RepayMode.DISCARD] discards
     * the hand card identified by [discardInstanceId] for a flat [DebtConfig.REPAY_DISCARD_VALUE]
     * cut, clamped so Debt never goes negative.
     */
    fun repayDebt(mode: RepayMode, discardInstanceId: String? = null): RepayResult {
        if (debt <= 0) {
            return RepayResult(false, "No debt to repay")
        }
        return when (mode) {
            RepayMode.GOLD -> {
                if (gold <= 0) {
                    return RepayResult(false, "No gold available")
                }
                val amount = minOf(gold, debt)
                gold -= amount
                debt -= amount
                log.add(CombatLogEntry.create("Repaid $amount Debt with Gold.", turnNumber))
                RepayResult(true, "Repaid $amount Debt", amount)
            }
            RepayMode.DISCARD -> {
                val index = hand.indexOfFirst { it.id == discardInstanceId }
                if (index == -1) {
                    return RepayResult(false, "Card not in hand")
                }
                val card = hand.removeAt(index)
                discardPile.add(card)
                val amount = minOf(DebtConfig.REPAY_DISCARD_VALUE, debt)
                debt -= amount
                log.add(CombatLogEntry.create("Discarded ${card.name} to repay $amount Debt.", turnNumber))
                RepayResult(true, "Repaid $amount Debt", amount)
            }
        }
    }

    enum class RepayMode { GOLD, DISCARD }

    fun endPlayerTurn(): TurnResult {
        if (currentPhase != TurnPhase.PLAYER_ACTION) {
            return TurnResult(false, "Not player action phase")
        }

        // Discard remaining hand
        discardPile.addAll(hand)
        hand.clear()

        currentPhase = TurnPhase.ENEMY_ACTION

        // Enemy turns
        val enemyLog = mutableListOf<CombatLogEntry>()
        for (enemy in enemies.filter { !it.isDead() }) {
            val poisonDmg = enemy.tickPoison()
            if (poisonDmg > 0) {
                enemyLog.add(CombatLogEntry.create("Poison deals $poisonDmg damage to ${enemy.name}!", turnNumber))
            }
            if (enemy.isDead()) continue
            val ai = enemyAIs[enemy.id]!!
            enemyLog.addAll(ai.executeIntent(player, enemies, turnNumber))
        }
        log.addAll(enemyLog)

        // Check win condition (poison may have finished off the last enemy)
        if (enemies.all { it.isDead() }) {
            endCombat(victory = true)
            return TurnResult(true, "Victory!")
        }

        // Check lose condition
        if (player.isDead()) {
            endCombat(victory = false)
            return TurnResult(true, "Defeat!")
        }

        // End turn cleanup
        currentPhase = TurnPhase.TURN_END
        player.endTurnReset()
        for (enemy in enemies) {
            enemy.endTurnReset()
        }

        beginTurn()

        return TurnResult(true, "Turn ended")
    }

    private fun beginTurn() {
        turnNumber++
        currentPhase = TurnPhase.PLAYER_DRAW
        energy = maxEnergy

        val poisonBefore = player.poison
        val regenBefore = player.regen
        player.tickTurnStart()
        if (poisonBefore > 0) log.add(CombatLogEntry.create("Poison deals $poisonBefore damage to you!", turnNumber))
        if (regenBefore > 0) log.add(CombatLogEntry.create("Regen heals you for $regenBefore!", turnNumber))
        if (player.isDead()) {
            endCombat(victory = false)
            return
        }

        // Draw cards
        drawCards(HAND_SIZE)

        currentPhase = TurnPhase.PLAYER_ACTION

        log.add(CombatLogEntry.create("--- Turn $turnNumber ---", turnNumber))
    }

    private fun drawCards(count: Int) {
        repeat(count) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) return
                // Shuffle discard into draw
                drawPile.addAll(discardPile.shuffled(rng))
                discardPile.clear()
                log.add(CombatLogEntry.create("Reshuffled discard pile!", turnNumber))
            }
            if (hand.size < HAND_SIZE) {
                val card = drawPile.removeFirst()
                hand.add(card)
            }
        }
    }

    private fun applyEffects(effects: List<CardResolver.Effect>, sourceCard: CardInstance) {
        for (effect in effects) {
            when (effect) {
                is CardResolver.Effect.Damage -> {
                    val enemy = enemies.find { it.id == effect.targetId }
                    if (enemy != null) {
                        val actualDamage = enemy.takeDamage(effect.amount)
                        log.add(CombatLogEntry.create("Dealt $actualDamage damage to ${enemy.name}!", turnNumber))
                    }
                }
                is CardResolver.Effect.Block -> {
                    player.gainBlock(effect.amount)
                }
                is CardResolver.Effect.Draw -> {
                    drawCards(effect.count)
                }
                is CardResolver.Effect.StrengthGain -> {
                    player.gainStrength(effect.amount)
                }
                is CardResolver.Effect.WeakApply -> {
                    val enemy = enemies.find { it.id == effect.targetId }
                    enemy?.applyWeak(effect.turns)
                }
                is CardResolver.Effect.VulnerableApply -> {
                    val enemy = enemies.find { it.id == effect.targetId }
                    enemy?.applyVulnerable(effect.turns)
                }
                is CardResolver.Effect.PoisonApply -> {
                    val enemy = enemies.find { it.id == effect.targetId }
                    enemy?.applyPoison(effect.amount)
                }
                is CardResolver.Effect.ThornsGain -> {
                    player.gainThorns(effect.amount)
                }
                is CardResolver.Effect.RegenGain -> {
                    player.gainRegen(effect.amount)
                }
                is CardResolver.Effect.SelfDamage -> {
                    player.hp = maxOf(0, player.hp - effect.amount)
                }
                is CardResolver.Effect.AddCopyToDiscard -> {
                    discardPile.add(CardInstance(sourceCard.definition))
                }
                is CardResolver.Effect.ExhaustSelf -> {
                    // Handled in playCard
                }
                is CardResolver.Effect.RepayDebt -> {
                    debt = maxOf(0, debt - effect.amount)
                }
                is CardResolver.Effect.GainGold -> {
                    gold += effect.amount
                }
                is CardResolver.Effect.WipeDebt -> {
                    debt = 0
                }
                is CardResolver.Effect.EscrowShieldActivate -> {
                    activateEscrowShield()
                }
            }
        }
    }

    private fun endCombat(victory: Boolean) {
        currentPhase = TurnPhase.COMBAT_END
        log.add(CombatLogEntry.create(if (victory) "VICTORY!" else "DEFEAT!", turnNumber))
    }

    data class PlayResult(val success: Boolean, val message: String)
    data class TurnResult(val success: Boolean, val message: String)
    data class RepayResult(val success: Boolean, val message: String, val debtReduced: Int = 0)
}