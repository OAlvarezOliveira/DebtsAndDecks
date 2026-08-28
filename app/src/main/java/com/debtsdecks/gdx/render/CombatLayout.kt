package com.debtsdecks.gdx.render

import com.badlogic.gdx.math.Rectangle

/**
 * Geometry for the combat screen's furniture, derived from the live world width.
 *
 * The viewport is an ExtendViewport: 720 world units tall, width following the device aspect. A
 * 20:9 phone therefore gets ~2133 units of world instead of the 1280 a FitViewport pillarboxed it
 * into, so nothing here may assume 1280 any more.
 *
 * The screen reads as three columns — player state left, hand centred, log and END TURN right. The
 * side columns are sized first and the hand takes whatever band is left over, which is what keeps
 * the panels off the cards on any aspect ratio instead of relying on hand-tuned constants.
 */
object CombatLayout {
    const val WORLD_HEIGHT = 720f
    const val MARGIN = 24f
    private const val SIDE_WIDTH_FRACTION = 0.16f
    private const val MIN_SIDE_WIDTH = 190f
    private const val MAX_SIDE_WIDTH = 300f

    fun sideWidth(worldWidth: Float): Float =
        (worldWidth * SIDE_WIDTH_FRACTION).coerceIn(MIN_SIDE_WIDTH, MAX_SIDE_WIDTH)

    /** Horizontal band left for the hand once both side columns and their margins are reserved. */
    fun handBandWidth(worldWidth: Float): Float =
        worldWidth - 2f * sideWidth(worldWidth) - 4f * MARGIN

    fun playerPanel(worldWidth: Float): Rectangle =
        Rectangle(MARGIN, MARGIN, sideWidth(worldWidth), 244f)

    fun endTurnButton(worldWidth: Float): Rectangle {
        val w = sideWidth(worldWidth)
        return Rectangle(worldWidth - MARGIN - w, MARGIN, w, 64f)
    }

    fun logPanel(worldWidth: Float): Rectangle {
        val w = sideWidth(worldWidth)
        val button = endTurnButton(worldWidth)
        return Rectangle(worldWidth - MARGIN - w, button.y + button.height + 16f, w, 292f)
    }

    // District title card (F2 R2.7): centred on the live world width so it tracks the viewport,
    // never a fixed 1280-space coordinate. CombatRenderer draws the name + descriptor into it.
    const val DISTRICT_TITLE_WIDTH = 520f
    const val DISTRICT_TITLE_HEIGHT = 120f
    private const val DISTRICT_TITLE_Y = 600f

    /** Top-centre bounds of the district title card, centred on [worldWidth]. */
    fun districtTitle(worldWidth: Float): Rectangle =
        Rectangle((worldWidth - DISTRICT_TITLE_WIDTH) / 2f, DISTRICT_TITLE_Y, DISTRICT_TITLE_WIDTH, DISTRICT_TITLE_HEIGHT)
}
