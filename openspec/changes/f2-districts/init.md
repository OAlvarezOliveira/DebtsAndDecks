# sdd-verify — init

**Change:** `f2-districts` (PR2 — `feat(ui): district identity`)
**Task in scope (this run):** Section 4 — Design system, first. **Sub-tasks:** 4.1, 4.2.
**Out of scope (explicit):** 5.x (art/asset generation), 7.x (render — handled in a prior run, see below), and every task outside section 4. PR2's spec delta (R2.7–R2.9) already exists in `specs/run-structure/spec.md`; archiving/sync-to-main is deferred until PR2 ships (matches the PR1 merge-then-archive precedent).

> **Prior run note.** A previous `sdd-verify` run on this change closed tasks 7.1–7.4 (Render) on
> branch `feat/f2-districts-runmanager`. Its artifacts (`apply-progress.md`, `verify-report.md`,
> `sync-report.md`, `archive-report.md`) are still in this folder and remain authoritative for that
> scope. This run is a *different* sub-scope (design system), so `init.md` is re-authored for 4.1–4.2
> and `apply-progress.md` is **appended** (not overwritten) so the 7.x evidence survives.

## Context gathered

- `proposal.md` — PR2 = backgrounds, names on screen, i18n, `docs/DESIGN-SYSTEM.md`. The design-system
  half of PR2 is exactly tasks 4.1/4.2: extract the ZIP's tokens (palette, type scale, spacing, the
  district-card treatment) into a tracked `docs/DESIGN-SYSTEM.md`, scoping to what F2 actually uses.
- `design.md` — "Design system extraction": the decisions inside the ZIP become text (hex values, type
  scale, spacing rhythm, district title-card treatment) in `docs/DESIGN-SYSTEM.md`, with the ZIP cited as
  provenance. Scoped to what F2 needs; do not transcribe the whole system, do not invent unused tokens.
- `tasks.md` — 4.1 / 4.2 are **unchecked**; 4.1 carried a "input not in the repository" blocker because
  `Arts/` is gitignored and a fresh clone cannot reach the ZIP. That blocker is resolved *this run*: the
  orchestrator extracted and verified the ZIP this session and supplied the exact token values, so no
  token is invented. 7.1–7.5 are already done (prior run). 5.1–5.4 (art) remain unchecked and **out of
  scope here**.

## Source of truth for values (this run)

The orchestrator provided the exact extracted values (do NOT re-open or re-scan the ZIP). Provenance ZIP:

```
/home/oscardev/DebtsAndDecks/Arts/Debts & Decks Design System.zip
```

Mapped to kit files: `tokens/colors.css` (palette), `tokens/typography.css` (type scale),
`tokens/effects.css` (spacing/radius/effects). The district title-card treatment is **not** a kit
pattern — the kit's `_ds_manifest.json` lists only GameCard, CombatLog, HUDPanel, IntentBadge, StatBar,
Button, and brand/color guideline cards (zero hits for `district` or `title-card`). The title-card
recommendation is therefore this document's own, composed from general tokens.

## Constraints

- Tracked output only: no ZIP binary is committed. `docs/DESIGN-SYSTEM.md` must be **tracked**
  (`git ls-files docs/DESIGN-SYSTEM.md` non-empty after staging) — that is task 4.2's verification.
- Scope to F2's two usages: (a) district backdrop rendering, (b) district name/descriptor title display
  (tasks 7.1–7.3). Do not pull in tokens F2 does not use.
- Cite the ZIP path as provenance for every Palette/Type/Spacing value.
- Do **not** touch tasks 5.x or any file outside this scope. Do **not** commit.

## Environment

- Working tree currently clean on `feat/fv-verbs-foreclose-hedge`; the f2-districts openspec folder is
  tracked on this branch. This run adds `docs/DESIGN-SYSTEM.md` (staged, not committed) and edits
  `tasks.md` + `apply-progress.md`.
- No code/test change in this run (doc extraction only) → no Gradle run required for verification.
