package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.NodeConfig
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** card-upgrades R10: the sim's NodePolicy ladder buys an upgrade when affordable and the cap has room. */
class NodePolicyTest {

    private val registry = CardRegistry.create(TestAssetLoader.loadCards())
    private val enemies = TestAssetLoader.loadEnemies()
    private val sequence = TestAssetLoader.loadSequence()

    /** Kills the first [times] encounters, free-picking after each but the last, ending in NODE. */
    private fun runAtNode(times: Int): RunManager {
        val rng = Random(3)
        val engine = CombatEngine(registry, NoOpLocalizer, rng)
        val run = RunManager(engine, registry, enemies, sequence, rng)
        var killed = 0
        var guard = 0
        while (killed < times) {
            guard++
            check(guard < 400) { "node driver exceeded guard" }
            when (run.phase) {
                RunManager.Phase.COMBAT -> {
                    val state = engine.getState()
                    if (state.currentTurn != TurnPhase.PLAYER_ACTION) {
                        engine.endPlayerTurn()
                        run.refresh()
                        continue
                    }
                    val enemy = state.enemies.firstOrNull { it.hp > 0 }
                    val atk = state.hand.firstOrNull {
                        it.type == CardType.ATTACK && it.targetType == TargetType.ENEMY && it.isPlayable()
                    }
                    if (enemy != null && atk != null) {
                        engine.playCard(atk.id, enemy.id)
                    } else {
                        engine.endPlayerTurn()
                    }
                    run.refresh()
                }
                RunManager.Phase.NODE -> {
                    killed++
                    if (killed < times) {
                        run.takeNodeFreePick(run.rewardChoices.first())
                    } else {
                        return run
                    }
                }
                else -> error("unexpected phase ${run.phase} after $killed kills")
            }
        }
        error("unreachable")
    }

    @Test
    fun `ladder buys the upgrade first when gold and cap allow`() {
        // Upgrade sits at position 3 (after cheap early shop + survival loan): reach a node where
        // upgrading is the best affordance — late gold, no cheap shop slot, cap open.
        val run = runAtNode(2) // upgrade sits FIRST in the ladder: gold 20 (10+10) >= 15 and cap open
        val gold0 = run.gold

        NodePolicy.act(run, ScriptedPolicy)

        assertEquals(NodeConfig.UPGRADE_BASE, gold0 - run.gold, "ladder spends exactly the flat upgrade cost")
        assertEquals(1, run.upgradesRemaining)
        assertEquals(RunManager.Phase.COMBAT, run.phase) // one purchase ends the node
    }

    @Test
    fun `ladder skips the upgrade when gold is below the flat base`() {
        val run = runAtNode(1) // slot 0 thug = 10 gold < 15

        NodePolicy.act(run, ScriptedPolicy)

        assertEquals(2, run.upgradesRemaining) // no upgrade bought
        assertEquals(RunManager.Phase.COMBAT, run.phase) // some other action ended the node
    }
}