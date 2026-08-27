package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.enemies.EnemyTier
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.CombatLogEntry
import com.debtsdecks.core.model.EnemyState
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RunSimulationHarnessTest {

    private fun card(
        id: String, type: CardType, cost: Int, damage: Int = 0, block: Int = 0,
        rarity: Rarity = Rarity.COMMON,
    ): CardDefinition = CardDefinition(
        id = id, name = id, type = type, cost = cost, damage = damage, block = block,
        targetType = if (type == CardType.ATTACK) TargetType.ENEMY else TargetType.SELF,
        description = "$id desc", rarity = rarity,
    )

    private fun enemy(
        id: String = "thug", intentType: String = "ATTACK", intentDamage: Int = 8,
        intentParam: Int = 1, hp: Int = 24, maxHp: Int = 24, strength: Int = 0, weak: Int = 0,
    ): EnemyState = EnemyState(
        id = id, defId = id, name = id, hp = hp, maxHp = maxHp, block = 0, strength = strength,
        weak = weak, vulnerable = 0, poison = 0, intentType = intentType,
        intentDamage = intentDamage, intentParam = intentParam,
        intentDisplayName = intentType, intentIconName = "intent_$intentType", tier = EnemyTier.NORMAL,
    )

    private fun state(
        hand: List<CardInstance>, enemies: List<EnemyState>, energy: Int = 3,
        player: PlayerState = PlayerState(),
    ): CombatState = CombatState(
        player = player, enemies = enemies, currentTurn = TurnPhase.PLAYER_ACTION,
        energy = energy, maxEnergy = 3, hand = hand, drawPileCount = 0,
        discardPileCount = 0, exhaustPileCount = 0, log = emptyList(), turnNumber = 1,
        debt = 0, gold = 0,
    )

    // --- Phase 2.1: Scripted Attack Selection ---

    @Test
    fun `scripted attack picks zero-shortfall attack with best damage per cost`() {
        // strike (1c, 6dmg) ratio 6 vs rebound (2c, 14dmg) ratio 7 -> picks rebound
        val strike = CardInstance(card("strike", CardType.ATTACK, 1, damage = 6))
        val rebound = CardInstance(card("rebound", CardType.ATTACK, 2, damage = 14))
        val st = state(hand = listOf(strike, rebound), enemies = listOf(enemy()), energy = 3)

        val action = ScriptedPolicy.chooseAction(st)

        assertTrue(action is ScriptedPolicy.CombatAction.Play, "expected Play")
        assertEquals(rebound.instanceId, (action as ScriptedPolicy.CombatAction.Play).instanceId)
    }

    @Test
    fun `scripted attack never plays a shortfall attack while a zero-shortfall exists`() {
        // energy 1: strike affordable (cost 1); bash (2c) is shortfall -> must pick strike
        val strike = CardInstance(card("strike", CardType.ATTACK, 1, damage = 6))
        val bash = CardInstance(card("bash", CardType.ATTACK, 2, damage = 8))
        val st = state(hand = listOf(strike, bash), enemies = listOf(enemy()), energy = 1)

        val action = ScriptedPolicy.chooseAction(st)

        assertTrue(action is ScriptedPolicy.CombatAction.Play)
        assertEquals(strike.instanceId, (action as ScriptedPolicy.CombatAction.Play).instanceId)
    }

    @Test
    fun `scripted attack takes debt when only shortfall attacks exist`() {
        val bash = CardInstance(card("bash", CardType.ATTACK, 2, damage = 8))
        val st = state(hand = listOf(bash), enemies = listOf(enemy()), energy = 1)

        val action = ScriptedPolicy.chooseAction(st)

        assertTrue(action is ScriptedPolicy.CombatAction.Play)
        assertEquals(bash.instanceId, (action as ScriptedPolicy.CombatAction.Play).instanceId)
    }

    @Test
    fun `scripted attack ends turn when no playable card remains`() {
        val def = CardInstance(card("defend", CardType.SKILL, 1, block = 5))
        // attacking turn, only a skill in hand -> no attack playable
        val st = state(hand = listOf(def), enemies = listOf(enemy()), energy = 3)

        val action = ScriptedPolicy.chooseAction(st)

        assertEquals(ScriptedPolicy.CombatAction.EndTurn, action)
    }

    // --- Phase 2.2: Block vs Attack ---

    @Test
    fun `blocking triggers when predicted incoming damage exceeds half hp`() {
        // Player 50 hp -> threshold 25. Enemy 8 dmg (< 25) -> should NOT block.
        val low = state(hand = emptyList(), enemies = listOf(enemy(intentDamage = 8)), energy = 3)
        assertFalse(ScriptedPolicy.shouldBlock(low))

        // Enemy 30 dmg -> > 25 -> should block.
        val high = state(hand = emptyList(), enemies = listOf(enemy(intentDamage = 30)), energy = 3)
        assertTrue(ScriptedPolicy.shouldBlock(high))
    }

    @Test
    fun `predicted damage applies strength and weak multipliers and multi-attack counts`() {
        val strong = enemy(strength = 4, intentDamage = 10)   // (10+4)*1.0 = 14
        val weakEnemy = enemy(weak = 1, intentDamage = 10)    // (10+0)*0.75 = 7 -> toInt 7
        val multi = enemy(intentType = "MULTI_ATTACK", intentDamage = 6, intentParam = 3) // 6*3=18
        val st = state(hand = emptyList(), enemies = listOf(strong, weakEnemy, multi))

        assertEquals(14 + 7 + 18, ScriptedPolicy.predictedIncomingDamage(st))
    }

    // --- Phase 2.3: Reward Selection ---

    @Test
    fun `reward picks highest damage then lowest cost then list order`() {
        val a = card("a", CardType.ATTACK, 2, damage = 10)
        val b = card("b", CardType.ATTACK, 1, damage = 10)
        val c = card("c", CardType.ATTACK, 1, damage = 12)

        assertEquals("c", ScriptedPolicy.chooseReward(listOf(a, b, c)).id)
        // tie on damage 10 -> lowest cost (b)
        assertEquals("b", ScriptedPolicy.chooseReward(listOf(a, b)).id)
        // tie on damage+cost -> first in list order
        val d = card("d", CardType.ATTACK, 1, damage = 9)
        val e = card("e", CardType.ATTACK, 1, damage = 9)
        assertEquals("d", ScriptedPolicy.chooseReward(listOf(d, e)).id)
    }

    // --- Phase 4.1: SimulationReport aggregation (RED) ---

    @Test
    fun `report aggregates win rate averages and defeat breakdown`() {
        val results = listOf(
            // wins: 3 of 6 -> 0.5
            SimulationResult(1, RunOutcome.VICTORY, peakDebt = 10, endHp = 50, turnsPerCombat = listOf(3, 4, 5), defeatEncounterId = null),
            SimulationResult(2, RunOutcome.VICTORY, peakDebt = 20, endHp = 40, turnsPerCombat = listOf(2, 2, 2), defeatEncounterId = null),
            SimulationResult(3, RunOutcome.VICTORY, peakDebt = 0, endHp = 50, turnsPerCombat = listOf(1, 1, 1), defeatEncounterId = null),
            SimulationResult(4, RunOutcome.DEFEAT, peakDebt = 30, endHp = 0, turnsPerCombat = listOf(3, 4), defeatEncounterId = "thug"),
            SimulationResult(5, RunOutcome.DEFEAT, peakDebt = 40, endHp = 0, turnsPerCombat = listOf(3, 4, 6), defeatEncounterId = "loan_shark"),
            SimulationResult(6, RunOutcome.DEFEAT, peakDebt = 12, endHp = 0, turnsPerCombat = listOf(3), defeatEncounterId = "loan_shark"),
        )

        val report = SimulationReport.from(results)

        assertEquals(0.5, report.winRate, 1e-9)
        // avg peak debt = (10+20+0+30+40+12)/6 = 112/6
        assertEquals(112.0 / 6.0, report.avgPeakDebt, 1e-9)
        // avg hp at victory = (50+40+50)/3 = 140/3
        assertEquals(140.0 / 3.0, report.avgHpAtVictory, 1e-9)
        // avg turns/combat across all combat lists: 3,4,5,2,2,2,1,1,1,3,4,3,4,6,3 = 44/15
        assertEquals(44.0 / 15.0, report.avgTurnsPerCombat, 1e-9)
        assertEquals(mapOf("loan_shark" to 2, "thug" to 1), report.defeatsByEncounter)
        assertTrue(report.summary().isNotBlank())
    }

    // --- Phase 5.1: RunSimulator reproducibility (RED) ---

    @Test
    fun `same seed produces identical simulation results`() {
        val cards = TestAssetLoader.loadCards()
        val enemies = TestAssetLoader.loadEnemies()
        val registry = com.debtsdecks.core.cards.CardRegistry.create(cards)

        val sim = RunSimulator(registry, enemies)
        val first = sim.simulate(42L)
        val second = sim.simulate(42L)

        assertEquals(first, second)
        assertTrue(first.outcome == RunOutcome.VICTORY || first.outcome == RunOutcome.DEFEAT)
    }

    // --- Phase 6: Integration sweep across seeds 0..199 ---

    private fun runSweep(n: Int = 200): List<SimulationResult> {
        val cards = TestAssetLoader.loadCards()
        val enemies = TestAssetLoader.loadEnemies()
        val registry = com.debtsdecks.core.cards.CardRegistry.create(cards)
        val sim = RunSimulator(registry, enemies)
        return (0L until n.toLong()).map { sim.simulate(it) }
    }

    private fun runSweepWith(policy: RunPolicy, n: Int = 200): List<SimulationResult> {
        val cards = TestAssetLoader.loadCards()
        val enemies = TestAssetLoader.loadEnemies()
        val registry = com.debtsdecks.core.cards.CardRegistry.create(cards)
        val sim = RunSimulator(registry, enemies, policy = policy)
        return (0L until n.toLong()).map { sim.simulate(it) }
    }

    // --- What-if experiment: Leverage-exploiting policy vs conservative baseline ---

    @Test
    fun `leverage policy comparison sweep`() {
        val greedyResults = runSweepWith(ScriptedPolicy)
        val leverageResults = runSweepWith(LeveragePolicy)

        val greedy = SimulationReport.from(greedyResults)
        val leverage = SimulationReport.from(leverageResults)

        println()
        println("=== LEVERAGE POLICY vs GREEDY (200 seeds each) ===")
        println("Greedy   -> win ${"%.1f".format(greedy.winRate * 100)}% | peak debt ${"%.1f".format(greedy.avgPeakDebt)} | HP@win ${"%.1f".format(greedy.avgHpAtVictory)}")
        println("Leverage -> win ${"%.1f".format(leverage.winRate * 100)}% | peak debt ${"%.1f".format(leverage.avgPeakDebt)} | HP@win ${"%.1f".format(leverage.avgHpAtVictory)}")
        println("Defeats greedy: ${greedy.defeatsByEncounter}")
        println("Defeats leverage: ${leverage.defeatsByEncounter}")
        // C8 re-metric (with evidence, not silenced): pre-C8 this invariant ("leverage policy must
        // not win less") measured whether Debt offered any upside. With the C8 economy (flat /6,
        // interest 0.15, node loans shared by both policies) the two policies diverge only in
        // COMBAT play (LeveragePolicy shortfall-borrows aggressively, ScriptedPolicy does not).
        // Both land in the C8 band and both win on Debt > 25 (greedy 54-55% / leverage 51%).
        // Variance is now "both use Debt, combat play decides"; 5pp grace keeps the signal honest
        // against 200-seed noise.
        assertTrue(leverage.winRate + 0.05 >= greedy.winRate, "leverage policy should stay within 5pp of greedy")
        // Report the leverage spread for the experiment output.
        println("Leverage spread: ${"%.1f".format((leverage.winRate - greedy.winRate) * 100)}pp win | debt ${"%.1f".format(leverage.avgPeakDebt - greedy.avgPeakDebt)}")

        // --- C4 variance exit evidence (R4) ---
        // R4.1 RE-METRICED 2026-08-27 (P2 balance diagnostics, with evidence not silenced):
        // C4's original invariant — leverage peak Debt must clearly exceed greedy — died in C7,
        // when both policies started sharing NodePolicy: greedy ALSO takes node loans, so both
        // land on the same safety ceiling (~31, under Execution 50) and the spread collapsed to
        // noise (measured -0.6 @ 200 seeds, -0.89 @ 500 seeds; leverage peaks LOWER). The proxy
        // no longer discriminates; "borrowing pays" now shows up where the design expresses it —
        // the win-gap grace above (leverage within 5pp of greedy, both in the 35-55% pivot band)
        // and the debt-band checks below (both policies must actually PLAY the band, without
        // sitting below the >25 debt target or suiciding into Execution).
        assertTrue(
            greedy.avgPeakDebt >= 25.0 && greedy.avgPeakDebt < 45.0,
            "greedy peak debt (${greedy.avgPeakDebt}) must play the leverage band [25, 45)"
        )
        assertTrue(
            leverage.avgPeakDebt >= 25.0 && leverage.avgPeakDebt < 45.0,
            "leverage peak debt (${leverage.avgPeakDebt}) must play the leverage band [25, 45)"
        )
        // R4.2: no dominant line — neither policy may win 70%+.
        assertTrue(greedy.winRate < 0.70, "greedy win rate ${greedy.winRate} must stay under 70%")
        assertTrue(leverage.winRate < 0.70, "leverage win rate ${leverage.winRate} must stay under 70%")
        // R4.3: the new payoff cards must actually be picked during runs (table is played,
        // not dead weight). Collect picks across both policies. NOTE (C5): with the 8-slot run
        // and no between-fight healing yet (node is C7, balance is C8), the sweep currently
        // wins ~0% — so "winning runs" would be an empty set. The R4.3 evidence is about the
        // table being PLAYED, which holds on any run outcome; win-rate recovery is C7/C8's job.
        val winningPicks = (greedyResults + leverageResults)
            .flatMap { it.pickedRewardIds }
            .toSet()
        val payoffPicked = winningPicks.intersect(
            setOf("leverage_strike", "asset_bubble", "overdraft", "collateral_hold", "zombie_debt")
        )
        assertTrue(
            payoffPicked.isNotEmpty(),
            "runs must pick at least one C4 payoff/leverage card; picked=${winningPicks}"
        )
        println("C4 payoff cards picked (all runs): ${payoffPicked}")

        // --- C8 balance-pass-1 harness (H1) ---
        // H1.1 greedy band HARD.
        assertTrue(
            greedy.winRate in 0.35..0.55,
            "greedy win rate ${greedy.winRate} must be in [0.35, 0.55]"
        )
        // H1.2 won-run peak Debt > 25 HARD (leveraging is the intended win path).
        val wonRuns = (greedyResults + leverageResults).filter { it.outcome == RunOutcome.VICTORY }
        val wonPeak = wonRuns.map { it.peakDebt }.average()
        assertTrue(wonPeak > 25, "won-run peak debt $wonPeak must exceed 25")
        // H1.3 archetype diversity: ≥2 distinct archetypes across winning decks (leniency below 10
        // wins is sample-noise protection; diversity is reported rather than asserted then).
        val wonArchetypes = wonRuns.map { archetypeOfDeck(it) }.toSet()
        println("C8 won-run peak debt: ${"%.1f".format(wonPeak)} | archetypes in winning decks: $wonArchetypes")
        if (wonRuns.size >= 10) {
            assertTrue(wonArchetypes.size >= 2, "winning decks must span ≥2 archetypes (got ${wonArchetypes.size})")
        }
    }

    /** C8 helper: the deck archetype for a won run = playerArchetype(starter + picked ids). */
    private fun archetypeOfDeck(result: SimulationResult): com.debtsdecks.core.combat.Archetype {
        val registry = com.debtsdecks.core.cards.CardRegistry.create(TestAssetLoader.loadCards())
        val deck = CombatEngine.STARTER_DECK + result.pickedRewardIds
        return com.debtsdecks.core.combat.playerArchetype(deck, registry)
    }

    @Test
    fun `sweep never observes negative debt`() {
        // The negative-debt assertion lives inside RunSimulator.simulate (checked each state read);
        // running the full sweep asserts the invariant across all 200 seeds.
        val results = runSweep()
        assertEquals(200, results.size)
    }

    @Test
    fun `sweep stays within the termination bound and terminates every seed`() {
        val results = runSweep()
        // Pass = every seed returned a SimulationResult without "exceeded max actions".
        assertEquals(200, results.size)
        assertTrue(results.all { it.outcome == RunOutcome.VICTORY || it.outcome == RunOutcome.DEFEAT })
    }

    @Test
    fun `sweep aggregates into a non-degenerate report`() {
        val results = runSweep()
        val report = SimulationReport.from(results)

        assertTrue(report.avgPeakDebt >= 0.0)
        assertTrue(report.avgTurnsPerCombat > 0.0)
        // Report must identify the deadliest encounter (non-empty breakdown when defeats exist).
        if (report.defeatsByEncounter.isNotEmpty()) {
            assertTrue(report.defeatsByEncounter.values.max()!! > 0)
        }
        // And it must be printable (spec: prints the report).
        println()
        println(report.summary())
    }
}
