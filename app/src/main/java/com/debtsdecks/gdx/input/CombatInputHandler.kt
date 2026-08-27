package com.debtsdecks.gdx.input

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import com.debtsdecks.core.combat.NodeConfig
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
            RunManager.Phase.NODE -> handleNodeTap(worldPos.x, worldPos.y)
            RunManager.Phase.VICTORY, RunManager.Phase.DEFEAT -> {
                runManager.restartRun()
                deselect()
                true
            }
        }
    }

    /** C7: node screen taps — main choices + sub-offers (shop/remove/loan) + cancel. */
    private fun handleNodeTap(x: Float, y: Float): Boolean {
        val mode = renderer.getNodeMode()
        if (mode == CombatRenderer.NodeMode.CHOICES) {
            // Main choice buttons (0..4).
            for (i in 0..5) {
                if (renderer.nodeChoiceBounds(i).contains(x, y)) {
                    return when (i) {
                        0 -> { runManager.takeNodeFreePick(runManager.rewardChoices.first()); true }
                        1 -> { if (runManager.repayViaNode()) { announce(); true } else false }
                        2 -> { renderer.setNodeMode(CombatRenderer.NodeMode.SHOP); true }
                        3 -> { renderer.setNodeMode(CombatRenderer.NodeMode.REMOVE); true }
                        4 -> { renderer.setNodeMode(CombatRenderer.NodeMode.LOAN); true }
                        5 -> {
                            // P1-E: the 6th button is only actionable while an upgrade is available;
                            // otherwise the tap silently does nothing (button renders disabled).
                            val upgradeAvailable = runManager.gold >= NodeConfig.UPGRADE_BASE &&
                                runManager.upgradesRemaining > 0 &&
                                runManager.resolveNodeUpgradeCards().isNotEmpty()
                            if (upgradeAvailable) {
                                renderer.setNodeMode(CombatRenderer.NodeMode.UPGRADE)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            }
            return false
        }
        // Sub-offer modes: tap a card to choose, or the CANCEL button (top-right corner).
        if (y > 650f && x > 1100f) { // CANCEL stub zone
            renderer.setNodeMode(CombatRenderer.NodeMode.CHOICES)
            return true
        }
        return when (mode) {
            CombatRenderer.NodeMode.SHOP -> {
                runManager.nodeShopChoices.forEachIndexed { index, card ->
                    if (renderer.nodeSubCardBounds(index, runManager.nodeShopChoices.size).contains(x, y)) {
                        return runManager.buyCard(card)
                    }
                }
                false
            }
            CombatRenderer.NodeMode.REMOVE -> {
                val removeCards = runManager.resolveNodeRemoveCards()
                removeCards.forEachIndexed { index, card ->
                    if (renderer.nodeSubCardBounds(index, removeCards.size).contains(x, y)) {
                        return runManager.removeCardFromDeck(card.id)
                    }
                }
                false
            }
            CombatRenderer.NodeMode.UPGRADE -> {
                val upgradeCards = runManager.resolveNodeUpgradeCards()
                upgradeCards.forEachIndexed { index, card ->
                    if (renderer.nodeSubCardBounds(index, upgradeCards.size).contains(x, y)) {
                        return runManager.upgradeCard(card.id)
                    }
                }
                false
            }
            CombatRenderer.NodeMode.LOAN -> {
                renderer.setNodeMode(CombatRenderer.NodeMode.CHOICES)
                runManager.takeLoan()
            }
            CombatRenderer.NodeMode.CHOICES -> false
        }
    }

    private fun announce() {
        soundManager.playCardPlay()
        refreshAndAnnounce()
    }

    private fun handlePlayerAction(x: Float, y: Float, state: CombatState): Boolean {
        if (CombatRenderer.endTurnButtonBounds.contains(x, y)) {
            soundManager.playEndTurn()
            combatEngine.endPlayerTurn()
            refreshAndAnnounce()
            deselect()
            return true
        }

        val tappedCard = handCardAt(x, y, state.hand)
        if (tappedCard != null) {
            when {
                selectedCard?.id == tappedCard.id -> deselect() // tap the selected card again to cancel it
                tappedCard.isPlayable(state.debt) -> {
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
