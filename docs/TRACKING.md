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
- [ ] **Measure** Metrics dashboard below (combat duration, win rate, FPS, launch time)

### Doing
- [ ] `play-store-launch` P2 (next phase)

### Done
- [x] **Platform** `play-store-launch` P1 `android-platform-modernization` — compileSdk/targetSdk 36, AGP 8.10.1, Gradle 8.11.1, Kotlin 2.2.20, LibGDX 1.14.2; 16 KB verdict `ALIGNED-AFTER-FALLBACK` verified on shipping bytes and on a 16 KB device (2026-08-27, see ADR 0002)
- [x] **Build** APK builds, installs and runs — **13 MB** release APK / 12 MB AAB, under the `< 20 MB` target (2026-08-27). *Run was on a 16 KB **emulator**; a physical-device run is still outstanding (OQ-1).*
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
- [x] **Test** Unit tests: CombatEngine, CardResolver, CardInstance, EnemyInstance, RunManager, DebtConfig, EnemyTierRegression, I18nBundle, LeveragePayoffCardsData, RunSimulationHarness, RunSequenceTest (131/131 green across 11 classes, measured 2026-08-27 via `:app:testDebugUnitTest` at develop tip `b142528`)
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

### 2026-08-27 (Session 3 — `play-store-launch` P1 `android-platform-modernization`)
**Goal:** Raise the Android platform baseline to `compileSdk`/`targetSdk` 36 and settle the 16 KB
page-size question with measured bytes, not version numbers.
**Done:**
- **16 KB verdict: `ALIGNED-AFTER-FALLBACK`.** LibGDX 1.12.1 genuinely failed — its 64-bit `.so`
  files ship `p_align 0x1000` where Play requires `>= 0x4000`. Measured every candidate release
  from Maven rather than guessing: **1.13.0 is the alignment floor**. Pinned **1.14.2** (trigger
  **T1**), which measures `0x4000` on both 64-bit ABIs. Cross-validated with `readelf -lW`.
