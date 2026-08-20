package com.debtsdecks.core.i18n

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.I18NBundle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Locale

/**
 * Covers combat-progression-and-i18n Phase 4a: the i18n bundle infra loads cleanly in a headless
 * JVM unit test, with no Android/emulator dependency. [I18NBundle] and [FileHandle] are pure
 * `gdx.utils`/`gdx.files` classes when [FileHandle] is constructed directly from a [File]
 * (`FileType.Absolute`), bypassing the `Gdx.files`/`Gdx.app` statics that only exist inside a
 * running LibGDX application (this project has no `gdx-backend-headless` dependency).
 *
 * The JVM default [Locale] is pinned to [Locale.US] around each test: gdx's locale-candidate
 * fallback chain consults `Locale.getDefault()` once the requested locale's own candidates are
 * exhausted, so on a host/CI machine whose default locale is already Spanish (e.g. `es_ES`), an
 * explicit `Locale.ENGLISH` request can otherwise resolve to the Spanish properties file. Pinning
 * makes this test hermetic and independent of the machine it runs on.
 *
 * Only the seed key needed to prove the DI-wired bundle resolves both locales is covered here;
 * the full string migration (card/enemy/log/UI text) lands in later slices (Phase 4b-i..iv).
 */
class I18nBundleTest {

    private val bundleBase = FileHandle(File("src/main/assets/i18n/strings"))
    private lateinit var originalDefaultLocale: Locale

    @BeforeEach
    fun pinDefaultLocale() {
        originalDefaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterEach
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale)
    }

    @Test
    fun `English bundle resolves the seed key`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("Debts & Decks", bundle.get("app.name"))
    }

    @Test
    fun `Spanish bundle resolves the seed key with a distinct neutral-Spanish translation`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("Deudas y Cartas", bundle.get("app.name"))
    }
}
