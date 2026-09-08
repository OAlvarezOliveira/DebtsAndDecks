package com.debtsdecks.core.simulation

import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.model.RunSequence

/**
 * Test-source mirror of the private `RunManager.slotIndex` (`RunManager.kt:140`), transcribed from
 * `advanceToNextCombat` (`RunManager.kt:305-327`).
 *
 * It exists because the node-level response channel (`openspec/changes/fv-e1-node-response-channel/`)
 * needs one fact `NodePolicy` cannot currently see: **which enemy the next combat will face**. No
 * accessor exposes `slotIndex`, and adding one would be an `app/src/main` change the proposal forbids
 * in both phases (§8). Deriving the slot from `nodeIndex` is forbidden too (decision §6.5) — and it is
 * genuinely wrong: a BREAK rematch adds a node without advancing a slot.
 *
 * The mirror is never trusted on its own. Every consumer asserts [expected] against the engine's real
 * enemy at every combat start (design D2), which is what makes decision §6.5 mechanical rather than
 * rhetorical.
 *
 * Usage per node, in this order:
 * ```
 * val pendingBefore = run.pendingBreakEncounter   // sample BEFORE acting
 * val debtBefore = run.debt
 * val next = cursor.nextEnemyId(pendingBefore)    // who the upcoming combat targets
 * NodePolicy.act(run, policy)
 * cursor.advance(pendingBefore, debtBefore, run)
 * assertEquals(cursor.expected, engine.getState().enemies.first().defId)
 * ```
 */
class RunSlotCursor(private val sequence: RunSequence) {

    /** 0-based sequence slot of the combat currently in progress; mirrors `RunManager.slotIndex`. */
    var slotIndex: Int = 0
        private set

    /** Mirrors `RunManager.breakEncounterUsedThisRun`: the rematch fires at most once per run. */
    var breakSeen: Boolean = false
        private set

    /** How many times the rematch was armed by a node LOAN *inside* `NodePolicy.act` (see [advance]). */
    var loanArmedBreakCount: Int = 0
        private set

    /** Enemy defId of the combat currently in progress — the value asserted against the engine. */
    var expected: String = sequence.slots[0].enemyId
        private set

    /**
     * The enemy the next combat will face, as knowable *before* the node acts:
     * a pending BREAK rematch keeps the run on the same slot and forces the collector
     * (`RunManager.kt:311-322`); otherwise the sequence advances by one slot.
     *
     * Null only past the last slot, which no node can reach (the final boss has no node after it).
     */
    fun nextEnemyId(pendingBefore: Boolean): String? =
        if (pendingBefore) BREAK_REMATCH_ENEMY_ID else sequence.slots.getOrNull(slotIndex + 1)?.enemyId

    /**
     * Advance the mirror after `NodePolicy.act` returned, i.e. after `advanceToNextCombat` ran.
     *
     * [pendingBefore] / [debtBefore] must be the values sampled BEFORE acting. The `loanArmedBreak`
     * term covers the case a naive mirror gets wrong: a node LOAN can arm the rematch *inside*
     * `NodePolicy.act` (`RunManager.kt:276-279`) after `pendingBreakEncounter` was sampled false, and
     * `advanceToNextCombat` then skips `slotIndex++`. `takeLoan` is the only node action that raises
     * debt (repay zeroes it; buy/remove/upgrade/free-pick never touch it), so the test is exact.
     */
    fun advance(pendingBefore: Boolean, debtBefore: Int, run: RunManager) {
        val loanArmedBreak = !breakSeen && !pendingBefore &&
            run.debt > debtBefore && run.debt >= DebtConfig.BREAK_THRESHOLD
        if (loanArmedBreak) loanArmedBreakCount++
        if (pendingBefore || loanArmedBreak) {
            breakSeen = true
            expected = BREAK_REMATCH_ENEMY_ID
        } else {
            slotIndex++
            expected = sequence.slots[slotIndex].enemyId
        }
    }

    companion object {
        /** The forced BREAK rematch enemy, mirroring `RunManager.kt:316`. */
        const val BREAK_REMATCH_ENEMY_ID: String = "collector"
    }
}
