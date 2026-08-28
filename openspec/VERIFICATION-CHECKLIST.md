# Independent verification checklist

**This program is delivered unverified, on purpose.**

The pass that produced these documents cannot be the pass that certifies them: whoever writes
something and then reviews it shares its blind spots. Every row below must be checked by
someone — a new session, a fork, a human — working **against the source named in the row**,
never against this program, never against a prior summary, and never against the document
that makes the claim.

A row is closed by recording the **command that was run and what it printed**. "Verified" on
its own closes nothing.

All commands run from `/home/oscardev/DebtsAndDecks` on `develop` unless stated.

---

## A. Facts the program is built on

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| A1 | `STARTING_DEBT = 6`, `EXECUTION_THRESHOLD = 50`, `BREAK_THRESHOLD = 30`, `INTEREST_RATE = 0.15`, `LEVERAGE_DIVISOR = 6` | code | `rg -n "STARTING_DEBT\|EXECUTION_THRESHOLD\|BREAK_THRESHOLD\|INTEREST_RATE\|LEVERAGE_DIVISOR" app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt` — **no `-E`**: in ripgrep `-E` is `--encoding` and the command exits 2 with `unknown encoding`. Alternation is on by default. |
| A2 | `LOAN_GOLD_BASE = 12`, `LOAN_DEBT_BASE = 8`, `UPGRADE_BASE = 15` and the upgrade cost is **not** escalated | code | `rg -n -B2 -A2 "UPGRADE_BASE\|LOAN_GOLD_BASE\|LOAN_DEBT_BASE" app/src/main/java/com/debtsdecks/core/combat/NodeConfig.kt` |
| A3 | The run is 8 slots: 3 thug, 3 loan_shark, 2 collector | data | `python3 -c "import json,collections;print(collections.Counter(s['enemyId'] for s in json.load(open('app/src/main/assets/run/sequence.json'))['slots']))"` |
| A4 | Exactly 3 enemies exist, and each is a strict superset of the previous one's verbs | data | `python3 -c "import json;[print(e['id'],[i['type'] for i in e['intentPattern']]) for e in json.load(open('app/src/main/assets/enemies/all.json'))]"` |
| A5 | `IntentType` has exactly 5 values | code | `rg -n "enum class IntentType" app/src/main/java/com/debtsdecks/core/enemies/EnemyDefinition.kt` |
| A6 | `RunManager.Phase` has 4 values, and **5** `when (phase)` sites of which **3** are exhaustive | code | `rg -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` then `rg -n -A6 "when \(.*phase\)" --glob '*.kt' app/src` and read each for an `else` branch. Corrected 2026-08-28: this row said "exhaustive `when` in 4 places". There are 5 sites; `CombatInputHandler.kt` (`else -> Unit`) and `NodePolicyTest.kt` (`else -> error(...)`) have `else` arms, so adding a `Phase` value would **not** break them — it would silently fall into the `else`. That is a weaker safety net than the phase docs assume, and it is the reason `SlotRole` was kept as a separate enum. The grep alone cannot decide exhaustiveness; a reader must open each site. |
| A7 | **There is no `startPlayerTurn`.** `applyInterest` is called from `private fun beginTurn()`, once per combat turn | code | `rg -n "startPlayerTurn" --glob '*.kt' app/src` (expect: nothing) then `rg -n "applyInterest\|fun beginTurn" app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt` |
| A8 | 27 cards; 44 `card.*.description` keys; 39 of them carry a hand-written number in each language | data | `python3 -c "import json,re;d=json.load(open('app/src/main/assets/cards/all.json'));print('cards',len(d));ks=[l for l in open('app/src/main/assets/i18n/strings.properties') if re.match(r'card\..*\.description=',l)];print('keys',len(ks),'with digits',sum(1 for l in ks if re.search(r'[0-9]',l.split('=',1)[1])))"` — **run it again against `strings_es.properties`**: the claim says "in each language" and this reads only the English file. Both sides give 44 / 39 as of `6b50164`. |
| A9 | The `@Test` count on `develop` matches what the last merged PR claimed | tests | Count from the **git tree**, not from disk: `git ls-tree -r develop --name-only \| rg '^app/src/test/.*\.kt$' \| while read f; do git show develop:"$f"; done \| rg -o "@Test" \| wc -l` — **199 as of `6b50164`** (it was 180 before PR #7). Corrected 2026-08-28: the command here read the working tree, which returned **201**, because a concurrent session had uncommitted F1 tests checked out. A row that reports another session's scratch work as `develop`'s state is worse than no row. An absolute count also rots on every merge; treat the number as a timestamped observation and compare against the count stated in the most recently merged PR, not against this row. |
| A10 | `Arts/` is gitignored with zero tracked files | git | `git check-ignore -v Arts/ ; git ls-files Arts/ \| wc -l` |
| A11 | `openspec/` is **not** ignored | git | `git check-ignore -v openspec/config.yaml` (expect exit 1, no output) |

## B. Claims this program makes that are NOT in the brief — check these hardest

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| B1 | The simulated **policies** are scale-coupled, not only the assertions: `LEVERAGE_TARGET = 35`, `SAFE_AFTER_LOAN = 45`, `REPAY_BAND = 25` — i.e. 0.70 / 0.90 / 0.50 of the execution line | test code | `rg -n "LEVERAGE_TARGET" app/src/test/java/com/debtsdecks/core/simulation/LeveragePolicy.kt; rg -n "SAFE_AFTER_LOAN\|REPAY_BAND\|LOAN_GOLD_NEED" app/src/test/java/com/debtsdecks/core/simulation/NodePolicy.kt` |
| B2 | The policy-gap assertion is **one-sided**, not a symmetric ±5pp | test code | `rg -n -B3 "leverage policy should stay within 5pp" app/src/test/java/com/debtsdecks/core/simulation/RunSimulationHarnessTest.kt` |
| B3 | Of the 44 description keys only **27 map to a live card**; 17 are orphans, and of the 27 live ones **23** carry a hand-written number per language — so the F3 parametrization job is 46 strings on the critical path, not 78 | data | `python3 -c "import json,re;ids={c['id'] for c in json.load(open('app/src/main/assets/cards/all.json'))};ks={};[ks.setdefault(m.group(1),m.group(2)) for l in open('app/src/main/assets/i18n/strings.properties') if (m:=re.match(r'card\.([a-z0-9_+]+)\.description=(.*)',l))];print('orphans',len(set(ks)-ids));print('live with digits',sum(1 for k,v in ks.items() if k in ids and re.search(r'[0-9]',v)))"` — again, **repeat for `strings_es.properties`**; the "per language" half of the claim is otherwise untested. Both give 23 as of `6b50164`, hence 46 strings, not 78. |
| B4 | **The release build type has no `signingConfig`** and no keystore is in the tree — so FV's external playtest cannot be distributed today | build | `rg -n "signingConfig" app/build.gradle.kts` (expect: nothing) then `bat --plain -r 21:30 app/build.gradle.kts` and `fd -d 1 -e jks -e keystore` |
| B5 | GDD success criterion #4 exists; no measurement of it is recorded in the repo | doc + history | `rg -n "third run" docs/GDD.md` then `git log --oneline -S"playtest" -- docs/` (10 commits as of `6b50164` — read them; none records a measurement). Corrected 2026-08-28: this row claimed the criterion "has never been measured", which **no command can establish** — absence from the repo is not absence in the world. The checkable claim is the narrower one now stated. |
| B6 | The band `[25, 45)` straddles `BREAK_THRESHOLD` (30 = 0.60 of execution), so "playing the band" means routinely summoning the collector | arithmetic on A1 | `python3 -c "print(25/50, 30/50, 45/50)"` |

## C. Per-phase acceptance (check when each phase ships, not now)

> **Precondition for every report-diff row (C3, C6), added 2026-08-28.**
> These rows diff a harness report against a saved baseline and demand they be identical.
> That demand was **not satisfiable when this checklist was written**: the harness was
> non-deterministic, so two runs of the *same* code disagreed. Measured: 53.5% / 54.0% /
> 54.5% greedy win rate across three sweeps of one commit; seed 172 alternated between
> victory and defeat inside a single JVM. Cause: the card-choice comparators tie-broke on
> `CardInstance.instanceId`, a fresh `UUID.randomUUID()` per instance.
>
> Fixed in [PR #7](https://github.com/OAlvarezOliveira/DebtsAndDecks/pull/7), **squash-merged
> into `develop` as `6b50164` on 2026-08-28**. Cite that SHA and no other: the branch commit
> (`f02b421`) is not an ancestor of `develop` and is absent from a fresh clone entirely. C3 and C6 are runnable from
> `develop` as of that commit. Before trusting either row, confirm the fix is present:
> `git log --oneline -S 'thenBy { it.cardId }' -- app/src/test/java/com/debtsdecks/core/simulation/`
> and check that `HarnessDeterminismTest` exists and passes.
>
> The same caveat applies backwards: **any balance number this program cites as a fact was
> measured on the broken gate.** Treat pre-2026-08-28 balance figures as approximate.

| # | Claim | Source | Command |
| --- | --- | --- | --- |
> **Where to run C1, C2 and C7 from, added 2026-08-28.** These three diff `develop...HEAD`.
> Run from `develop` itself — as this file's preamble otherwise instructs — that range is
> empty and all three print nothing, which reads as PASS while testing nothing. **Run them
> from the phase branch under review**, or they are theatre.

| C1 | F0 touched only `docs/` and `openspec/` | git | `git diff --name-only develop...HEAD \| rg -v '^docs/\|^openspec/'` (expect: nothing). Corrected 2026-08-28: this used `--stat`, which elides long paths to `.../…`, so the filter could not match them and the row reported a false failure on a branch that actually complied. |
| C2 | F0 added or changed no test | tests | `git diff develop...HEAD -- app/` is empty (the absolute test count is not a usable check — PR #7 changes it) |
| C3 | F1 is behaviour-preserving | harness | Do **not** depend on a `/tmp` baseline file: an independent verifier does not have yours, and this project has already lost working state to a wiped `/tmp`. Generate both sides yourself — run the harness on the merge-base and on the branch, extracting `<system-out>` from `app/build/test-results/testDebugUnitTest/*RunSimulationHarnessTest*.xml` each time, and compare by `sha256sum`. Identical, no tolerance. |
| C4 | F1's ratios resolve to the historical absolutes at `EXECUTION_THRESHOLD = 50` | test | `./gradlew test --tests '*HarnessBandsTest*'` |
| C5 | F1's derivation is live, not frozen at class-load | test | the stubbed-execution-line-of-100 case in `HarnessBandsTest` |
| C6 | F2 has zero balance delta | harness | Same self-generated procedure as C3. **Done for PR1**: the harness output hashed identically on both sides, and independently again after the merge. PR2 must repeat it. |
| C7 | F2 changed no enemy and no reward | git | **Compare the parsed values, not the diff.** The textual diff of `sequence.json` across `6b50164` rewrites every line (the file was reformatted to fit `districtId` and `role`), so a scan for `^-` reports eight deletions and reads as a failure on a change that altered nothing. Extract `(enemyId, rewards.gold, rewards.cardChoices)` per slot from `git show 6b50164^:app/src/main/assets/run/sequence.json` and from `git show 6b50164:...`, and compare the two lists. **Run 2026-08-28: identical, all 8 slots** — `thug 10/1, thug 10/1, loan_shark 15/1, thug 12/1, loan_shark 18/2, loan_shark 20/1, collector 25/1, collector 30/0`. *Corrected the same day: this row named `develop...HEAD`, which is empty on the docs branch and would have passed while testing nothing, and expected "only added fields", which the reformat makes false.* |
| C8 | F2 added no run phase | code | `rg -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` — still 4 values |
| C9 | F2's new backgrounds carry no lettering | eyes | open the three PNGs. This one cannot be automated and must not be skipped for that reason. |
| C10 | Every `district.*` key exists in both bundles | data | `python3 -c "import re;g=lambda f:{re.match(r'(district\.[a-z_.]+)=',l).group(1) for l in open(f) if re.match(r'district\.',l)};a=g('app/src/main/assets/i18n/strings.properties');b=g('app/src/main/assets/i18n/strings_es.properties');print('EN only',a-b,'ES only',b-a)"` — note the filter (`district\.`) is looser than the extractor (`district\.[a-z_.]+=`), so a key with a digit or a capital raises `AttributeError` instead of reporting a mismatch. A crash here is a **finding**, not a broken command. Clean as of `6b50164` (`EN only set() ES only set()`). This row is also the one place the raw-file form was right all along — see E8. |

## D. Things that are judgement, not fact

These cannot be verified by command. They need a human to disagree with them, which is a
different and more valuable thing:

- **D9 (no card renumbering).** Is the two-unit reading — dollars for the ledger, points for
  damage — actually clean for a player, or is it the double mental language the brief warned
  about? Nobody has playtested it.
- **The starting-pressure ratio.** The program says 12%-vs-58% is a design question for F3.
  It could equally be a sign that the vision wants a different game than the one the harness
  is currently calibrating.
- **The three FV verbs.** FORECLOSE, AUDIT and HEDGE are argued to be non-overlapping. That
  argument is reasoning, not evidence, and FV's E1 criterion exists to test it.
- **Phase order.** The one hard dependency found is B4. A second reader should look for
  another; the strongest candidate is whether F5's bosses can really be designed before F8's
  leads exist, given each boss needs a weakness that F8 will reveal.

---

## What "closed" means

A row is closed when the command has been run **in a pass with no memory of writing these
documents** and the output is recorded next to it. Not when it looks right. Not when a
summary says it passed.

---

## E. Corrections applied to this program on 2026-08-28

The program was written on 2026-08-28 and reconciled the same day, once its first phase had
actually been implemented. What follows is what the reconciliation changed and why. It is a
log of defects in the *program*, not in the code.

| # | Defect | Where | What changed |
| --- | --- | --- | --- |
| E1 | **R0.5 was violated by its own program.** The F0 spec requires that no claim be asserted as verified, and its scenario says a reviewer searching for the word finds none. Four places asserted it anyway. | `config.yaml:9`, `f1/proposal.md` ×2, `fv/proposal.md`, `f7/charter.md` | Each rewritten as a claim pointing at the checklist row that carries its command. **Re-run 2026-08-28**, and the honest result is not "R0.5 now holds": `rg -in "verified\|confirmed\|validated" openspec/ \| rg -v VERIFICATION-CHECKLIST` still returns 10 lines. Every one of them is either the rule stating itself, a statement about what the *owner* decided (`fv/proposal.md:30`, `f5/charter.md:50`), a *future* obligation (`f4/charter.md:48`), or a note recording that this check was run. None asserts a technical claim as verified. The gate is a read, not a count — a hit count cannot pass or fail it, and writing down that it passed raises the count. |
| E2 | **The zero-delta gates were unrunnable.** C3 and C6 demand an identical report across two runs; the harness was non-deterministic, so identical runs disagreed. Nothing in the program mentioned it — the defect was found after these documents were written. | C3, C6, `f1/tasks.md`, `f2/tasks.md`, both proposals | Precondition added above section C, and a blocking-dependency note in each phase. The determinism fix is on `develop` in `6b50164` (`git log --oneline -S 'thenBy { it.cardId }' develop -- app/src/test/java/com/debtsdecks/core/simulation/`), so as of 2026-08-28 `develop` versus the branch is a valid comparison again. Before that merge it was not, and the row used to name the branch commit `f02b421`, which is not an ancestor of `develop`. |
| E3 | **Balance facts were measured on the broken gate.** Any number this program cites was read off a noisy instrument. | program-wide | Stated in the section C precondition. Pre-2026-08-28 balance figures are approximate. |
| E4 | **Absolute test counts were already stale** when written, because implementing a phase changes them. | C2, `f1/tasks.md` 5.3, `f0/tasks.md` 5.3, `f0/specs/vision-program/spec.md` R0.4, A9 | The first pass fixed two and claimed that was all of them; it was not. `rg -n "180" openspec/` found **two more** — `f0/specs/vision-program/spec.md` R0.4 and `f0/tasks.md` 5.3 — and the first of those sat inside a **delivered spec scenario**, making a requirement false rather than merely a note stale. (The `180` still in `f1/proposal.md` is a review-size estimate in lines, not a test count, and is left alone.) All are now relative checks — an empty diff, or equality with the merge-base counted from the git tree. A9 keeps a number on purpose but labels it a timestamped observation. **Count from `git show`, never from the working tree**: a concurrent session's uncommitted tests made the on-disk count 201 while `develop` and `HEAD` both held 199. |
| E5 | **F2's tasks described work that had already shipped**, under names that differ from the shipped ones. Following the file literally would have meant reimplementing PR #7 and colliding with it. | `f2/tasks.md` | Reconciled against what shipped. Shipped names win over planned names and are recorded inline. Task 2.4, the one PR1 item genuinely unmet at reconciliation time, was then implemented and is now ticked. **All of PR1 is on `develop` in the single squash commit `6b50164`**; this row used to cite `38e0b9b` and `c018648`, which are branch commits — `git merge-base --is-ancestor 38e0b9b develop` fails, and a fresh clone does not have the object at all. Locate the work by content: `git log --oneline -S 'districtId' develop -- app/src/main/java/com/debtsdecks/core/data/DataLoader.kt` → `6b50164`. |
| E6 | **F1 was gated on "the post-FV `develop`"**, but FV cannot complete — B4 shows `release` has no `signingConfig` and no keystore exists, so its external playtest is undistributable. | `f1/tasks.md` 6.1 | Re-gated on PR #7, which is the real prerequisite. FV is independent of F1. |
| E7 | **Prescribed commands used `grep`, `sed` and `ls`**, against the project's stated tooling convention. | this file | Rewritten as `rg`, `bat` and `fd`. |
| E8 | **A translation-parity test that goes through the localization API cannot fail.** PR #7 shipped an i18n check calling `bundle.get(key)`; libGDX's `I18NBundle` falls back to the parent bundle, so a key missing only from `strings_es.properties` resolved to the English string and the assertion passed. The PR claimed an untranslated district failed the build. It did not. | PR #7, and any future phase adding user-facing text | Fixed in `6b50164` (`git log --oneline -S 'rawProperties' develop -- app/src/test/java/com/debtsdecks/core/i18n/`; the branch commits `4597e61`/`503d75c` this row used to cite are not ancestors of `develop` and do not exist in a fresh clone): parity is checked against the raw `.properties` key sets, and a sibling test builds a throwaway bundle pair under `@TempDir` where a key exists only in English, asserting that the Spanish bundle answers **with the English string** — that is the fallback, reproduced, so the guard cannot quietly go vacuous again. **C10 below was already written in the raw-file form and is correct** — the defect was in the Kotlin test, not in this checklist. Rule for later phases: a parity guard never consults the API that implements the fallback, and is proven by deleting a key and watching it go red. |

**This section is itself unverified.** It is written by the same pass that made the changes,
so it carries the same blind spots as everything else here. Check it the way you would check
any other row: against `git diff`, not against this table.
