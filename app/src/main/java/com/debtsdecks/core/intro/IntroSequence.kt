package com.debtsdecks.core.intro

/**
 * One opening still: the image to draw and the key of the line drawn over it.
 *
 * The caption is a key, never a literal. It is the reason the opening is four stills instead of a
 * generated clip: prose lives in `strings.properties` and is translated in `strings_es.properties`,
 * and a rendered video would have to be regenerated per language.
 */
data class IntroFrame(val image: String, val captionKey: String)

/**
 * The opening stills, hand-advanced. Pure state, no rendering and no GDX: the screen asks what to
 * draw and reports taps back, which keeps this testable in a plain JVM test per
 * docs/CONVENTIONS.md Architecture Rule #1.
 *
 * Both ends are closed deliberately. [advance] past the last frame stays finished rather than
 * throwing, because a double tap on the final still is two touch events a few milliseconds apart
 * and must not take the screen down; an empty sequence is finished on arrival, so a build with the
 * art stripped out falls through to the game instead of hanging on a blank frame.
 */
class IntroSequence(val frames: List<IntroFrame> = OPENING) {

    private var index = 0

    /** The frame to draw, or `null` once the sequence is spent. */
    val current: IntroFrame?
        get() = frames.getOrNull(index)

    val isFinished: Boolean
        get() = index >= frames.size

    /** Moves to the next frame. Past the last one the sequence is simply finished. */
    fun advance() {
        if (!isFinished) index++
    }

    /** Ends the sequence outright, wherever it had got to. */
    fun skip() {
        index = frames.size
    }

    companion object {
        /**
         * Alistair Vance, in four beats: his trade, its loss, the city that keeps the ledger, and
         * the walk into the first district. The art is written up in docs/ART-PROMPTS-INTRO.md.
         *
         * The fourth caption cites the opening balance, which is `DebtConfig.STARTING_DEBT`; if
         * that constant moves, the line has to move with it.
         */
        val OPENING = listOf(
            IntroFrame("art/intro/intro_01.png", "intro.caption.1"),
            IntroFrame("art/intro/intro_02.png", "intro.caption.2"),
            IntroFrame("art/intro/intro_03.png", "intro.caption.3"),
            IntroFrame("art/intro/intro_04.png", "intro.caption.4"),
        )
    }
}
