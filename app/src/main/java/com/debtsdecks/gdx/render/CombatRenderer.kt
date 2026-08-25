package com.debtsdecks.gdx.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.EnemyState
import com.debtsdecks.core.model.TurnPhase

/**
 * [bundle] was wired in the combat-progression-and-i18n Phase 4a DI slice. Phase 4b-i consumed it
 * for HUD/in-combat-screen strings (labels, status readouts, buttons, turn/pile indicators).
 * Phase 4b-ii consumes it for [renderReward]'s header/cost text and [renderRunEnd]'s victory/
 * defeat/restart text; [renderRunEnd] no longer takes a caller-supplied `message` — it derives the
 * outcome text itself from `won: Boolean` via two distinct bundle keys. As of Phase 4b-iv,
 * `EnemyState.name`/`CardDefinition.name`/`CardDefinition.description` (including [renderReward]'s
 * per-card name/description) hold `card.<id>.name`/`card.<id>.description`/`enemy.<id>.name` bundle
 * keys sourced from `assets/cards/all.json` / `assets/enemies/all.json`, resolved here via
 * `bundle.get(...)` at each draw call. `TurnPhase`/`CardType` enum `.name` values rendered as raw
 * debug-style readouts (e.g. the turn-phase indicator's argument, the reward-card type indicator)
 * are deliberately left untranslated — they are internal identifiers, not authored player-facing
 * copy, and localizing them would need a separate enum-to-bundle-key mapping outside this slice's
 * scope.
 */
class CombatRenderer(private val bundle: I18NBundle) {
    private val shapeRenderer = ShapeRenderer()
    private val font = BitmapFont() // Default 15pt Arial
    private val smallFont = BitmapFont()

