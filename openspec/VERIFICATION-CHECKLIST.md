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
| A1 | `STARTING_DEBT = 6`, `EXECUTION_THRESHOLD = 50`, `BREAK_THRESHOLD = 30`, `INTEREST_RATE = 0.15`, `LEVERAGE_DIVISOR = 6` | code | `grep -nE "STARTING_DEBT\|EXECUTION_THRESHOLD\|BREAK_THRESHOLD\|INTEREST_RATE\|LEVERAGE_DIVISOR" app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt` |
| A2 | `LOAN_GOLD_BASE = 12`, `LOAN_DEBT_BASE = 8`, `UPGRADE_BASE = 15` and the upgrade cost is **not** escalated | code | `grep -nB2 -A2 "UPGRADE_BASE\|LOAN_GOLD_BASE\|LOAN_DEBT_BASE" app/src/main/java/com/debtsdecks/core/combat/NodeConfig.kt` |
| A3 | The run is 8 slots: 3 thug, 3 loan_shark, 2 collector | data | `python3 -c "import json,collections;print(collections.Counter(s['enemyId'] for s in json.load(open('app/src/main/assets/run/sequence.json'))['slots']))"` |
| A4 | Exactly 3 enemies exist, and each is a strict superset of the previous one's verbs | data | `python3 -c "import json;[print(e['id'],[i['type'] for i in e['intentPattern']]) for e in json.load(open('app/src/main/assets/enemies/all.json'))]"` |
| A5 | `IntentType` has exactly 5 values | code | `grep -n "enum class IntentType" app/src/main/java/com/debtsdecks/core/enemies/EnemyDefinition.kt` |
| A6 | `RunManager.Phase` has 4 values, with exhaustive `when` in 4 places | code | `grep -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` then `grep -rn "when (.*phase)" --include=*.kt app/src` |
| A7 | **There is no `startPlayerTurn`.** `applyInterest` is called from `private fun beginTurn()`, once per combat turn | code | `grep -rn "startPlayerTurn" --include=*.kt app/src` (expect: nothing) then `grep -n "applyInterest\|fun beginTurn" app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt` |
| A8 | 27 cards; 44 `card.*.description` keys; 39 of them carry a hand-written number in each language | data | `python3 -c "import json,re;d=json.load(open('app/src/main/assets/cards/all.json'));print('cards',len(d));ks=[l for l in open('app/src/main/assets/i18n/strings.properties') if re.match(r'card\..*\.description=',l)];print('keys',len(ks),'with digits',sum(1 for l in ks if re.search(r'[0-9]',l.split('=',1)[1])))"` |
| A9 | 180 `@Test` on `develop` | tests | `grep -rho "@Test" app/src/test --include=*.kt \| wc -l` |
| A10 | `Arts/` is gitignored with zero tracked files | git | `git check-ignore -v Arts/ ; git ls-files Arts/ \| wc -l` |
| A11 | `openspec/` is **not** ignored | git | `git check-ignore -v openspec/config.yaml` (expect exit 1, no output) |

## B. Claims this program makes that are NOT in the brief — check these hardest

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| B1 | The simulated **policies** are scale-coupled, not only the assertions: `LEVERAGE_TARGET = 35`, `SAFE_AFTER_LOAN = 45`, `REPAY_BAND = 25` — i.e. 0.70 / 0.90 / 0.50 of the execution line | test code | `grep -n "LEVERAGE_TARGET" app/src/test/java/com/debtsdecks/core/simulation/LeveragePolicy.kt; grep -n "SAFE_AFTER_LOAN\|REPAY_BAND\|LOAN_GOLD_NEED" app/src/test/java/com/debtsdecks/core/simulation/NodePolicy.kt` |
| B2 | The policy-gap assertion is **one-sided**, not a symmetric ±5pp | test code | `grep -n "leverage policy should stay within 5pp" -B3 app/src/test/java/com/debtsdecks/core/simulation/RunSimulationHarnessTest.kt` |
| B3 | Of the 44 description keys only **27 map to a live card**; 17 are orphans, and of the 27 live ones **23** carry a hand-written number per language — so the F3 parametrization job is 46 strings on the critical path, not 78 | data | `python3 -c "import json,re;ids={c['id'] for c in json.load(open('app/src/main/assets/cards/all.json'))};ks={};[ks.setdefault(m.group(1),m.group(2)) for l in open('app/src/main/assets/i18n/strings.properties') if (m:=re.match(r'card\.([a-z0-9_+]+)\.description=(.*)',l))];print('orphans',len(set(ks)-ids));print('live with digits',sum(1 for k,v in ks.items() if k in ids and re.search(r'[0-9]',v)))"` |
| B4 | **The release build type has no `signingConfig`** and no keystore is in the tree — so FV's external playtest cannot be distributed today | build | `grep -n "signingConfig" app/build.gradle.kts` (expect: nothing) then `sed -n '21,30p' app/build.gradle.kts` and `ls *.jks *.keystore 2>/dev/null` |
| B5 | GDD success criterion #4 exists and has never been measured | doc + history | `grep -n "third run" docs/GDD.md` then `git log --oneline -S"playtest" -- docs/` |
| B6 | The band `[25, 45)` straddles `BREAK_THRESHOLD` (30 = 0.60 of execution), so "playing the band" means routinely summoning the collector | arithmetic on A1 | `python3 -c "print(25/50, 30/50, 45/50)"` |

## C. Per-phase acceptance (check when each phase ships, not now)

| # | Claim | Source | Command |
| --- | --- | --- | --- |
| C1 | F0 touched only `docs/` and `openspec/` | git | `git diff --stat develop...HEAD \| grep -v '^ *docs/\|^ *openspec/\|files changed'` (expect: nothing) |
| C2 | F0 added or changed no test | tests | `./gradlew test` — still 180, and `git diff develop...HEAD -- app/` is empty |
| C3 | F1 is behaviour-preserving | harness | `./gradlew test --tests '*RunSimulationHarnessTest*' --info > /tmp/after.txt` and `diff /tmp/f1-baseline.txt /tmp/after.txt` — identical, no tolerance |
| C4 | F1's ratios resolve to the historical absolutes at `EXECUTION_THRESHOLD = 50` | test | `./gradlew test --tests '*HarnessBandsTest*'` |
| C5 | F1's derivation is live, not frozen at class-load | test | the stubbed-execution-line-of-100 case in `HarnessBandsTest` |
| C6 | F2 has zero balance delta | harness | same diff procedure as C3, against `/tmp/f2-baseline.txt` |
| C7 | F2 changed no enemy and no reward | git | `git diff develop...HEAD -- app/src/main/assets/run/sequence.json` — only added fields |
| C8 | F2 added no run phase | code | `grep -n "enum class Phase" app/src/main/java/com/debtsdecks/core/combat/RunManager.kt` — still 4 values |
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
