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
 * Covers the seed key (Phase 4a) plus every HUD/in-combat-screen key CombatRenderer's Phase 4b-i
 * migration references. Reward/end-screen, core-domain log, and JSON-sourced strings remain out
 * of scope here and land in later slices (Phase 4b-ii..iv).
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

    // --- Phase 4b-i: CombatRenderer HUD/combat strings ---
    // Purely structural key-value data (no branching logic to triangulate beyond MessageFormat
    // interpolation, already proven correct by Phase 4a's fallback-locale fix). Coverage below
    // exercises every key CombatRenderer's HUD/combat migration references, in both locales, with
    // distinct numeric argument sets per call so a hardcoded/copy-pasted key would be caught.

    @Test
    fun `English HUD bundle resolves non-parameterized labels and buttons`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("PLAYER", bundle.get("hud.player.label"))
        assertEquals("END TURN", bundle.get("hud.button.end_turn"))
        assertEquals("REPAY GOLD", bundle.get("hud.button.repay_gold"))
        assertEquals("REPAY CARD", bundle.get("hud.button.repay_card"))
        assertEquals("CANCEL", bundle.get("hud.button.cancel"))
        assertEquals("Tap a card to discard it and repay Debt", bundle.get("hud.repay_discard_hint"))
        assertEquals("COMBAT LOG", bundle.get("hud.combat_log_header"))
    }

    @Test
    fun `English HUD bundle formats status and resource placeholders with real interpolation`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("HP: 42/60", bundle.format("hud.player.hp", 42, 60))
        assertEquals("HP: 5/50", bundle.format("hud.player.hp", 5, 50))
        assertEquals("Block: 7", bundle.format("hud.status.block", 7))
        assertEquals("Str: 3", bundle.format("hud.status.strength", 3))
        assertEquals("Weak: 2", bundle.format("hud.status.weak", 2))
        assertEquals("Vuln: 1", bundle.format("hud.status.vulnerable", 1))
        assertEquals("Poison: 4", bundle.format("hud.status.poison", 4))
        assertEquals("Thorns: 6", bundle.format("hud.status.thorns", 6))
        assertEquals("Regen: 8", bundle.format("hud.status.regen", 8))
        assertEquals("CREDIT: 3/5", bundle.format("hud.credit", 3, 5))
        assertEquals("DEBT: 10 | GOLD: 20", bundle.format("hud.debt_gold", 10, 20))
        assertEquals("DEBT: 0 | GOLD: 0", bundle.format("hud.debt_gold", 0, 0))
        assertEquals("Phase: PLAYER_ACTION", bundle.format("hud.turn_phase", "PLAYER_ACTION"))
        assertEquals("Turn: 3", bundle.format("hud.turn_number", 3))
        assertEquals("Deck: 15 | Discard: 4 | Exhaust: 1", bundle.format("hud.pile_counts", 15, 4, 1))
    }

    @Test
    fun `Spanish HUD bundle resolves non-parameterized labels and buttons with neutral thematic translations`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("JUGADOR", bundle.get("hud.player.label"))
        assertEquals("TERMINAR TURNO", bundle.get("hud.button.end_turn"))
        assertEquals("PAGAR CON ORO", bundle.get("hud.button.repay_gold"))
        assertEquals("PAGAR CON CARTA", bundle.get("hud.button.repay_card"))
        assertEquals("CANCELAR", bundle.get("hud.button.cancel"))
        assertEquals("Toca una carta para descartarla y pagar la Deuda", bundle.get("hud.repay_discard_hint"))
        assertEquals("REGISTRO DE COMBATE", bundle.get("hud.combat_log_header"))
    }

    @Test
    fun `Spanish HUD bundle formats status and resource placeholders with real interpolation`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("PS: 42/60", bundle.format("hud.player.hp", 42, 60))
        assertEquals("PS: 5/50", bundle.format("hud.player.hp", 5, 50))
        assertEquals("Bloqueo: 7", bundle.format("hud.status.block", 7))
        assertEquals("Fue: 3", bundle.format("hud.status.strength", 3))
        assertEquals("Débil: 2", bundle.format("hud.status.weak", 2))
        assertEquals("Vul: 1", bundle.format("hud.status.vulnerable", 1))
        assertEquals("Veneno: 4", bundle.format("hud.status.poison", 4))
        assertEquals("Espinas: 6", bundle.format("hud.status.thorns", 6))
        assertEquals("Reg: 8", bundle.format("hud.status.regen", 8))
        assertEquals("CRÉDITO: 3/5", bundle.format("hud.credit", 3, 5))
        assertEquals("DEUDA: 10 | ORO: 20", bundle.format("hud.debt_gold", 10, 20))
        assertEquals("Fase: PLAYER_ACTION", bundle.format("hud.turn_phase", "PLAYER_ACTION"))
        assertEquals("Turno: 3", bundle.format("hud.turn_number", 3))
        assertEquals("Mazo: 15 | Descarte: 4 | Agotados: 1", bundle.format("hud.pile_counts", 15, 4, 1))
    }
}
