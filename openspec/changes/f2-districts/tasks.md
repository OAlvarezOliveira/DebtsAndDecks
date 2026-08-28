# F2 — Tasks

Strict TDD. Two chained PRs: **PR1 = model + data + zero-delta proof** (no UI, no art),
**PR2 = identity on screen**.

## 0. Baseline capture

- [ ] 0.1 On the fork point, run the harness and save the printed report to
      `/tmp/f2-baseline.txt`.
- [ ] 0.2 Record the six headline numbers in the PR1 body.

---

# PR1 — `feat(run): district model + data + zero-delta proof`

## 1. District catalog

- [ ] 1.1 **RED** `DistrictCatalogTest`: `districts/all.json` parses into 3 definitions.
- [ ] 1.2 **RED** every catalog field is an i18n key, not prose (regex, no whitespace).
- [ ] 1.3 **GREEN** add `DistrictDefinition` + `districts/all.json` + loader.

## 2. Sequence partition

- [ ] 2.1 **RED** all 8 slots carry a `districtId`.
- [ ] 2.2 **RED** the partition is exactly `[1,2,3]`, `[4,5,6]`, `[7,8]` and districts are
      contiguous in slot order.
- [ ] 2.3 **RED** exactly one `role: BOSS` per district, and it is the district's last slot
      (3, 6, 8).
- [ ] 2.4 **RED** an unknown `districtId` fails at load, naming the id.
- [ ] 2.5 **GREEN** add `SlotRole`, extend `EncounterSlot`, amend `sequence.json`.
- [ ] 2.6 Confirm the existing `RunSequenceTest` assertions — 8 slots, gold list
      `[10,10,15,12,18,20,25,30]` — are **untouched**. If either needed editing, stop: the
      phase has acquired a balance delta.

## 3. Zero-delta gate

- [ ] 3.1 Re-run the harness; diff against `/tmp/f2-baseline.txt`.
- [ ] 3.2 Identical, or the change is wrong.
- [ ] 3.3 `git diff run/sequence.json` shows only added fields — no `enemyId` or `rewards`
      byte changed.

---

# PR2 — `feat(ui): district identity`

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

- [ ] 6.1 **RED** parity test: every `district.*` key exists in both `strings.properties` and
      `strings_es.properties`.
- [ ] 6.2 **GREEN** author the district names and descriptors in EN, noir tone, neutral
      professional register.
- [ ] 6.3 Translate to ES. Neutral Spanish, no regional forms, no voseo.

## 7. Render

- [ ] 7.1 `RunManager` exposes `currentDistrict`, derived from the current slot.
- [ ] 7.2 Combat and node renderers select the background from it.
- [ ] 7.3 District name + descriptor shown on entering a district.
- [ ] 7.4 **RED then GREEN** layout test: the title position derives from viewport width via
      the existing layout helpers. No fixed 1280-space coordinate.
- [ ] 7.5 Confirm `RunManager.Phase` is still `{ COMBAT, NODE, VICTORY, DEFEAT }` and none of
      the four exhaustive `when` sites gained a branch.

## 8. Deliver

- [ ] 8.1 Branch `feat/f2-districts` off `develop`.
- [ ] 8.2 Conventional commits: `feat(run):`, `feat(ui):`, `docs(art):`, `docs(design):`.
- [ ] 8.3 PR1 body carries the sweep diff. PR2 body carries screenshots of all three
      districts.
- [ ] 8.4 Do not self-close. Independent pass re-runs 3.1 and eyeballs 5.3.
