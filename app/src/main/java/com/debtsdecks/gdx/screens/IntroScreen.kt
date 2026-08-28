package com.debtsdecks.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.badlogic.gdx.utils.viewport.Viewport
import com.debtsdecks.core.intro.IntroSequence

/**
 * The opening stills, before the first fight. One frame per tap, with a SKIP affordance for the
 * second run onwards.
 *
 * Everything here is presentation: the walk itself lives in [IntroSequence], which is pure Kotlin
 * and unit-tested. The captions are drawn from the bundle rather than baked into the art, which is
 * the whole reason the opening is four stills instead of a generated clip.
 *
 * A still that fails to load ends the sequence rather than showing a caption floating on black:
 * falling through to the game is always better than a screen the player cannot get past.
 */
class IntroScreen(
    private val sequence: IntroSequence,
    private val viewport: Viewport,
    private val bundle: I18NBundle
) : Screen, InputProcessor {

    private val batch = SpriteBatch()
    private val font = BitmapFont().apply { data.setScale(CAPTION_SCALE) }
    private val skipFont = BitmapFont().apply { data.setScale(SKIP_SCALE) }
    private val layout = GlyphLayout()
    private val touchPos = Vector2()

    private var textures: Map<String, Texture> = emptyMap()
    /** Seconds the current frame has been on screen; drives the fade so cuts are not abrupt. */
    private var elapsed = 0f

    val isFinished: Boolean get() = sequence.isFinished

    var inputProcessor: InputProcessor = this

    override fun show() {
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        textures = try {
            sequence.frames.associate { it.image to loadTexture(it.image) }
        } catch (t: Throwable) {
            Gdx.app.error("DebtsDecks", "intro still failed to load, skipping the opening", t)
            sequence.skip()
            emptyMap()
        }
    }

    private fun loadTexture(path: String): Texture =
        Texture(Gdx.files.internal(path)).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }

    override fun render(delta: Float) {
        val frame = sequence.current ?: return
        elapsed += delta

        Gdx.gl.glClearColor(NAVY.r, NAVY.g, NAVY.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        val worldWidth = viewport.worldWidth
        val fade = (elapsed / FADE_SECONDS).coerceAtMost(1f)

        batch.begin()
        // Cover, not stretch: the stills are authored at 1280x720, so on a wider world both axes
        // scale by the same factor and the surplus height falls off the top and bottom equally.
        // Same contract as CombatRenderer.drawBackground, which is why nothing load-bearing was
        // composed near those edges (see docs/ART-PROMPTS-INTRO.md).
        val texture = textures[frame.image]
        if (texture != null) {
            val drawH = WORLD_HEIGHT * maxOf(worldWidth / DESIGN_WIDTH, 1f)
            batch.setColor(1f, 1f, 1f, fade)
            batch.draw(texture, 0f, (WORLD_HEIGHT - drawH) / 2f, worldWidth, drawH)
            batch.setColor(Color.WHITE)
        }

        // The caption sits in the quiet bottom third every frame reserves for it.
        layout.setText(
            font, bundle.get(frame.captionKey),
            Color(INK.r, INK.g, INK.b, fade),
            worldWidth - 2f * CAPTION_MARGIN, Align.center, true
        )
        font.draw(batch, layout, CAPTION_MARGIN, CAPTION_Y)

        skipFont.setColor(INK.r, INK.g, INK.b, fade * SKIP_ALPHA)
        skipFont.draw(
            batch, bundle.get("intro.skip"),
            worldWidth - SKIP_W - SKIP_MARGIN, SKIP_Y, SKIP_W, Align.center, false
        )
        batch.end()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer > 0) return false
        val world = viewport.unproject(touchPos.set(screenX.toFloat(), screenY.toFloat()))
        val skipX = viewport.worldWidth - SKIP_W - SKIP_MARGIN
        val inSkip = world.x >= skipX - SKIP_PAD && world.y >= SKIP_Y - SKIP_H
        if (inSkip) sequence.skip() else advance()
        return true
    }

    private fun advance() {
        sequence.advance()
        elapsed = 0f
    }

    override fun resize(width: Int, height: Int) = viewport.update(width, height, true)

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        batch.dispose()
        font.dispose()
        skipFont.dispose()
        textures.values.forEach { it.dispose() }
        textures = emptyMap()
    }

    // Advancing on key-up as well costs nothing and makes the opening usable on a desktop build.
    override fun keyDown(keycode: Int): Boolean = false
    override fun keyUp(keycode: Int): Boolean { advance(); return true }
    override fun keyTyped(character: Char): Boolean = false
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false

    private companion object {
        const val DESIGN_WIDTH = 1280f
        const val WORLD_HEIGHT = 720f
        const val FADE_SECONDS = 0.45f
        const val CAPTION_SCALE = 1.6f
        const val CAPTION_MARGIN = 140f
        const val CAPTION_Y = 110f
        const val SKIP_SCALE = 1.1f
        const val SKIP_ALPHA = 0.7f
        const val SKIP_W = 120f
        const val SKIP_H = 44f
        const val SKIP_MARGIN = 40f
        const val SKIP_Y = 680f
        const val SKIP_PAD = 20f
        val NAVY: Color = Color.valueOf("0c0c18")
        val INK: Color = Color.valueOf("eef0fb")
    }
}