- Toolchain resolved from primary sources *before* editing, then applied one variable per rung with
  the full suite after each: Gradle 8.9 → **8.11.1**, Kotlin 1.9.22 → **2.2.20**, AGP 8.4.0 →
  **8.10.1**, compileSdk/targetSdk 34 → **36**, LibGDX 1.12.1 → **1.14.2**. `minSdk` stays **24**.
  All seven rungs green — measured **131/131** across 11 classes at develop tip `b142528`; the
  test *surface* did not move across the seven rungs, but the 124/10 figure P0 recorded was itself
  stale (it predated C5's `RunSequenceTest`), so the correct baseline is 131/11, not 124/10.
- All three 16 KB layers verified on the **shipping bytes**: Check A on the `.so` extracted from the
  release APK (PASS), Check B `zipalign -c -P 16 -v 4` (exit 0, all four `.so` `Stored` and on exact
  16384-byte boundaries), and **Check C on a real 16 KB device** — AVD with
  `getconf PAGE_SIZE = 16384` on API 36: install `Success`, launch `Status: ok`, `nativeloader` maps
  `libgdx.so` straight out of the APK, LibGDX reaches OpenGL ES 3.1, no crashes.
- Manifest cleanup: removed the deprecated `package=` attribute (the AGP deprecation warning is now
  **0** occurrences) and all three unused permissions (`INTERNET`, `ACCESS_NETWORK_STATE`,
  `WAKE_LOCK`); disabled the accelerometer, compass and gyroscope.
- `copyAndroidNatives` rewritten as a real `Sync` task — now up-to-date-checkable and
  configuration-cache safe, which the old `doFirst { copy { } }` could never be.
- **Answered P0's open handoff on APK size:** release APK **13 MB**, AAB **12 MB** — comfortably
  under the `< 20 MB` target, so the PNG revert did not break the budget.
- **Answered P0's card-art handoff:** **19 of 27** cards render art (8 are deliberately art-less) —
  exactly the expected count. The decoder accepts the files; nothing to defer to P5.
- **Corrected a defect in the phase instructions:** they called for
  `android:layoutInDisplayCutoutMode` on `<activity>`, which does not exist (AAPT rejects it). The
  real attribute is the *theme* attribute `android:windowLayoutInDisplayCutoutMode`, now set to
  `shortEdges` in `Theme.DebtsAndDecks.Fullscreen`.
- Full rationale, raw evidence, the Check A script and the rollback pins are in
  `docs/ADR/0002-16kb-page-size-and-platform-baseline.md`.
**Next:** P2. Deferred on purpose: `allowBackup` → **P4**; `numSamples` and any cutout restyling →
**P5**; app signing → **P6**; headless render/GL harness and version catalogs → **P7**.

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
| 2026-08-27 | Card art format | Reverted to PNG by `fe281e1`; 19 files, all genuine PNG, ~355–470 KB each. APK-size impact against the `< 20 MB` target is handed to P1. — *answered by P1 2026-08-27: release APK **13 MB**, AAB **12 MB**; the budget holds, no action needed.* |
| 2026-08-27 | **16 KB page-size compliance** | **`ALIGNED-AFTER-FALLBACK`.** LibGDX 1.12.1 ships 64-bit `.so` with `p_align 0x1000`; Play requires `>= 0x4000` for `targetSdk >= 35`. Measured every release: **1.13.0 is the floor**, pinned **1.14.2** (trigger **T1**). Verified on shipping bytes at all three layers — ELF alignment, `zipalign -P 16`, and an **AVD emulator** with `PAGE_SIZE = 16384` (OQ-1: physical device still outstanding). **No shortcut was used**: 64-bit ABIs kept, `useLegacyPackaging` never set, no warning suppressed, `targetSdk` never lowered. Evidence in ADR 0002. |
| 2026-08-27 | Toolchain baseline | Gradle **8.11.1**, AGP **8.10.1**, Kotlin **2.2.20**, LibGDX **1.14.2**, compileSdk/targetSdk **36**, minSdk **24** (unchanged). Each rung is forced by the one above it, not chosen for recency — AGP 8.10 is the lowest stable line supporting API 36, and it dictates the Gradle and Kotlin floors. Suite green at 124/124 after every rung. |
| 2026-08-27 | Gradle wrapper status | **Not broken, and no repair pulled forward from P7.** The JAR is a valid 43 KB archive; it only ever pointed at a Gradle too old for AGP 8.10.1. Bumping `distributionUrl` to 8.11.1 is sufficient and `./gradlew` is the canonical invocation. A standalone 8.11.1 (SHA-256 verified) was installed while resolving this and is retained as a fallback only. |
| 2026-08-27 | Device run (Check C) | Emulated AVD, image `android-36 google_apis_ps16k x86_64`, `getconf PAGE_SIZE = 16384`, `ro.build.version.sdk = 36`. Debug build (release APK is unsigned until P6): install `Success`, launch `Status: ok` in 3502 ms, `nativeloader` mapped `libgdx.so` from the APK, OpenGL ES 3.1 reached, no crashes. **Open (OQ-1): still no run on a *physical* 16 KB device.** |
| 2026-08-27 | `allowBackup` | **Deferred to P4** (OQ-2). Left at `true` in P1; the decision belongs with the Play Data safety declaration, not with the platform baseline. |
| 2026-08-27 | `largeHeap` artifact discrepancy | Spec says remove it, design §6.4 says keep it. **Design followed** per the executor contract, and both positions are recorded. Shrinking the heap is a runtime-risk change with no submission benefit, and P1 must not change behaviour. |
| 2026-08-27 | Cutout attribute defect | The phase instructions named `android:layoutInDisplayCutoutMode` on `<activity>` — **that attribute does not exist** and AAPT rejects it. The real one is the *theme* attribute `android:windowLayoutInDisplayCutoutMode`, now `shortEdges` in `Theme.DebtsAndDecks.Fullscreen`. On API 35+ the platform widens it to `always`; the declaration still matters for API 27–34. |

---

## Playtest Notes

| Date | Build | Notes | Balance Changes |
|------|-------|-------|-----------------|
| 2026-08-27 | Debug APK, `b142528`, AVD `android-36 google_apis_ps16k x86_64` (`PAGE_SIZE=16384`) | Install `Success`, launch `Status: ok` in 3502 ms, `nativeloader` mapped `libgdx.so` from the APK, OpenGL ES 3.1 reached, no crashes (16 KB Check C — see ADR 0002). This was a launch/stability smoke check, not a full combat playtest; full loop + economy feel is still **To Do**. | None — no balance changes made this session. |

---

## Metrics Dashboard (MVP Targets)

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Combat duration | 4-6 min | — (not yet measured; needs full playtest) | ⬜ |
| Turns per combat | 8-10 | — (not yet measured; needs full playtest) | ⬜ |
| Player win rate | 60-70% | — (not yet measured; needs full playtest) | ⬜ |
| Avg HP on win | 15-30% | — (not yet measured; needs full playtest) | ⬜ |
| APK size | < 20 MB | 13 MB release APK / 12 MB AAB (measured 2026-08-27, P1) | ✅ |
| Launch time | < 2 sec | **3502 ms** on AVD (measured 2026-08-27, Check C, debug build) | 🔴 over target — release/signed build not yet timed (P6); re-measure before treating this as final |
| 60 FPS stability | 99% | — (not yet measured; needs full playtest) | ⬜ |
| Crash-free sessions | 100% | — (not yet measured; needs full playtest) | ⬜ |

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
- [ ] **Tech debt** `core/data/DataLoader.kt` imports `android.content.Context` (since `9d72ca3`) — violates the `core/` purity rule; correctly deferred by both P0 and P1 as out of scope, but no phase owns fixing it yet. Assign an owning phase before it is inherited a third time.

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