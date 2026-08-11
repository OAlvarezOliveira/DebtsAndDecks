package com.debtsdecks.gdx

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector3
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.TurnPhase

class GameScreen(
    private val combatEngine: CombatEngine,
    private val renderer: CombatRenderer,
    private val inputHandler: CombatInputHandler
) : Screen {

    private val camera = OrthographicCamera()
    private val batch = SpriteBatch()
    private val viewportWidth = 1280f
    private val viewportHeight = 720f

    var inputProcessor: InputProcessor = inputHandler

    override fun show() {
        camera.setToOrtho(false, viewportWidth, viewportHeight)
        camera.update()
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        batch.projectionMatrix = camera.combined

        val state = combatEngine.getState()

        batch.begin()
        renderer.render(state, batch, camera)
        batch.end()

        // Handle input
        inputHandler.update(state)
    }

    override fun resize(width: Int, height: Int) {
        val aspectRatio = viewportWidth / viewportHeight
        val screenAspectRatio = width.toFloat() / height

        if (screenAspectRatio > aspectRatio) {
            val newWidth = (height * aspectRatio).toInt()
            val offset = (width - newWidth) / 2
            Gdx.gl.glViewport(offset, 0, newWidth, height)
        } else {
            val newHeight = (width / aspectRatio).toInt()
            val offset = (height - newHeight) / 2
            Gdx.gl.glViewport(0, offset, width, newHeight)
        }
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        batch.dispose()
        renderer.dispose()
    }
}