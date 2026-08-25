package com.debtsdecks.core.i18n

/**
 * Core-side localization abstraction. Keeps `core/` free of any `com.badlogic.gdx.*` import per
 * docs/CONVENTIONS.md Architecture Rule #1 ("Core is pure Kotlin — no Android, no LibGDX, no
 * platform deps"). The three core-domain classes that produce player-facing strings
 * (`CombatEngine`, `CardResolver`, `EnemyInstance`/`EnemyAI`) depend only on this interface; the
 * GDX-backed implementation, [com.debtsdecks.gdx.i18n.BundleLocalizer], lives outside `core/` and
 * delegates to LibGDX `I18NBundle`.
 */
interface Localizer {
    fun get(key: String): String

    /**
     * Formats [key] with [args] using the same `MessageFormat`-style `{0}`/`{1}` placeholders that
     * LibGDX `I18NBundle.format` uses (verified via bytecode during the i18n migration —
     * `I18NBundle.simpleFormatter` defaults to `false`).
     */
    fun format(key: String, vararg args: Any?): String
}
