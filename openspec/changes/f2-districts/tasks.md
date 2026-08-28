# F2 — Tasks

Strict TDD. Two chained PRs: **PR1 = model + data + zero-delta proof** (no UI, no art),
**PR2 = identity on screen**.

> **Reconciled 2026-08-28.** PR1 was implemented and opened as
> [PR #7](https://github.com/OAlvarezOliveira/DebtsAndDecks/pull/7), branch
> `feat/districts`. The boxes below are ticked against what that branch actually
> contained, not against what this file originally asked for. Where the shipped
> names differ from the names planned here, **the shipped name wins** and is recorded
> inline — renaming working code to match a plan is churn, not progress.
>
> PR1's acceptance gate (§3) depends on the determinism fix, in the same PR. See §3.
>
> **Updated 2026-08-28, after the merge.** PR #7 was squash-merged into `develop` as
> **`6b50164`**. That is the only SHA a reader can follow: a squash discards the branch
> commits, so `38e0b9b`, `f02b421` and the rest are **not** ancestors of `develop` and never
> appear in `git log develop`. In a worktree that still has the deleted branch's objects they
> resolve, which is exactly the trap — `git show 38e0b9b` succeeds locally and fails for
> everyone who clones. `git merge-base --is-ancestor <sha> develop` is the test that does not
> lie. Where this file needs to point at a
> specific change, it uses a content search (`git log -S`) rather than a branch SHA.
> Task 2.4, left open at reconciliation time, was implemented before the merge and is now
> ticked. Two independent verification passes ran over the
> PR; between them they corrected three claims and forced one test to be rewritten —
> recorded in §8.4 and in the PR description. **PR1 is complete.** What remains in F2
> is PR2 in its entirety.

## 0. Baseline capture

- [x] 0.1 On the fork point, run the harness and save the printed report.
      *Done during PR #7. Note the ordering correction in §3: a baseline taken before the
      determinism fix is not reproducible. Post-merge this is moot — the fix is on
      `develop`, so a baseline taken from `develop` is now valid on its own.*
- [x] 0.2 Record the six headline numbers in the PR1 body.

---

# PR1 — `feat(run): district model + data + zero-delta proof`

**Shipped on branch `feat/districts` (PR #7), not `feat/f2-districts`; on `develop` as
`6b50164`.**

## 1. District catalog

- [x] 1.1 **RED** `districts/all.json` parses into 3 definitions.
      *Shipped as `DataLoaderDistrictTest` — "decodes a district catalog off the asset
      source" and "decodes the real shipped catalog end to end". The plan named a single
      `DistrictCatalogTest`; the work split across two files so that the fake-`AssetSource`
      case and the real-asset case are separately diagnosable.*
- [x] 1.2 **RED** every catalog field is an i18n key, not prose.
      *Shipped in `DistrictTest` — "district names and descriptions are bundle keys, never
      literal prose".*
- [x] 1.3 **GREEN** add the model + `districts/all.json` + loader.
      *Shipped as `core/model/District.kt` (**not** `DistrictDefinition` — the codebase's
      other models are unsuffixed), `assets/districts/all.json`, and the loader wiring:
      `AssetSource.readDistricts()`, `AndroidAssetSource.readDistricts()`,
      `DataLoader.loadDistricts()`, `TestAssetLoader.loadDistricts()`.*

## 2. Sequence partition

- [x] 2.1 **RED** all 8 slots carry a `districtId`.
      *`DistrictTest` — "every slot names a district that exists in the catalog".*
- [x] 2.2 **RED** the partition is exactly `[1,2,3]`, `[4,5,6]`, `[7,8]` and districts are
      contiguous in slot order.
      *`DistrictTest` — "districts cut the run into contiguous blocks of three, three and
      two".*
- [x] 2.3 **RED** exactly one `role: BOSS` per district, and it is the district's last slot
      (3, 6, 8).
      *Three tests: "each district closes on exactly one boss seat", "the boss seats are
      slots three, six and eight", "a boss seat is always the last slot of its district".
      Plus "street slots stay street so the reskin adds no hidden encounter".*
- [x] 2.4 **RED** an unknown `districtId` fails at load, naming the id.
      *Done after this file was first reconciled. `DataLoader.loadRunSequence` now loads
      the district catalog from the same `AssetSource` and `require`s every slot's
      `districtId` to be in it, failing with the slot index, the enemy id, the offending id
      and the known set. The check is on the production path: `di/Module.kt` binds
      `single<RunSequence> { DataLoader.loadRunSequence(get()) }`.*
      *Reproduce on `develop`:*
      `rg -n -A12 'fun loadRunSequence' app/src/main/java/com/debtsdecks/core/data/DataLoader.kt`
      *and the test:*
      `./gradlew testDebugUnitTest --tests '*DataLoaderDistrictTest*'`
      *(4 tests). To see it fail without the production change, restore the pre-merge
      version of that one file —*
      `git show "$(git log -1 --format=%H -S'names an unknown district' -- app/src/main/java/com/debtsdecks/core/data/DataLoader.kt)^:app/src/main/java/com/debtsdecks/core/data/DataLoader.kt"`
      *— run the test class, then restore that exact path with `git checkout -- <path>`.
      Never a wildcard: this checkout has had uncommitted work destroyed that way.*
      *Known limit, deliberately not addressed: the guard is one-directional. A district
      in the catalog that no slot references is not detected.*
- [x] 2.5 **GREEN** add `SlotRole`, extend `EncounterSlot`, amend `sequence.json`.
      *`core/model/RunSequence.kt`: `EncounterSlot` gains `districtId` and
      `role: SlotRole = SlotRole.STREET`; `enum class SlotRole { STREET, BOSS }`. KDoc
      records that `SlotRole` is deliberately not a `RunManager.Phase` value — see 7.5.*
- [x] 2.6 Confirm the existing `RunSequenceTest` assertions — 8 slots, gold list
      `[10,10,15,12,18,20,25,30]` — are **untouched**.
      *Untouched, and no assertion anywhere was weakened. Corrected 2026-08-28: this claimed
      `RunManagerTest` was "the only test file edited", which
      `git show --name-status --format="" 6b50164 | rg "src/test"` disproves — PR1 touched eight
      files under `app/src/test/`. Four modified: `RunManagerTest` (fixture helper gains
      `districtId = "fixture"` — the only one edited purely for compilation), `TestAssetLoader`
      (gains `loadDistricts()`), and `ScriptedPolicy` + `LeveragePolicy` (the `instanceId` →
      `cardId` tie-break, i.e. the determinism fix, not this task). Four added:
      `DataLoaderDistrictTest`, `DistrictTest`, `HarnessDeterminismTest`, and the district cases
      in `I18nBundleTest`. Task 1.3 four lines above already records `TestAssetLoader`, so this
      file contradicted itself.*

## 3. Zero-delta gate

> **Ordering correction, 2026-08-28.** This gate as originally written — "identical, or
> the change is wrong; no tolerance" — was **unrunnable on `develop`**. The harness was
> non-deterministic: three sweeps of identical code returned 53.5% / 54.0% / 54.5% greedy
> win rate, because the card-choice comparators tie-broke on `CardInstance.instanceId`,
> a fresh `UUID.randomUUID()` per instance. Any zero-delta claim was unprovable, for this
> change or any other.
>
> The fix (tie-break on `cardId`, with `HarnessDeterminismTest` as the regression) ships
> in the same PR. **At the time, the valid comparison was `develop` + that fix
> versus the full branch — not `develop` versus the branch.** Anyone re-running this gate
> from `develop` alone will chase a phantom diff.

- [x] 3.1 Re-run the harness; diff against the baseline.
- [x] 3.2 Identical, or the change is wrong. *Claimed identical; the command and its output are checklist row C6's to carry, not this line's.*
- [x] 3.3 The **parsed** slots of `run/sequence.json` are unchanged: the list of
      `(enemyId, rewards.gold, rewards.cardChoices)` is identical, slot for slot.
      *Rewritten 2026-08-28. This task used to claim "only added fields — no `enemyId` or
      `rewards` byte changed", and it was ticked while being false:
      `git diff 6b50164^ 6b50164 -- app/src/main/assets/run/sequence.json` shows 9 removed and
      9 added lines, because `districtId` and `role` realigned every slot line. Checklist row
      C7 already said so; this line did not. The values are untouched and that is the claim
      worth making — verified, all 8 slots: `thug 10/1, thug 10/1, loan_shark 15/1,
      thug 12/1, loan_shark 18/2, loan_shark 20/1, collector 25/1, collector 30/0`.*

---

# PR2 — `feat(ui): district identity`

**Not started.** Nothing in §§4–8 has been implemented. PR #7 is PR1 only: model, data
and proof, with no UI and no art, exactly as this file scoped it.

## 4. Design system, first

- [ ] 4.1 Extract from `Arts/Debts & Decks Design System.zip` into a tracked
      `docs/DESIGN-SYSTEM.md`: palette, type scale, spacing, district title-card treatment.
      Scope it to what F2 uses. Cite the ZIP as provenance.
      > **This input is not in the repository.** `git ls-files Arts/` returns nothing and
      > `.gitignore` excludes the whole directory, so a fresh clone cannot run this task at
      > all — it depends on a file that exists only on the owner's machine. That is the exact
      > condition the proposal calls out ("a style guide no other machine can read is not a
      > style guide"), so the task is blocked on the owner producing the ZIP's contents, not
      > on anything an implementer can do. Whoever picks up PR2 must ask for it first rather
      > than inventing a palette and calling it extraction.
- [ ] 4.2 Confirm it is tracked: `git ls-files docs/DESIGN-SYSTEM.md` is non-empty.

## 5. Art, with the pipeline fix applied first

- [ ] 5.1 Apply the fix already written at `docs/ART-PIPELINE.md:90` — the explicit
      "no text, no lettering, no numbers" instruction — to the generation prompt. **Before**
      generating anything.
- [ ] 5.2 Generate 3 district backgrounds at the corrected resolution (pipeline §3.6), real
      alpha, no lettering.
- [ ] 5.3 Visually confirm no lettering in any of the three. A background that ships with
      text makes this phase a net contributor to the debt it was told to avoid.
- [ ] 5.4 Update `docs/ART-PIPELINE.md` §1 inventory with the three assets, and record in §3
      that F2 **pays** the sizing defect for its own backgrounds and **carries** the 15-of-27
      card-text defect, assigned to F5.

## 6. i18n

- [x] 6.1 **RED** parity test: every `district.*` key exists in both `strings.properties` and
      `strings_es.properties`.
      *Shipped early, in PR1 rather than PR2, because the catalog was useless without it.
      **Four** tests added to `I18nBundleTest`, not three
      (`git diff 6b50164^ 6b50164 -- app/src/test/java/com/debtsdecks/core/i18n/I18nBundleTest.kt
      | rg '^\+.*fun `'`): `English district keys resolve`,
      `Spanish district keys resolve with neutral thematic translations`,
      `every district in the catalog is translated in both bundles` — the data-driven one, so
      adding a district without translating it fails the build — and
      `a key present only in English resolves to English through the bundle, which is why parity
      reads raw files`, the guard against `I18NBundle`'s parent fallback making the whole check
      vacuous. Counting `@Test` in the file gives 24 and answers a different question: that is
      the file's total, not what `6b50164` added.*
- [x] 6.2 **GREEN** author the district names and descriptors in EN, noir tone, neutral
      professional register.
- [x] 6.3 Translate to ES. Neutral Spanish, no regional forms, no voseo.
      *Six keys per locale: `district.{slaughterhouse,casino,boardroom}.{name,description}`.*

## 7. Render

- [ ] 7.1 `RunManager` exposes `currentDistrict`, derived from the current slot.
- [ ] 7.2 Combat and node renderers select the background from it.
- [ ] 7.3 District name + descriptor shown on entering a district.
- [ ] 7.4 **RED then GREEN** layout test: the title position derives from viewport width via
      the existing layout helpers. No fixed 1280-space coordinate.
- [x] 7.5 Confirm `RunManager.Phase` is still `{ COMBAT, NODE, VICTORY, DEFEAT }` and none of
      the `when (phase)` sites gained a branch. `SlotRole` was introduced as a separate enum
      precisely so that no phase dispatch had to change for presentation data.
      *Held, but the count in this task was wrong and is corrected here.
      `git grep -n "when *( *\(run\|runManager\)\.phase" develop -- 'app/src/**/*.kt'`
      returns **five** sites, not four, and only **three** of them are exhaustive. The
      breakdown is in **§7.5 detail** below, lifted out of this list item on 2026-08-28: a
      table indented to line up under a list item's prose sits 4+ columns past the item's
      content column, which GFM renders as an indented code block. It looked correct in every
      local preview and shipped as literal pipe characters on GitHub — the same defect class as
      checklist rows E13 and C6.*

      *This matters for F6/F7, not for F2. F2 adds no phase, so nothing here changes either
      way. But a phase added later gets a compile error from three sites and **silence** from
      `CombatInputHandler.kt:213`, whose `else -> Unit` swallows it: the new phase would just
      play no sound and nobody would be told. `NodePolicyTest.kt:32` fails at runtime with a
      readable message, which is second best. Whichever phase adds `EVENT` or `MARKET` has to
      open `CombatInputHandler.kt:213` deliberately, because the compiler will not.*

### 7.5 detail — the five `when (phase)` sites

| Site | Exhaustive? |
| --- | --- |
| `CombatInputHandler.kt:34` | yes |
| `GameScreen.kt:43` | yes |
| `RunSimulator.kt:71` | yes |
| `CombatInputHandler.kt:213` | **no** — `else -> Unit` |
| `NodePolicyTest.kt:32` | **no** — `else -> error(...)` |

## 8. Deliver

- [x] 8.1 Branch off `develop`. *Shipped as `feat/districts`, not `feat/f2-districts`.*
- [x] 8.2 Conventional commits. *`feat(run):` and `fix(sim):`. `docs(art):` and
      `docs(design):` belong to PR2 and are still pending.*
- [x] 8.3 PR1 body carries the sweep diff.
      *PR2's screenshot requirement is still open.*
- [x] 8.4 Do not self-close. Independent pass re-runs 3.1 and eyeballs 5.3.
      *Two independent passes ran on PR #7, each without knowledge of the implementation;
      their findings and the commands behind them are in the PR #7 description, which is
      the durable record — this line only summarizes it.*
      *The first re-ran the zero-delta and determinism checks and found the i18n parity
      test vacuous — `I18NBundle` falls back to the parent bundle, so a Spanish-missing
      key resolved to English and passed — and found a wrong comment on the one line a
      reader is sent to inspect. The second found the first attempt at fixing that i18n
      test still proved nothing, and only passed once the fixture was rebuilt as a
      genuine English-only/Spanish-missing pair. Three claims in the PR description were
      corrected rather than deleted.*
      *5.3 remains out of reach until PR2 generates the art.*

---

## What is left in F2

**PR1 is complete and merged** (`6b50164`). What is left is **all of PR2** — §§4, 5, 7
and the remainder of 8: design system, art, render.
