package com.debtsdecks.gdx.input

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.math.Vector3
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.TurnPhase
import com.debtsdecks.gdx.render.CombatRenderer

class CombatInputHandler(
    private val combatEngine: CombatEngine,
    private val camera: com.badlogic.gdx.graphics.OrthographicCamera,
    private val renderer: CombatRenderer
) : InputProcessor {

    private var selectedCard: CardInstance? = null
    private val touchPos = Vector3()
    private val worldPos = Vector3()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer > 0) return false

        val state = combatEngine.getState()

        // Convert screen to world coordinates
        touchPos.set(screenX.toFloat(), screenY.toFloat(), 0f)
        camera.unproject(touchPos)
        worldPos.set(touchPos)

        // Handle based on phase
        return when (state.currentTurn) {
            TurnPhase.PLAYER_ACTION -> handlePlayerAction(worldPos.x, worldPos.y, state)
            TurnPhase.COMBAT_END -> handleCombatEnd()
            else -> false
        }
    }

    private fun handlePlayerAction(x: Float, y: Float, state: CombatState): Boolean {
        // Check end turn button
        if (CombatRenderer.endTurnButtonBounds.contains(x, y)) {
            combatEngine.endPlayerTurn()
            selectedCard = null
            renderer.setSelectedCard(null)
            return true
        }

        // Check hand cards (only if no card selected)
        if (selectedCard == null) {
            val hand = state.hand
            val cardWidth = 140f
            val cardHeight = 200f
            val cardSpacing = 10f
            val handY = 50f
            val totalWidth = hand.size * cardWidth + (hand.size - 1) * cardSpacing
            val startX = (1280f - totalWidth) / 2

            for ((index, card) in hand.withIndex()) {
                val cardX = startX + index * (cardWidth + cardSpacing)
                val cardY = handY

                if (x in cardX..cardX + cardWidth && y in cardY..cardY + cardHeight) {
                    if (card.canPlay(state.energy)) {
                        selectedCard = card
                        renderer.setSelectedCard(card)
                        return true
                    }
                }
            }
        } else {
            // Card selected - check enemy targets
            if (selectedCard.targetType.name == "ENEMY" || selectedCard.targetType.name == "RANDOM_ENEMY") {
                val enemies = state.enemies
                val enemyW = 180f
                val enemyH = 220f
                val enemyAreaY = 450f
                val startX = (1280f - (enemies.size * 200f + (enemies.size - 1) * 20f)) / 2

                for ((index, enemy) in enemies.withIndex()) {
                    if (enemy.hp <= 0) continue
                    val ex = startX + index * 220f
                    val ey = enemyAreaY

                    if (x in ex..ex + enemyW && y in ey..ey + enemyH) {
                        val result = combatEngine.playCard(selectedCard.id, enemy.id)
                        selectedCard = null
                        renderer.setSelectedCard(null)
                        return result.success
                    }
                }
            } else if (selectedCard.targetType.name == "SELF") {
                // Self-target: play immediately on tap anywhere (or tap player area)
                val result = combatEngine.playCard(selectedCard.id, null)
                selectedCard = null
                renderer.setSelectedCard(null)
                return result.success
            } else if (selectedCard.targetType.name == "ALL_ENEMIES") {
                val result = combatEngine.playCard(selectedCard.id, "ALL")
                selectedCard = null
                renderer.setSelectedCard(null)
                return result.success
            }
        }

        return false
    }

    private fun handleCombatEnd(): Boolean {
        // Restart combat
        restartCombat()
        return true
    }

    private fun restartCombat() {
        // Re-create combat with first enemy (Thug)
        // This is MVP - just restart the same fight
        val app = com.debtsdecks.DebtsAndDecksApp.container
        val cardRegistry = app.get<com.debtsdecks.core.cards.CardRegistry>()
        val rng = app.get<kotlin.random.Random>()

        val engine = CombatEngine(cardRegistry, rng)

        // Load enemy definitions
        val context = android.content.ContextCompat.getApplicationContext(
            com.debtsdecks.DebtsAndDecksApp.container.get<android.app.Application>()
        ) ?: return

        val enemyDefs = com.debtsdecks.core.data.DataLoader.loadEnemies(context)
        val starterDeck = listOf(
            "strike", "strike", "strike", "strike", "strike",
            "defend", "defend", "defend",
            "bash", "survive"
        )

        // Start with just the first enemy for MVP
        engine.startCombat(listOf(enemyDefs[0]), starterDeck)

        // Replace the engine in the screen (hack for MVP - proper DI would be better)
        // For now, we just note this needs a proper reset mechanism
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
    override fun keyDown(keycode: Int): Boolean = false
    override fun keyUp(keycode: Int): Boolean = false
    override fun keyTyped(character: Char): Boolean = false
}