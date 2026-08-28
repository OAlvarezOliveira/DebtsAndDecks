package com.debtsdecks.core.enemies

import com.debtsdecks.core.i18n.Localizer
import com.debtsdecks.gdx.render.CombatRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * An intent is the game's telegraph: the player decides what to play by reading what the enemy
 * announced. That telegraph is assembled from two assets the compiler cannot see — a display-text
 * key in both i18n bundles, and an icon PNG — and both fail *silently* when they are missing.
 * `CombatRenderer.drawIntent` treats an absent texture as "draw nothing", and `I18NBundle` answers
 * a missing Spanish key with the English string rather than throwing. A sixth [IntentType] added
 * without either asset therefore ships as a blank, iconless bar and no build goes red.
 *
 * These tests are that enforcement. They walk [IntentType.entries], so a new value is covered the
 * moment it is declared, and they read the `.properties` files directly rather than through
 * [com.badlogic.gdx.utils.I18NBundle] — a parity check written on top of `bundle.get()` passes on
 * exactly the failure it was written to catch, because of the parent-bundle fallback. The same
 * reasoning is spelled out at length in `I18nBundleTest`'s district parity block.
 */
class IntentTypeCoverageTest {

    @Test
    fun `every intent type declares display text present in both bundles`() {
        val tables = listOf(
            "en" to rawProperties("strings.properties"),
            "es" to rawProperties("strings_es.properties")
        )
        for (type in IntentType.entries) {
            for ((tag, table) in tables) {
                val text = table[type.l10nKey]
                assertNotNull(
                    text,
                    "${type.name} declares l10n key '${type.l10nKey}', absent from the $tag properties file"
                )
                assertTrue(
                    text!!.isNotBlank(),
                    "${type.name}'s key '${type.l10nKey}' is present but blank in the $tag properties file"
                )
            }
        }
    }

    @Test
    fun `the renderer's icon map covers every IntentType`() {
        // The gap this closes: intentTextures used to be five string literals typed by hand, and
        // the lookup that reads it treats a miss as "draw nothing". A sixth IntentType with its PNG
        // committed passed every other test in this file and still rendered a blank bar, because
        // nothing connected the enum to the map.
        //
        // What this test can and cannot prove. It compares key sets, so it catches a map that has
        // gone STALE -- which is the property that actually protects a sixth intent, and the reason
        // `drawIntent` may now use `getValue`. It does NOT prove the map is *derived*: swap the
        // production `associateWith` for five hand-typed literals covering today's five values and
        // this test still passes, because at that instant the two maps are equal. Derivation is a
        // property of the source text, not of any value the JVM can be asked about, so no assertion
        // here can pin it. Keeping the map derived is what makes staleness unrepresentable in the
        // first place; this test is the net for the day someone stops.
        assertEquals(
            IntentType.entries.toSet(),
            CombatRenderer.INTENT_ICON_PATHS.keys,
            "CombatRenderer.INTENT_ICON_PATHS must cover every IntentType -- a map that does not " +
                "is one the renderer will miss a key in, and a missed key is a blank intent bar"
        )
    }

    @Test
    fun `every icon the renderer will load exists on disk`() {
        // Reads the renderer's own paths rather than rebuilding them here. The previous version of
        // this test built the "art/<iconName>.png" string itself, so it proved a file existed at a
        // path nothing was obliged to use.
        for ((type, path) in CombatRenderer.INTENT_ICON_PATHS) {
            val icon = File("src/main/assets/$path")
            assertTrue(
                icon.isFile,
                "${type.name} declares icon '${type.iconName}', so the renderer will load " +
                    "'$path', but ${icon.path} does not exist"
            )
        }
    }

    @Test
    fun `intentDisplayName asks the localizer for the key its intent type declares`() {
        // Guards the guard above: the enum could declare a key that nothing looks up. This walks
        // the real EnemyInstance path with a recording Localizer, so the two stay tied together.
        for (type in IntentType.entries) {
            val recorder = RecordingLocalizer()
            enemyWith(type, recorder).intentDisplayName()

            assertEquals(
                listOf(type.l10nKey),
                recorder.keys,
                "${type.name}'s display name must resolve through the key the enum declares"
            )
        }
    }

    @Test
    fun `intentIconName reports the icon its intent type declares`() {
        for (type in IntentType.entries) {
            assertEquals(type.iconName, enemyWith(type, RecordingLocalizer()).intentIconName())
        }
    }

    private fun enemyWith(type: IntentType, l10n: Localizer) = EnemyInstance(
        EnemyDefinition(
            id = "probe",
            name = "Probe",
            hp = 10,
            intentPattern = listOf(IntentStep(type, damage = 3, param = 2)),
            rewards = EnemyRewards(gold = 0, cardChoices = 0)
        ),
        l10n
    )

    /** Records every key asked for and answers with the key itself, so nothing else can match. */
    private class RecordingLocalizer : Localizer {
        val keys = mutableListOf<String>()
        override fun get(key: String): String {
            keys.add(key)
            return key
        }
        override fun format(key: String, vararg args: Any?): String {
            keys.add(key)
            return key
        }
    }

    /** Loads a `.properties` file as raw key/value pairs, with no bundle fallback of any kind. */
    private fun rawProperties(fileName: String): Map<String, String> {
        val file = File("src/main/assets/i18n/$fileName")
        assertTrue(file.isFile, "expected ${file.path} to exist")
        val props = java.util.Properties()
        file.inputStream().reader(Charsets.UTF_8).use { props.load(it) }
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }
}
