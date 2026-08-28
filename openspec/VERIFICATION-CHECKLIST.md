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
| A1 | `STARTING_DEBT = 6`, `EXECUTION_THRESHOLD = 50`, `BREAK_THRESHOLD = 30`, `INTEREST_RATE = 0.15`, `LEVERAGE_DIVISOR = 6` | code | `rg -nE "STARTING_DEBT\|EXECUTION_THRESHOLD\|BREAK_THRESHOLD\|INTEREST_RATE\|LEVERAGE_DIVISOR" app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt` |
| A2 | `LOAN_GOLD_BASE = 12`, `LOAN_DEBT_BASE = 8`, `UPGRADE_BASE = 15` and the upgrade cost is **not** escalated | code | `rg -n -B2 -A2 "UPGRADE_BASE\|LOAN_GOLD_BASE\|LOAN_DEBT_BASE" app/src/main/java/com/debtsdecks/core/combat/NodeConfig.kt` |
| A3 | The run is 8 slots: 3 thug, 3 loan_shark, 2 collector | data | `python3 -c "import json,collections;print(collections.Counter(s['enemyId'] for s in json.load(open('app/src/main/assets/run/sequence.json'))['slots']))"` |
| A4 | Exactly 3 enemies exist, and each is a strict superset of the previous one's verbs | data | `python3 -c "import json;[print(e['id'],[i['type'] for i in e['intentPattern']]) for e in json.load(open('app/src/main/assets/enemies/all.json'))]"` |
| A5 | `IntentType` has exactly 5 values | code | `rg -n "enum class IntentType" app/src/main/java/com/debtsdecks/core/enemies/EnemyDefinition.kt` |
| A6 | `RunManager.Phase` has 4 values, with exhaustive `when` in 4 places | code | `rg -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` then `rg -n "when \(.*phase\)" --glob '*.kt' app/src` |
| A7 | **There is no `startPlayerTurn`.** `applyInterest` is called from `private fun beginTurn()`, once per combat turn | code | `rg -n "startPlayerTurn" --glob '*.kt' app/src` (expect: nothing) then `rg -n "applyInterest\|fun beginTurn" app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt` |
| A8 | 27 cards; 44 `card.*.description` keys; 39 of them carry a hand-written number in each language | data | `python3 -c "import json,re;d=json.load(open('app/src/main/assets/cards/all.json'));print('cards',len(d));ks=[l for l in open('app/src/main/assets/i18n/strings.properties') if re.match(r'card\..*\.description=',l)];print('keys',len(ks),'with digits',sum(1 for l in ks if re.search(r'[0-9]',l.split('=',1)[1])))"` |
| A9 | 180 `@Test` on `develop` | tests | `rg --no-filename -o "@Test" --glob '*.kt' app/src/test \| wc -l` |
| A10 | `Arts/` is gitignored with zero tracked files | git | `git check-ignore -v Arts/ ; git ls-files Arts/ \| wc -l` |
| A11 | `openspec/` is **not** ignored | git | `git check-ignore -v openspec/config.yaml` (expect exit 1, no output) |

