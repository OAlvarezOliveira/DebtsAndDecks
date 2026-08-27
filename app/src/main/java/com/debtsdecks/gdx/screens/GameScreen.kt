package com.debtsdecks.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.viewport.Viewport
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.gdx.input.CombatInputHandler
import com.debtsdecks.gdx.render.CombatRenderer

class GameScreen(
    private val combatEngine: CombatEngine,
    private val viewport: Viewport,
    private val renderer: CombatRenderer,
    private val inputHandler: CombatInputHandler,
    private val runManager: RunManager
) : Screen {

    private val batch = SpriteBatch()

    var inputProcessor: InputProcessor = inputHandler

    override fun show() {
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        // ExtendViewport gives a different world width per device aspect; the renderer anchors its
        // right-hand column and every centred row off this, so it has to be current before drawing.
        renderer.worldWidth = viewport.worldWidth

        // NOTE: CombatRenderer's draw methods manage their own batch.begin()/end() pairs
        // (interleaved with ShapeRenderer calls), so this must NOT wrap them in an outer
        // begin()/end() - SpriteBatch throws IllegalStateException on a nested begin().
        when (runManager.phase) {
            RunManager.Phase.COMBAT -> renderer.render(combatEngine.getState(), batch)
            RunManager.Phase.NODE -> renderer.renderNode(runManager, batch)
            RunManager.Phase.VICTORY -> renderer.renderRunEnd(batch, won = true)
            RunManager.Phase.DEFEAT -> renderer.renderRunEnd(batch, won = false)
        }
    }

    override fun resize(width: Int, height: Int) {
        // Viewport.update() keeps the 720-unit world height and extends the width to the device
        // aspect (no letterboxing), AND records the actual on-screen viewport rect, which
        // CombatInputHandler needs for correct touch-to-world unprojection (a manual glViewport
        // call does not expose that rect, so unproject() silently assumes the full screen instead).
        viewport.update(width, height, true)
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        batch.dispose()
        renderer.dispose()
    }
}