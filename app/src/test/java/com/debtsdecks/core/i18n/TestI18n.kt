package com.debtsdecks.core.i18n

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.I18NBundle
import com.debtsdecks.gdx.i18n.BundleLocalizer
import java.io.File
import java.util.Locale

/**
 * Shared headless-JVM [I18NBundle] fixture for tests that need a valid bundle instance purely to
 * satisfy a constructor dependency (combat-progression-and-i18n Phase 4a DI wiring) without
 * exercising any bundle content themselves — see [I18nBundleTest] for the tests that actually
 * cover key resolution.
 *
 * Pins the JVM default [Locale] to [Locale.US] for the duration of bundle creation only (then
 * restores it), since gdx's `I18NBundle` locale-candidate resolution consults `Locale.getDefault()`
 * and would otherwise behave differently depending on the host machine's own default locale.
 */
fun testLocalizer(): Localizer {
    val original = Locale.getDefault()
    Locale.setDefault(Locale.US)
    try {
        val bundle = I18NBundle.createBundle(FileHandle(File("src/main/assets/i18n/strings")), Locale.ENGLISH)
        return BundleLocalizer(bundle)
    } finally {
        Locale.setDefault(original)
    }
}
