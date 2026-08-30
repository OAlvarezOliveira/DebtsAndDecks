package com.debtsdecks.core.combat

import com.debtsdecks.core.i18n.Localizer
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.resolution.CardResolver
import com.debtsdecks.core.enemies.EnemyAI
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyInstance
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.model.CombatLogEntry
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.EnemyState
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.TurnPhase
import java.util.ArrayDeque
import kotlin.random.Random

class CombatEngine(
    private val cardRegistry: CardRegistry,
    private val l10n: Localizer,
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

    /** Run-persistent currency, carried in via [startCombat] and spent through the run boundary. */
    private var gold: Int = 0

    /** Per-combat flag (see [activateEscrowShield]) that halves Debt added from a shortfall while active. */
    private var escrowShieldActive: Boolean = false

    /**
     * FV.E1 "En Mora" arrears lock (see [addDebt]/[armArrearsIfCrossed]): true while the debt
     * charge is armed for the current combat. [CombatEngine.beginTurn]'s interest tick and
     * [RunManager]'s Gatillo B outcome check both read this. Reset in [startCombat].
     */
    private var inArrears: Boolean = false

    /**
     * One-shot charge: true once [inArrears] has armed at least once this combat, even after it
     * clears (debt reaches 0) — the lock never re-arms within the same combat (design Q2).
     */
    private var arrearsUsedThisCombat: Boolean = false

    /**
     * FV calibration instrumentation: how many times FORECLOSE took the seizure branch
     * (player Debt at/above the announced threshold) since this engine was created. The
     * simulator creates one engine per run, so the count accumulates across the whole run.
     */
    var forecloseSeizureCount: Int = 0
        private set

    /**
     * FV.E1 instrumentation: how many times the "En Mora" arrears lock has armed since this
     * engine was created (mirrors [forecloseSeizureCount]'s per-run accumulation pattern).
     */
    var arrearsArmedCount: Int = 0
        private set

    private enum class DebtSource { LEVY, OTHER }

    /**
     * Adds [amount] Debt, clamped to [DebtConfig.INTEREST_CAP], then arms the "En Mora" arrears
     * lock the first time Debt reaches [DebtConfig.ARREARS_THRESHOLD] this combat (see
     * [armArrearsIfCrossed]). All in-combat debt increases (Credit-shortfall borrow, enemy LEVY,
     * card AddDebt) route through this helper; the per-turn interest tick in beginTurn is
     * deliberately excluded (Decision B) and instead frozen while the lock is armed.
     */
    private fun addDebt(amount: Int, source: DebtSource = DebtSource.OTHER) {
        debt = minOf(debt + amount, DebtConfig.INTEREST_CAP)
        armArrearsIfCrossed(source)
    }

    /**
     * Arms the FV.E1 "En Mora" lock the first time [debt] reaches [DebtConfig.ARREARS_THRESHOLD]
     * in this combat. No-ops once [arrearsUsedThisCombat] is already true — the player is immune
     * to re-arming for the rest of the combat, even after escaping (design Q2).
     */
    private fun armArrearsIfCrossed(source: DebtSource) {
        if (arrearsUsedThisCombat || debt < DebtConfig.ARREARS_THRESHOLD) return
        inArrears = true
        arrearsUsedThisCombat = true
        arrearsArmedCount++
        val key = if (source == DebtSource.LEVY) "log.arrears_locked_levy" else "log.arrears_locked"
        log.add(CombatLogEntry.create(l10n.get(key), turnNumber))
    }

    /** Clears the arrears lock once Debt returns to zero — the only exit rule (design D4). */
    private fun clearArrearsIfEscaped() {
        inArrears = inArrears && debt > 0
    }

    private val cardResolver = CardResolver(l10n)
    private var enemyAIs: Map<String, EnemyAI> = emptyMap()

    val HAND_SIZE = 5

    companion object {
        /** Soft cap on Credit a player can hold at once (gain-credit economy). */
        const val MAX_ENERGY_CAP = 6
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
        startingHp: Int = PlayerState().maxHp,
        upgradedCopiesById: Map<String, Int> = emptyMap()
    ) {
        // Create enemies
        enemies = enemyDefinitions.map { EnemyInstance(it, l10n) }.toMutableList()
        enemyAIs = enemies.associateBy({ it.id }, { EnemyAI(it, l10n) })

        // Create player
        player = PlayerState(hp = startingHp)

        // Build draw pile from starter deck
        // card-upgrades (decision A): the upgraded-copies map selects the FIRST N copies of each
        // id (one upgrade = one copy); track created instances per id while building the pile.
        val createdBy = mutableMapOf<String, Int>()
        drawPile = ArrayDeque(starterDeck.map { cardId ->
            val def = cardRegistry.getOrThrow(cardId)
            val created = createdBy[cardId] ?: 0
            createdBy[cardId] = created + 1
            CardInstance(def).apply {
                upgraded = created < (upgradedCopiesById[cardId] ?: 0)
                // R6: cost-2+ upgraded cards gain the cost cut, never the stat bonus.
                if (upgraded && def.cost >= 2) cost = def.cost - 1
            }
        }.shuffled(rng))

        discardPile.clear()
        exhaustPile.clear()
        hand.clear()
        energy = maxEnergy
        turnNumber = 0
        log.clear()

        gold = startingGold
        debt = startingDebt
        escrowShieldActive = false
        // FV.E1: reset per-combat lock state. arrearsArmedCount is intentionally NOT reset here —
        // it mirrors forecloseSeizureCount's accumulate-across-the-whole-run pattern (design.md
        // "Harness Policy Contract" instrumentation note), since the simulator creates one engine
        // per run and both counters are read once at run end.
        inArrears = false
        arrearsUsedThisCombat = false

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
            gold = gold,
            inArrears = inArrears,
            arrearsUsedThisCombat = arrearsUsedThisCombat
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
        if (!card.isPlayable(debt)) {
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
                addDebt(actualBorrow)
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
                enemyLog.add(CombatLogEntry.create(l10n.format("log.poison_damage_enemy", poisonDmg, enemy.name), turnNumber))
            }
            if (enemy.isDead()) continue
            // Boss `interest`: the engine owns the player's Debt and applies the squeeze here.
            // EnemyAI treats LEVY as advance-only; only its combat effect (debt) is applied above.
            val intent = enemy.currentIntent()
            // Exhaustive on purpose, and with no `else`. This is where an intent whose effect the
            // *engine* owns gets wired; EnemyAI owns the rest. Written as an `if` on LEVY, a sixth
            // IntentType needing engine-side handling would fall through in silence and simply
            // never fire. The compiler now refuses to let that happen.
            when (intent.type) {
                IntentType.LEVY -> {
                    addDebt(intent.param, DebtSource.LEVY)
                    enemyLog.add(CombatLogEntry.create(l10n.format("log.intent_levy", intent.param), turnNumber))
                }
                IntentType.FORECLOSE -> {
                    // FV deliverable 1: engine-owned intent (reads the player's Debt, which only
                    // the engine holds). The creditor ALWAYS calls in the debt — the seizure is
                    // run-ending at/above the announced threshold (FV §2 "run-ending, per balance"),
                    // and a standing fee below it, so the verb's turn is never free (a purely
                    // conditional seizure let the shark give away its turn 1 and E1 collapsed).
                    if (debt >= intent.param) {
                        forecloseSeizureCount++
                        player.takeDamage(player.hp) // outright seizure: the insolvent debtor is foreclosed
                        enemyLog.add(CombatLogEntry.create(l10n.format("log.intent_foreclose", intent.param), turnNumber))
                    } else {
                        val fee = player.takeDamage(intent.damage)
                        enemyLog.add(CombatLogEntry.create(l10n.format("log.intent_foreclose_fee", intent.damage, fee, intent.param), turnNumber))
                    }
                }
                IntentType.HEDGE -> {
                    // FV deliverable 1: engine-owned mirror of the player's own leverage bonus —
                    // the enemy gains block equal to floor(debt / hedgeParam). The divisor is
                    // data-driven (intent.param), so balance tunes how much engine it co-opts.
                    // "Your own engine arms the enemy."
                    val hedged = debt / (intent.param.takeIf { it > 0 } ?: DebtConfig.LEVERAGE_DIVISOR)
                    enemy.gainBlock(hedged)
                    enemyLog.add(CombatLogEntry.create(l10n.format("log.intent_hedge", hedged), turnNumber))
                }
                IntentType.ATTACK,
                IntentType.BUFF,
                IntentType.DEBUFF,
                IntentType.MULTI_ATTACK -> Unit // resolved by EnemyAI.executeIntent below
            }
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

        // Per-turn Debt economy: compounding interest only. The arrears lock is deliberately NOT
        // armed here — inheriting high Debt from a won combat must not auto-arm before the player
        // acts; danger comes from accumulating in-combat (borrow/LEVY/card), which routes through
        // addDebt() (design D2). FV.E1: while the lock is armed, the passive interest tick freezes
        // entirely — only active in-combat debt increases (addDebt) still apply.
        if (!inArrears) {
            debt = DebtConfig.applyInterest(debt)
            if (debt > 0) log.add(CombatLogEntry.create(l10n.format("log.debt_interest", debt), turnNumber))
        }
        if (poisonBefore > 0) log.add(CombatLogEntry.create(l10n.format("log.poison_damage_player", poisonBefore), turnNumber))
        if (regenBefore > 0) log.add(CombatLogEntry.create(l10n.format("log.regen_heal_player", regenBefore), turnNumber))
        if (player.isDead()) {
            endCombat(victory = false)
            return
        }

        // Draw cards
        drawCards(HAND_SIZE)

        currentPhase = TurnPhase.PLAYER_ACTION

        log.add(CombatLogEntry.create(l10n.format("log.turn_header", turnNumber), turnNumber))
    }

    private fun drawCards(count: Int) {
        repeat(count) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) return
                // Shuffle discard into draw
                drawPile.addAll(discardPile.shuffled(rng))
                discardPile.clear()
                log.add(CombatLogEntry.create(l10n.get("log.reshuffle_discard"), turnNumber))
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
                        log.add(CombatLogEntry.create(l10n.format("log.dealt_damage", actualDamage, enemy.name), turnNumber))
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
                    clearArrearsIfEscaped()
                }
                is CardResolver.Effect.GainGold -> {
                    gold += effect.amount
                }
                is CardResolver.Effect.WipeDebt -> {
                    debt = 0
                    clearArrearsIfEscaped()
                }
                is CardResolver.Effect.EscrowShieldActivate -> {
                    activateEscrowShield()
                }
                is CardResolver.Effect.AddDebt -> {
                    // Debt added directly to the player; capped, and never escrow-halved (the
                    // escrow only shields Credit-shortfall borrowing, handled in playCard).
                    addDebt(effect.amount)
                }
                is CardResolver.Effect.GainCredit -> {
                    energy = minOf(energy + effect.amount, MAX_ENERGY_CAP)
                }
                is CardResolver.Effect.ExhaustFromHand -> {
                    // Cost one card from the hand; prefer a card other than the just-played source.
                    val sacrificed = hand.firstOrNull { it.id != sourceCard.id } ?: hand.firstOrNull()
                    if (sacrificed != null) {
                        hand.remove(sacrificed)
                        exhaustPile.add(sacrificed)
                        sacrificed.exhausted = true
                    }
                }
            }
        }
    }

    private fun endCombat(victory: Boolean) {
        currentPhase = TurnPhase.COMBAT_END
        log.add(CombatLogEntry.create(l10n.get(if (victory) "log.victory" else "log.defeat"), turnNumber))
    }

    data class PlayResult(val success: Boolean, val message: String)
    data class TurnResult(val success: Boolean, val message: String)
}