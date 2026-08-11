package com.debtsdecks.gdx.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.EnemyState
import com.debtsdecks.core.model.TurnPhase

class CombatRenderer {
    private val shapeRenderer = ShapeRenderer()
    private val font = BitmapFont() // Default 15pt Arial
    private val smallFont = BitmapFont()

    // Layout constants
    private val screenWidth = 1280f
    private val screenHeight = 720f
    private val cardWidth = 140f
    private val cardHeight = 200f
    private val cardSpacing = 10f
    private val handY = 50f
    private val enemyAreaY = 450f
    private val playerAreaY = 50f
    private val energyX = 50f
    private val energyY = 620f
    private val endTurnBtnX = 1100f
    private val endTurnBtnY = 50f
    private val endTurnBtnW = 150f
    private val endTurnBtnH = 60f

    fun render(state: CombatState, batch: SpriteBatch, camera: com.badlogic.gdx.graphics.OrthographicCamera) {
        // Background
        drawBackground()

        // Enemy area
        drawEnemies(state.enemies, batch)

        // Player area (HP, Block, Energy)
        drawPlayer(state, batch)

        // Hand
        drawHand(state.hand, batch, state.currentTurn)

        // UI
        drawUI(state, batch)

        // Log
        drawLog(state.log, batch)
    }

