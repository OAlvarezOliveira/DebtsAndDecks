package com.debtsdecks

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.debtsdecks.gdx.GameScreen
import com.debtsdecks.gdx.audio.SoundManager

class GameApp : ApplicationAdapter() {
    private lateinit var screen: GameScreen

    private fun <T> withScreen(block: GameScreen.() -> T): T? =
        if (::screen.isInitialized) screen.block() else null

    override fun create() {
        screen = DebtsAndDecksApp.container.get()
        Gdx.input.inputProcessor = screen.inputProcessor
        screen.show()
    }

    override fun render() {
        super.render()
        screen.render(Gdx.graphics.deltaTime)
    }

    override fun dispose() {
        screen.dispose()
        DebtsAndDecksApp.container.get<SoundManager>().dispose()
    }

    override fun pause() {
        super.pause()
        withScreen { pause() }
    }

    override fun resume() {
        super.resume()
        withScreen { resume() }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        withScreen { resize(width, height) }
    }
}