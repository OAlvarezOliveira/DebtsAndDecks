---
name: debtsanddecks
description: "Trigger: any work in the Debts & Decks repo (Android/LibGDX roguelike deck-builder), Kotlin files, cards/enemies all.json, i18n keys, economy/debt effects, or SDD apply/refactor tasks. Enforce repo conventions and harness gotchas: core purity via Localizer, 4-space indent, direct gradle binary, TestI18n, EN/ES thematic no-voseo."
---

# Debts & Decks — Repo Conventions & Harness Gotchas

Follow `docs/CONVENTIONS.md` for full style. These are the hard rules and non-obvious gotchas.

## Repository invariants
- **Core is pure Kotlin.** Never add `com.badlogic.gdx.*` imports to anything under `core/`. All player-facing strings in core go through the `Localizer` interface (`core/i18n/Localizer.kt`); the GDX adapter `gdx/i18n/BundleLocalizer.kt` lives outside core. Verify with `grep -rn "com.badlogic" core/` → must be empty.
- **Indent = 4 spaces** (no tabs). The `edit` tool can fail to match text here because reads may render as 8 spaces; prefer anchored python replace when `edit` oldText won't match.
- **Kotlin data is JSON**: cards `assets/cards/all.json`, enemies `assets/enemies/all.json`. Card `name`/`description` and enemy `name` store **bundle keys**, not literal text.
- Conventional commits, no AI attribution, no "Co-Authored-By". Types: feat/fix/refactor/balance/content/docs/chore/wip.

## i18n / localization
- Localize via `localizer.get(key)` / `localizer.format(key, args)`. `I18NBundle.format` uses `MessageFormat` `{0}` placeholders (not `simpleFormatter`).
- **EN/ES thematic, neutral Spanish, no voseo.** Tone is debt/collections/finance, not literal translation.
- Tests: `I18nBundleTest` builds `I18NBundle` directly for per-key coverage (both locales). Domain fixtures use `TestI18n.testLocalizer()` (returns a `Localizer`).
- Core saves nothing raw: no bundle-key leaks reach logs/UI.

## Build & tests (headless harness)
- Use the cached gradle binary directly — `~/.gradle/wrapper/dists/gradle-8.9-bin/<hash>/gradle-8.9/bin/gradle --no-daemon :app:testDebugUnitTest --tests "<FQCN>"`. Do NOT rely on `./gradlew`. Harmless daemon-socket stderr noise ("Unexpected type tag 71") can be ignored.
- Strict TDD: RED → GREEN → TRIANGULATE → REFACTOR for every task; deterministic `Random(seed)`; assert concrete expected values (no tautologies).
- No headless GL harness: `CombatRenderer`/`GameScreen` rendering and per-card art are verified by build + manual playtest (disclosed gap), not unit tests.
- Baseline suite: **220 tests, 0 failures, 2 skipped (23 classes)** as of 2026-08-28 (`develop` @ `ea0b4a6`). The 2 skipped are deliberate `@Disabled` F2 gates in `DebtPressureTest.kt`, expected to fail on trunk — leave them alone. Tier-ordering regression test guards the 3-roster stat invariant. Count it, do not trust this line: `./gradlew testDebugUnitTest` then sum `tests`/`failures` over `app/build/test-results/testDebugUnitTest/TEST-*.xml` (quiet mode prints nothing on success).

## Economy & cards
- Reward pool = the 15 economy cards; `starter`-tagged cards never offered as rewards.
- New player economy state (Debt/Gold/Credit) is owned by `CombatEngine` (single source of truth).
- Boss economies the player: `IntentType.LEVY` on collector raises player Debt via `CombatEngine.endPlayerTurn`. Escrow Shield halves only Credit-shortfall debt, **not** boss/card-applied debt.
- `CardResolver` reads `state.debt` for debt-scaled effects (e.g. Reverse Mortgage).

## Git & delivery
- Stacked branches from `develop` (repo has no `main`); each PR under 400 changed lines; local commits only unless the user explicitly authorizes push/PR.
- **Git commit is lifecycle-gated in this harness** (fail-closed on "compound/wrapped lifecycle command"). Route commits through the sanctioned review path; never force a commit that fail-closes.