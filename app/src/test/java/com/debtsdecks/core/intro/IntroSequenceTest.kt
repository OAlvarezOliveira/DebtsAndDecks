package com.debtsdecks.core.intro

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The opening stills are a hand-advanced sequence, so the only thing worth testing is the walk:
 * it starts at the first frame, moves forward one tap at a time, ends after the last one, and
 * cannot be walked off either end. A double tap on the final frame is the realistic way to break
 * this, so it gets its own case.
 *
 * The catalog cases are the ones that earn their keep: they fail when the art and the sequence
 * drift apart, which on a device shows up as a black screen rather than an exception.
 */
class IntroSequenceTest {

    private val probe = listOf(
        IntroFrame("art/intro/a.png", "intro.caption.a"),
        IntroFrame("art/intro/b.png", "intro.caption.b"),
    )

    @Test
    fun `starts on the first frame and is not finished`() {
        val seq = IntroSequence(probe)
        assertEquals(probe[0], seq.current)
        assertFalse(seq.isFinished)
    }

    @Test
    fun `advance walks every frame in order and then finishes`() {
        val seq = IntroSequence(probe)
        val seen = mutableListOf<IntroFrame>()
        while (!seq.isFinished) {
            seen.add(seq.current!!)
            seq.advance()
        }
        assertEquals(probe, seen)
        assertNull(seq.current, "a finished sequence has no current frame")
    }

    @Test
    fun `advancing past the end stays finished instead of throwing`() {
        // A double tap on the last frame is one touch event apart; it must not crash the screen.
        val seq = IntroSequence(probe)
        repeat(probe.size + 5) { seq.advance() }
        assertTrue(seq.isFinished)
        assertNull(seq.current)
    }

    @Test
    fun `skip finishes from the first frame`() {
        val seq = IntroSequence(probe)
        seq.skip()
        assertTrue(seq.isFinished)
        assertNull(seq.current)
    }

    @Test
    fun `skip finishes from the middle`() {
        val seq = IntroSequence(probe)
        seq.advance()
        seq.skip()
        assertTrue(seq.isFinished)
    }

    @Test
    fun `an empty sequence is finished on arrival`() {
        // Not hypothetical: it is what shipping the screen with the art stripped out would produce,
        // and it has to fall through to the game rather than hang on a blank frame.
        val seq = IntroSequence(emptyList())
        assertTrue(seq.isFinished)
        assertNull(seq.current)
    }

    @Test
    fun `the default sequence is the four opening stills, in story order`() {
        assertEquals(
            listOf("art/intro/intro_01.png", "art/intro/intro_02.png",
                   "art/intro/intro_03.png", "art/intro/intro_04.png"),
            IntroSequence().frames.map { it.image }
        )
        assertEquals(
            listOf("intro.caption.1", "intro.caption.2", "intro.caption.3", "intro.caption.4"),
            IntroSequence().frames.map { it.captionKey }
        )
    }

    @Test
    fun `every frame in the default sequence has its image on disk`() {
        // Catches the drift that is invisible until a device runs it: a renamed or missing still
        // is not an exception, it is a black screen with a caption floating on it.
        for (frame in IntroSequence().frames) {
            val file = File("src/main/assets/${frame.image}")
            assertTrue(file.isFile, "intro still ${frame.image} is missing from the assets")
        }
    }

    @Test
    fun `every caption in the default sequence is translated in both bundles`() {
        val en = rawProperties("strings.properties")
        val es = rawProperties("strings_es.properties")
        for (frame in IntroSequence().frames) {
            for ((tag, table) in listOf("en" to en, "es" to es)) {
                val text = table[frame.captionKey]
                assertNotNull(text, "caption ${frame.captionKey} is missing from the $tag properties file")
                assertTrue(text!!.isNotBlank(), "caption ${frame.captionKey} is blank in the $tag properties file")
            }
        }
    }

    @Test
    fun `the skip affordance is translated in both bundles`() {
        for (name in listOf("strings.properties", "strings_es.properties")) {
            val text = rawProperties(name)["intro.skip"]
            assertNotNull(text, "intro.skip is missing from $name")
            assertTrue(text!!.isNotBlank(), "intro.skip is blank in $name")
        }
    }

    /**
     * Raw key/value pairs, with no bundle fallback of any kind: I18NBundle answers a missing
     * Spanish key with the English text, so a parity check routed through it passes on exactly
     * the failure it was written to catch. Same reasoning as I18nBundleTest.
     */
    private fun rawProperties(fileName: String): Map<String, String> {
        val file = File("src/main/assets/i18n/$fileName")
        assertTrue(file.isFile, "expected ${file.path} to exist")
        val props = java.util.Properties()
        file.inputStream().reader(Charsets.UTF_8).use { props.load(it) }
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }
}
