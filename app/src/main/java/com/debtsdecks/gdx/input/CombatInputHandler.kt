package com.debtsdecks.gdx.input

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.model.TurnPhase
import com.debtsdecks.gdx.audio.SoundManager
import com.debtsdecks.gdx.render.CombatRenderer

class CombatInputHandler(
    private val combatEngine: CombatEngine,
    private val viewport: Viewport,
    private val renderer: CombatRenderer,
    private val runManager: RunManager,
    private val soundManager: SoundManager
) : InputProcessor {

    private var selectedCard: CardInstance? = null
    private val touchPos = Vector2()

    // R6: dedicated repay-by-discard flow — tap the REPAY CARD button to arm it, then tap a
    // hand card to discard it for a flat Debt cut. Kept separate from selectedCard/card-play
    // state since repaying never resolves a CardDefinition effect.
    private var repayDiscardModeActive: Boolean = false

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer > 0) return false

        touchPos.set(screenX.toFloat(), screenY.toFloat())
        val worldPos = viewport.unproject(touchPos)

        return when (runManager.phase) {
            RunManager.Phase.COMBAT -> {
                val state = combatEngine.getState()
                if (state.currentTurn == TurnPhase.PLAYER_ACTION) {
                    handlePlayerAction(worldPos.x, worldPos.y, state)
                } else {
                    false
                }
            }
            RunManager.Phase.REWARD -> handleRewardTap(worldPos.x, worldPos.y)
            RunManager.Phase.VICTORY, RunManager.Phase.DEFEAT -> {
                runManager.restartRun()
                deselect()
                setRepayDiscardMode(false)
                true
            }
        }
    }

    private fun handleRewardTap(x: Float, y: Float): Boolean {
        val choices = runManager.rewardChoices
        choices.forEachIndexed { index, card ->
            if (CombatRenderer.rewardCardBounds(index, choices.size).contains(x, y)) {
                runManager.chooseReward(card)
                return true
            }
        }
        return false
    }

    private fun handlePlayerAction(x: Float, y: Float, state: CombatState): Boolean {
        if (CombatRenderer.endTurnButtonBounds.contains(x, y)) {
            soundManager.playEndTurn()
            combatEngine.endPlayerTurn()
            refreshAndAnnounce()
            deselect()
            setRepayDiscardMode(false)
            return true
        }

        // R6/R8: REPAY controls are only surfaced/enabled here, i.e. gated behind the same
        // `currentTurn == PLAYER_ACTION` check `touchDown` already applies before ever calling
        // handlePlayerAction. This is a deliberate choice (mirrors playCard's own UX gate) even
        // though CombatEngine.repayDebt() itself has no phase gate and would accept calls at any
        // time — see apply-progress-phase3 for the full rationale.
        if (CombatRenderer.repayGoldButtonBounds.contains(x, y)) {
            return handleRepayGoldTap(state)
        }
        if (CombatRenderer.repayDiscardButtonBounds.contains(x, y)) {
            return handleRepayDiscardToggleTap(state)
        }

        val tappedCard = handCardAt(x, y, state.hand)
        if (tappedCard != null) {
            if (repayDiscardModeActive) {
                return repayWithDiscardedCard(tappedCard)
            }
            when {
                selectedCard?.id == tappedCard.id -> deselect() // tap the selected card again to cancel it
                tappedCard.isPlayable() -> {
                    soundManager.playCardSelect()
                    select(tappedCard)
                }
                else -> Unit // exhausted, ignore the tap (cost no longer gates playability — see isPlayable())
            }
            return true
        }

        val card = selectedCard ?: return false
        return when (card.targetType) {
            TargetType.ENEMY, TargetType.RANDOM_ENEMY -> {
                val enemy = enemyAt(x, y, state.enemies) ?: return false
                playCard(card, enemy.id)
            }
            TargetType.SELF -> playCard(card, null)
            TargetType.ALL_ENEMIES -> playCard(card, null)
        }
    }

    private fun handCardAt(x: Float, y: Float, hand: List<CardInstance>): CardInstance? {
        val totalWidth = hand.size * CARD_WIDTH + (hand.size - 1) * CARD_SPACING
        val startX = (WORLD_WIDTH - totalWidth) / 2

        hand.forEachIndexed { index, card ->
            val cardX = startX + index * (CARD_WIDTH + CARD_SPACING)
            if (x in cardX..cardX + CARD_WIDTH && y in HAND_Y..HAND_Y + CARD_HEIGHT) {
                return card
            }
        }
        return null
    }

    private fun enemyAt(x: Float, y: Float, enemies: List<com.debtsdecks.core.model.EnemyState>): com.debtsdecks.core.model.EnemyState? {
        // Mirrors CombatRenderer.drawEnemies' layout exactly: centering uses ENEMY_CENTERING_WIDTH
        // per slot while the per-index step is the wider ENEMY_STEP_X (matches the renderer's own
        // 200f/220f split) so hitboxes stay aligned with what's actually drawn.
        val startX = (WORLD_WIDTH - (enemies.size * ENEMY_CENTERING_WIDTH + (enemies.size - 1) * ENEMY_SLOT_GAP)) / 2

        enemies.forEachIndexed { index, enemy ->
            if (enemy.hp <= 0) return@forEachIndexed
            val ex = startX + index * ENEMY_STEP_X
            if (x in ex..ex + ENEMY_WIDTH && y in ENEMY_AREA_Y..ENEMY_AREA_Y + ENEMY_HEIGHT) {
                return enemy
            }
        }
        return null
    }

    /** R6 GOLD mode: spends `min(gold, debt)` 1:1, no card selection needed. */
    private fun handleRepayGoldTap(state: CombatState): Boolean {
        if (state.debt <= 0 || state.gold <= 0) return true // button tapped but nothing to do; consume the tap
        val result = combatEngine.repayDebt(CombatEngine.RepayMode.GOLD)
        if (result.success) soundManager.playCardPlay()
        refreshAndAnnounce()
        return true
    }

    /** R6 DISCARD mode: arms/disarms tap-a-card-to-repay; the actual repay happens in [repayWithDiscardedCard]. */
    private fun handleRepayDiscardToggleTap(state: CombatState): Boolean {
        if (state.debt <= 0) return true // nothing to repay; consume the tap without arming the mode
        if (!repayDiscardModeActive) soundManager.playCardSelect()
        setRepayDiscardMode(!repayDiscardModeActive)
        return true
    }

    private fun repayWithDiscardedCard(card: CardInstance): Boolean {
        val result = combatEngine.repayDebt(CombatEngine.RepayMode.DISCARD, card.id)
        if (result.success) soundManager.playCardPlay()
        setRepayDiscardMode(false)
        refreshAndAnnounce()
        deselect()
        return true
    }

    private fun setRepayDiscardMode(active: Boolean) {
        repayDiscardModeActive = active
        renderer.setRepayDiscardMode(active)
    }

    private fun select(card: CardInstance) {
        selectedCard = card
        renderer.setSelectedCard(card)
    }

    private fun deselect() {
        selectedCard = null
        renderer.setSelectedCard(null)
    }

    private fun playCard(card: CardInstance, targetId: String?): Boolean {
        val result = combatEngine.playCard(card.id, targetId)
        if (result.success) soundManager.playCardPlay()
        refreshAndAnnounce()
        deselect()
        return result.success
    }

    private fun refreshAndAnnounce() {
        val before = runManager.phase
        runManager.refresh()
        if (before == RunManager.Phase.COMBAT) {
            when (runManager.phase) {
                RunManager.Phase.VICTORY -> soundManager.playVictory()
                RunManager.Phase.DEFEAT -> soundManager.playDefeat()
                else -> Unit
            }
        }
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun keyDown(keycode: Int): Boolean = false
    override fun keyUp(keycode: Int): Boolean = false
    override fun keyTyped(character: Char): Boolean = false

    companion object {
        private const val WORLD_WIDTH = 1280f
        private const val CARD_WIDTH = 140f
        private const val CARD_HEIGHT = 200f
        private const val CARD_SPACING = 10f
        private const val HAND_Y = 50f
        private const val ENEMY_WIDTH = 180f
        private const val ENEMY_HEIGHT = 220f
        private const val ENEMY_CENTERING_WIDTH = 200f
        private const val ENEMY_STEP_X = 220f
        private const val ENEMY_SLOT_GAP = 20f
        private const val ENEMY_AREA_Y = 450f
    }
}
