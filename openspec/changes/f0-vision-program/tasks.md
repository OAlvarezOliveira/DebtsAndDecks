# F0 — Tasks

Documentation only. No test-first cycle applies (nothing executable changes), but the
blast-radius check in 5.2 is the equivalent gate.

## 1. Scaffold the dual store

- [ ] 1.1 Create `openspec/config.yaml` with project context, conventions, and the
      "git tree is authoritative" store policy.
- [ ] 1.2 Create `openspec/project.md` — where truth lives per question, non-negotiables,
      program shape.
- [ ] 1.3 Confirm `.gitignore` contains no pattern matching `openspec/`
      (`git check-ignore -v openspec/config.yaml` must exit non-zero).
- [ ] 1.4 `git add openspec/` in the **first** commit of the branch. Not later.

## 2. Write the vision

- [ ] 2.1 Create `docs/VISION.md`: pitch, seven systems, tone, the ten districts, program.
- [ ] 2.2 Record decisions D1-D9. Each one states the choice, the rejected alternative, and
      the concrete reason — not a preference.
- [ ] 2.3 Record D3 as closed by the owner (3 zones over 8 slots) and resolve only the
      internal split (which slot is boss, which is street).
- [ ] 2.4 Record in D9 that the vision's "-$58,000 / -$100,000" is illustrative, and that the
      *ratio* it implies (58% of the way to execution at start, vs 12% today) is a real F3
      balance question, not a formatting one.

## 3. Amend the GDD

- [ ] 3.1 Append `## Vision (planned)` pointing at `docs/VISION.md`. Nothing above is edited.
- [ ] 3.2 Add forward-reference annotations where a current statement will be superseded
      (run structure -> F2; economy -> F3; enemy roster -> FV/F5). Original text untouched.
- [ ] 3.3 Correct only statements that are false about `develop` today, citing the file each
      was checked against.
- [ ] 3.4 Leave the success criteria section alone. Criterion #4 is FV's reason to exist.
- [ ] 3.5 Verify the delta shape: `git diff develop -- docs/GDD.md` shows no removed line
      that was true about `develop`.

## 4. Write the change artifacts

- [ ] 4.1 `fv-core-validation/proposal.md` — short proposal with exit criteria E1-E4 and the
      signing-config hard dependency. **No tasks file.**
- [ ] 4.2 F0 proposal / spec / design / tasks (this set).
- [ ] 4.3 F1 proposal / spec / design / tasks.
- [ ] 4.4 F2 proposal / spec / design / tasks.
- [ ] 4.5 One-page charter for each of F3, F4, F5, F6, F7, F8. **No tasks files.** A charter
      that looks apply-ready is worse than no charter.

## 5. Verification handoff

- [ ] 5.1 Write `openspec/VERIFICATION-CHECKLIST.md`: claim -> source -> command, where the
      source is always code, `git log -S`, `gh`, or harness output — never this program.
- [ ] 5.2 Blast radius: `git diff --stat develop...HEAD` shows only `docs/` and `openspec/`.
- [ ] 5.3 `./gradlew test` — 180 tests, same as `develop`, none added, none modified.
- [ ] 5.4 Grep the F0 output for self-certification language. Zero hits.
- [ ] 5.5 Mirror every artifact to Engram (`sdd/<change>/<artifact>`, project
      `debtsanddecks`, `capture_prompt: false`), plus `sdd/vision-programa/done`.

## 6. Deliver

- [ ] 6.1 Branch `docs/f0-vision-program` off `develop` (per `docs/CONVENTIONS.md`
      §Branch Naming).
- [ ] 6.2 Commits: `docs(vision): ...`, `docs(gdd): ...`, `docs(sdd): ...` — conventional,
      no AI attribution.
- [ ] 6.3 Open the PR. **Do not self-close.** Closing requires an independent pass with no
      memory of this one, running the checklist from 5.1.
