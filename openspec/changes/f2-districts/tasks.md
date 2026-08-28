# F2 — Tasks

Strict TDD. Two chained PRs: **PR1 = model + data + zero-delta proof** (no UI, no art),
**PR2 = identity on screen**.

> **Reconciled 2026-08-28.** PR1 was implemented and opened as
> [PR #7](https://github.com/OAlvarezOliveira/DebtsAndDecks/pull/7), branch
> `feat/districts`, commit `38e0b9b`. The boxes below are ticked against what that commit
> actually contains, not against what this file originally asked for. Where the shipped
> names differ from the names planned here, **the shipped name wins** and is recorded
> inline — renaming working code to match a plan is churn, not progress. One task, 2.4,
> is **not** implemented and stays open.
>
> PR1's acceptance gate (§3) depends on `f02b421`, in the same PR. See the note in §3.

## 0. Baseline capture

- [x] 0.1 On the fork point, run the harness and save the printed report.
      *Done during PR #7. Note the ordering correction in §3: a baseline taken before
      `f02b421` is not reproducible, so the usable baseline is `develop` + `f02b421`.*
- [x] 0.2 Record the six headline numbers in the PR1 body.

---

# PR1 — `feat(run): district model + data + zero-delta proof`

**Shipped as `38e0b9b` on branch `feat/districts` (PR #7), not `feat/f2-districts`.**

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
- [ ] 2.4 **RED** an unknown `districtId` fails at load, naming the id.
      **OPEN — not implemented.** `DistrictTest` asserts that the ids in the shipped
      `sequence.json` all resolve against the shipped catalog, which is a different and
      weaker guarantee: it pins today's data, but the **loader** still accepts a bad id
      without complaint. `DataLoader` decodes with
      `Json { ignoreUnknownKeys = true }` and performs no cross-catalog validation. A
      typo introduced later fails one assertion in one test rather than failing at load
      with a message naming the offender. Keep this task; it is correct and unmet.
- [x] 2.5 **GREEN** add `SlotRole`, extend `EncounterSlot`, amend `sequence.json`.
      *`core/model/RunSequence.kt`: `EncounterSlot` gains `districtId` and
      `role: SlotRole = SlotRole.STREET`; `enum class SlotRole { STREET, BOSS }`. KDoc
      records that `SlotRole` is deliberately not a `RunManager.Phase` value — see 7.5.*
- [x] 2.6 Confirm the existing `RunSequenceTest` assertions — 8 slots, gold list
      `[10,10,15,12,18,20,25,30]` — are **untouched**.
      *Untouched. The only test file edited for compilation was `RunManagerTest`, whose
      fixture helper gained `districtId = "fixture"`. No assertion was weakened.*

## 3. Zero-delta gate

> **Ordering correction, 2026-08-28.** This gate as originally written — "identical, or
> the change is wrong; no tolerance" — was **unrunnable on `develop`**. The harness was
> non-deterministic: three sweeps of identical code returned 53.5% / 54.0% / 54.5% greedy
> win rate, because the card-choice comparators tie-broke on `CardInstance.instanceId`,
> a fresh `UUID.randomUUID()` per instance. Any zero-delta claim was unprovable, for this
> change or any other.
>
> `f02b421` fixes that (tie-break on `cardId`, with `HarnessDeterminismTest` as the
> regression) and ships in the same PR. **The valid comparison is `develop` + `f02b421`
> versus the full branch — not `develop` versus the branch.** Anyone re-running this gate
> from `develop` alone will chase a phantom diff.

- [x] 3.1 Re-run the harness; diff against the baseline.
- [x] 3.2 Identical, or the change is wrong. *Identical.*
- [x] 3.3 `git diff run/sequence.json` shows only added fields — no `enemyId` or `rewards`
      byte changed.

---

# PR2 — `feat(ui): district identity`

**Not started.** Nothing in §§4–8 has been implemented. PR #7 is PR1 only: model, data
and proof, with no UI and no art, exactly as this file scoped it.

## 4. Design system, first

- [ ] 4.1 Extract from `Arts/Debts & Decks Design System.zip` into a tracked
      `docs/DESIGN-SYSTEM.md`: palette, type scale, spacing, district title-card treatment.
      Scope it to what F2 uses. Cite the ZIP as provenance.
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
      Three tests added to `I18nBundleTest`, one of them data-driven over the real catalog,
      so adding a district without translating it fails the build.*
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
      the four exhaustive `when` sites gained a branch.
      *Held. `SlotRole` was introduced as a separate enum precisely so that
      `CombatInputHandler.kt:34`, `GameScreen.kt:43`, `RunSimulator.kt:71` and
      `NodePolicyTest.kt:32` did not have to change for presentation data.*

## 8. Deliver

- [x] 8.1 Branch off `develop`. *Shipped as `feat/districts`, not `feat/f2-districts`.*
- [x] 8.2 Conventional commits. *`feat(run):` and `fix(sim):`. `docs(art):` and
      `docs(design):` belong to PR2 and are still pending.*
- [x] 8.3 PR1 body carries the sweep diff.
      *PR2's screenshot requirement is still open.*
- [ ] 8.4 Do not self-close. Independent pass re-runs 3.1 and eyeballs 5.3.
      **Open by design.** 3.1 is covered by the independent pass on PR #7; 5.3 cannot be
      done until PR2 generates the art.

---

## What is left in F2

1. **2.4** — make the loader reject an unknown `districtId` and name it. Small, and the
   only unmet task in PR1.
2. **All of PR2** — §§4, 5, 7, and the remainder of 8. Design system, art, render.
