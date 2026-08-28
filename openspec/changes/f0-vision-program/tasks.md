# F0 — Tasks

Documentation only. No test-first cycle applies (nothing executable changes), but the
blast-radius check in 5.2 is the equivalent gate.

> **Reconciled 2026-08-28.** Every box below was unticked while the work it describes had
> shipped in `b7bfe4a`/`986a46e`/`d20bdb3` on this branch — the file said F0 had not started.
> Each tick below carries the command that established it, run from this worktree on branch
> `docs/vision-program`. Boxes that are still open are open on purpose, and say why.

## 1. Scaffold the dual store

- [x] 1.1 Create `openspec/config.yaml` with project context, conventions, and the
      "git tree is authoritative" store policy.
- [x] 1.2 Create `openspec/project.md` — where truth lives per question, non-negotiables,
      program shape.
- [x] 1.3 Confirm `.gitignore` contains no pattern matching `openspec/`
      (`git check-ignore -v openspec/config.yaml` must exit non-zero).
      *`git check-ignore -v openspec/config.yaml` → no output, `rc=1`.*
- [x] 1.4 `git add openspec/` in the **first** commit of the branch. Not later.
      *`git show --name-only --format="" b7bfe4a` lists `openspec/config.yaml` and the whole
      `openspec/changes/` tree; `b7bfe4a` is the output of
      `git log --format=%H develop..HEAD | tail -1`.*

## 2. Write the vision

- [x] 2.1 Create `docs/VISION.md`: pitch, seven systems, tone, the ten districts, program.
- [x] 2.2 Record decisions D1-D9. Each one states the choice, the rejected alternative, and
      the concrete reason — not a preference.
      *`rg -c "^### D[0-9]" docs/VISION.md` → 9.*
- [x] 2.3 Record D3 as closed by the owner (3 zones over 8 slots) and resolve only the
      internal split (which slot is boss, which is street).
- [x] 2.4 Record in D9 that the vision's "-$58,000 / -$100,000" is illustrative, and that the
      *ratio* it implies (58% of the way to execution at start, vs 12% today) is a real F3
      balance question, not a formatting one.

## 3. Amend the GDD

- [x] 3.1 Append `## Vision (planned)` pointing at `docs/VISION.md`. Nothing above is edited.
- [x] 3.2 Add forward-reference annotations where a current statement will be superseded
      (run structure -> F2; economy -> F3; enemy roster -> FV/F5). Original text untouched.
- [x] 3.3 Correct only statements that are false about `develop` today, citing the file each
      was checked against.
- [x] 3.4 Leave the success criteria section alone. Criterion #4 is FV's reason to exist.
      *No hunk of `git diff develop -- docs/GDD.md` touches it.*
- [x] 3.5 Verify the delta shape: `git diff develop -- docs/GDD.md` shows no removed line
      that was true about `develop`.
      *`git diff develop -- docs/GDD.md | rg '^-[^-]'` → 7 removed lines, all of them false
      about `develop`: the enemy stat table (HP 24/40/56, contradicted by
      `app/src/main/assets/enemies/all.json`), the "`LEVY` fires once per fight" claim, and a
      stale "last updated" footer. Nothing true was dropped.*

## 4. Write the change artifacts

- [x] 4.1 `fv-core-validation/proposal.md` — short proposal with exit criteria E1-E4 and the
      signing-config hard dependency. **No tasks file.**
- [x] 4.2 F0 proposal / spec / design / tasks (this set).
- [x] 4.3 F1 proposal / spec / design / tasks.
- [x] 4.4 F2 proposal / spec / design / tasks.
- [x] 4.5 One-page charter for each of F3, F4, F5, F6, F7, F8. **No tasks files.** A charter
      that looks apply-ready is worse than no charter.
      *`fd charter.md openspec/changes` → six, one per phase.*

## 5. Verification handoff

- [x] 5.1 Write `openspec/VERIFICATION-CHECKLIST.md`: claim -> source -> command, where the
      source is always code, `git log -S`, `gh`, or harness output — never this program.
- [x] 5.2 Blast radius: `git diff --name-only develop...HEAD` shows only `docs/` and
      `openspec/`. *`git diff --name-only develop...HEAD | rg -v '^docs/|^openspec/'` → no
      output, `rc=1`. Use `--name-only`, not `--stat`: `--stat` elides long paths to `.../`,
      which the filter cannot match — see checklist row C1.*
- [x] 5.3 The test count is unchanged from the merge-base, none added, none modified.
      *F0 adds no test, so the check is equality with `develop`, not a literal. This line
      said "180 tests" and was stale by PR #7. Counted on both sides without a build, so it
      cannot be contaminated by an unrelated working tree:
      `git ls-tree -r develop --name-only | rg '^app/src/test/.*\.kt$' | while read f; do
      git show develop:"$f"; done | rg -o "@Test" | wc -l` → **199**, and the same command
      with `HEAD` gives 199. Note that counting the checked-out files instead returns 201
      here, because a concurrent session has uncommitted F1 tests in the main worktree —
      count from the git tree, never from disk.*
- [x] 5.4 Search the F0 output for self-certification language. Zero *assertions*.
      *`rg -in "verified|confirmed|validated" openspec/changes/f0-vision-program/ | wc -l`
      → 10, every one of them the rule stating itself ("proposed, unverified", "Not marking
      anything verified", R0.5's own title and scenario) or this very note recording the
      check. None asserts that a claim has been verified. A raw hit count is the wrong gate
      here — writing down that the check passed raises the count, so the words have to be
      read, not counted.*
- [ ] 5.5 **Open.** Mirror every artifact to Engram (`sdd/<change>/<artifact>`, project
      `debtsanddecks`, `capture_prompt: false`), plus `sdd/vision-programa/done`.

## 6. Deliver

- [x] 6.1 Branch off `develop` (per `docs/CONVENTIONS.md` §Branch Naming).
      *Shipped as **`docs/vision-program`**, not `docs/f0-vision-program`: the branch carries
      the whole program, not F0 alone. The planned name is recorded here because it was wrong
      and a reader chasing it finds nothing — `git branch --show-current` is the authority.*
- [x] 6.2 Commits: `docs(vision): ...`, `docs(gdd): ...`, `docs(sdd): ...` — conventional,
      no AI attribution.
- [x] 6.3 Open the PR. **Do not self-close.** Closing requires an independent pass with no
      memory of this one, running the checklist from 5.1.
      *[PR #8](https://github.com/OAlvarezOliveira/DebtsAndDecks/pull/8) is open. The ticked
      box means "opened", not "closed" — 6.3's own second sentence is why this file cannot
      tick its own closure.*
