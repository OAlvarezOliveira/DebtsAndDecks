# Independent verification checklist

**This program is delivered unverified, on purpose.**

The pass that produced these documents cannot be the pass that certifies them: whoever writes
something and then reviews it shares its blind spots. Every row below must be checked by
someone — a new session, a fork, a human — working **against the source named in the row**,
never against this program, never against a prior summary, and never against the document
that makes the claim.

A row is closed by recording the **command that was run and what it printed**. "Verified" on
its own closes nothing.

All commands run from **a checkout of this repository on `develop`** unless stated — any
checkout, not one machine's. *Corrected 2026-08-28: this named an absolute path that exists
on exactly one machine, and not even in the worktree these checks were run from. The three
rows that diff `develop...HEAD` are the exception; see the note above section C.*

---

## A. Facts the program is built on

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| A1 | `STARTING_DEBT = 6`, `BREAK_THRESHOLD = 30`, `INTEREST_RATE = 0.15`, `LEVERAGE_DIVISOR = 6`, and — since `fv-e1-arrears-lock` — `ARREARS_THRESHOLD = 40` plus `DEBT_SCALE_ANCHOR = 50` in place of the deleted `EXECUTION_THRESHOLD` | code | `rg -n "STARTING_DEBT\|ARREARS_THRESHOLD\|DEBT_SCALE_ANCHOR\|BREAK_THRESHOLD\|INTEREST_RATE\|LEVERAGE_DIVISOR" app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt` — **no `-E`**: in ripgrep `-E` is `--encoding` and the command exits 2 with `unknown encoding`. Alternation is on by default. *Corrected 2026-08-29: this row asserted `EXECUTION_THRESHOLD = 50`, which `fv-e1-arrears-lock` renamed to `DEBT_SCALE_ANCHOR` while adding the behavioral `ARREARS_THRESHOLD = 40`. The old command still exits 0 and prints four of five lines, so it read as PASS while its second claim had become false — confirm the deletion separately with `rg -n EXECUTION_THRESHOLD app/src` (expect: nothing). The rename is only on the `fv-e1-arrears-lock` branch until it merges; on `develop` the old name is still the live one.* |
| A2 | `LOAN_GOLD_BASE = 12`, `LOAN_DEBT_BASE = 8`, `UPGRADE_BASE = 15` and the upgrade cost is **not** escalated | code | `rg -n -B2 -A2 "UPGRADE_BASE\|LOAN_GOLD_BASE\|LOAN_DEBT_BASE" app/src/main/java/com/debtsdecks/core/combat/NodeConfig.kt` |
| A3 | The run is 8 slots: 3 thug, 3 loan_shark, 2 collector | data | `python3 -c "import json,collections;print(collections.Counter(s['enemyId'] for s in json.load(open('app/src/main/assets/run/sequence.json'))['slots']))"` |
| A4 | Exactly 3 enemies exist, and each is a strict superset of the previous one's verbs | data | `python3 -c "import json;[print(e['id'],[i['type'] for i in e['intentPattern']]) for e in json.load(open('app/src/main/assets/enemies/all.json'))]"` |
| A5 | `IntentType` has exactly 5 values | code | `rg -n "enum class IntentType" app/src/main/java/com/debtsdecks/core/enemies/EnemyDefinition.kt` |
| A6 | `RunManager.Phase` has 4 values, and **5** `when (phase)` sites of which **3** are exhaustive | code | `rg -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` then `rg -n -A6 "when \(.*phase\)" --glob '*.kt' app/src` and read each for an `else` branch. Corrected 2026-08-28: this row said "exhaustive `when` in 4 places". There are 5 sites; `CombatInputHandler.kt` (`else -> Unit`) and `NodePolicyTest.kt` (`else -> error(...)`) have `else` arms, so adding a `Phase` value would **not** break them — it would silently fall into the `else`. That is a weaker safety net than the phase docs assume, and it is the reason `SlotRole` was kept as a separate enum. The grep alone cannot decide exhaustiveness; a reader must open each site. |
| A7 | **There is no `startPlayerTurn`.** `applyInterest` is called from `private fun beginTurn()`, once per combat turn | code | `rg -n "startPlayerTurn" --glob '*.kt' app/src` (expect: nothing) then `rg -n "applyInterest\|fun beginTurn" app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt` |
| A8 | 27 cards; 44 `card.*.description` keys; 39 of them carry a hand-written number in each language | data | `python3 -c "import json,re;d=json.load(open('app/src/main/assets/cards/all.json'));print('cards',len(d));ks=[l for l in open('app/src/main/assets/i18n/strings.properties') if re.match(r'card\..*\.description=',l)];print('keys',len(ks),'with digits',sum(1 for l in ks if re.search(r'[0-9]',l.split('=',1)[1])))"` — **run it again against `strings_es.properties`**: the claim says "in each language" and this reads only the English file. Both sides give 44 / 39 as of `6b50164`. |
| A9 | The `@Test` count on `develop` matches what the last merged PR claimed | tests | Count from the **git tree**, not from disk: `git ls-tree -r develop --name-only \| rg '^app/src/test/.*\.kt$' \| while read f; do git show develop:"$f"; done \| rg -o "@Test" \| wc -l` — **199 as of `6b50164`; 201 as of `c8ebcd3`**, the two added by `3a7c201` being `HarnessBandsTest` (it was 180 before PR #7). Corrected 2026-08-28: the command here read the working tree, which returned **201**, because a concurrent session had uncommitted F1 tests checked out. A row that reports another session's scratch work as `develop`'s state is worse than no row. An absolute count also rots on every merge; treat the number as a timestamped observation and compare against the count stated in the most recently merged PR, not against this row. |
| A10 | `Arts/` is gitignored with zero tracked files | git | `git check-ignore -v Arts/ ; git ls-files Arts/ \| wc -l` |
| A11 | `openspec/` is **not** ignored | git | `git check-ignore -v openspec/config.yaml` (expect exit 1, no output) |

## B. Claims this program makes that are NOT in the brief — check these hardest

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| B1 | **Historical, and now fixed.** The simulated *policies* were scale-coupled, not only the assertions: `LEVERAGE_TARGET = 35`, `SAFE_AFTER_LOAN = 45`, `REPAY_BAND = 25` — i.e. 0.70 / 0.90 / 0.50 of the execution line. This is the defect F1 existed to remove, and F1 shipped as `3a7c201`, so **running the command below against today's `develop` will not reproduce it**: all three now read `get() = HarnessBands.<band>`. Re-point it at `6b50164` to see the original. `LOAN_GOLD_NEED = 20` is the one that stays absolute, on purpose — it is coupled to the gold economy, and F3 picks its anchor. | test code | `rg -n "LEVERAGE_TARGET" app/src/test/java/com/debtsdecks/core/simulation/LeveragePolicy.kt; rg -n "SAFE_AFTER_LOAN\|REPAY_BAND\|LOAN_GOLD_NEED" app/src/test/java/com/debtsdecks/core/simulation/NodePolicy.kt` |
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

> **Where to run C1, C2 and C7 from, added 2026-08-28.** These three diff `develop...HEAD`.
> Run from `develop` itself — as this file's preamble otherwise instructs — that range is
> empty and all three print nothing, which reads as PASS while testing nothing. **Run them
> from the phase branch under review**, or they are theatre. *Moved above the table the same
> day: it sat between the delimiter row and the first body row, which ends the table in GFM,
> so rows C1-C10 rendered on GitHub as literal pipe characters.*

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| C1 | F0 touched only `docs/` and `openspec/` | git | `git diff --name-only develop...HEAD \| rg -v '^docs/\|^openspec/'` (expect: nothing). Corrected 2026-08-28: this used `--stat`, which elides long paths to `.../…`, so the filter could not match them and the row reported a false failure on a branch that actually complied. |
| C2 | F0 added or changed no test | tests | `git diff develop...HEAD -- app/` is empty (the absolute test count is not a usable check — PR #7 changes it) |
| C3 | F1 is behaviour-preserving | harness | **F1 shipped as `3a7c201` (PR #9). This row is still open, because shipping is not the same as proving.** Nothing in this repo records the two hashes for F1's own before/after comparison; the claim rests on PR #9's description, which is not a source of truth. Close it by generating both sides yourself: the recipe in C6, on `3a7c201^` and on `3a7c201`, same locale. Do **not** depend on a `/tmp` baseline file: an independent verifier does not have yours, and this project has already lost working state to a wiped `/tmp`. Generate both sides yourself with the recipe in C6, on the merge-base and on the branch, and compare the two hashes. Identical, no tolerance. The pre-F1 side of that comparison is already recorded: `b0313d603580` on `6b50164`. |
| C4 | F1's ratios resolve to the historical absolutes at `EXECUTION_THRESHOLD = 50` | test | `./gradlew test --tests '*HarnessBandsTest*'` — **runnable on `develop` since `3a7c201`**; `HarnessBands.kt`'s own KDoc states the same invariant ("at EXECUTION_THRESHOLD = 50 these resolve to 25.0, 45.0, 35, 45, 25"), so the test and the doc must agree or one of them is wrong. |
| C5 | F1's derivation is live, not frozen at class-load | test | the stubbed-execution-line-of-100 case in `HarnessBandsTest` — **runnable on `develop` since `3a7c201`**. Read the bands' declarations too: `get()` rather than constructor values is what the row actually asserts. |
| C6 | F2 has zero balance delta | harness | **Open for PR2; recorded for PR1.** Run the recipe in **§C6 recipe** below and compare hashes. Value on `6b50164`: `b0313d603580`, twice, under `LANG=es_ES.UTF-8` — the hash is locale-dependent, see the section. |
| C7 | F2 changed no enemy and no reward | git | **Compare the parsed values, not the diff.** The textual diff of `sequence.json` across `6b50164` rewrites every line (the file was reformatted to fit `districtId` and `role`), so a scan for `^-` reports eight deletions and reads as a failure on a change that altered nothing. Extract `(enemyId, rewards.gold, rewards.cardChoices)` per slot from `git show 6b50164^:app/src/main/assets/run/sequence.json` and from `git show 6b50164:...`, and compare the two lists. **Run 2026-08-28: identical, all 8 slots** — `thug 10/1, thug 10/1, loan_shark 15/1, thug 12/1, loan_shark 18/2, loan_shark 20/1, collector 25/1, collector 30/0`. *Corrected the same day: this row named `develop...HEAD`, which is empty on the docs branch and would have passed while testing nothing, and expected "only added fields", which the reformat makes false.* |
| C8 | F2 added no run phase | code | `rg -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` — still 4 values |
| C9 | F2's new backgrounds carry no lettering | eyes | open the three PNGs. This one cannot be automated and must not be skipped for that reason. |
| C10 | Every `district.*` key exists in both bundles | data | `python3 -c "import re;g=lambda f:{re.match(r'(district\.[a-z_.]+)=',l).group(1) for l in open(f) if re.match(r'district\.',l)};a=g('app/src/main/assets/i18n/strings.properties');b=g('app/src/main/assets/i18n/strings_es.properties');print('EN only',a-b,'ES only',b-a)"` — note the filter (`district\.`) is looser than the extractor (`district\.[a-z_.]+=`), so a key with a digit or a capital raises `AttributeError` instead of reporting a mismatch. A crash here is a **finding**, not a broken command. Clean as of `6b50164` (`EN only set() ES only set()`). This row is also the one place the raw-file form was right all along — see E8. |

### C6 recipe — the zero-delta measurement

*Lifted out of the table 2026-08-28: a fenced code block inside a table cell ends the table in GFM, so rows C7-C10 rendered as literal pipe characters. Same defect as E13, one row further down.*

**The command, and what it printed.** Run from a worktree on the ref under test (the docs branch's `app/` is byte-identical to `develop`, so it measures `develop`):

```
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # gitignored; the build needs it
./gradlew testDebugUnitTest --tests '*RunSimulationHarnessTest*' --rerun-tasks -q
python3 -c "import re,hashlib;s=open('app/build/test-results/testDebugUnitTest/TEST-com.debtsdecks.core.simulation.RunSimulationHarnessTest.xml',encoding='utf-8').read();print(hashlib.sha256(re.search(r'<system-out><!\[CDATA\[(.*?)\]\]></system-out>',s,re.S).group(1).encode()).hexdigest()[:12])"
```

**Run twice on `6b50164` (2026-08-28), independently of the pass that wrote the fix: `b0313d603580` both times.** 13 tests, 0 failures. Report body: greedy win 54.0% / peak debt 30.8 / HP@win 12.6; leverage win 49.5% / peak debt 30.3; defeats `{collector=92}` greedy, `{collector=101}` leverage.

> **The hash is locale-dependent, and that is a latent defect, not a property of the balance.** `SimulationReport` formats with `"%.1f".format(...)` and no `Locale.US`, so on this machine (`es_ES`) it prints `54,0%` and on `en_US` it prints `54.0%` — same simulation, different hash. Compare hashes only between runs in the **same locale**, or normalize the decimal separator before hashing. **F1 was expected to carry the fix and did not.** `3a7c201` shipped without adding
`Locale.US` anywhere, and added three more locale-dependent sites while it was there —
`"%.3f".format(...)` inside the assertion messages at `RunSimulationHarnessTest.kt:247`, `:251`
and `:285`. Count them on the trunk:
`git show develop:app/src/test/java/com/debtsdecks/core/simulation/RunSimulationHarnessTest.kt | rg -c 'format\('`
→ **7**, none with a locale. The defect is therefore wider than this row's original scope
(which named only `SimulationReport`) and it is now unowned: whichever phase next touches the
harness picks it up, and until then every hash in this file is only comparable against another
hash produced in the same locale. *Corrected 2026-08-28: this row previously read "Done for PR1: the harness output hashed identically on both sides" — an assertion with no command and no hash, closing the single largest claim in the programme ("provably zero balance delta") by exactly the mechanism this file's own preamble forbids. **PR2 must repeat the recipe above and record its own hash.**

### C.AL — `fv-e1-arrears-lock` (FV.E1 "En Mora") acceptance, added 2026-08-29 by its own Phase 8.2

> **Read this before reading the rows.** Every number cited below was produced by the pass that
> implemented the change, from `app/build/test-results/testDebugUnitTest/TEST-*.xml` `system-out`,
> and is recorded in `docs/BALANCE-BASELINE.md` §"Post fv-e1-arrears-lock". By this file's own
> rule that is **evidence, not closure**: none of these rows is closed until a pass with no memory
> of writing them re-runs the command and records its own output. The rows exist so that pass has
> a command to run instead of a summary to trust.
>
> **Where to run them.** `fv-e1-arrears-lock` is implemented on branch
> `feat/fv-verbs-foreclose-hedge` (the `DebtsAndDecks-fv-e1-leverage` worktree) and **not merged**: on `develop` these commands describe code that is not there, and
> `EXECUTION_THRESHOLD` is still the live constant name. Run them from the change branch.
>
> **Locale.** Cited harness numbers were captured under `LANG=C`, so report decimals print as
> dots. The §C6 locale defect is unfixed and still unowned — compare hashes only within one locale.

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| AL1 | Instant Execution is **gone**, not merely bypassed: no `EXECUTION_THRESHOLD` symbol survives anywhere under `app/src`, and no debt-driven `endCombat(false)` path remains | code | `rg -n "EXECUTION_THRESHOLD" app/src` (expect: nothing, exit 1) then read `CombatEngine.addDebt` and `armArrearsIfCrossed` — `rg -n -A6 "private fun armArrearsIfCrossed" app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt`. The grep alone cannot prove the second half of the claim; a reader must confirm the arm path returns instead of ending combat. Recorded 2026-08-29: grep prints nothing |
| AL2 | The two constants are split, distinct, and the split is test-enforced — `ARREARS_THRESHOLD = 40` (behavioral) vs `DEBT_SCALE_ANCHOR = 50` (harness/loan scale only) | test + code | `<cached-gradle>/bin/gradle --no-daemon :app:testDebugUnitTest --tests '*DebtConfigTest'`, which asserts both values, `!=` between them, positivity, and `DEBT_SCALE_ANCHOR > BREAK_THRESHOLD`. Then check the call-site division by hand: `rg -n "ARREARS_THRESHOLD\|DEBT_SCALE_ANCHOR" app/src` — behavioral sites (`CombatEngine.armArrearsIfCrossed`, `CombatRenderer:365,388`) must read `ARREARS_THRESHOLD`; scale sites (`HarnessBands`, `ScriptedPolicy`, `LeveragePolicy`, `RespondingPolicy`, `RunManager.takeLoan`, `CombatRenderer:742`) must read `DEBT_SCALE_ANCHOR`. **A behavioral site reading the anchor, or a policy reading 40, is a finding** — design D3: pointing the blind policies at 40 makes them lock-avoidant and reproduced a 0/200 fire rate in a prior attempt |
| AL3 | The lock's unit contract holds: arms at exactly 40 (`>=`, not `>`), no arm at 39, arms on a 38→42 jump, never re-arms in the same combat once the charge is spent, a dip to 1 keeps it, `debt == 0` clears it, and passive interest is frozen while armed but card-applied debt is not | tests | `<cached-gradle>/bin/gradle --no-daemon :app:testDebugUnitTest --tests '*CombatEngineTest' --tests '*CombatStateTest'`. `CombatStateTest.kt` is a **new file** in this change; on `develop` it does not exist |
| AL4 | Gatillo B: last enemy dead while `inArrears` resolves `Phase.DEFEAT`; escape-then-kill resolves `Phase.VICTORY`; the check does not fire outside `COMBAT_END` | tests | `<cached-gradle>/bin/gradle --no-daemon :app:testDebugUnitTest --tests '*RunManagerTest'`, and read the branch order in `RunManager.refresh()` — `rg -n -B4 -A4 "state.inArrears" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` must sit after the `allEnemiesDead` check and **before** garnishment |
| AL5 | E1 gate green post-change — and the gate is **not** the `10.0pp` response gap `tasks.md` 7.1 names | harness | `<cached-gradle>/bin/gradle --no-daemon :app:testDebugUnitTest --tests '*IntentVerbsE1Test'`. Recorded 2026-08-29, 200 seeds/policy: `Responding verbs-on 49.0% / off 16.0% / weight 33.0pp`, `Ignoring 47.0% / 25.5% / 21.5pp`, response gap **+2.0pp**. **The literal 10pp assertion is absent on purpose** — the 2026-08-28 re-metric (§"FV criterion E1" in `docs/BALANCE-BASELINE.md`) replaced it with `responseGap >= -5.0` (advisory) plus difficulty-weight floors `>= 20.0pp` / `>= 15.0pp`, and this change did not re-open that decision. A verifier reading `tasks.md` 7.1 alone will look for a gate the test does not contain; read the test, not the task |
| AL6 | E2 bands hold post-change and the lock actually fires for **both** blind policies (a lock that never arms is decoration, design D2) | harness | `<cached-gradle>/bin/gradle --no-daemon :app:testDebugUnitTest --tests '*RunSimulationHarnessTest'`. Recorded 2026-08-29, 200 seeds/policy: greedy `win 47.5% / peak 30.1 / HP@win 20.0 / arrears fire 2.5%`, leverage `win 47.0% / peak 30.1 / HP@win 21.0 / arrears fire 0.5%`. Asserted: win rate ∈ `[0.35,0.55]`, peak debt ∈ `[25,45)`, neither policy ≥ 70%, leverage within 5pp of greedy. **Fire rate > 0 for both is the row's real content** — leverage's 0.5% is ~1 run in 200, so this is the fragile assertion in the whole change: any tuning that lowers peak debt can silently take it to zero |
| AL7 | The post-change balance delta is attributable, and the owner-locked `40`/Gatillo-B-`on` choice is not re-decided by measurement | harness | The 2×2 sweep recorded in `docs/BALANCE-BASELINE.md` §7.3 (`ARREARS_THRESHOLD ∈ {40,45}` × Gatillo B `{on,off}`, 200 seeds/policy, **temporary local source overrides, not shipped**). Recorded reading: Gatillo B is the only lever that moves anything (off lifts greedy win 47.5% → 49.5%); 40 vs 45 changes zero outcomes because peak debt ~30 never approaches either line. To re-run it you must re-apply the overrides yourself — `git status` must show `DebtConfig.kt` and `RunManager.kt` clean of them afterwards, which is the only part of this row a command can check today |
| AL8 | Determinism survives the new state | test | `<cached-gradle>/bin/gradle --no-daemon :app:testDebugUnitTest --tests '*HarnessDeterminismTest'` — 3/3, matching the pre-change control in `docs/BALANCE-BASELINE.md` §"Pre fv-e1-arrears-lock" task 1.2. The mechanism claim is checkable by reading `CombatState`: the change adds two `Boolean`s and one `Int`, no UUID-keyed map and no iteration-order dependence |
| AL9 | Full-suite regression count, as a **timestamped observation** and not a threshold (E4's rule) | tests | `<cached-gradle>/bin/gradle --no-daemon :app:testDebugUnitTest --rerun-tasks` then sum the XML: `python3 -c "import glob,xml.etree.ElementTree as ET;r=[ET.parse(p).getroot() for p in glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml')];print('classes',len(r),'tests',sum(int(x.get('tests')) for x in r),'failures',sum(int(x.get('failures')) for x in r),'skipped',sum(int(x.get('skipped')) for x in r))"`. Recorded 2026-08-29 on the change branch: **29 classes, 251 tests, 0 failures, 2 skipped**. The 2 skipped are the pre-existing `@Disabled` F2 gates in `DebtPressureTest.kt` (open F2 design debt, unrelated to this change) — this change renamed their symbols (`DefeatCause.EXECUTION` → `ARREARS`, `EXECUTION_SHARE_FLOOR` → `ARREARS_SHARE_FLOOR`) to keep the package compiling and left them disabled. Do not compare 251 against a later commit; compare against the count stated by the most recently merged PR |
| AL10 | **Open defect, not a passing row.** The on-screen warning the lock now triggers still describes the deleted mechanic: `hud.execution_warning` reads `DEBT OVER EXECUTION — ANY NEW DEBT KILLS` (EN) / `DEUDA SOBRE EJECUCIÓN — CUALQUIER DEUDA NUEVA MATA` (ES), while `CombatRenderer:388` gates it on `debt >= ARREARS_THRESHOLD` and new debt no longer kills. **The game tells the player a falsehood at exactly the moment the mechanic fires.** | data + code | `rg -n "hud.execution_warning" app/src/main/assets/i18n/ app/src/main/java/com/debtsdecks/gdx/render/CombatRenderer.kt`. Not fixed by this change: design D6 deferred UI/i18n to "the two log keys" only (`log.arrears_locked{,_levy}`, which *were* renamed), and `tasks.md` 6.2 lists only those two. There is **no headless GL harness**, so no test catches it — it is visible only in a manual playtest. Verify by reading the two bundles; close it by owning the string in a follow-up change |

**Two things this change did not do, named so nobody has to discover them.** `docs/ANALISIS-*` are
point-in-time records and were deliberately left untouched (`git status docs/` shows only
`BALANCE-BASELINE.md` modified) — they still describe the Execution death line, correctly, as of
their own date. And `openspec/config.yaml`'s `balance_gate` block, `docs/VISION.md`,
`docs/TRACKING.md` and `docs/PLAN-PI.md` still name `EXECUTION_THRESHOLD` as a live constant: those
are **live** documents, not records, so each is a real stale reference — out of this change's
declared documentation scope (`docs/GDD.md` + this file) and therefore **unowned**, exactly like
§C6's locale defect. Whichever change next touches them picks them up.

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

> **`f1/…` in the Where column points at files this PR does not carry.** F1's four artifacts
> were written on this branch; F1 was then implemented from them and shipped as `3a7c201`
> (PR #9), leaving the branch snapshot describing an unstarted phase. Rather than merge a false
> project state, the folder was removed from this PR and returns in its own, reconciled to what
> shipped. The rows below are kept verbatim because they are a log of *what was corrected and
> when* — rewriting their Where column to hide a path would defeat the point of keeping the log
> at all. Recover the files with
> `git log --oneline --diff-filter=D -- openspec/changes/f1-harness-ratio-normalization/`.

| # | Defect | Where | What changed |
| --- | --- | --- | --- |
| E1 | **R0.5 was violated by its own program.** The F0 spec requires that no claim be asserted as verified, and its scenario says a reviewer searching for the word finds none. Four places asserted it anyway. | `config.yaml:9`, `f1/proposal.md` ×2, `fv/proposal.md`, `f7/charter.md` | Each rewritten as a claim pointing at the checklist row that carries its command. **Re-run 2026-08-28 after F1's folder was removed**, and the honest result is not "R0.5 now holds": `rg -in "verified\|confirmed\|validated" openspec/ \| rg -v VERIFICATION-CHECKLIST \| wc -l` returns **16**. This row has now carried three different numbers (10, then 15, then 16) and every one of them was a number nobody re-derived after editing the tree the command reads — which is the whole failure mode, one level up. Treat it as a timestamped observation, not a threshold. Reading the 16: most are the rule stating itself, a statement about what the *owner* decided (`fv/proposal.md:33`, `f5/charter.md:50`), a *future* obligation (`f4/charter.md:48`), or a note recording that this check was run. **Two are not, and they are named rather than waved past:** `f2/specs/run-structure/spec.md:82` and `f2/tasks.md:130` both say "verified" of the 8-slot reward comparison. They are the mild form — each carries its evidence inline (`thug 10/1, thug 10/1, loan_shark 15/1, …`) and row C7 carries the command that reproduces it — but they do assert a technical claim as verified, so the accurate statement is "two remain, with their evidence attached", not "none". *This row said "10 lines" and was wrong at every commit on the branch — it was written without running the command, in the row whose entire content is a count, inside the file whose rule is that a row closes by recording what a command printed. The lexical sweep was never the real gate anyway: R0.5's two actual failures on this branch (C6 closing the balance gate with no command, and this row) used none of those three words.* The gate is a read, not a count — a hit count cannot pass or fail it, and writing down that it passed raises the count. |
| E2 | **The zero-delta gates were unrunnable.** C3 and C6 demand an identical report across two runs; the harness was non-deterministic, so identical runs disagreed. Nothing in the program mentioned it — the defect was found after these documents were written. | C3, C6, `f1/tasks.md`, `f2/tasks.md`, both proposals | Precondition added above section C, and a blocking-dependency note in each phase. The determinism fix is on `develop` in `6b50164` (`git log --oneline -S 'thenBy { it.cardId }' develop -- app/src/test/java/com/debtsdecks/core/simulation/`), so as of 2026-08-28 `develop` versus the branch is a valid comparison again. Before that merge it was not, and the row used to name the branch commit `f02b421`, which is not an ancestor of `develop`. |
| E3 | **Balance facts were measured on the broken gate.** Any number this program cites was read off a noisy instrument. | program-wide | Stated in the section C precondition. Pre-2026-08-28 balance figures are approximate. |
| E4 | **Absolute test counts were already stale** when written, because implementing a phase changes them. | C2, `f1/tasks.md` 5.3, `f0/tasks.md` 5.3, `f0/specs/vision-program/spec.md` R0.4, A9 | The first pass fixed two and claimed that was all of them; it was not. `rg -n "180" openspec/` found **two more** — `f0/specs/vision-program/spec.md` R0.4 and `f0/tasks.md` 5.3 — and the first of those sat inside a **delivered spec scenario**, making a requirement false rather than merely a note stale. (The `180` still in `f1/proposal.md` is a review-size estimate in lines, not a test count, and is left alone.) All are now relative checks — an empty diff, or equality with the merge-base counted from the git tree. A9 keeps a number on purpose but labels it a timestamped observation. **Count from `git show`, never from the working tree**: a concurrent session's uncommitted tests made the on-disk count 201 while `develop` and `HEAD` both held 199. |
| E5 | **F2's tasks described work that had already shipped**, under names that differ from the shipped ones. Following the file literally would have meant reimplementing PR #7 and colliding with it. | `f2/tasks.md` | Reconciled against what shipped. Shipped names win over planned names and are recorded inline. Task 2.4, the one PR1 item genuinely unmet at reconciliation time, was then implemented and is now ticked. **All of PR1 is on `develop` in the single squash commit `6b50164`**; this row used to cite `38e0b9b` and `c018648`, which are branch commits — `git merge-base --is-ancestor 38e0b9b develop` fails, and a fresh clone does not have the object at all. Locate the work by content: `git log --oneline -S 'districtId' develop -- app/src/main/java/com/debtsdecks/core/data/DataLoader.kt` → `6b50164`. |
| E6 | **F1 was gated on "the post-FV `develop`"**, but FV cannot complete — B4 shows `release` has no `signingConfig` and no keystore exists, so its external playtest is undistributable. | `f1/tasks.md` 6.1 | Re-gated on PR #7, which is the real prerequisite. FV is independent of F1. |
| E7 | **Prescribed commands used `grep`, `sed` and `ls`**, against the project's stated tooling convention. | this file | Rewritten as `rg`, `bat` and `fd`. |
| E8 | **A translation-parity test that goes through the localization API cannot fail.** PR #7 shipped an i18n check calling `bundle.get(key)`; libGDX's `I18NBundle` falls back to the parent bundle, so a key missing only from `strings_es.properties` resolved to the English string and the assertion passed. The PR claimed an untranslated district failed the build. It did not. | PR #7, and any future phase adding user-facing text | Fixed in `6b50164` (`git log --oneline -S 'rawProperties' develop -- app/src/test/java/com/debtsdecks/core/i18n/`; the branch commits `4597e61`/`503d75c` this row used to cite are not ancestors of `develop` and do not exist in a fresh clone): parity is checked against the raw `.properties` key sets, and a sibling test builds a throwaway bundle pair under `@TempDir` where a key exists only in English, asserting that the Spanish bundle answers **with the English string** — that is the fallback, reproduced, so the guard cannot quietly go vacuous again. **C10 below was already written in the raw-file form and is correct** — the defect was in the Kotlin test, not in this checklist. Rule for later phases: a parity guard never consults the API that implements the fallback, and is proven by deleting a key and watching it go red. |
| E9 | **The `when (phase)` site count was wrong, in the charter of the phase it was meant to protect.** F2's proposal congratulates itself on making a new `RunManager.Phase` value fail to compile at four exhaustive sites. There are **five** sites and only **three** are exhaustive: `CombatInputHandler.kt:213` (`else -> Unit`) swallows a new phase silently and `NodePolicyTest.kt:32` (`else -> error(...)`) fails only at runtime. F6 is the phase that actually adds `EVENT`. | `f2/proposal.md`, `f2/design.md`, `f2/specs/run-structure/spec.md`, `f2/tasks.md` 7.5, `f6-events/charter.md` | Replaced everywhere with the five-site table and which three are exhaustive. Command: `rg -n 'when \(phase\)\|when \(runManager.phase\)' app/src/` then read each site's `else` branch. |
| E10 | **The docs still said the district re-cut had not shipped**, after `6b50164` merged it. `docs/GDD.md` declares itself "the record of what `develop` actually does" and its footer named F2 as the *next* change to alter anything. `openspec/project.md` and `docs/VISION.md` still presented `F1 -> F2` as the execution order, which F2 PR1 had already broken by merging first. | `docs/GDD.md` (MVP Scope + footer), `openspec/project.md`, `docs/VISION.md` | Rewritten to record what merged, what did not (PR2: names and backgrounds on screen), and that phase numbers are not the execution order — the `Depends on` / `Blocks` lines are. |
| E11 | **Two files each contradicted themselves.** `f1/proposal.md` said "Depends on: nothing outstanding… independent of this phase" in its header and "F1 depends on FV" in its Risk section. `f2/tasks.md` 2.6 said `RunManagerTest` was the only test file edited, four lines below task 1.3 recording `TestAssetLoader`. | `f1/proposal.md`, `f2/tasks.md` 2.6 | Header wins in both. The FV paragraph is now a risk (the anchor may need re-deriving), not a dependency; 2.6 lists all eight test files `git show --name-status` reports. |
| E12 | **Evidence that only exists on one machine.** A `/tmp/f1-baseline.txt` an independent verifier does not have; `/home/oscardev/DebtsAndDecks` as the working directory for every command; branch SHAs (`b7bfe4a`, `986a46e`, `d20bdb3`) that this repo's squash-merge deletes; and a 1.4 derivation (`git log --format=%H develop..HEAD`) that returns nothing once the branch merges. | preamble, `f1/tasks.md` 0.1 and 5.1, `f0/tasks.md` header and 1.4, `openspec/config.yaml` | Replaced with the C6 hash recipe, "a checkout of this repository", a content-based `git log -S`, and a GitHub slug. The honest test for any cited SHA is `git merge-base --is-ancestor <sha> develop`. |
| E13 | **Section C's table did not render.** A blockquote and a blank line sat between the delimiter row and the first body row, which terminates a table in GFM, so rows C1-C10 appeared on GitHub as literal pipe characters. Introduced by the reconciliation pass itself, one row after it added a correctness note. | this file, section C | Blockquote moved above the header row. |
| E14 | **Counts written by eye were wrong, every time they were re-checked with a command.** "Three tests added to `I18nBundleTest`" (four). "7 removed GDD lines" (eight — the table's header row was forgotten). "~1200 lines across 20 files" (2236 insertions across 24). "10 lines" in E1 (fifteen). | `f2/tasks.md` 6.1, `f0/tasks.md` 3.5, `f0/proposal.md`, E1 | Each replaced with the command that produced the figure. The pattern is the finding: a number nobody re-ran is a guess wearing a fact's clothes. |

**This section is itself unverified.** It is written by the same pass that made the changes,
so it carries the same blind spots as everything else here. Check it the way you would check
any other row: against `git diff`, not against this table.