    private fun drawBackground() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 1f)
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.end()
    }

    private fun drawEnemies(enemies: List<EnemyState>, batch: SpriteBatch) {
        val startX = (screenWidth - (enemies.size * 200f + (enemies.size - 1) * 20f)) / 2

        enemies.forEachIndexed { index, enemy ->
            val x = startX + index * 220f
            val y = enemyAreaY
            val w = 180f
            val h = 220f

            // Enemy body (placeholder rectangle)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(0.8f, 0.3f, 0.3f, 1f)
            shapeRenderer.rect(x, y, w, h)
            shapeRenderer.end()

            // Enemy name
            batch.begin()
            font.draw(batch, enemy.name, x + 10f, y + h - 10f)
            batch.end()

            // HP bar
            drawHPBar(x, y - 30f, w, 20f, enemy.hpPercent, Color.RED, Color.GREEN)

            // Block
            if (enemy.block > 0) {
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                shapeRenderer.setColor(0.3f, 0.6f, 1f, 1f)
                shapeRenderer.rect(x, y - 55f, w, 20f)
                shapeRenderer.end()
                batch.begin()
                font.draw(batch, "Block: ${enemy.block}", x + 10f, y - 40f)
                batch.end()
            }

            // Intent
            drawIntent(x, y + h + 10f, w, enemy)

            // Strength
            if (enemy.strength > 0) {
                batch.begin()
                font.draw(batch, "Str: ${enemy.strength}", x + 10f, y - 80f)
                batch.end()
            }
        }
    }

    private fun drawIntent(x: Float, y: Float, w: Float, enemy: EnemyState) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(1f, 0.8f, 0.2f, 1f)
        shapeRenderer.rect(x, y, w, 40f)
        shapeRenderer.end()

        batch.begin()
        font.draw(batch, enemy.intentDisplayName, x + 10f, y + 28f)
        batch.end()
    }

    private fun drawPlayer(state: CombatState, batch: SpriteBatch) {
        val x = 50f
        val y = 280f
        val w = 200f
        val h = 140f

        // Player panel
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.3f, 1f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()

        // HP
        batch.begin()
        font.draw(batch, "PLAYER", x + 10f, y + h - 10f)
        font.draw(batch, "HP: ${state.player.hp}/${state.player.maxHp}", x + 10f, y + h - 40f)
        batch.end()

        // HP bar
        drawHPBar(x, y + h - 55f, w, 20f, state.player.hpPercent, Color.RED, Color.GREEN)

        // Block
        if (state.player.block > 0) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(0.3f, 0.6f, 1f, 1f)
            shapeRenderer.rect(x, y + h - 80f, w, 20f)
            shapeRenderer.end()
            batch.begin()
            font.draw(batch, "Block: ${state.player.block}", x + 10f, y + h - 65f)
            batch.end()
        }

        // Strength/Weak/Vulnerable
        var statusY = y + 10f
        if (state.player.strength != 0) {
            batch.begin()
            font.draw(batch, "Str: ${state.player.strength}", x + 10f, statusY)
            batch.end()
            statusY += 25f
        }
        if (state.player.weak > 0) {
            batch.begin()
            smallFont.draw(batch, "Weak: ${state.player.weak}", x + 10f, statusY)
            batch.end()
            statusY += 20f
        }
        if (state.player.vulnerable > 0) {
            batch.begin()
            smallFont.draw(batch, "Vuln: ${state.player.vulnerable}", x + 10f, statusY)
            batch.end()
        }
    }

    private fun drawHand(hand: List<CardInstance>, batch: SpriteBatch, phase: TurnPhase) {
        val playable = phase == TurnPhase.PLAYER_ACTION
        val totalWidth = hand.size * cardWidth + (hand.size - 1) * cardSpacing
        val startX = (screenWidth - totalWidth) / 2

        hand.forEachIndexed { index, card ->
            val x = startX + index * (cardWidth + cardSpacing)
            val y = handY
            val selected = inputHandlerSelectedCard?.id == card.id

            // Card background
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(if (selected) Color.YELLOW else if (playable && card.canPlay(0)) Color.WHITE else Color.GRAY)
            shapeRenderer.rect(x, y, cardWidth, cardHeight)
            shapeRenderer.end()

            // Card border
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.setColor(0f, 0f, 0f, 1f)
            shapeRenderer.rect(x, y, cardWidth, cardHeight)
            shapeRenderer.end()

            // Card content
            batch.begin()
            // Cost
            font.draw(batch, card.cost.toString(), x + 10f, y + cardHeight - 10f)
            // Name
            font.draw(batch, card.name, x + 10f, y + cardHeight - 40f)
            // Description (wrapped)
            smallFont.draw(batch, card.description, x + 10f, y + 80f, cardWidth - 20f)
            // Type indicator
            smallFont.draw(batch, card.type.name, x + 10f, y + 50f)
            batch.end()
        }
    }

    private var inputHandlerSelectedCard: CardInstance? = null
    fun setSelectedCard(card: CardInstance?) { inputHandlerSelectedCard = card }

    private fun drawUI(state: CombatState, batch: SpriteBatch) {
        // Energy
        batch.begin()
        font.draw(batch, "ENERGY: ${state.energy}/${state.maxEnergy}", energyX, energyY)
        batch.end()

        // End Turn Button
        val canEndTurn = state.currentTurn == TurnPhase.PLAYER_ACTION
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(if (canEndTurn) Color.GREEN else Color.GRAY)
        shapeRenderer.rect(endTurnBtnX, endTurnBtnY, endTurnBtnW, endTurnBtnH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0f, 0f, 0f, 1f)
        shapeRenderer.rect(endTurnBtnX, endTurnBtnY, endTurnBtnW, endTurnBtnH)
        shapeRenderer.end()

        batch.begin()
        font.draw(batch, "END TURN", endTurnBtnX + 20f, endTurnBtnY + 40f)
        batch.end()

        // Turn phase indicator
        batch.begin()
        smallFont.draw(batch, "Phase: ${state.currentTurn.name}", 50f, 680f)
        smallFont.draw(batch, "Turn: ${state.turnNumber}", 50f, 660f)
        smallFont.draw(batch, "Deck: ${state.drawPileCount} | Discard: ${state.discardPileCount} | Exhaust: ${state.exhaustPileCount}", 50f, 640f)
        batch.end()

        // Win/Lose overlay
        if (state.currentTurn == TurnPhase.COMBAT_END) {
            val msg = if (state.player.hp > 0) "VICTORY!" else "DEFEAT"
            val color = if (state.player.hp > 0) Color.GREEN else Color.RED
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(0f, 0f, 0f, 0.8f)
            shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)
            shapeRenderer.end()
            batch.begin()
            font.getData().setScale(3f)
            font.setColor(color)
            val bounds = font.getBounds(msg)
            font.draw(batch, msg, (screenWidth - bounds.width) / 2, (screenHeight + bounds.height) / 2)
            font.getData().setScale(1f)
            font.setColor(Color.WHITE)
            smallFont.draw(batch, "Tap to restart", (screenWidth - 120f) / 2, (screenHeight - bounds.height) / 2 - 50f)
            batch.end()
        }
    }

    private fun drawLog(log: List<com.debtsdecks.core.model.CombatLogEntry>, batch: SpriteBatch) {
        val x = 900f
        val y = 100f
        val w = 350f
        val h = 500f

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()

        batch.begin()
        smallFont.draw(batch, "COMBAT LOG", x + 10f, y + h - 10f)

        var lineY = y + h - 35f
        log.reversed().take(20).forEach { entry ->
            if (lineY < y + 20f) return@forEach
            smallFont.draw(batch, entry.message, x + 10f, lineY, w - 20f)
            lineY -= 22f
        }
        batch.end()
    }

    private fun drawHPBar(x: Float, y: Float, w: Float, h: Float, percent: Float, emptyColor: Color, fullColor: Color) {
        // Background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(emptyColor.r * 0.3f, emptyColor.g * 0.3f, emptyColor.b * 0.3f, 1f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()

        // Fill
        val fillW = w * percent
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(fullColor)
        shapeRenderer.rect(x, y, fillW, h)
        shapeRenderer.end()

        // Border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(1f, 1f, 1f, 1f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()
    }

    fun dispose() {
        shapeRenderer.dispose()
        font.dispose()
        smallFont.dispose()
    }

    companion object {
        val endTurnButtonBounds = com.badlogic.gdx.math.Rectangle(1100f, 50f, 150f, 60f)
    }
}