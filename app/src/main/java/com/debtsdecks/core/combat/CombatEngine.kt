package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.resolution.CardResolver
import com.debtsdecks.core.enemies.EnemyAI
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
    private val cardResolver = CardResolver()
    private var enemyAIs: Map<String, EnemyAI> = emptyMap()

    val HAND_SIZE = 5

    fun startCombat(enemyDefinitions: List<EnemyDefinition>, starterDeck: List<String>) {
        // Create enemies
        enemies = enemyDefinitions.map { EnemyInstance(it) }.toMutableList()
        enemyAIs = enemies.associateBy({ it.id }, { EnemyAI(it) })

        // Create player
        player = PlayerState()

        // Build draw pile from starter deck
        drawPile = ArrayDeque(starterDeck.map { cardId ->
            val def = cardRegistry.getOrThrow(cardId)
            CardInstance(def)
        }.shuffled())

        discardPile.clear()
        exhaustPile.clear()
        hand.clear()
        energy = maxEnergy
        turnNumber = 0
        log.clear()

        // Start first turn
        beginTurn()
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
            turnNumber = turnNumber
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
        if (!card.canPlay(energy)) {
            return PlayResult(false, "Not enough energy")
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

        // Pay energy
        energy -= card.cost

        // Resolve card
        val resolution = cardResolver.resolve(card, target, getState())

        // Apply effects
        applyEffects(resolution.effects, card)

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
            val ai = enemyAIs[enemy.id]!!
            enemyLog.addAll(ai.executeIntent(player, enemies))
        }
        log.addAll(enemyLog)

        // Check lose condition
        if (player.isDead()) {
            endCombat(victory = false)
            return TurnResult(true, "Defeat!")
        }

        // End turn cleanup
        currentPhase = TurnPhase.TURN_END
        player.endTurnReset()
        for (enemy in enemies) {
            enemy.block = 0
        }

        beginTurn()

        return TurnResult(true, "Turn ended")
    }

    private fun beginTurn() {
        turnNumber++
        currentPhase = TurnPhase.PLAYER_DRAW
        energy = maxEnergy

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
                drawPile.addAll(discardPile.shuffled())
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
                    enemy?.takeDamage(effect.amount)
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
                    enemy?.let { player.applyWeak(effect.turns) } // Applied to player for now
                }
                is CardResolver.Effect.VulnerableApply -> {
                    val enemy = enemies.find { it.id == effect.targetId }
                    enemy?.let { player.applyVulnerable(effect.turns) } // Applied to player for now
                }
                is CardResolver.Effect.ExhaustSelf -> {
                    // Handled in playCard
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
}