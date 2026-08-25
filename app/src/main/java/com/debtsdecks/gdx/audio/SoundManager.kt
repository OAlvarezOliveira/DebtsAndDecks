package com.debtsdecks.gdx.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound

class SoundManager {
    private val cardSelect: Sound = load("sounds/card_select.wav")
    private val cardPlay: Sound = load("sounds/card_play.wav")
    private val endTurn: Sound = load("sounds/end_turn.wav")
    private val victory: Sound = load("sounds/victory.wav")
    private val defeat: Sound = load("sounds/defeat.wav")

    private fun load(path: String): Sound = Gdx.audio.newSound(Gdx.files.internal(path))

    fun playCardSelect() = cardSelect.play(0.6f)
    fun playCardPlay() = cardPlay.play(0.7f)
    fun playEndTurn() = endTurn.play(0.6f)
    fun playVictory() = victory.play(0.8f)
    fun playDefeat() = defeat.play(0.8f)

    fun dispose() {
        cardSelect.dispose()
        cardPlay.dispose()
        endTurn.dispose()
        victory.dispose()
        defeat.dispose()
    }
}