    private fun loadTexture(path: String): Texture =
        Texture(Gdx.files.internal(path)).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }

    // Keyed by EnemyDefinition.id (see app/src/main/assets/enemies/all.json)
    private val enemyTextures: Map<String, Texture> = mapOf(
        "thug" to loadTexture("art/enemy_thug.png"),
        "loan_shark" to loadTexture("art/enemy_loan_shark.png"),
        "collector" to loadTexture("art/enemy_collector.png")
    )

    private val cardFrameTextures: Map<CardType, Texture> = mapOf(
        CardType.ATTACK to loadTexture("art/card_frame_attack.png"),
        CardType.SKILL to loadTexture("art/card_frame_skill.png"),
        CardType.POWER to loadTexture("art/card_frame_power.png")
    )

    // Keyed by EnemyInstance.Intent.iconName
    private val intentTextures: Map<String, Texture> = mapOf(
        "intent_attack" to loadTexture("art/intent_attack.png"),
        "intent_buff" to loadTexture("art/intent_buff.png"),
        "intent_debuff" to loadTexture("art/intent_debuff.png"),
        "intent_multi" to loadTexture("art/intent_multi.png")
    )
    
    // Per-card art, keyed by CardDefinition.id. Missing assets are skipped at construction
    // so a missing art/cards/<id>.webp falls back to the frame-only card (no hard crash).
    private val cardTextures: Map<String, Texture> = run {
        val ids = listOf(
            "compound_interest", "subprime_loan", "debt_forgiveness", "partial_forgiveness",
            "tactical_bankruptcy", "reverse_mortgage", "foreclosure_express", "ghost_collector",
            "golden_credit", "mortgage_collateral", "asset_auction", "risky_investment",
            "bounced_check", "zombie_debt", "eternal_debt"
        )
        val m = mutableMapOf<String, Texture>()
        for (id in ids) {
            try {
                m[id] = loadTexture("art/cards/$id.webp")
            } catch (_: Throwable) {
                // missing art -> frame-only fallback for this card
            }
        }
        m
    }

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
    private val debtGoldY = 590f
    private val endTurnBtnX = 1100f
    private val endTurnBtnY = 50f
    private val endTurnBtnW = 150f
    private val endTurnBtnH = 60f

    // R6: dedicated REPAY controls, stacked in the same right-hand column as END TURN (above it,
    // not beside it) so their x-range never collides with a centered hand of cards, and their
    // y-range (120-230) stays clear of the combat log panel below (y >= 260, see drawLog).
    private val repayGoldBtnX = 1100f
    private val repayGoldBtnY = 120f
    private val repayDiscardBtnX = 1100f
    private val repayDiscardBtnY = 180f
    private val repayBtnW = 150f
    private val repayBtnH = 50f

    // Warning-orange, distinct from selection (YELLOW), unplayable (GRAY) and the at-risk-Debt
    // RED used on the HUD line below: signals "this card is playable but will add to Debt if
    // played" per R8/R9. Design doc doesn't pin an exact color, so this is this apply run's
    // documented choice — see apply-progress-phase3.
    private val borrowTintColor = Color(1f, 0.55f, 0.15f, 1f)

    private var repayDiscardModeActive: Boolean = false
    fun setRepayDiscardMode(active: Boolean) { repayDiscardModeActive = active }

    fun render(state: CombatState, batch: SpriteBatch) {
        // ShapeRenderer defaults to a raw screen-pixel projection; without this it draws
        // out of sync with the batch text, which uses the viewport's world-space camera.
        shapeRenderer.projectionMatrix = batch.projectionMatrix

        // Background
        drawBackground()

        // Enemy area
        drawEnemies(state.enemies, batch)

        // Player area (HP, Block, Energy)
        drawPlayer(state, batch)

        // Hand
        drawHand(state.hand, state.energy, batch, state.currentTurn)

        // UI
        drawUI(state, batch)

        // Log
        drawLog(state.log, batch)
    }

    private fun darken(color: Color, factor: Float): Color =
        Color(color.r * factor, color.g * factor, color.b * factor, color.a)

    private fun gradientRect(x: Float, y: Float, w: Float, h: Float, bottom: Color, top: Color) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.rect(x, y, w, h, bottom, bottom, top, top)
        shapeRenderer.end()
    }

    private fun shadowRect(x: Float, y: Float, w: Float, h: Float) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.35f)
        shapeRenderer.rect(x + 6f, y - 6f, w, h)
        shapeRenderer.end()
    }

    private fun intentColor(intentType: String): Color = when (intentType) {
        "ATTACK", "MULTI_ATTACK" -> Color(0.85f, 0.25f, 0.2f, 1f)
        "BUFF" -> Color(0.3f, 0.75f, 0.35f, 1f)
        "DEBUFF" -> Color(0.6f, 0.3f, 0.85f, 1f)
        else -> Color(1f, 0.8f, 0.2f, 1f)
    }

    private fun cardTypeColor(type: CardType): Color = when (type) {
        CardType.ATTACK -> Color(0.85f, 0.25f, 0.2f, 1f)
        CardType.SKILL -> Color(0.25f, 0.55f, 0.85f, 1f)
        CardType.POWER -> Color(0.8f, 0.6f, 0.15f, 1f)
    }

    private fun drawBackground() {
        gradientRect(0f, 0f, screenWidth, screenHeight, Color(0.07f, 0.07f, 0.11f, 1f), Color(0.16f, 0.14f, 0.22f, 1f))
    }

    private fun drawEnemies(enemies: List<EnemyState>, batch: SpriteBatch) {
        val startX = (screenWidth - (enemies.size * 200f + (enemies.size - 1) * 20f)) / 2

        enemies.forEachIndexed { index, enemy ->
            val x = startX + index * 220f
            val y = enemyAreaY
            val w = 180f
            val h = 220f

            // Enemy body — sprite fit to the box width, anchored at the bottom so it stays
            // grounded within the taller box (art is square, box is 180x220)
            shadowRect(x, y, w, h)
            val texture = enemyTextures[enemy.defId]
            if (texture != null) {
                val spriteH = w * (texture.height.toFloat() / texture.width)
                batch.begin()
                batch.draw(texture, x, y, w, spriteH)
                batch.end()
            } else {
                val enemyColor = Color(0.8f, 0.3f, 0.3f, 1f)
                gradientRect(x, y, w, h, darken(enemyColor, 0.55f), enemyColor)
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                shapeRenderer.setColor(darken(enemyColor, 1.3f))
                shapeRenderer.rect(x, y, w, h)
                shapeRenderer.end()
            }

            // Enemy name
            batch.begin()
            font.draw(batch, bundle.get(enemy.name), x + 10f, y + h - 10f)
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
                font.draw(batch, bundle.format("hud.status.block", enemy.block), x + 10f, y - 40f)
                batch.end()
            }

            // Intent
            drawIntent(x, y + h + 6f, w, enemy, batch)

            // Strength
            var statusY = y - 80f
            if (enemy.strength > 0) {
                batch.begin()
                font.draw(batch, bundle.format("hud.status.strength", enemy.strength), x + 10f, statusY)
                batch.end()
                statusY -= 20f
            }
            if (enemy.weak > 0) {
                batch.begin()
                smallFont.draw(batch, bundle.format("hud.status.weak", enemy.weak), x + 10f, statusY)
                batch.end()
                statusY -= 20f
            }
            if (enemy.vulnerable > 0) {
                batch.begin()
                smallFont.draw(batch, bundle.format("hud.status.vulnerable", enemy.vulnerable), x + 10f, statusY)
                batch.end()
                statusY -= 20f
            }
            if (enemy.poison > 0) {
                batch.begin()
                smallFont.draw(batch, bundle.format("hud.status.poison", enemy.poison), x + 10f, statusY)
                batch.end()
            }
        }
    }

    private fun drawIntent(x: Float, y: Float, w: Float, enemy: EnemyState, batch: SpriteBatch) {
        val barH = 30f
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(intentColor(enemy.intentType))
        shapeRenderer.rect(x, y, w, barH)
        shapeRenderer.end()

        val icon = intentTextures[enemy.intentIconName]
        val textX = if (icon != null) x + 36f else x + 10f

        batch.begin()
        if (icon != null) {
            val iconSize = 26f
            batch.draw(icon, x + 4f, y + (barH - iconSize) / 2f, iconSize, iconSize)
        }
        font.draw(batch, enemy.intentDisplayName, textX, y + 20f)
        batch.end()
    }

    private fun drawPlayer(state: CombatState, batch: SpriteBatch) {
        val x = 50f
        val y = 280f
        val w = 200f
        val h = 140f

        // Player panel
        shadowRect(x, y, w, h)
        gradientRect(x, y, w, h, Color(0.16f, 0.16f, 0.24f, 1f), Color(0.28f, 0.28f, 0.4f, 1f))
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0.5f, 0.5f, 0.65f, 1f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()

        // HP
        batch.begin()
        font.draw(batch, bundle.get("hud.player.label"), x + 10f, y + h - 10f)
        font.draw(batch, bundle.format("hud.player.hp", state.player.hp, state.player.maxHp), x + 10f, y + h - 40f)
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
            font.draw(batch, bundle.format("hud.status.block", state.player.block), x + 10f, y + h - 65f)
            batch.end()
        }

        // Strength/Weak/Vulnerable
        var statusY = y + 10f
        if (state.player.strength != 0) {
            batch.begin()
            font.draw(batch, bundle.format("hud.status.strength", state.player.strength), x + 10f, statusY)
            batch.end()
            statusY += 25f
        }
        if (state.player.weak > 0) {
            batch.begin()
            smallFont.draw(batch, bundle.format("hud.status.weak", state.player.weak), x + 10f, statusY)
            batch.end()
            statusY += 20f
        }
        if (state.player.vulnerable > 0) {
            batch.begin()
            smallFont.draw(batch, bundle.format("hud.status.vulnerable", state.player.vulnerable), x + 10f, statusY)
            batch.end()
            statusY += 20f
        }
        if (state.player.poison > 0) {
            batch.begin()
            smallFont.draw(batch, bundle.format("hud.status.poison", state.player.poison), x + 10f, statusY)
            batch.end()
            statusY += 20f
        }
        if (state.player.thorns > 0) {
            batch.begin()
            smallFont.draw(batch, bundle.format("hud.status.thorns", state.player.thorns), x + 10f, statusY)
            batch.end()
            statusY += 20f
        }
        if (state.player.regen > 0) {
            batch.begin()
            smallFont.draw(batch, bundle.format("hud.status.regen", state.player.regen), x + 10f, statusY)
            batch.end()
        }
    }

    private fun drawHand(hand: List<CardInstance>, energy: Int, batch: SpriteBatch, phase: TurnPhase) {
        val canAct = phase == TurnPhase.PLAYER_ACTION
        val totalWidth = hand.size * cardWidth + (hand.size - 1) * cardSpacing
        val startX = (screenWidth - totalWidth) / 2

        hand.forEachIndexed { index, card ->
            val x = startX + index * (cardWidth + cardSpacing)
            val y = handY
            val selected = inputHandlerSelectedCard?.id == card.id
            // R8: identical isPlayable()/shortfall() pair CombatEngine.playCard and
            // CombatInputHandler use — no card is ever cost-blocked (GRAY only means "not your
            // turn" or exhausted), but one that would borrow gets the warning tint instead of
            // plain WHITE so the player can see a play will add to Debt before committing to it.
            val tint = when {
                selected -> Color.YELLOW
                !canAct || !card.isPlayable() -> Color.GRAY
                card.shortfall(energy) > 0 -> borrowTintColor
                else -> Color.WHITE
            }

            // Card background — real frame art per type, tinted for selection/affordability
            shadowRect(x, y, cardWidth, cardHeight)
            val frame = cardFrameTextures[card.type]
            if (frame != null) {
                batch.begin()
                batch.color = tint
                batch.draw(frame, x, y, cardWidth, cardHeight)
                batch.color = Color.WHITE
                batch.end()
            } else {
                gradientRect(x, y, cardWidth, cardHeight, darken(tint, 0.75f), tint)
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                shapeRenderer.setColor(0f, 0f, 0f, 1f)
                shapeRenderer.rect(x, y, cardWidth, cardHeight)
                shapeRenderer.end()
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                shapeRenderer.setColor(cardTypeColor(card.type))
                shapeRenderer.rect(x, y, 6f, cardHeight)
                shapeRenderer.end()
            }

            // Per-card art over the frame (falls back to frame-only when absent)
            val cardArt = cardTextures[card.cardId]
            if (cardArt != null) {
                batch.begin()
                batch.draw(cardArt, x + 6f, y + 6f, cardWidth - 12f, cardHeight - 12f)
                batch.end()
            }

            // Card content
            batch.begin()
            // Cost
            font.draw(batch, card.cost.toString(), x + 10f, y + cardHeight - 10f)
            // Name
            font.draw(batch, bundle.get(card.name), x + 10f, y + cardHeight - 40f)
            // Description (wrapped)
            val descY = y + 80f
            val descWidth = cardWidth - 20f
            val cardDescription = bundle.get(card.description)
            smallFont.draw(batch, cardDescription, x + 10f, descY, descWidth, Align.left, true)
            // Type indicator — positioned below the wrapped description so multi-line
            // descriptions (longer new cards) don't collide with a fixed offset
            val descLayout = GlyphLayout(smallFont, cardDescription, Color.WHITE, descWidth, Align.left, true)
            val typeY = (descY - descLayout.height - 10f).coerceAtLeast(y + 10f)
            smallFont.draw(batch, card.type.name, x + 10f, typeY)
            batch.end()
        }
    }

    private var inputHandlerSelectedCard: CardInstance? = null
    fun setSelectedCard(card: CardInstance?) { inputHandlerSelectedCard = card }

    private fun drawUI(state: CombatState, batch: SpriteBatch) {
        // Credit — HUD label only; the backing field stays `energy`/`maxEnergy` (Money→Credit
        // rename decision, design.md), refilled each turn, gates card play.
        batch.begin()
        font.draw(batch, bundle.format("hud.credit", state.energy, state.maxEnergy), energyX, energyY)
        batch.end()

        // R9: Debt/Gold HUD readout, flagged in a distinct at-risk color once Debt reaches the
        // shared break-threshold constant (same one that schedules the collector encounter).
        val debtAtRisk = state.debt >= DebtConfig.BREAK_THRESHOLD
        batch.begin()
        font.setColor(if (debtAtRisk) Color.RED else Color.WHITE)
        font.draw(batch, bundle.format("hud.debt_gold", state.debt, state.gold), energyX, debtGoldY)
        font.setColor(Color.WHITE)
        batch.end()

        // End Turn Button
        val canAct = state.currentTurn == TurnPhase.PLAYER_ACTION
        val btnColor = if (canAct) Color.GREEN else Color.GRAY
        shadowRect(endTurnBtnX, endTurnBtnY, endTurnBtnW, endTurnBtnH)
        gradientRect(endTurnBtnX, endTurnBtnY, endTurnBtnW, endTurnBtnH, darken(btnColor, 0.65f), btnColor)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0f, 0f, 0f, 1f)
        shapeRenderer.rect(endTurnBtnX, endTurnBtnY, endTurnBtnW, endTurnBtnH)
        shapeRenderer.end()

        batch.begin()
        font.draw(batch, bundle.get("hud.button.end_turn"), endTurnBtnX + 20f, endTurnBtnY + 40f)
        batch.end()

        // R6: REPAY controls — only drawn "live" (colored, not GRAY) during PLAYER_ACTION, the
        // same phase gate CombatInputHandler enforces before it will ever route a tap to either
        // button; see that file's handlePlayerAction for the enforcement side of this decision.
        val canRepayGold = canAct && state.debt > 0 && state.gold > 0
        drawRepayButton(repayGoldBtnX, repayGoldBtnY, repayBtnW, repayBtnH, bundle.get("hud.button.repay_gold"), canRepayGold, false, batch)

        val canRepayDiscard = canAct && state.debt > 0 && state.hand.isNotEmpty()
        val discardLabel = if (repayDiscardModeActive) bundle.get("hud.button.cancel") else bundle.get("hud.button.repay_card")
        drawRepayButton(
            repayDiscardBtnX, repayDiscardBtnY, repayBtnW, repayBtnH,
            discardLabel, canRepayDiscard || repayDiscardModeActive, repayDiscardModeActive, batch
        )

        if (repayDiscardModeActive) {
            batch.begin()
            smallFont.setColor(borrowTintColor)
            smallFont.draw(batch, bundle.get("hud.repay_discard_hint"), 300f, 690f)
            smallFont.setColor(Color.WHITE)
            batch.end()
        }

        // Turn phase indicator. state.currentTurn.name/pile counts are numeric/enum data, not
        // authored copy — see the class KDoc for why the enum identifier itself stays untranslated.
        batch.begin()
        smallFont.draw(batch, bundle.format("hud.turn_phase", state.currentTurn.name), 50f, 680f)
        smallFont.draw(batch, bundle.format("hud.turn_number", state.turnNumber), 50f, 660f)
        smallFont.draw(
            batch,
            bundle.format("hud.pile_counts", state.drawPileCount, state.discardPileCount, state.exhaustPileCount),
            50f, 640f
        )
        batch.end()
    }

    private fun drawRepayButton(
        x: Float, y: Float, w: Float, h: Float,
        label: String, enabled: Boolean, highlighted: Boolean, batch: SpriteBatch
    ) {
        val base = when {
            highlighted -> borrowTintColor
            enabled -> Color(0.3f, 0.6f, 1f, 1f)
            else -> Color.GRAY
        }
        shadowRect(x, y, w, h)
        gradientRect(x, y, w, h, darken(base, 0.65f), base)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0f, 0f, 0f, 1f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()

        batch.begin()
        smallFont.draw(batch, label, x + 10f, y + h / 2f + 6f)
        batch.end()
    }

    fun renderReward(choices: List<CardDefinition>, batch: SpriteBatch) {
        shapeRenderer.projectionMatrix = batch.projectionMatrix
        drawBackground()

        batch.begin()
        font.draw(batch, bundle.get("reward.header"), 50f, 680f)
        batch.end()

        choices.forEachIndexed { index, card ->
            val bounds = rewardCardBounds(index, choices.size)

            shadowRect(bounds.x, bounds.y, bounds.width, bounds.height)
            gradientRect(bounds.x, bounds.y, bounds.width, bounds.height, Color(0.85f, 0.85f, 0.85f, 1f), Color.WHITE)

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.setColor(0f, 0f, 0f, 1f)
            shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height)
            shapeRenderer.end()

            val cardArt = cardTextures[card.id]
            if (cardArt != null) {
                batch.begin()
                batch.draw(cardArt, bounds.x + 6f, bounds.y + 6f, bounds.width - 12f, bounds.height - 12f)
                batch.end()
            }
            batch.begin()
            font.setColor(Color.BLACK)
            font.draw(batch, bundle.get(card.name), bounds.x + 15f, bounds.y + bounds.height - 20f)
            smallFont.setColor(Color.BLACK)
            smallFont.draw(batch, bundle.get(card.description), bounds.x + 15f, bounds.y + bounds.height - 60f, bounds.width - 30f, Align.left, true)
            smallFont.draw(batch, bundle.format("reward.cost", card.cost), bounds.x + 15f, bounds.y + 30f)
            font.setColor(Color.WHITE)
            smallFont.setColor(Color.WHITE)
            batch.end()
        }
    }

    fun renderRunEnd(batch: SpriteBatch, won: Boolean) {
        shapeRenderer.projectionMatrix = batch.projectionMatrix
        drawBackground()

        val message = bundle.get(if (won) "run_end.victory" else "run_end.defeat")
        val restartHint = bundle.get("run_end.restart_hint")

        batch.begin()
        font.getData().setScale(3f)
        font.setColor(if (won) Color.GREEN else Color.RED)
        val bounds = GlyphLayout(font, message)
        font.draw(batch, message, (screenWidth - bounds.width) / 2, (screenHeight + bounds.height) / 2)
        font.getData().setScale(1f)
        font.setColor(Color.WHITE)
        smallFont.draw(batch, restartHint, (screenWidth - 120f) / 2, (screenHeight - bounds.height) / 2 - 50f)
        batch.end()
    }

    private fun drawLog(log: List<com.debtsdecks.core.model.CombatLogEntry>, batch: SpriteBatch) {
        val x = 900f
        val y = 260f
        val w = 350f
        val h = 340f

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0.5f, 0.5f, 0.6f, 0.8f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()

        batch.begin()
        smallFont.draw(batch, bundle.get("hud.combat_log_header"), x + 10f, y + h - 10f)

        var lineY = y + h - 35f
        log.reversed().take(20).forEach { entry ->
            if (lineY < y + 20f) return@forEach
            smallFont.draw(batch, entry.message, x + 10f, lineY, w - 20f, Align.left, true)
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
        enemyTextures.values.forEach { it.dispose() }
        cardFrameTextures.values.forEach { it.dispose() }
        cardTextures.values.forEach { it.dispose() }
        intentTextures.values.forEach { it.dispose() }
    }

    companion object {
        val endTurnButtonBounds = com.badlogic.gdx.math.Rectangle(1100f, 50f, 150f, 60f)
        val repayGoldButtonBounds = com.badlogic.gdx.math.Rectangle(1100f, 120f, 150f, 50f)
        val repayDiscardButtonBounds = com.badlogic.gdx.math.Rectangle(1100f, 180f, 150f, 50f)

        private const val REWARD_CARD_WIDTH = 280f
        private const val REWARD_CARD_HEIGHT = 380f
        private const val REWARD_CARD_SPACING = 40f
        private const val SCREEN_WIDTH = 1280f
        private const val SCREEN_HEIGHT = 720f

        fun rewardCardBounds(index: Int, count: Int): Rectangle {
            val totalWidth = count * REWARD_CARD_WIDTH + (count - 1) * REWARD_CARD_SPACING
            val startX = (SCREEN_WIDTH - totalWidth) / 2
            val y = (SCREEN_HEIGHT - REWARD_CARD_HEIGHT) / 2
            val x = startX + index * (REWARD_CARD_WIDTH + REWARD_CARD_SPACING)
            return Rectangle(x, y, REWARD_CARD_WIDTH, REWARD_CARD_HEIGHT)
        }
    }
}