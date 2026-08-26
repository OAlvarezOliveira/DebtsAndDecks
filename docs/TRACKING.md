# Project Tracking — Debts & Decks

> Single source of truth for tasks, blockers, decisions. Update **daily** at end of session.

---

## Current Sprint: Play Store launch (`play-store-launch`, phases P0–P9)

**Goal:** Take the shipped Debt-as-Leverage combat loop to a published Play Store release.

**Status:** The MVP combat loop and the Debt-as-Leverage pivot have **shipped** — C2 `debt-as-leverage`
(`0fb163b`) and C4 `leverage-payoff-cards` (`57b11c2`) are merged, and the combat-progression/i18n
stack is delivered. The active programme is `play-store-launch`: P0 resyncs the docs with verified
code state (this entry), P1 modernizes the platform baseline, later phases cover playtest, balance,
signing, store listing and closed testing. See **BD-1** below — v1 ships free.

---

## Task Board

### To Do
- [ ] **Polish** Manual playtest on device/emulator: full loop + economy feel
- [ ] **Polish** Balance pass from playtest (debt pressure, boss LEVY, reward pool)
- [ ] **Build** APK installs and runs on device (target < 20 MB)
- [ ] **Measure** Metrics dashboard below (combat duration, win rate, FPS, launch time)

### Doing
- [ ] `play-store-launch` P1 `platform-baseline` (platform modernization — not started here)

### Done
- [x] **Delivery** Commit debt-economy work (delivered — `38d584d`, `5f0cc3e`, `d07196b`; verified 2026-08-27 via `git log`)
- [x] **Delivery** Merge combat-progression-i18n stack (pr1–pr7, 8 commits) into `develop`
- [x] **Setup** Gradle project with LibGDX + Kotlin + Serialization + Koin
- [x] **Core** CombatEngine with turn orchestration
- [x] **Core** Card system: definitions, registry, resolution pipeline
- [x] **Core** Enemy AI: intent pattern, execution
- [x] **Core** State model + immutable snapshots
- [x] **Core** Debt/Gold/Credit economy + garnishment + threshold-break encounter (`DebtConfig`)
- [x] **Core** Enemy tiers with tag-driven mechanics and per-tier rewards
- [x] **Core** Player HP persisted through `startCombat` across encounters
- [x] **Core** `Localizer` interface keeps `core/` pure Kotlin (no GDX imports)
- [x] **Data** Card JSON: 23 reward-pool cards + 4 starters = 27 total; Enemy JSON: 3 enemies (measured 2026-08-27 from `all.json` / `enemies/*.json`)
- [x] **Data** DataLoader + JSON i18n wiring (bundle keys in data, literals via `I18NBundle`)
- [x] **GDX** GameScreen + basic render loop
- [x] **GDX** CombatRenderer: HP, block, energy, hand, enemy intents, reward/end screens
- [x] **GDX** CombatInputHandler: card select → target → play + end turn
- [x] **GDX** SoundManager (5 SFX wired: card play/select, end turn, victory, defeat)
- [x] **GDX** Launcher icon + themed resources (`res/`)
- [x] **Integration** Wire Core ↔ GDX via DI (Module.kt), MainActivity launches GameScreen
- [x] **Polish** Win/Lose screens
- [x] **Polish** Card reward screen (pick 1 of 3; pool = 23 non-starter cards, starters excluded)
- [x] **Test** Unit tests: CombatEngine, CardResolver, CardInstance, EnemyInstance, RunManager, DebtConfig, EnemyTierRegression, I18nBundle, LeveragePayoffCardsData, RunSimulationHarness (124/124 green across 10 classes, measured 2026-08-27 via `:app:testDebugUnitTest`)
- [x] **SDD** Change `debt-economy-cards-and-boss-interest`: verify PASS (7/7 requirements, 12/12 scenarios) — delivered (`38d584d`)
- [x] **SDD** Change `combat-progression-and-i18n`: verify completed (core-purity CRITICAL resolved via `Localizer`)
- [x] **SDD** Change C1 `run-simulation-harness`: headless balance simulator (`dc65f08`, `9615b5e`) — `RunSimulationHarnessTest`, `RunSimulator`, `LeveragePolicy` under `core/simulation/`
- [x] **SDD** Change C2 `debt-as-leverage`: leverage, execution and liquidation (`0fb163b`)
- [x] **SDD** Change C4 `leverage-payoff-cards`: 6 new cards, 3 reworks, 3 resolver primitives (`57b11c2`)
- [x] **Assets** 19 card arts + 3 enemy arts + card frames + intent icons (`assets/art/`) — 8 of 27 cards still have no art

---

## Daily Log

