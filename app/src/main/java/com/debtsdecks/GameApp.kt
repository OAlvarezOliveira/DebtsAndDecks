package com.debtsdecks

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.debtsdecks.di.container
import com.debtsdecks.gdx.GameScreen

class GameApp : ApplicationAdapter() {
    private lateinit var screen: GameScreen

    override fun create() {
        screen = container.get()
        Gdx.input.inputProcessor = screen.inputProcessor
        setScreen(screen)
    }

    override fun render() {
        super.render()
        screen.render(Gdx.graphics.deltaTime)
    }

    override fun dispose() {
        screen.dispose()
    }

    override fun pause() {
        super.pause()
        screen.pause()
    }

    override fun resume() {
        super.resume()
        screen.resume()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        screen.resize(width, height)
    }
}