## B. Claims this program makes that are NOT in the brief — check these hardest

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| B1 | The simulated **policies** are scale-coupled, not only the assertions: `LEVERAGE_TARGET = 35`, `SAFE_AFTER_LOAN = 45`, `REPAY_BAND = 25` — i.e. 0.70 / 0.90 / 0.50 of the execution line | test code | `rg -n "LEVERAGE_TARGET" app/src/test/java/com/debtsdecks/core/simulation/LeveragePolicy.kt; rg -n "SAFE_AFTER_LOAN\|REPAY_BAND\|LOAN_GOLD_NEED" app/src/test/java/com/debtsdecks/core/simulation/NodePolicy.kt` |
| B2 | The policy-gap assertion is **one-sided**, not a symmetric ±5pp | test code | `rg -n -B3 "leverage policy should stay within 5pp" app/src/test/java/com/debtsdecks/core/simulation/RunSimulationHarnessTest.kt` |
| B3 | Of the 44 description keys only **27 map to a live card**; 17 are orphans, and of the 27 live ones **23** carry a hand-written number per language — so the F3 parametrization job is 46 strings on the critical path, not 78 | data | `python3 -c "import json,re;ids={c['id'] for c in json.load(open('app/src/main/assets/cards/all.json'))};ks={};[ks.setdefault(m.group(1),m.group(2)) for l in open('app/src/main/assets/i18n/strings.properties') if (m:=re.match(r'card\.([a-z0-9_+]+)\.description=(.*)',l))];print('orphans',len(set(ks)-ids));print('live with digits',sum(1 for k,v in ks.items() if k in ids and re.search(r'[0-9]',v)))"` |
| B4 | **The release build type has no `signingConfig`** and no keystore is in the tree — so FV's external playtest cannot be distributed today | build | `rg -n "signingConfig" app/build.gradle.kts` (expect: nothing) then `bat --plain -r 21:30 app/build.gradle.kts` and `fd -d 1 -e jks -e keystore` |
| B5 | GDD success criterion #4 exists and has never been measured | doc + history | `rg -n "third run" docs/GDD.md` then `git log --oneline -S"playtest" -- docs/` |
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
> Fixed by `f02b421` in [PR #7](https://github.com/OAlvarezOliveira/DebtsAndDecks/pull/7),
> **merged into `develop` as `6b50164` on 2026-08-28**. C3 and C6 are runnable from
> `develop` as of that commit. Before trusting either row, confirm the fix is present:
> `git log --oneline -S 'thenBy { it.cardId }' -- app/src/test/java/com/debtsdecks/core/simulation/`
> and check that `HarnessDeterminismTest` exists and passes.
>
> The same caveat applies backwards: **any balance number this program cites as a fact was
> measured on the broken gate.** Treat pre-2026-08-28 balance figures as approximate.

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| C1 | F0 touched only `docs/` and `openspec/` | git | `git diff --stat develop...HEAD \| rg -v '^ *docs/|^ *openspec/|files changed'` (expect: nothing) |
| C2 | F0 added or changed no test | tests | `git diff develop...HEAD -- app/` is empty (the absolute test count is not a usable check — PR #7 changes it) |
| C3 | F1 is behaviour-preserving | harness | `./gradlew test --tests '*RunSimulationHarnessTest*' --info > /tmp/after.txt` and `diff /tmp/f1-baseline.txt /tmp/after.txt` — identical, no tolerance |
| C4 | F1's ratios resolve to the historical absolutes at `EXECUTION_THRESHOLD = 50` | test | `./gradlew test --tests '*HarnessBandsTest*'` |
| C5 | F1's derivation is live, not frozen at class-load | test | the stubbed-execution-line-of-100 case in `HarnessBandsTest` |
| C6 | F2 has zero balance delta | harness | same diff procedure as C3, against `/tmp/f2-baseline.txt` |
| C7 | F2 changed no enemy and no reward | git | `git diff develop...HEAD -- app/src/main/assets/run/sequence.json` — only added fields |
| C8 | F2 added no run phase | code | `rg -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` — still 4 values |
| C9 | F2's new backgrounds carry no lettering | eyes | open the three PNGs. This one cannot be automated and must not be skipped for that reason. |
| C10 | Every `district.*` key exists in both bundles | data | `python3 -c "import re;g=lambda f:{re.match(r'(district\.[a-z_.]+)=',l).group(1) for l in open(f) if re.match(r'district\.',l)};a=g('app/src/main/assets/i18n/strings.properties');b=g('app/src/main/assets/i18n/strings_es.properties');print('EN only',a-b,'ES only',b-a)"` |

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
| E1 | **R0.5 was violated by its own program.** The F0 spec requires that no claim be asserted as verified, and its scenario says a reviewer searching for the word finds none. Four places asserted it anyway. | `config.yaml:9`, `f1/proposal.md` ×2, `fv/proposal.md`, `f7/charter.md` | Each rewritten as a claim pointing at the checklist row that carries its command. R0.5 now holds. |
| E2 | **The zero-delta gates were unrunnable.** C3 and C6 demand an identical report across two runs; the harness was non-deterministic, so identical runs disagreed. Nothing in the program mentioned it — the defect was found after these documents were written. | C3, C6, `f1/tasks.md`, `f2/tasks.md`, both proposals | Precondition added above section C, and a blocking-dependency note in each phase. The valid F2 comparison is `develop` + `f02b421` versus the branch, **not** `develop` versus the branch. |
| E3 | **Balance facts were measured on the broken gate.** Any number this program cites was read off a noisy instrument. | program-wide | Stated in the section C precondition. Pre-2026-08-28 balance figures are approximate. |
| E4 | **Two absolute test counts were already stale** when written, because implementing a phase changes them. | C2, `f1/tasks.md` 5.3 | Both replaced with checks that cannot rot: a diff being empty, and a count relative to the fork point. |
| E5 | **F2's tasks described work that had already shipped**, under names that differ from the shipped ones. Following the file literally would have meant reimplementing PR #7 and colliding with it. | `f2/tasks.md` | Reconciled against `38e0b9b`. Shipped names win over planned names and are recorded inline. Task 2.4, the one PR1 item genuinely unmet at reconciliation time, was then implemented in `c018648` and is now ticked. |
| E6 | **F1 was gated on "the post-FV `develop`"**, but FV cannot complete — B4 shows `release` has no `signingConfig` and no keystore exists, so its external playtest is undistributable. | `f1/tasks.md` 6.1 | Re-gated on PR #7, which is the real prerequisite. FV is independent of F1. |
| E7 | **Prescribed commands used `grep`, `sed` and `ls`**, against the project's stated tooling convention. | this file | Rewritten as `rg`, `bat` and `fd`. |
| E8 | **A translation-parity test that goes through the localization API cannot fail.** PR #7 shipped an i18n check calling `bundle.get(key)`; libGDX's `I18NBundle` falls back to the parent bundle, so a key missing only from `strings_es.properties` resolved to the English string and the assertion passed. The PR claimed an untranslated district failed the build. It did not. | PR #7, and any future phase adding user-facing text | Fixed in `4597e61`/`503d75c`: parity is checked against the raw `.properties` key sets. **C10 below was already written in the raw-file form and is correct** — the defect was in the Kotlin test, not in this checklist. Rule for later phases: a parity guard never consults the API that implements the fallback, and is proven by deleting a key and watching it go red. |

**This section is itself unverified.** It is written by the same pass that made the changes,
so it carries the same blind spots as everything else here. Check it the way you would check
any other row: against `git diff`, not against this table.