### 2026-08-27 (Session 3 — `play-store-launch` P0 `docs-resync-current-state`)
**Goal:** Make every claim in `GDD.md`, `TRACKING.md`, `HARNESS.md` and `README.md` match verified code
state, so the launch programme starts from facts instead of stale prose.
**Done:**
- Measured, not copied, the real baselines: **124 unit tests green across 10 classes**, 27 cards
  (4 starters + 23 reward pool), 3 enemies, 19 card-art files all genuine PNG. Three different test
  counts were in circulation across HARNESS.md, TRACKING.md and an older Engram note — **all three
  were wrong**, which is why the number above was measured rather than copied.
- GDD: deleted the two constants that do not exist in code (`USURY_HP_RATIO`, `REPAY_DISCARD_VALUE`),
  listed all 10 that do, documented `EXECUTION_THRESHOLD = 50` and why it sits above
  `BREAK_THRESHOLD = 30`, retitled the card pool 19 → 27, marked Part 2 as shipped, and added a
  Status column to the C0–C9 table.
- Confirmed by `rg` that the free in-combat Debt valve (`RepayMode`, `repayDebt`,
  `REPAY_DISCARD_VALUE`, `USURY_HP_RATIO`) is **gone from `app/src/`** — removed by
  `9afd532`. C3 `remove-free-debt-valve` is therefore recorded as **NEEDS RE-VERIFICATION**, not
  DONE: its scope may already be satisfied, but that is a deliberate decision P0 does not make.
- Deleted the orphan `log.debt_usury` key from both locale bundles (zero code references); EN/ES
  parity holds.
- Docs-only change: no `.kt`/`.kts`/`.xml`/`.json` touched, test count identical before and after.
**Next:** P1 `platform-baseline` (Gradle/AGP/Kotlin, `compileSdk`/`targetSdk` 35+, 16 KB page size,
LibGDX version question, wrapper-jar repair). Then decide C3's real scope and give Gold a sink (C7).

### 2026-08-25 (Session 2 — delivery, fallback harness, Pi/gentle-engram)
**Goal:** Deliver the pending debt-economy work to `develop`.
**Done:**
- Optimized the 15 card images: JPEG 2048px disguised as `.png` (70 MB) → WebP 512px q82 (760 KB); originals moved to `Arts/original-2048/` (gitignored); `CombatRenderer` now loads `art/cards/<id>.webp`; suite still green (24 tasks)
  > **Correction 2026-08-27:** the entry above no longer describes the tree. Measured by magic bytes,
  > `app/src/main/assets/art/cards/` holds **19 files, all of them genuine PNG** (`\x89PNG\r\n\x1a\n`)
  > under `.png` names — zero WebP and zero JPEG. `CombatRenderer.kt` requests `art/cards/$id.png`,
  > so bytes, extension and loader all agree; there is no format mismatch to decode around. The
  > `.webp` conversion described here was **reverted the same day** by `fe281e1 fix(ui): decode card
  > art as png and guard lifecycle resume crash`, which deleted every `.webp` and restored `.png`
  > (verified 2026-08-27 with `git show --stat fe281e1`). That undid the size win: the restored card
  > PNGs are ~355–470 KB each against ~40–60 KB for the WebP versions they replaced, which is a live
  > risk to the `< 20 MB` APK target in the metrics dashboard below. The original entry is preserved
  > as written; only this note is new. Whether to re-attempt WebP (and why the PNG decode fix was
  > needed) is a P1 question, deliberately not answered here. Note also that 8 of the 27 cards (the
  > C2/C4 additions) have **no** art file at all.
- RDD disabled clone-scope (authorized `gentle-ai review mode disable --scope clone`) → delivery under ordinary repo policy; learned the Pi gate fail-closes on wrapped/compound lifecycle commands (direct command only, no `cd &&` prefix)
- 3 commits: `feat(combat)` debt economy + Localizer, `feat(art)` assets, `chore(docs)` tracking
- Fast-forward merged combat-progression-i18n stack (pr1–pr7) + the 3 new commits into `develop`; working tree clean
**Next:** Manual playtest + balance; APK build (< 20 MB); fill metrics dashboard.

### 2026-08-20 (Session)
**Goal:** Economy + combat progression + i18n foundation.
**Done:**
- `feat(combat)` Debt/Gold/Credit economy with garnishment and threshold-break encounter (merged to develop)
- Stack `feat/combat-progression-i18n` pr1–pr7: HP persistence, enemy tiers, I18NBundle infra, HUD/reward/end-screen/domain strings, JSON key wiring (local branches, not yet on develop)
- Established `.pi/` skill + registry + HARNESS structure; configured Engram project detection (`.engram/config.json`, project name `debtsanddecks`)
- Resolved sdd-verify CRITICAL: extracted pure-Kotlin `Localizer` interface in `core/`, GDX `BundleLocalizer` adapter outside; `core/` GDX-free again
- Delivered SDD change `debt-economy-cards-and-boss-interest`: 4 new card-effect primitives, LEVY boss intent, reward pool = 15 economy cards; verify PASS
**Next:** Commit debt-economy work; merge stack to develop.

