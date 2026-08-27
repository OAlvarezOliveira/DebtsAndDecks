package com.debtsdecks.gdx.render

import com.badlogic.gdx.math.Rectangle

/**
 * Single source of truth for where the hand cards sit in world space.
 *
 * [CombatRenderer] draws from here and [com.debtsdecks.gdx.input.CombatInputHandler] hit-tests from
 * here, so a size change can no longer leave the hitboxes behind the art — the two used to carry
 * their own private copies of the same four constants.
 *
 * The base size keeps the 5:7 ratio of the frame PNGs (336x480). The row is centred on the world
 * and scaled down uniformly to fit the band [CombatLayout.handBandWidth] leaves between the side
 * columns, so cards never collide with the player panel, the log or the END TURN button. A 20:9
 * phone has room for the full 180x252; a 16:9 screen lands near the old 140x200.
 */
object HandLayout {
    const val BASE_WIDTH = 180f
    const val BASE_HEIGHT = 252f
    const val BASE_SPACING = 12f
    const val HAND_Y = 24f

    fun cardBounds(index: Int, handSize: Int, worldWidth: Float): Rectangle {
        val band = CombatLayout.handBandWidth(worldWidth)
        val unscaled = handSize * BASE_WIDTH + (handSize - 1) * BASE_SPACING
        val scale = if (handSize <= 0 || unscaled <= band) 1f else band / unscaled
        val w = BASE_WIDTH * scale
        val h = BASE_HEIGHT * scale
        val spacing = BASE_SPACING * scale
        val startX = (worldWidth - (handSize * w + (handSize - 1) * spacing)) / 2f
        return Rectangle(startX + index * (w + spacing), HAND_Y, w, h)
    }
}
