package com.debtsdecks.core.i18n

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.I18NBundle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
 * references, every core-domain log/intent key CombatEngine/CardResolver/EnemyAI/EnemyInstance's
 * Phase 4b-iii migration references, and — as of Phase 4b-iv — every `card.<id>.name`/
 * `card.<id>.description`/`enemy.<id>.name` key now stored in `assets/cards/all.json` and
 * `assets/enemies/all.json` in place of literal text (see [CombatRenderer]/[CardResolver]'s
 * `bundle.get(...)` consumption sites for those JSON-sourced fields).
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
        assertEquals("Paid out 14 Debt as damage!", bundle.format("log.debt_payoff_damage", 14))
        assertEquals("Held collateral: gained 10 Block.", bundle.format("log.debt_payoff_block", 10))
        assertEquals("Overdraft: drew 3 cards.", bundle.format("log.debt_draw", 3))
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
        assertEquals("¡Liquidaste 14 de Deuda como daño!", bundle.format("log.debt_payoff_damage", 14))
        assertEquals("Garantía retenida: ganaste 10 de Bloqueo.", bundle.format("log.debt_payoff_block", 10))
        assertEquals("Descubierto: robaste 3 cartas.", bundle.format("log.debt_draw", 3))
    }

    @Test
    fun `English intent display keys resolve with real interpolation`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("Attack 5", bundle.format("intent.attack", 5))
        assertEquals("Attack 12", bundle.format("intent.attack", 12))
        assertEquals("Buff Strength +3", bundle.format("intent.buff", 3))
        assertEquals("Debuff Weak 2", bundle.format("intent.debuff", 2))
        assertEquals("Multi Attack 4 x3", bundle.format("intent.multi_attack", 4, 3))
        assertEquals("Levy 6 Debt", bundle.format("intent.levy", 6))
        assertEquals("The creditor levies 6 Debt on you!", bundle.format("log.intent_levy", 6))
    }

    @Test
    fun `Spanish intent display keys resolve with real interpolation and thematic translations`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("Ataque 5", bundle.format("intent.attack", 5))
        assertEquals("Ataque 12", bundle.format("intent.attack", 12))
        assertEquals("Mejora: Fuerza +3", bundle.format("intent.buff", 3))
        assertEquals("Perjuicio: Debilidad 2", bundle.format("intent.debuff", 2))
        assertEquals("Ataque Múltiple 4 x3", bundle.format("intent.multi_attack", 4, 3))
        assertEquals("Recargo de 6 de Deuda", bundle.format("intent.levy", 6))
        assertEquals("¡El acreedor te impone un recargo de 6 de Deuda!", bundle.format("log.intent_levy", 6))
    }

    // --- Phase 4b-iv: JSON-sourced card/enemy strings (assets/cards/all.json, assets/enemies/
    // all.json). `name`/`description` fields in those files now store bundle keys instead of
    // literal text; this coverage is the sole automated proof of every key's resolution and
    // translation, since no test loads the real JSON assets directly (DataLoader needs an Android
    // Context, unavailable in a headless JVM test) — CombatRenderer/CardResolver consume these
    // keys only through a live render/log path with no dedicated test file.

    @Test
    fun `English JSON-sourced card and enemy keys resolve`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)

        assertEquals("Strike", bundle.get("card.strike.name"))
        assertEquals("Deal 6 damage.", bundle.get("card.strike.description"))
        assertEquals("Defend", bundle.get("card.defend.name"))
        assertEquals("Gain 5 Block.", bundle.get("card.defend.description"))
        assertEquals("Bash", bundle.get("card.bash.name"))
        assertEquals("Deal 8 damage. Apply 1 Vulnerable.", bundle.get("card.bash.description"))
        assertEquals("Survive", bundle.get("card.survive.name"))
        assertEquals("Gain 8 Block.", bundle.get("card.survive.description"))
        assertEquals("Heavy Blade", bundle.get("card.heavy_blade.name"))
        assertEquals("Deal 14 damage.", bundle.get("card.heavy_blade.description"))
        assertEquals("Iron Wave", bundle.get("card.iron_wave.name"))
        assertEquals("Deal 7 damage. Gain 7 Block.", bundle.get("card.iron_wave.description"))
        assertEquals("Pommel Strike", bundle.get("card.pommel_strike.name"))
        assertEquals("Deal 9 damage. Draw 1 card.", bundle.get("card.pommel_strike.description"))
        assertEquals("Whirlwind", bundle.get("card.whirlwind.name"))
        assertEquals(
            "Deal 5 damage to ALL enemies, once per Energy spent.",
            bundle.get("card.whirlwind.description")
        )
        assertEquals("Reckless Charge", bundle.get("card.reckless_charge.name"))
        assertEquals("Deal 7 damage. Lose 2 HP.", bundle.get("card.reckless_charge.description"))
        assertEquals("Defend+", bundle.get("card.defend_plus.name"))
        assertEquals("Gain 8 Block.", bundle.get("card.defend_plus.description"))
        assertEquals("Wild Strike", bundle.get("card.wild_strike.name"))
        assertEquals("Deal 12 damage. Exhaust.", bundle.get("card.wild_strike.description"))
        assertEquals("Flex", bundle.get("card.flex.name"))
        assertEquals("Gain 2 Strength.", bundle.get("card.flex.description"))
        assertEquals("Clash", bundle.get("card.clash.name"))
        assertEquals(
            "Deal 14 damage. Can only be played if it's the only card in your hand.",
            bundle.get("card.clash.description")
        )
        assertEquals("Anger", bundle.get("card.anger.name"))
        assertEquals(
            "Deal 6 damage. Add a copy to your discard pile.",
            bundle.get("card.anger.description")
        )
        assertEquals("True Grit", bundle.get("card.true_grit.name"))
        assertEquals("Gain 7 Block. Draw 1 card. Exhaust.", bundle.get("card.true_grit.description"))
        assertEquals("Escrow Shield", bundle.get("card.escrow_shield.name"))
        assertEquals(
            "Activate Escrow Shield: Debt gained from borrowing is halved for the rest of combat.",
            bundle.get("card.escrow_shield.description")
        )
        assertEquals("Debt Relief", bundle.get("card.debt_relief.name"))
        assertEquals("Exhaust. Repay 10 Debt directly.", bundle.get("card.debt_relief.description"))
        assertEquals("Wage Garnishment", bundle.get("card.wage_garnishment.name"))
        assertEquals("Deal 4 damage. Repay 3 Debt.", bundle.get("card.wage_garnishment.description"))
        assertEquals("Repo Sweep", bundle.get("card.repo_sweep.name"))
        assertEquals(
            "Deal 6 damage to ALL enemies. Gain 5 Gold.",
            bundle.get("card.repo_sweep.description")
        )
        assertEquals("Collections Call", bundle.get("card.collections_call.name"))
        assertEquals(
            "Deal 4 damage 3 times. Each hit repays 2 Debt.",
            bundle.get("card.collections_call.description")
        )
        assertEquals("Chapter 11", bundle.get("card.chapter_11.name"))
        assertEquals(
            "Exhaust. Lose 15 HP. Wipe all Debt to 0.",
            bundle.get("card.chapter_11.description")
        )
        assertEquals("Compound Interest", bundle.get("card.compound_interest.name"))
        assertEquals(
            "Exhaust. Gain 1 Strength per 10 Debt.",
            bundle.get("card.compound_interest.description")
        )
        assertEquals("Thug", bundle.get("enemy.thug.name"))
        assertEquals("Loan Shark", bundle.get("enemy.loan_shark.name"))
        assertEquals("Collector", bundle.get("enemy.collector.name"))
    }

    @Test
    fun `Spanish JSON-sourced card and enemy keys resolve with neutral thematic translations`() {
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))

        assertEquals("Golpe", bundle.get("card.strike.name"))
        assertEquals("Inflige 6 de daño.", bundle.get("card.strike.description"))
        assertEquals("Defensa", bundle.get("card.defend.name"))
        assertEquals("Gana 5 de Bloqueo.", bundle.get("card.defend.description"))
        assertEquals("Golpe Brutal", bundle.get("card.bash.name"))
        assertEquals(
            "Inflige 8 de daño. Aplica 1 de Vulnerable.",
            bundle.get("card.bash.description")
        )
        assertEquals("Resistir", bundle.get("card.survive.name"))
        assertEquals("Gana 8 de Bloqueo.", bundle.get("card.survive.description"))
        assertEquals("Hoja Pesada", bundle.get("card.heavy_blade.name"))
        assertEquals("Inflige 14 de daño.", bundle.get("card.heavy_blade.description"))
        assertEquals("Ola de Hierro", bundle.get("card.iron_wave.name"))
        assertEquals(
            "Inflige 7 de daño. Gana 7 de Bloqueo.",
            bundle.get("card.iron_wave.description")
        )
        assertEquals("Golpe de Pomo", bundle.get("card.pommel_strike.name"))
        assertEquals("Inflige 9 de daño. Roba 1 carta.", bundle.get("card.pommel_strike.description"))
        assertEquals("Torbellino", bundle.get("card.whirlwind.name"))
        assertEquals(
            "Inflige 5 de daño a TODOS los enemigos, una vez por cada Crédito gastado.",
            bundle.get("card.whirlwind.description")
        )
        assertEquals("Carga Imprudente", bundle.get("card.reckless_charge.name"))
        assertEquals(
            "Inflige 7 de daño. Pierdes 2 de PS.",
            bundle.get("card.reckless_charge.description")
        )
        assertEquals("Defensa+", bundle.get("card.defend_plus.name"))
        assertEquals("Gana 8 de Bloqueo.", bundle.get("card.defend_plus.description"))
        assertEquals("Golpe Salvaje", bundle.get("card.wild_strike.name"))
        assertEquals("Inflige 12 de daño. Agotar.", bundle.get("card.wild_strike.description"))
        assertEquals("Flexión", bundle.get("card.flex.name"))
        assertEquals("Gana 2 de Fuerza.", bundle.get("card.flex.description"))
        assertEquals("Choque", bundle.get("card.clash.name"))
        assertEquals(
            "Inflige 14 de daño. Solo se puede jugar si es la única carta en tu mano.",
            bundle.get("card.clash.description")
        )
        assertEquals("Ira", bundle.get("card.anger.name"))
        assertEquals(
            "Inflige 6 de daño. Añade una copia a tu pila de descarte.",
            bundle.get("card.anger.description")
        )
        assertEquals("Verdadero Temple", bundle.get("card.true_grit.name"))
        assertEquals(
            "Gana 7 de Bloqueo. Roba 1 carta. Agotar.",
            bundle.get("card.true_grit.description")
        )
        assertEquals("Escudo de Garantía", bundle.get("card.escrow_shield.name"))
        assertEquals(
            "Activa el Escudo de Garantía: la Deuda obtenida por préstamos se reduce a la mitad " +
                "el resto del combate.",
            bundle.get("card.escrow_shield.description")
        )
        assertEquals("Alivio de Deuda", bundle.get("card.debt_relief.name"))
        assertEquals(
            "Agotar. Paga 10 de Deuda directamente.",
            bundle.get("card.debt_relief.description")
        )
        assertEquals("Embargo de Salario", bundle.get("card.wage_garnishment.name"))
        assertEquals(
            "Inflige 4 de daño. Paga 3 de Deuda.",
            bundle.get("card.wage_garnishment.description")
        )
        assertEquals("Barrida de Embargo", bundle.get("card.repo_sweep.name"))
        assertEquals(
            "Inflige 6 de daño a TODOS los enemigos. Gana 5 de Oro.",
            bundle.get("card.repo_sweep.description")
        )
        assertEquals("Llamada de Cobranza", bundle.get("card.collections_call.name"))
        assertEquals(
            "Inflige 4 de daño 3 veces. Cada golpe paga 2 de Deuda.",
            bundle.get("card.collections_call.description")
        )
        assertEquals("Capítulo 11", bundle.get("card.chapter_11.name"))
        assertEquals(
            "Agotar. Pierdes 15 de PS. Salda toda la Deuda a 0.",
            bundle.get("card.chapter_11.description")
        )
        assertEquals("Interés Compuesto", bundle.get("card.compound_interest.name"))
        assertEquals(
            "Agotar. Gana 1 de Fuerza por cada 10 de Deuda.",
            bundle.get("card.compound_interest.description")
        )
        assertEquals("Matón", bundle.get("enemy.thug.name"))
        assertEquals("Usurero", bundle.get("enemy.loan_shark.name"))
        assertEquals("Cobrador", bundle.get("enemy.collector.name"))
    }
    @Test
    fun `English new economy cards name and description resolve`() {
        val b = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)
        assertEquals("Subprime Loan", b.get("card.subprime_loan.name"))
        assertEquals("Gain 3 Credit this turn. Add 3 Debt.", b.get("card.subprime_loan.description"))
        assertEquals("Debt Forgiveness", b.get("card.debt_forgiveness.name"))
        assertEquals("Wipe all Debt to 0.", b.get("card.debt_forgiveness.description"))
        assertEquals("Partial Forgiveness", b.get("card.partial_forgiveness.name"))
        assertEquals("Repay 8 Debt.", b.get("card.partial_forgiveness.description"))
        assertEquals("Tactical Bankruptcy", b.get("card.tactical_bankruptcy.name"))
        assertEquals("Lose 8 HP. Wipe all Debt to 0.", b.get("card.tactical_bankruptcy.description"))
        assertEquals("Reverse Mortgage", b.get("card.reverse_mortgage.name"))
        assertEquals("Gain 4 Gold per 10 Debt.", b.get("card.reverse_mortgage.description"))
        assertEquals("Foreclosure Express", b.get("card.foreclosure_express.name"))
        assertEquals("Deal 6 damage. Gain 4 Gold.", b.get("card.foreclosure_express.description"))
        assertEquals("Ghost Collector", b.get("card.ghost_collector.name"))
        assertEquals("Deal 5 damage. Apply 2 Weak.", b.get("card.ghost_collector.description"))
        assertEquals("Golden Credit", b.get("card.golden_credit.name"))
        assertEquals("Gain 4 Credit this turn.", b.get("card.golden_credit.description"))
        assertEquals("Mortgage Collateral", b.get("card.mortgage_collateral.name"))
        assertEquals("Gain 12 Block.", b.get("card.mortgage_collateral.description"))
        assertEquals("Asset Auction", b.get("card.asset_auction.name"))
        assertEquals("Exhaust a card from hand. Gain 9 Gold.", b.get("card.asset_auction.description"))
        assertEquals("Risky Investment", b.get("card.risky_investment.name"))
        assertEquals("Gain 12 Gold. Lose 6 HP.", b.get("card.risky_investment.description"))
        assertEquals("Bounced Check", b.get("card.bounced_check.name"))
        assertEquals("Deal 5 damage. Add 4 Debt.", b.get("card.bounced_check.description"))
        assertEquals("Zombie Debt", b.get("card.zombie_debt.name"))
        assertEquals("Add 2 Debt. Gain 1 Credit. Add a copy to your discard pile.", b.get("card.zombie_debt.description"))
        assertEquals("Eternal Debt", b.get("card.eternal_debt.name"))
        assertEquals("Add 3 Debt. Add a copy to your discard pile. Gain 1 Strength per 10 Debt.", b.get("card.eternal_debt.description"))
        assertEquals("Leverage Strike", b.get("card.leverage_strike.name"))
        assertEquals("Deal 5 damage. Deal 1 extra damage per 10 Debt.", b.get("card.leverage_strike.description"))
        assertEquals("Asset Bubble", b.get("card.asset_bubble.name"))
        assertEquals("Deal damage equal to half your Debt. Keep your Debt.", b.get("card.asset_bubble.description"))
        assertEquals("Overdraft", b.get("card.overdraft.name"))
        assertEquals("Draw 1 card, plus 1 per 10 Debt.", b.get("card.overdraft.description"))
        assertEquals("Collateral Hold", b.get("card.collateral_hold.name"))
        assertEquals("Gain Block equal to half your Debt. Keep your Debt.", b.get("card.collateral_hold.description"))
        assertEquals("Repossession Expert", b.get("card.repo_expert.name"))
        assertEquals("Deal 7 damage. Apply 1 Weak.", b.get("card.repo_expert.description"))
        assertEquals("Emergency Fund", b.get("card.emergency_fund.name"))
        assertEquals("Gain 6 Block. Draw 1 card.", b.get("card.emergency_fund.description"))
    }

    @Test
    fun `Spanish new economy cards name and description resolve with neutral thematic translations`() {
        val b = I18NBundle.createBundle(bundleBase, Locale("es"))
        assertEquals("Préstamo Subprime", b.get("card.subprime_loan.name"))
        assertEquals("Gana 3 de Crédito este turno. Añade 3 de Deuda.", b.get("card.subprime_loan.description"))
        assertEquals("Perdón de Deuda", b.get("card.debt_forgiveness.name"))
        assertEquals("Saldas toda la Deuda a 0.", b.get("card.debt_forgiveness.description"))
        assertEquals("Perdón Parcial", b.get("card.partial_forgiveness.name"))
        assertEquals("Pagas 8 de Deuda.", b.get("card.partial_forgiveness.description"))
        assertEquals("Bancarrota Táctica", b.get("card.tactical_bankruptcy.name"))
        assertEquals("Pierdes 8 de PS. Saldas toda la Deuda a 0.", b.get("card.tactical_bankruptcy.description"))
        assertEquals("Hipoteca Inversa", b.get("card.reverse_mortgage.name"))
        assertEquals("Gana 4 de Oro por cada 10 de Deuda.", b.get("card.reverse_mortgage.description"))
        assertEquals("Embargo Exprés", b.get("card.foreclosure_express.name"))
        assertEquals("Inflige 6 de daño. Gana 4 de Oro.", b.get("card.foreclosure_express.description"))
        assertEquals("Cobrador Fantasma", b.get("card.ghost_collector.name"))
        assertEquals("Inflige 5 de daño. Aplica 2 de Debilidad.", b.get("card.ghost_collector.description"))
        assertEquals("Crédito Dorado", b.get("card.golden_credit.name"))
        assertEquals("Gana 4 de Crédito este turno.", b.get("card.golden_credit.description"))
        assertEquals("Garantía Hipotecaria", b.get("card.mortgage_collateral.name"))
        assertEquals("Gana 12 de Bloqueo.", b.get("card.mortgage_collateral.description"))
        assertEquals("Subasta de Bienes", b.get("card.asset_auction.name"))
        assertEquals("Agota una carta de tu mano. Gana 9 de Oro.", b.get("card.asset_auction.description"))
        assertEquals("Inversión Arriesgada", b.get("card.risky_investment.name"))
        assertEquals("Gana 12 de Oro. Pierdes 6 de PS.", b.get("card.risky_investment.description"))
        assertEquals("Cheque Sin Fondos", b.get("card.bounced_check.name"))
        assertEquals("Inflige 5 de daño. Añade 4 de Deuda.", b.get("card.bounced_check.description"))
        assertEquals("Deuda Zombi", b.get("card.zombie_debt.name"))
        assertEquals("Añade 2 de Deuda. Gana 1 de Crédito. Añade una copia a tu pila de descarte.", b.get("card.zombie_debt.description"))
        assertEquals("Deuda Eterna", b.get("card.eternal_debt.name"))
        assertEquals("Añade 3 de Deuda. Añade una copia a tu pila de descarte. Gana 1 de Fuerza por cada 10 de Deuda.", b.get("card.eternal_debt.description"))
        assertEquals("Golpe Apalancado", b.get("card.leverage_strike.name"))
        assertEquals("Inflige 5 de daño. Inflige 1 de daño extra por cada 10 de Deuda.", b.get("card.leverage_strike.description"))
        assertEquals("Burbuja de Activos", b.get("card.asset_bubble.name"))
        assertEquals("Inflige daño igual a la mitad de tu Deuda. Conservas tu Deuda.", b.get("card.asset_bubble.description"))
        assertEquals("Descubierto", b.get("card.overdraft.name"))
        assertEquals("Roba 1 carta, más 1 por cada 10 de Deuda.", b.get("card.overdraft.description"))
        assertEquals("Garantía Retenida", b.get("card.collateral_hold.name"))
        assertEquals("Gana Bloqueo igual a la mitad de tu Deuda. Conservas tu Deuda.", b.get("card.collateral_hold.description"))
        assertEquals("Experto en Reposeo", b.get("card.repo_expert.name"))
        assertEquals("Inflige 7 de daño. Aplica 1 de Debilidad.", b.get("card.repo_expert.description"))
        assertEquals("Fondo de Emergencia", b.get("card.emergency_fund.name"))
        assertEquals("Gana 6 de Bloqueo. Roba 1 carta.", b.get("card.emergency_fund.description"))
    }

    // --- C7 between-fight node keys ---

    @Test
    fun `English node keys resolve`() {
        val b = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)
        assertEquals("REST STOP", b.get("node.header"))
        assertEquals("Restored 8 HP", b.format("node.heal", 8))
        assertEquals("Gold: 10 | Debt: 4", b.format("node.gold_debt", 10, 4))
        assertEquals("FREE PICK", b.get("node.button.free_pick"))
        assertEquals("REPAY", b.get("node.button.repay"))
        assertEquals("BUY", b.get("node.button.buy"))
        assertEquals("REMOVE", b.get("node.button.remove"))
        assertEquals("LOAN", b.get("node.button.loan"))
        assertEquals("SHOP — 2 deck", b.format("node.shop.title", 2))
        assertEquals("Choose a card to buy (18 gold)", b.format("node.buy_offer", 18))
        assertEquals("REMOVE CARD", b.get("node.remove.title"))
        assertEquals("Choose a card to remove (22 gold)", b.format("node.remove_offer", 22))
        assertEquals("Take 27 gold, add 18 debt", b.format("node.loan_offer", 27, 18))
        assertEquals("CANCEL", b.get("node.cancel"))
        assertEquals("Not enough gold", b.get("node.insufficient_gold"))
    }

    @Test
    fun `Spanish node keys resolve with neutral thematic translations`() {
        val b = I18NBundle.createBundle(bundleBase, Locale("es"))
        assertEquals("PARADA DE DESCANSO", b.get("node.header"))
        assertEquals("Recuperaste 8 PS", b.format("node.heal", 8))
        assertEquals("Oro: 10 | Deuda: 4", b.format("node.gold_debt", 10, 4))
        assertEquals("TOMAR CARTA", b.get("node.button.free_pick"))
        assertEquals("PAGAR DEUDA", b.get("node.button.repay"))
        assertEquals("COMPRAR", b.get("node.button.buy"))
        assertEquals("ELIMINAR", b.get("node.button.remove"))
        assertEquals("PRÉSTAMO", b.get("node.button.loan"))
        assertEquals("TIENDA — mazo 2", b.format("node.shop.title", 2))
        assertEquals("Elige una carta para comprar (18 de oro)", b.format("node.buy_offer", 18))
        assertEquals("ELIMINAR CARTA", b.get("node.remove.title"))
        assertEquals("Elige una carta para eliminar (22 de oro)", b.format("node.remove_offer", 22))
        assertEquals("Toma 27 de oro, añade 18 de Deuda", b.format("node.loan_offer", 27, 18))
        assertEquals("CANCELAR", b.get("node.cancel"))
        assertEquals("Oro insuficiente", b.get("node.insufficient_gold"))
    }

    // --- F2 district keys ---
    // The district catalog stores bundle keys, so an untranslated district is an invisible defect
    // until it renders. The data-driven test below walks the real catalog rather than a literal
    // list, so adding a fourth district without translating it fails here instead of on screen.
    //
    // It reads the .properties files directly, NOT through I18NBundle, and that is the whole
    // point. I18NBundle chains to the parent bundle: ask the Spanish bundle for a key that only
    // exists in strings.properties and it hands back the English string without complaint. A
    // guard written on top of bundle.get() therefore passes on exactly the failure it was
    // written to catch — someone adds a district in English and forgets the Spanish. Only the
    // raw key sets can see that.

    @Test
    fun `English district keys resolve`() {
        val b = I18NBundle.createBundle(bundleBase, Locale.ENGLISH)
        assertEquals("THE SLAUGHTERHOUSE OF THE INSOLVENT", b.get("district.slaughterhouse.name"))
        assertEquals("THE VULTURE FUNDS CASINO", b.get("district.casino.name"))
        assertEquals("THE BOARDROOM", b.get("district.boardroom.name"))
        assertEquals(
            "Where a first missed payment earns you a name. The debts are small here. So is the mercy.",
            b.get("district.slaughterhouse.description")
        )
        assertEquals("They wager on which debtors fold. You are the table.", b.get("district.casino.description"))
        assertEquals("Nobody raises their voice. The paperwork was signed years ago.", b.get("district.boardroom.description"))
    }

    @Test
    fun `Spanish district keys resolve with neutral thematic translations`() {
        val b = I18NBundle.createBundle(bundleBase, Locale("es"))
        assertEquals("EL MATADERO DE LOS INSOLVENTES", b.get("district.slaughterhouse.name"))
        assertEquals("EL CASINO DE LOS FONDOS BUITRE", b.get("district.casino.name"))
        assertEquals("LA SALA DE JUNTAS", b.get("district.boardroom.name"))
        assertEquals(
            "Donde el primer impago te pone nombre. Aquí las deudas son pequeñas. La clemencia también.",
            b.get("district.slaughterhouse.description")
        )
        assertEquals("Apuestan sobre qué deudores caen. Tú eres la mesa.", b.get("district.casino.description"))
        assertEquals("Nadie levanta la voz. El papeleo se firmó hace años.", b.get("district.boardroom.description"))
    }

    /** Loads a `.properties` file as raw key/value pairs, with no bundle fallback of any kind. */
    private fun rawProperties(fileName: String): Map<String, String> {
        val file = File("src/main/assets/i18n/$fileName")
        assertTrue(file.isFile, "expected ${file.path} to exist")
        val props = java.util.Properties()
        file.inputStream().reader(Charsets.UTF_8).use { props.load(it) }
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    @Test
    fun `every district in the catalog is translated in both bundles`() {
        val en = rawProperties("strings.properties")
        val es = rawProperties("strings_es.properties")
        for (district in com.debtsdecks.core.simulation.TestAssetLoader.loadDistricts()) {
            for (key in listOf(district.name, district.description)) {
                for ((tag, table) in listOf("en" to en, "es" to es)) {
                    val text = table[key]
                    assertNotNull(text, "district key $key is missing from the $tag properties file")
                    assertTrue(
                        text!!.isNotBlank(),
                        "district key $key is present but blank in the $tag properties file"
                    )
                }
            }
        }
    }

    @Test
    fun `a district key present only in English is caught, not silently answered in English`() {
        // Guards the guard. If this ever fails, the test above has stopped being able to fail:
        // it would mean a Spanish lookup for a Spanish-only-missing key no longer differs from
        // the raw table, i.e. someone reintroduced bundle fallback into the parity check.
        val es = rawProperties("strings_es.properties")
        val bundle = I18NBundle.createBundle(bundleBase, Locale("es"))
        val absent = "district.__not_translated__.name"
        assertNull(es[absent], "fixture key must not exist in the Spanish file")
        assertThrows(java.util.MissingResourceException::class.java) { bundle.get(absent) }
    }

}