### 2025-08-11 (Session 1)
**Goal:** Documentation foundation complete
**Done:**
- Created README with conventions, commit style, branch strategy
- Created GDD with core loop, cards, enemies, status effects
- Created TDD with architecture, modules, data flow
- Created CONVENTIONS (this file)
- Created ADR/0001-use-libgdx.md
- Set up directory structure
- Initialized Git repo with initial commit (feat: initial project foundation)
- Created develop branch
**Next:** Gradle project setup with LibGDX - verify build works

---

## Blockers & Decisions

| Date | Blocker/Decision | Resolution |
|------|------------------|------------|
| 2025-08-11 | Engine choice: LibGDX vs Godot vs Native | **LibGDX** — lightweight, Kotlin-first, no editor lock-in, easy Android. See ADR/0001. |
| 2025-08-11 | DI: Koin vs Hilt vs Manual | **Koin** — lightweight, Kotlin-first, no annotation processing. |
| 2025-08-11 | Serialization: Gson vs Kotlinx | **Kotlinx Serialization** — multiplatform, compile-time, Kotlin-native. |
| 2025-08-11 | State: Mutable vs Immutable | **Immutable snapshots** — Core emits CombatState, GDX renders. Easier testing, no sync issues. |
| 2026-08-20 | Core purity (sdd-verify CRITICAL) | **Localizer** — pure-Kotlin interface in `core/`, GDX `BundleLocalizer` adapter outside. `grep -rn "com.badlogic" core/` must be empty. |
| 2026-08-20 | Reward pool | Exactly the **15 economy cards**; `starter`-tagged cards never offered as rewards. — *corrected 2026-08-27: the rule (starters excluded) still holds, but the pool is now **23 cards** after C2/C4 added 8.* |
| 2026-08-20 | Player-facing strings | **Bundle keys** in JSON data; literal text only via `I18NBundle`, EN/ES thematic, neutral Spanish (no voseo). |
| 2026-08-20 | Delivery in fallback harness | `git commit` is **lifecycle-gated** (fail-closed on wrapped commands). Route commits through sanctioned review path; batch-at-end pending. |
| 2026-08-25 | Engram tooling | Server on `127.0.0.1:7437`; project name is **`debtsanddecks`** (lowercase) — `mem_search` with any other casing fails connection. |
| 2026-08-27 | **BD-1 — v1 ships free** | v1 ships **free**: no billing, no IAP, no ads, no analytics, no third-party SDKs. Two permanent consequences: (1) the Play **Data safety** declaration stays "no data collected, no data shared" for as long as no analytics/ads/billing SDK is added — adding one **reopens BD-1**; (2) publication uses a **personal** developer account, so **closed testing with 12 opted-in testers for 14 continuous days** is required before production access can be requested *(policy verified 2026-08-27 — re-check the Play Console requirement page before relying on it for a release date)*. |
| 2026-08-27 | C3 `remove-free-debt-valve` status | **NEEDS RE-VERIFICATION.** `rg "RepayMode\|repayDebt\|REPAY_DISCARD_VALUE\|USURY_HP_RATIO" app/src/` returns zero matches and `9afd532` removed the valve, so C3's scope may already be satisfied by C2/C4 — or C3 may be mis-scoped. P0 records the evidence and deliberately does not decide. |
| 2026-08-27 | Card art format | Reverted to PNG by `fe281e1`; 19 files, all genuine PNG, ~355–470 KB each. APK-size impact against the `< 20 MB` target is handed to P1. |

---

## Playtest Notes

| Date | Build | Notes | Balance Changes |
|------|-------|-------|-----------------|
| — | — | — | — |

---

## Metrics Dashboard (MVP Targets)

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Combat duration | 4-6 min | — | ⬜ |
| Turns per combat | 8-10 | — | ⬜ |
| Player win rate | 60-70% | — | ⬜ |
| Avg HP on win | 15-30% | — | ⬜ |
| APK size | < 20 MB | — | ⬜ |
| Launch time | < 2 sec | — | ⬜ |
| 60 FPS stability | 99% | — | ⬜ |
| Crash-free sessions | 100% | — | ⬜ |

---

## Backlog (Post-MVP)

- [ ] Map / path selection
- [ ] Relics system
- [ ] Potions system
- [ ] Events (non-combat)
- [ ] Shop
- [ ] Card upgrades
- [ ] Save/Load (DataStore)
- [ ] Meta progression (Debt currency)
- [ ] Act 2, 3 + Bosses
- [ ] Elites
- [ ] Settings / Pause / Accessibility
- [ ] Sound / Music / Particles
- [ ] Tutorial / Encyclopedia

---

## Definition of Done (Per Task)

- [x] Code compiles
- [x] Unit tests pass (core logic)
- [ ] Manual test on device/emulator
- [ ] No new lint warnings
- [x] TRACKING.md updated
- [ ] Commit follows convention (authorized)

---

*Update this file at end of every session. Commit with `chore(docs): update tracking`.*