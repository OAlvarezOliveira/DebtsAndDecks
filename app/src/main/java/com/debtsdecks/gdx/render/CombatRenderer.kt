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
import com.debtsdecks.core.combat.NodeConfig
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.District
import com.debtsdecks.core.enemies.IntentType
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

    /**
     * Derived from [INTENT_ICON_PATHS], so it covers every [IntentType] by construction. It used to
     * be five hand-typed string literals, which a sixth intent would not have extended: the lookup
     * below treats a missing key as "draw nothing", so the bar rendered blank and no build noticed.
     */
    private val intentTextures: Map<IntentType, Texture> =
        INTENT_ICON_PATHS.mapValues { (_, path) -> loadTexture(path) }

    // Screen backdrops, keyed by screen. A missing file falls back to the original
    // gradient instead of crashing, same contract as the card art below.
    private val backgroundTextures: Map<String, Texture> = run {
        val m = mutableMapOf<String, Texture>()
        for (id in listOf("bg_combat", "bg_reststop")) {
            try {
                m[id] = loadTexture("art/backgrounds/$id.png")
            } catch (t: Throwable) {
                Gdx.app.error("DebtsDecks", "background load failed: art/backgrounds/" + id)
            }
        }
        m
    }
    
    // Per-card art, keyed by CardDefinition.id. Missing assets are skipped at construction
    // so a missing art/cards/<id>.png falls back to the frame-only card (no hard crash).
    private val cardTextures: Map<String, Texture> = run {
        val ids = listOf(
            "strike", "defend", "bash", "survive",
                "compound_interest", "subprime_loan", "debt_forgiveness", "partial_forgiveness",
            "tactical_bankruptcy", "reverse_mortgage", "foreclosure_express", "ghost_collector",
            "golden_credit", "mortgage_collateral", "asset_auction", "risky_investment",
            "bounced_check", "zombie_debt", "eternal_debt",
            "leverage_strike", "asset_bubble", "repo_expert", "refinanciar",
            "overdraft", "collateral_hold", "emergency_fund", "ejecucion"
        )
        val m = mutableMapOf<String, Texture>()
        for (id in ids) {
            try {
                m[id] = loadTexture("art/cards/$id.png")
            } catch (t: Throwable) {
                // missing art -> frame-only fallback; log device-side load failures
                    Gdx.app.error("DebtsDecks", "card art load failed: art/cards/" + id)
                    for (el in t.stackTrace.take(6)) Gdx.app.error("DebtsDecks", el.toString())
            }
        }
        m
    }

    // Layout constants
    /**
     * Live world width, pushed in by GameScreen every frame from the ExtendViewport. The viewport
     * stops pillarboxing to 1280 and hands us the device's real aspect instead, so every centring
     * and right-edge anchor reads this rather than a constant.
     */
    var worldWidth: Float = 1280f
    private val screenHeight = 720f
    private val enemyAreaY = 450f
    private val playerAreaY = 50f
    private val energyX = 50f
    private val energyY = 620f
    private val debtGoldY = 590f
    private val endTurnBtnX = 1100f
    private val endTurnBtnY = 50f
    private val endTurnBtnW = 150f
    private val endTurnBtnH = 60f

    // Warning-orange, distinct from selection (YELLOW), unplayable (GRAY) and the at-risk-Debt
    // RED used on the HUD line below: signals "this card is playable but will add to Debt if
    // played" per R8/R9. Design doc doesn't pin an exact color, so this is this apply run's
    // documented choice — see apply-progress-phase3.
    private val borrowTintColor = Color(1f, 0.55f, 0.15f, 1f)

    // Design-system tokens (tokens/colors.css) used by the card face.
    private val navy950 = Color.valueOf("0c0c18")
    private val navy900 = Color.valueOf("14142a")
    private val ink100 = Color.valueOf("eef0fb")
    private val ink300 = Color.valueOf("c7c9e0")
    private val brass500 = Color.valueOf("c9a227")

    // C7 node screen: which sub-view is active (main choices vs shop/remove/loan sub-offers).
    // Input and render share this via [setNodeMode]/[nodeMode].
    enum class NodeMode { CHOICES, SHOP, REMOVE, LOAN, UPGRADE }
    private var nodeMode: NodeMode = NodeMode.CHOICES
    private var lastFrameWasNode = false
    fun setNodeMode(mode: NodeMode) { nodeMode = mode }
    fun getNodeMode(): NodeMode = nodeMode

    fun render(state: CombatState, run: RunManager, batch: SpriteBatch) {
        lastFrameWasNode = false
        // ShapeRenderer defaults to a raw screen-pixel projection; without this it draws
        // out of sync with the batch text, which uses the viewport's world-space camera.
        shapeRenderer.projectionMatrix = batch.projectionMatrix

        // Background
        drawBackground(batch, run.currentDistrict.backgroundKey())

        // Enemy area
        drawEnemies(state.enemies, batch)

        // Player area (HP, Block, Energy)
        drawPlayer(state, batch)

        // Hand
        drawHand(state.hand, state.energy, state.debt, batch, state.currentTurn)

        // UI
        drawUI(state, batch)

        // Log
        drawLog(state.log, batch)
        if (run.isDistrictEntrance) drawDistrictTitle(run.currentDistrict, batch)
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

    // Takes the enum, not its name, and has no `else`. As a `when` over String with a fallback
    // this compiled happily for any intent it had never heard of and painted the bar the LEVY
    // yellow -- which is how LEVY itself got its colour, by accident rather than by decision.
    private fun intentColor(intentType: IntentType): Color = when (intentType) {
        IntentType.ATTACK, IntentType.MULTI_ATTACK -> Color(0.85f, 0.25f, 0.2f, 1f)
        IntentType.BUFF -> Color(0.3f, 0.75f, 0.35f, 1f)
        IntentType.DEBUFF -> Color(0.6f, 0.3f, 0.85f, 1f)
        IntentType.LEVY -> Color(1f, 0.8f, 0.2f, 1f)
        IntentType.FORECLOSE -> Color(1f, 0.45f, 0.1f, 1f)
        IntentType.HEDGE -> Color(0.3f, 0.7f, 0.95f, 1f)
    }

    private fun cardTypeColor(type: CardType): Color = when (type) {
        CardType.ATTACK -> Color(0.85f, 0.25f, 0.2f, 1f)
        CardType.SKILL -> Color(0.25f, 0.55f, 0.85f, 1f)
        CardType.POWER -> Color(0.8f, 0.6f, 0.15f, 1f)
    }

    private fun drawBackground(batch: SpriteBatch, id: String) {
        val backdrop = backgroundTextures[id]
        if (backdrop == null) {
            gradientRect(0f, 0f, worldWidth, screenHeight, Color(0.07f, 0.07f, 0.11f, 1f), Color(0.16f, 0.14f, 0.22f, 1f))
            return
        }
        // Cover, not stretch: the backdrops are authored at 1280x720, so on a wider world we scale
        // both axes by the same factor and let the extra height fall off the top and bottom.
        val coverScale = maxOf(worldWidth / 1280f, 1f)
        val drawH = 720f * coverScale
        batch.begin()
        batch.draw(backdrop, 0f, (screenHeight - drawH) / 2f, worldWidth, drawH)
        batch.end()
    }

    private fun drawEnemies(enemies: List<EnemyState>, batch: SpriteBatch) {
        val startX = (worldWidth - (enemies.size * 200f + (enemies.size - 1) * 20f)) / 2

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

        // Total by construction: intentTextures is keyed by the enum and built from its entries,
        // so there is no missing-icon branch left to take.
        val icon = intentTextures.getValue(enemy.intentType)

        batch.begin()
        val iconSize = 26f
        batch.draw(icon, x + 4f, y + (barH - iconSize) / 2f, iconSize, iconSize)
        font.draw(batch, enemy.intentDisplayName, x + 36f, y + 20f)
        batch.end()
    }

    /**
     * Every player-facing number in one panel anchored to the bottom-left corner.
     *
     * Credit, Debt and Gold used to float loose over the artwork near the top-left while the panel
     * itself sat mid-screen holding only an HP bar. Collecting them here frees the whole upper area
     * for the fight and keeps the readouts out of the hand's way.
     */
    private fun drawPlayer(state: CombatState, batch: SpriteBatch) {
        val panel = CombatLayout.playerPanel(worldWidth)
        val x = panel.x
        val y = panel.y
        val w = panel.width
        val h = panel.height
        val pad = 12f
        val barW = w - 2f * pad

        // Row positions are laid out top-down in one pass first, because ShapeRenderer and
        // SpriteBatch cannot interleave: the bars have to be drawn before any text begins.
        var cursor = y + h - pad
        val labelY = cursor
        cursor -= 22f
        val hpTextY = cursor
        // BitmapFont.draw() takes y as the TOP of the line, not the baseline, so the gap has to
        // clear the glyph descent or the HP bar rides up into the text.
        cursor -= 22f
        val hpBarY = cursor - 14f
        cursor = hpBarY - 24f
        val creditY = cursor
        cursor -= 24f
        val blockBarY = if (state.player.block > 0) (cursor - 18f).also { cursor = it - 24f } else null
        val debtY = cursor
        cursor -= 22f
        val warningY = cursor

        panelBackdrop(x, y, w, h)
        drawHPBar(x + pad, hpBarY, barW, 14f, state.player.hpPercent, Color.RED, Color.GREEN)
        if (blockBarY != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(0.3f, 0.6f, 1f, 0.85f)
            shapeRenderer.rect(x + pad, blockBarY, barW, 18f)
            shapeRenderer.end()
        }

        val debtColor = when {
            state.debt >= DebtConfig.EXECUTION_THRESHOLD -> Color.RED
            state.debt >= DebtConfig.BREAK_THRESHOLD -> Color(1f, 0.6f, 0.1f, 1f) // amber
            else -> ink100
        }

        batch.begin()
        smallFont.data.setScale(0.78f)
        smallFont.color = ink300
        smallFont.draw(batch, bundle.get("hud.player.label"), x + pad, labelY)
        smallFont.color = ink100
        smallFont.draw(batch, bundle.format("hud.player.hp", state.player.hp, state.player.maxHp), x + pad, hpTextY)
        smallFont.draw(batch, bundle.format("hud.credit", state.energy, state.maxEnergy), x + pad, creditY)
        if (blockBarY != null) {
            smallFont.color = navy950
            smallFont.draw(batch, bundle.format("hud.status.block", state.player.block), x + pad + 4f, blockBarY + 14f)
        }

        // R9: Debt/Gold flagged at both thresholds — amber at BREAK (the collector is coming),
        // hard red plus an explicit warning past EXECUTION, where any debt-raising action is
        // instant death (the interest tick is exempt). NEW-5 playtest: the zone was invisible.
        smallFont.color = debtColor
        smallFont.draw(batch, bundle.format("hud.debt_gold", state.debt, state.gold), x + pad, debtY)
        if (state.debt >= DebtConfig.EXECUTION_THRESHOLD) {
            smallFont.color = Color.RED
            smallFont.data.setScale(0.66f)
            smallFont.draw(batch, bundle.get("hud.execution_warning"), x + pad, warningY, barW, Align.left, true)
            smallFont.data.setScale(0.78f)
        }

        // Status effects stack up from the panel floor so a long list grows into the empty middle
        // instead of running off the bottom edge.
        smallFont.data.setScale(0.7f)
        var statusY = y + pad + 14f
        val statuses = buildList {
            if (state.player.strength != 0) add(bundle.format("hud.status.strength", state.player.strength) to brass500)
            if (state.player.weak > 0) add(bundle.format("hud.status.weak", state.player.weak) to Color(0.7f, 0.5f, 0.9f, 1f))
            if (state.player.vulnerable > 0) add(bundle.format("hud.status.vulnerable", state.player.vulnerable) to Color(1f, 0.45f, 0.35f, 1f))
            if (state.player.poison > 0) add(bundle.format("hud.status.poison", state.player.poison) to Color(0.4f, 0.85f, 0.4f, 1f))
            if (state.player.thorns > 0) add(bundle.format("hud.status.thorns", state.player.thorns) to Color(0.75f, 0.75f, 0.85f, 1f))
            if (state.player.regen > 0) add(bundle.format("hud.status.regen", state.player.regen) to Color(0.45f, 0.9f, 0.7f, 1f))
        }
        statuses.reversed().forEach { (text, color) ->
            smallFont.color = color
            smallFont.draw(batch, text, x + pad, statusY)
            statusY += 18f
        }
        smallFont.color = Color.WHITE
        smallFont.data.setScale(1f)
        batch.end()
    }

    /** Shared chrome for the two side panels: translucent fill plus a thin cool border. */
    private fun panelBackdrop(x: Float, y: Float, w: Float, h: Float) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(navy950.r, navy950.g, navy950.b, 0.72f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0.42f, 0.44f, 0.6f, 0.85f)
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()
    }

    private var inputHandlerSelectedCard: CardInstance? = null

    fun setSelectedCard(card: CardInstance?) { inputHandlerSelectedCard = card }

    private fun drawHand(hand: List<CardInstance>, energy: Int, debt: Int, batch: SpriteBatch, phase: TurnPhase) {
        val canAct = phase == TurnPhase.PLAYER_ACTION

        hand.forEachIndexed { index, card ->
            val slot = HandLayout.cardBounds(index, hand.size, worldWidth)
            val x = slot.x
            val y = slot.y
            val w = slot.width
            val h = slot.height
            val selected = inputHandlerSelectedCard?.id == card.id
            // R8: identical isPlayable()/shortfall() pair CombatEngine.playCard and
            // CombatInputHandler use — no card is ever cost-blocked (GRAY only means "not your
            // turn" or exhausted), but one that would borrow gets the warning tint instead of
            // plain WHITE so the player can see a play will add to Debt before committing to it.
            val tint = when {
                selected -> Color.YELLOW
                !canAct || !card.isPlayable(debt) -> Color.GRAY
                card.shortfall(energy) > 0 -> borrowTintColor
                else -> Color.WHITE
            }

            drawCardFace(
                batch, x, y, w, h,
                art = cardTextures[card.cardId],
                frame = cardFrameTextures[card.type],
                tint = tint,
                cost = card.cost,
                name = bundle.get(card.name),
                description = bundle.get(card.description),
                type = card.type,
                upgraded = card.upgraded
            )
        }
    }

    /**
     * Draws one card as three separate zones — cost roundel, art window, text panel — instead of
     * stacking every glyph straight on top of a full-bleed illustration.
     *
     * The old layout drew the art across the whole card and then the cost, name, description and
     * type tag over it, which is what made the copy unreadable and let a long description run into
     * the type tag (playtest bugs #4/#5). Here the art is clipped to the upper slice, the lower
     * slice is an opaque panel, and the description is hard-clamped to three lines so it can never
     * push the tag out of the card.
     *
     * Draw order matters: backing, art, panel, then the frame PNG last over the full bounds. The
     * frames are ornate borders with a fully transparent middle (measured: the border eats 13.7% of
     * the width and 12.9% of the height per side), so content lives inside that hole and the frame
     * paints on top of everything without hiding it. The cost roundel goes after the frame on
     * purpose, breaking the top-left corner.
     */
    private fun drawCardFace(
        batch: SpriteBatch,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        art: Texture?,
        frame: Texture?,
        tint: Color,
        cost: Int,
        name: String,
        description: String,
        type: CardType,
        upgraded: Boolean
    ) {
        val s = w / HandLayout.BASE_WIDTH
        val innerX = x + w * FRAME_INSET_X
        val innerW = w * (1f - 2f * FRAME_INSET_X)
        val innerY = y + h * FRAME_INSET_Y
        val innerH = h * (1f - 2f * FRAME_INSET_Y)
        val panelH = innerH * TEXT_PANEL_FRACTION
        val artH = innerH - panelH
        val pad = innerW * 0.06f
        val textW = innerW - 2f * pad
        val typeColor = cardTypeColor(type)

        shadowRect(x, y, w, h)

        // Opaque backing: the card art carries real alpha since the checkerboard strip, so without
        // this the battlefield would show through the illustration.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(navy950)
        shapeRenderer.rect(innerX, innerY, innerW, innerH)
        shapeRenderer.end()

        if (art != null) {
            // objectFit: cover. The illustrations are square (512x512) and the art window is
            // landscape, so a plain draw() stretches them horizontally. Crop a centred slice of
            // the source at the window's aspect instead and scale that.
            val windowAspect = innerW / artH
            val srcAspect = art.width.toFloat() / art.height.toFloat()
            val srcW: Int
            val srcH: Int
            if (srcAspect > windowAspect) {
                srcH = art.height
                srcW = (art.height * windowAspect).toInt()
            } else {
                srcW = art.width
                srcH = (art.width / windowAspect).toInt()
            }
            batch.begin()
            batch.draw(
                art, innerX, innerY + panelH, innerW, artH,
                (art.width - srcW) / 2, (art.height - srcH) / 2, srcW, srcH,
                false, false
            )
            batch.end()
        } else {
            gradientRect(innerX, innerY + panelH, innerW, artH, darken(typeColor, 0.35f), darken(typeColor, 0.7f))
        }

        // Text panel — solid, never translucent, so nothing bleeds through the copy.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(navy900)
        shapeRenderer.rect(innerX, innerY, innerW, panelH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(typeColor)
        shapeRenderer.rect(innerX, innerY + panelH - 2f * s, innerW, 2f * s)
        shapeRenderer.end()

        // Frame last, over the full card bounds.
        if (frame != null) {
            batch.begin()
            batch.color = tint
            batch.draw(frame, x, y, w, h)
            batch.color = Color.WHITE
            batch.end()
        } else {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.setColor(tint)
            shapeRenderer.rect(x, y, w, h)
            shapeRenderer.end()
        }

        batch.begin()
        // Name: one line, ellipsised rather than wrapped, so the row height is fixed.
        font.data.setScale(0.85f * s)
        val nameText = ellipsize(font, name, textW)
        val nameLayout = GlyphLayout(font, nameText)
        font.color = ink100
        font.draw(batch, nameText, innerX + pad, innerY + panelH - pad)
        font.color = Color.WHITE
        font.data.setScale(1f)

        // Type tag on its own row at the panel floor — the description above can never reach it.
        smallFont.data.setScale(0.62f * s)
        val typeText = type.name
        val typeLayout = GlyphLayout(smallFont, typeText)
        smallFont.color = typeColor
        smallFont.draw(batch, typeText, innerX + pad, innerY + pad + typeLayout.height)
        smallFont.color = Color.WHITE
        smallFont.data.setScale(1f)

        // Description: hard-clamped to three lines in the gap the other two rows leave over.
        smallFont.data.setScale(0.68f * s)
        val descTop = innerY + panelH - pad - nameLayout.height - 6f * s
        val descText = clampLines(smallFont, description, textW, DESCRIPTION_MAX_LINES)
        smallFont.color = ink300
        smallFont.draw(batch, descText, innerX + pad, descTop, textW, Align.left, true)
        smallFont.color = Color.WHITE
        smallFont.data.setScale(1f)
        batch.end()

        // Cost roundel, breaking the top-left corner of the frame.
        val radius = w * 0.11f
        val cx = x + w * 0.115f
        val cy = y + h - h * 0.085f
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(navy950)
        shapeRenderer.circle(cx, cy, radius + 2.5f * s)
        shapeRenderer.setColor(typeColor)
        shapeRenderer.circle(cx, cy, radius)
        shapeRenderer.end()

        batch.begin()
        font.data.setScale(0.95f * s)
        val costText = cost.toString()
        val costLayout = GlyphLayout(font, costText)
        font.color = ink100
        font.draw(batch, costText, cx - costLayout.width / 2f, cy + costLayout.height / 2f)
        font.color = Color.WHITE
        font.data.setScale(1f)
        batch.end()

        // card-upgrades R7: brass badge, bottom-right of the text panel, opposite the type tag.
        if (upgraded) {
            smallFont.data.setScale(0.58f * s)
            val badgeText = bundle.get("node.upgraded")
            val badgeLayout = GlyphLayout(smallFont, badgeText)
            val badgeW = badgeLayout.width + 8f * s
            val badgeH = badgeLayout.height + 6f * s
            val badgeX = innerX + innerW - pad - badgeW
            val badgeY = innerY + pad - 3f * s
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(brass500)
            shapeRenderer.rect(badgeX, badgeY, badgeW, badgeH)
            shapeRenderer.end()
            batch.begin()
            smallFont.color = navy950
            smallFont.draw(batch, badgeText, badgeX + 4f * s, badgeY + badgeH - 3f * s)
            smallFont.color = Color.WHITE
            smallFont.data.setScale(1f)
            batch.end()
        }
    }

    /** Trims [text] with an ellipsis until it fits [maxWidth] on a single line at [f]'s scale. */
    private fun ellipsize(f: BitmapFont, text: String, maxWidth: Float): String {
        if (GlyphLayout(f, text).width <= maxWidth) return text
        var candidate = text
        while (candidate.length > 1) {
            candidate = candidate.substring(0, candidate.length - 1)
            if (GlyphLayout(f, candidate + "...").width <= maxWidth) return candidate + "..."
        }
        return "..."
    }

    /**
     * Wraps [text] at [width] and drops trailing words until it fits [maxLines], appending an
     * ellipsis. Never shrinks the font: the handoff sets 11px as the readability floor, so an
     * over-long description loses words rather than legibility.
     */
    private fun clampLines(f: BitmapFont, text: String, width: Float, maxLines: Int): String {
        if (GlyphLayout(f, text, Color.WHITE, width, Align.left, true).runs.size <= maxLines) return text
        var candidate = text
        while (candidate.contains(' ')) {
            candidate = candidate.substringBeforeLast(' ')
            val trimmed = candidate + "..."
            if (GlyphLayout(f, trimmed, Color.WHITE, width, Align.left, true).runs.size <= maxLines) return trimmed
        }
        return candidate
    }

    /**
     * Only the two things that are not player state: the END TURN button and a muted turn/deck
     * strip in the top-left. Credit, Debt and Gold moved into [drawPlayer]'s panel.
     */
    private fun drawUI(state: CombatState, batch: SpriteBatch) {
        val button = endTurnButtonBounds()
        val canAct = state.currentTurn == TurnPhase.PLAYER_ACTION
        val btnColor = if (canAct) Color(0.16f, 0.62f, 0.32f, 1f) else Color(0.28f, 0.28f, 0.34f, 1f)
        shadowRect(button.x, button.y, button.width, button.height)
        gradientRect(button.x, button.y, button.width, button.height, darken(btnColor, 0.6f), btnColor)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(navy950)
        shapeRenderer.rect(button.x, button.y, button.width, button.height)
        shapeRenderer.end()

        batch.begin()
        val label = bundle.get("hud.button.end_turn")
        font.data.setScale(0.85f)
        val labelLayout = GlyphLayout(font, label)
        font.color = if (canAct) ink100 else Color(0.6f, 0.6f, 0.66f, 1f)
        font.draw(
            batch, label,
            button.x + (button.width - labelLayout.width) / 2f,
            button.y + (button.height + labelLayout.height) / 2f
        )
        font.color = Color.WHITE
        font.data.setScale(1f)

        // Turn phase / pile counts. The enum identifier and the counts are internal data, not
        // authored copy — see the class KDoc for why the enum name stays untranslated. Muted and
        // pushed into the corner so it reads as a debug strip, not as part of the HUD.
        smallFont.data.setScale(0.72f)
        smallFont.color = Color(1f, 1f, 1f, 0.45f)
        smallFont.draw(batch, bundle.format("hud.turn_number", state.turnNumber), CombatLayout.MARGIN, 700f)
        smallFont.draw(batch, bundle.format("hud.turn_phase", state.currentTurn.name), CombatLayout.MARGIN, 682f)
        smallFont.draw(
            batch,
            bundle.format("hud.pile_counts", state.drawPileCount, state.discardPileCount, state.exhaustPileCount),
            CombatLayout.MARGIN, 664f
        )
        smallFont.color = Color.WHITE
        smallFont.data.setScale(1f)
        batch.end()
    }

    // --- C7 between-fight node screen (logic in RunManager; pure presentation here) ---

    fun renderNode(run: RunManager, batch: SpriteBatch) {
        // NEW-1 (playtest): a node must open on the MAIN choices, never inherit the previous
        // node's sub-mode (stuck SHOP/REMOVE/UPGRADE screen). Reset on the combat->node
        // transition; survives restartRun() because restart renders a combat frame first.
        if (!lastFrameWasNode) nodeMode = NodeMode.CHOICES
        lastFrameWasNode = true
        shapeRenderer.projectionMatrix = batch.projectionMatrix
        drawBackground(batch, run.currentDistrict.backgroundKey())
        drawDistrictTitle(run.currentDistrict, batch)

        batch.begin()
        font.draw(batch, bundle.format("node.header"), 50f, 690f)
        smallFont.draw(batch, bundle.format("node.heal", NodeConfig.HEAL_AMOUNT), 50f, 665f)
        smallFont.draw(batch, bundle.format("node.gold_debt", run.gold, run.debt), 50f, 640f)
        batch.end()

        val buyCost = NodeConfig.escalatedCost(NodeConfig.BUY_BASE, run.nodeIndex)
        val removeCost = NodeConfig.escalatedCost(NodeConfig.REMOVE_BASE, run.nodeIndex)
        val loanGold = NodeConfig.escalatedCost(NodeConfig.LOAN_GOLD_BASE, run.nodeIndex)
        val loanDebt = NodeConfig.escalatedCost(NodeConfig.LOAN_DEBT_BASE, run.nodeIndex)

        when (nodeMode) {
            NodeMode.CHOICES -> {
                drawNodeButton(0, bundle.get("node.button.free_pick"), run.rewardChoices.isNotEmpty(), batch)
                drawNodeButton(1, bundle.get("node.button.repay"), run.gold > 0 && run.debt > 0, batch)
                drawNodeButton(2, bundle.get("node.button.buy"), run.gold >= buyCost, batch)
                drawNodeButton(3, bundle.get("node.button.remove"), run.gold >= removeCost, batch)
                val affordableLoan = run.debt + loanDebt <= DebtConfig.EXECUTION_THRESHOLD
                drawNodeButton(4, bundle.get("node.button.loan"), affordableLoan, batch)
                // card-upgrades R9: 6th action — flat upgrade, capped, gold-gated.
                val upgradeEnabled = run.gold >= NodeConfig.UPGRADE_BASE &&
                    run.upgradesRemaining > 0 && run.resolveNodeUpgradeCards().isNotEmpty()
                drawNodeButton(5, bundle.get("node.button.upgrade"), upgradeEnabled, batch)
            }
            NodeMode.SHOP -> {
                batch.begin()
                font.draw(batch, bundle.format("node.shop.title", run.nodeIndex), 50f, 690f)
                font.draw(batch, bundle.format("node.buy_offer", buyCost), 50f, 660f)
                batch.end()
                drawCardOffers(run.nodeShopChoices, batch)
            }
            NodeMode.REMOVE -> {
                batch.begin()
                font.draw(batch, bundle.get("node.remove.title"), 50f, 690f)
                font.draw(batch, bundle.format("node.remove_offer", removeCost), 50f, 660f)
                batch.end()
                drawCardOffers(run.resolveNodeRemoveCards(), batch)
            }
            NodeMode.UPGRADE -> {
                batch.begin()
                font.draw(batch, bundle.get("node.upgrade.title"), 50f, 690f)
                font.draw(batch, bundle.format("node.upgrade_offer", NodeConfig.UPGRADE_BASE), 50f, 660f)
                batch.end()
                drawCardOffers(run.resolveNodeUpgradeCards(), batch)
            }
            NodeMode.LOAN -> {
                batch.begin()
                font.draw(batch, bundle.get("node.loan.title"), 50f, 690f)
                font.draw(batch, bundle.format("node.loan_offer", loanGold, loanDebt), 50f, 660f)
                batch.end()
                drawNodeButton(0, bundle.get("node.button.loan"), true, batch)
            }
        }
    }

    /**
     * District title card (F2 R2.7): the district name + descriptor, drawn into the bounds
     * [CombatLayout.districtTitle] returns — centred on the live world width, never a fixed 1280
     * coordinate. Shown on district entrance (combat) and on the node screen.
     */
    private fun drawDistrictTitle(district: District, batch: SpriteBatch) {
        val bounds = CombatLayout.districtTitle(worldWidth)
        shapeRenderer.projectionMatrix = batch.projectionMatrix

        // Panel + brass border (district-card treatment, per design.md).
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(navy950.r, navy950.g, navy950.b, 0.82f)
        shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(brass500)
        shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height)
        shapeRenderer.end()

        val padX = 18f
        batch.begin()
        // Name: one line, top of the card.
        font.data.setScale(1.5f)
        font.color = ink100
        font.draw(batch, bundle.get(district.name), bounds.x + padX, bounds.y + bounds.height - 18f)
        font.data.setScale(1f)
        // Descriptor: wrapped, up to three lines, below the name.
        smallFont.data.setScale(0.72f)
        smallFont.color = ink300
        smallFont.draw(
            batch, bundle.get(district.description),
            bounds.x + padX, bounds.y + bounds.height - 64f,
            bounds.width - 2f * padX, Align.left, true
        )
        smallFont.data.setScale(1f)
        smallFont.color = Color.WHITE
        batch.end()
    }

    private fun drawNodeButton(index: Int, label: String, enabled: Boolean, batch: SpriteBatch) {
        val b = nodeChoiceBounds(index)
        val base = if (enabled) Color(0.3f, 0.6f, 1f, 1f) else Color.GRAY
        shadowRect(b.x, b.y, b.width, b.height)
        gradientRect(b.x, b.y, b.width, b.height, darken(base, 0.65f), base)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0f, 0f, 0f, 1f)
        shapeRenderer.rect(b.x, b.y, b.width, b.height)
        shapeRenderer.end()
        batch.begin()
        smallFont.draw(batch, label, b.x + 10f, b.y + b.height / 2f + 6f)
        batch.end()
    }

    private fun drawCardOffers(cards: List<CardDefinition>, batch: SpriteBatch) {
        cards.forEachIndexed { index, card ->
            val bounds = nodeSubCardBounds(index, cards.size)
            drawCardFace(
                batch, bounds.x, bounds.y, bounds.width, bounds.height,
                art = cardTextures[card.id],
                frame = cardFrameTextures[card.type],
                tint = Color.WHITE,
                cost = card.cost,
                name = bundle.get(card.name),
                description = bundle.get(card.description),
                type = card.type,
                upgraded = false
            )
        }
    }

    // --- Node layout helpers (shared with CombatInputHandler via the public bounds fn) ---

    /** END TURN sits in the right column, so it moves with the world's right edge. */
    fun endTurnButtonBounds(): Rectangle = CombatLayout.endTurnButton(worldWidth)

    fun rewardCardBounds(index: Int, count: Int): Rectangle {
        val totalWidth = count * REWARD_CARD_WIDTH + (count - 1) * REWARD_CARD_SPACING
        val startX = (worldWidth - totalWidth) / 2
        val y = (SCREEN_HEIGHT - REWARD_CARD_HEIGHT) / 2
        val x = startX + index * (REWARD_CARD_WIDTH + REWARD_CARD_SPACING)
        return Rectangle(x, y, REWARD_CARD_WIDTH, REWARD_CARD_HEIGHT)
    }

    fun nodeChoiceBounds(index: Int): Rectangle {
        // The six buttons span 1120 units; centre that span on the live world instead of pinning
        // it to the left edge, which is what a wider-than-1280 viewport would otherwise do.
        val offset = (worldWidth - 1120f) / 2f - 40f
        val x = listOf(40f, 230f, 420f, 610f, 800f, 990f)[index]
        return Rectangle(x + offset, 120f, 170f, 50f)
    }

    fun nodeSubCardBounds(index: Int, count: Int): Rectangle {
        val w = 280f
        val h = 380f
        val spacing = 40f
        val total = count * w + (count - 1) * spacing
        val startX = (worldWidth - total) / 2f
        return Rectangle(startX + index * (w + spacing), 180f, w, h)
    }

    fun renderReward(choices: List<CardDefinition>, batch: SpriteBatch) {
        shapeRenderer.projectionMatrix = batch.projectionMatrix
        drawBackground(batch, "bg_combat")

        batch.begin()
        font.draw(batch, bundle.get("reward.header"), 50f, 680f)
        batch.end()

        choices.forEachIndexed { index, card ->
            val bounds = rewardCardBounds(index, choices.size)
            drawCardFace(
                batch, bounds.x, bounds.y, bounds.width, bounds.height,
                art = cardTextures[card.id],
                frame = cardFrameTextures[card.type],
                tint = Color.WHITE,
                cost = card.cost,
                name = bundle.get(card.name),
                description = bundle.get(card.description),
                type = card.type,
                upgraded = false
            )
        }
    }

    fun renderRunEnd(batch: SpriteBatch, won: Boolean) {
        shapeRenderer.projectionMatrix = batch.projectionMatrix
        drawBackground(batch, "bg_combat")

        val message = bundle.get(if (won) "run_end.victory" else "run_end.defeat")
        val restartHint = bundle.get("run_end.restart_hint")

        batch.begin()
        font.getData().setScale(3f)
        font.setColor(if (won) Color.GREEN else Color.RED)
        val bounds = GlyphLayout(font, message)
        font.draw(batch, message, (worldWidth - bounds.width) / 2, (screenHeight + bounds.height) / 2)
        font.getData().setScale(1f)
        font.setColor(Color.WHITE)
        smallFont.draw(batch, restartHint, (worldWidth - 120f) / 2, (screenHeight - bounds.height) / 2 - 50f)
        batch.end()
    }

    private fun drawLog(log: List<com.debtsdecks.core.model.CombatLogEntry>, batch: SpriteBatch) {
        val panel = CombatLayout.logPanel(worldWidth)
        val pad = 10f
        panelBackdrop(panel.x, panel.y, panel.width, panel.height)

        batch.begin()
        smallFont.data.setScale(0.72f)
        smallFont.color = ink300
        smallFont.draw(batch, bundle.get("hud.combat_log_header"), panel.x + pad, panel.y + panel.height - pad)

        // Newest first, fading with age, stopping as soon as the next entry would fall out of the
        // panel — the old version reserved room for 20 lines in a box half the screen tall.
        var lineY = panel.y + panel.height - 32f
        val textW = panel.width - 2f * pad
        for ((index, entry) in log.reversed().withIndex()) {
            val layout = GlyphLayout(smallFont, entry.message, Color.WHITE, textW, Align.left, true)
            if (lineY - layout.height < panel.y + pad) break
            smallFont.color = Color(1f, 1f, 1f, (1f - index * 0.07f).coerceAtLeast(0.35f))
            smallFont.draw(batch, entry.message, panel.x + pad, lineY, textW, Align.left, true)
            lineY -= layout.height + 6f
        }
        smallFont.color = Color.WHITE
        smallFont.data.setScale(1f)
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
        backgroundTextures.values.forEach { it.dispose() }
    }

    companion object {
        /**
         * Where each intent's icon lives, derived from the enum rather than listed by hand. Adding
         * an [IntentType] extends this map for free.
         *
         * Keep it derived. `IntentTypeCoverageTest` asserts that the map covers every [IntentType]
         * and that every path it yields is a file on disk, so a value declared without its PNG
         * fails the build instead of shipping a blank intent bar. What no test can assert is the
         * derivation itself: replace `associateWith` with literals for today's values and the
         * suite stays green, because the two maps are equal until the next enum value lands. The
         * `associateWith` is therefore load-bearing on its own -- it is what makes a stale map
         * unrepresentable rather than merely detected one commit later.
         */
        internal val INTENT_ICON_PATHS: Map<IntentType, String> =
            IntentType.entries.associateWith { "art/${it.iconName}.png" }

        /**
         * How much of the card the frame PNG's opaque border covers on each side, measured off
         * art/card_frame_attack.png (336x480): 46px of 336 horizontally, 62px of 480 vertically.
         * Card content has to stay inside these insets or the frame paints over it.
         */
        private const val FRAME_INSET_X = 0.137f
        private const val FRAME_INSET_Y = 0.129f

        /** Share of the framed interior given to the text panel; the rest is the art window. */
        private const val TEXT_PANEL_FRACTION = 0.48f

        /** Hard ceiling from the handoff: clamp the copy, never shrink it below the 11px floor. */
        private const val DESCRIPTION_MAX_LINES = 3

        private const val REWARD_CARD_WIDTH = 280f
        private const val REWARD_CARD_HEIGHT = 380f
        private const val REWARD_CARD_SPACING = 40f
        private const val SCREEN_HEIGHT = 720f
    }
}