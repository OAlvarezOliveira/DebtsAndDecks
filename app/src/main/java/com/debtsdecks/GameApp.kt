package com.debtsdecks

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.debtsdecks.gdx.GameScreen
import com.debtsdecks.gdx.IntroScreen
import com.debtsdecks.gdx.audio.SoundManager

class GameApp : ApplicationAdapter() {
    private lateinit var screen: GameScreen
    /** The opening stills, or null once they are spent. Plays once per launch. */
    private var intro: IntroScreen? = null

    /** Whichever screen currently owns the frame: the opening while it lasts, then the game. */
    private val active: Screen?
        get() = intro ?: if (::screen.isInitialized) screen else null

    private fun <T> withScreen(block: Screen.() -> T): T? = active?.block()

    override fun create() {
        screen = DebtsAndDecksApp.container.get()
        val opening: IntroScreen = DebtsAndDecksApp.container.get()
        intro = opening
        Gdx.input.inputProcessor = opening.inputProcessor
        // A still that fails to load leaves the sequence already finished, so the first render
        // below hands straight over to the game rather than sitting on an empty screen.
        opening.show()
    }

    override fun render() {
        super.render()
        intro?.takeIf { it.isFinished }?.let { spent ->
            spent.dispose()
            intro = null
            Gdx.input.inputProcessor = screen.inputProcessor
            screen.show()
        }
        active?.render(Gdx.graphics.deltaTime)
    }

    override fun dispose() {
        intro?.dispose()
        intro = null
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