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
 * Covers the seed key (Phase 4a), every HUD/in-combat-screen key CombatRenderer's Phase 4b-i
 * migration references, every reward/end-screen key CombatRenderer's Phase 4b-ii migration
 * references, and every core-domain log/intent key CombatEngine/CardResolver/EnemyAI/EnemyInstance's
 * Phase 4b-iii migration references. JSON-sourced card/enemy strings remain out of scope here and
 * land in Phase 4b-iv.
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

    // --- Phase 4b-ii: CombatRenderer reward/end-screen + GameScreen strings ---
    // renderRunEnd() drops its `message` param entirely in this slice; victory/defeat text is
    // derived from the `won: Boolean` parameter via two distinct bundle keys instead.

    @Test
    fun `English reward and run-end bundle resolves header, cost, and outcome text`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("CHOOSE A CARD", bundle.get("reward.header"))
        assertEquals("Cost: 3", bundle.format("reward.cost", 3))
        assertEquals("Cost: 0", bundle.format("reward.cost", 0))
        assertEquals("YOU CLEARED YOUR DEBTS!", bundle.get("run_end.victory"))
        assertEquals("REPOSSESSED...", bundle.get("run_end.defeat"))
        assertEquals("Tap to restart", bundle.get("run_end.restart_hint"))
    }

    @Test
    fun `Spanish reward and run-end bundle resolves header, cost, and outcome text with neutral thematic translations`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("ELIGE UNA CARTA", bundle.get("reward.header"))
        assertEquals("Coste: 3", bundle.format("reward.cost", 3))
        assertEquals("Coste: 0", bundle.format("reward.cost", 0))
        assertEquals("¡HAS SALDADO TU DEUDA!", bundle.get("run_end.victory"))
        assertEquals("EMBARGADO...", bundle.get("run_end.defeat"))
        assertEquals("Toca para reiniciar", bundle.get("run_end.restart_hint"))
    }

    // --- Phase 4b-iii: Core-domain log strings (CombatEngine/CardResolver/EnemyAI) + intent
    // display text (EnemyInstance.intentDisplayName(), relocated structurally in Phase 4a, content
    // swapped here). intentIconName()'s asset-lookup keys (intent_attack/etc.) are internal
    // identifiers, not player-facing text, and are deliberately NOT bundle keys.

    @Test
    fun `English CombatEngine and EnemyAI log strings resolve with real interpolation`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("Repaid 12 Debt with Gold.", bundle.format("log.repay_gold", 12))
        assertEquals("Discarded Strike to repay 3 Debt.", bundle.format("log.repay_discard", "Strike", 3))
        assertEquals("Poison deals 4 damage to Thug!", bundle.format("log.poison_damage_enemy", 4, "Thug"))
        assertEquals("Poison deals 2 damage to you!", bundle.format("log.poison_damage_player", 2))
        assertEquals("Regen heals you for 5!", bundle.format("log.regen_heal_player", 5))
        assertEquals("--- Turn 7 ---", bundle.format("log.turn_header", 7))
        assertEquals("Reshuffled discard pile!", bundle.get("log.reshuffle_discard"))
        assertEquals("Dealt 9 damage to Loan Shark!", bundle.format("log.dealt_damage", 9, "Loan Shark"))
        assertEquals("VICTORY!", bundle.get("log.victory"))
        assertEquals("DEFEAT!", bundle.get("log.defeat"))
        assertEquals("Thug attacks for 6 damage!", bundle.format("log.enemy_attacks", "Thug", 6))
        assertEquals("Collector attacks for 11 damage!", bundle.format("log.enemy_attacks", "Collector", 11))
        assertEquals("Loan Shark gains 3 Strength!", bundle.format("log.enemy_gains_strength", "Loan Shark", 3))
        assertEquals("Thug applies Weak (2)!", bundle.format("log.enemy_applies_weak", "Thug", 2))
        assertEquals("Collector takes 4 Thorns damage!", bundle.format("log.enemy_takes_thorns", "Collector", 4))
    }

    @Test
    fun `Spanish CombatEngine and EnemyAI log strings resolve with real interpolation and thematic translations`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("Pagaste 12 de Deuda con Oro.", bundle.format("log.repay_gold", 12))
        assertEquals("Descartaste Strike para pagar 3 de Deuda.", bundle.format("log.repay_discard", "Strike", 3))
        assertEquals("¡El Veneno causa 4 de daño a Thug!", bundle.format("log.poison_damage_enemy", 4, "Thug"))
        assertEquals("¡El Veneno te causa 2 de daño!", bundle.format("log.poison_damage_player", 2))
        assertEquals("¡La Regeneración te cura 5!", bundle.format("log.regen_heal_player", 5))
        assertEquals("--- Turno 7 ---", bundle.format("log.turn_header", 7))
        assertEquals("¡Se rebarajó la pila de descarte!", bundle.get("log.reshuffle_discard"))
        assertEquals("¡Causaste 9 de daño a Loan Shark!", bundle.format("log.dealt_damage", 9, "Loan Shark"))
        assertEquals("¡VICTORIA!", bundle.get("log.victory"))
        assertEquals("¡DERROTA!", bundle.get("log.defeat"))
        assertEquals("¡Thug ataca causando 6 de daño!", bundle.format("log.enemy_attacks", "Thug", 6))
        assertEquals("¡Loan Shark gana 3 de Fuerza!", bundle.format("log.enemy_gains_strength", "Loan Shark", 3))
        assertEquals("¡Thug aplica Debilidad (2)!", bundle.format("log.enemy_applies_weak", "Thug", 2))
        assertEquals("¡Collector recibe 4 de daño por Espinas!", bundle.format("log.enemy_takes_thorns", "Collector", 4))
    }

    @Test
    fun `English CardResolver log strings resolve with real interpolation`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("No valid target!", bundle.get("log.no_valid_target"))
        assertEquals("Repaid 2 Debt per hit (3 hit(s))!", bundle.format("log.repay_per_hit", 2, 3))
        assertEquals("Bash fizzles with no energy to spend!", bundle.format("log.card_fizzles", "Bash"))
        assertEquals("Applied Weak (2)!", bundle.format("log.applied_weak", 2))
        assertEquals("Applied Vulnerable (3)!", bundle.format("log.applied_vulnerable", 3))
        assertEquals("Applied Poison (4)!", bundle.format("log.applied_poison", 4))
        assertEquals("Gained 5 Block!", bundle.format("log.gained_block", 5))
        assertEquals("Drew 2 card(s)!", bundle.format("log.drew_cards", 2))
        assertEquals("Debt fuels your resolve: gained 3 Strength!", bundle.format("log.debt_fuels_resolve", 3))
        assertEquals("Gained 1 Strength!", bundle.format("log.gained_strength", 1))
        assertEquals("Repaid 6 Debt!", bundle.format("log.repaid_debt", 6))
        assertEquals("Gained 2 Thorns!", bundle.format("log.gained_thorns", 2))
        assertEquals("Gained 1 Regen!", bundle.format("log.gained_regen", 1))
        assertEquals("Lost 3 HP!", bundle.format("log.lost_hp", 3))
        assertEquals("Gained 10 Gold!", bundle.format("log.gained_gold", 10))
        assertEquals("All Debt wiped clean!", bundle.get("log.debt_wiped"))
        assertEquals(
            "Escrow Shield active: Debt from borrowing is halved this combat!",
            bundle.get("log.escrow_shield_active")
        )
    }

    @Test
    fun `Spanish CardResolver log strings resolve with real interpolation and thematic translations`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("¡Sin objetivo válido!", bundle.get("log.no_valid_target"))
        assertEquals("¡Pagaste 2 de Deuda por golpe (3 golpe(s))!", bundle.format("log.repay_per_hit", 2, 3))
        assertEquals("¡Bash falla, sin Crédito que gastar!", bundle.format("log.card_fizzles", "Bash"))
        assertEquals("¡Debilidad aplicada (2)!", bundle.format("log.applied_weak", 2))
        assertEquals("¡Vulnerable aplicado (3)!", bundle.format("log.applied_vulnerable", 3))
        assertEquals("¡Veneno aplicado (4)!", bundle.format("log.applied_poison", 4))
        assertEquals("¡Ganaste 5 de Bloqueo!", bundle.format("log.gained_block", 5))
        assertEquals("¡Robaste 2 carta(s)!", bundle.format("log.drew_cards", 2))
        assertEquals("¡La Deuda alimenta tu determinación: ganaste 3 de Fuerza!", bundle.format("log.debt_fuels_resolve", 3))
        assertEquals("¡Ganaste 1 de Fuerza!", bundle.format("log.gained_strength", 1))
        assertEquals("¡Pagaste 6 de Deuda!", bundle.format("log.repaid_debt", 6))
        assertEquals("¡Ganaste 2 de Espinas!", bundle.format("log.gained_thorns", 2))
        assertEquals("¡Ganaste 1 de Regeneración!", bundle.format("log.gained_regen", 1))
        assertEquals("¡Perdiste 3 de PS!", bundle.format("log.lost_hp", 3))
        assertEquals("¡Ganaste 10 de Oro!", bundle.format("log.gained_gold", 10))
        assertEquals("¡Toda la Deuda quedó saldada!", bundle.get("log.debt_wiped"))
        assertEquals(
            "¡Escudo de Garantía activo: la Deuda por préstamos se reduce a la mitad este combate!",
            bundle.get("log.escrow_shield_active")
        )
    }

    @Test
    fun `English intent display keys resolve with real interpolation`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("Attack 5", bundle.format("intent.attack", 5))
        assertEquals("Attack 12", bundle.format("intent.attack", 12))
        assertEquals("Buff Strength +3", bundle.format("intent.buff", 3))
        assertEquals("Debuff Weak 2", bundle.format("intent.debuff", 2))
        assertEquals("Multi Attack 4 x3", bundle.format("intent.multi_attack", 4, 3))
    }

    @Test
    fun `Spanish intent display keys resolve with real interpolation and thematic translations`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("Ataque 5", bundle.format("intent.attack", 5))
        assertEquals("Ataque 12", bundle.format("intent.attack", 12))
        assertEquals("Mejora: Fuerza +3", bundle.format("intent.buff", 3))
        assertEquals("Perjuicio: Debilidad 2", bundle.format("intent.debuff", 2))
        assertEquals("Ataque Múltiple 4 x3", bundle.format("intent.multi_attack", 4, 3))
    }
}
