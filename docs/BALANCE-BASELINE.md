# Balance baseline — the "before" for FV

**Measured:** 2026-08-28, against `develop` at `3a18e4c`, with no code modified.
**Why it exists:** `openspec/changes/fv-core-validation/proposal.md` §4, criterion E2, requires
that if the new intent verbs move the win band, *"the new band is proposed with sim output
attached, never on paper."* Attaching output to a proposal is only meaningful against a
recorded prior. This file is that prior.

It is a measurement, not a plan. It proposes nothing and changes no behaviour.

---

## 1. How to reproduce

Both figures come from tests already on `develop`. Neither needs an argument or a flag.

```
./gradlew :app:testDebugUnitTest --tests '*RunSimulationHarnessTest*'
./gradlew :app:testDebugUnitTest --tests '*RunObservationTest*'
```

Their reports print to stdout, which Gradle does not surface. Read it out of the JUnit XML:

```
app/build/test-results/testDebugUnitTest/TEST-com.debtsdecks.core.simulation.RunSimulationHarnessTest.xml
app/build/test-results/testDebugUnitTest/TEST-com.debtsdecks.core.simulation.RunObservationTest.xml
```

inside the `<system-out><![CDATA[ ... ]]></system-out>` element.

A fresh worktree also needs a `local.properties` with `sdk.dir`; it is gitignored, so copy one
from an existing checkout or the build fails before any test runs.

**Locale note.** These runs used `LANG=es_ES.UTF-8`, so the harness report prints `54,0%` and
`30,8`. `SimulationReport.summary()` calls `format()` without a `Locale`, unlike
`RunSimulationCsvExportTest`, which passes `Locale.US`. The numbers are unaffected; only the
decimal separator moves. Read a comma as a decimal point below.

---

## 2. The numbers

### 2.1 Harness sweep — 200 seeds per policy

```
Greedy   -> win 54,0% | peak debt 30,8 | HP@win 12,6
Leverage -> win 49,5% | peak debt 30,3 | HP@win 10,6
Defeats greedy:   {collector=92}
Defeats leverage: {collector=101}
Avg turns/combat: 1,7
Peak debt as a fraction of the execution line: 0,615
```

`DebtConfig.EXECUTION_THRESHOLD` is `50`, `BREAK_THRESHOLD` is `30`, `STARTING_DEBT` is `6`.

### 2.2 Observation trace — 80 seeds, greedy policy

```
wins=42/80 (52%)  defeats=38
avg peakDebt(all)=30   avg endGold(win)=70   avg endHp(win)=11
die vs collector: 38 | avg peakDebt=31 avg endGold=47 avg endDebt=17 avg endHp=0
reached final boss (won >= 7 fights): 80/80

node1: BUY=80
node2: BUY=5  | LOAN=75
node3: FREE_PICK=78 | LOAN=2
node4: FREE_PICK=78 | LOAN=2
node5..node7: FREE_PICK=80

5 worst defeats by end Debt:
  seed=26 vs=collector endDebt=55 peakDebt=56 endGold=56 hp=30
  seed=25 vs=collector endDebt=30 peakDebt=35 endGold=30 hp=0
  seed=5  vs=collector endDebt=29 peakDebt=34 endGold=38 hp=0
  seed=30 vs=collector endDebt=27 peakDebt=32 endGold=31 hp=0
  seed=70 vs=collector endDebt=26 peakDebt=34 endGold=31 hp=0
```

---

## 3. What the numbers establish

### F1 — Two of the three enemies have never killed anyone.

Across 400 simulated runs (200 greedy + 200 leverage) every single defeat is against
`collector`: `{collector=92}` and `{collector=101}`, with no other key in either map. The
80-seed trace agrees from the other direction — `reached final boss: 80/80`.

`loan_shark` appears **three times** in `app/src/main/assets/run/sequence.json`, twice of them
as a district boss (slots 3 and 6). It has a losing record of zero. `thug` appears three times
and is likewise harmless.

This is stronger than the claim FV §1 makes from reading `all.json`. FV argues from structure
that each enemy is a strict superset of the last, so *"no enemy requires a different plan —
only more endurance."* The sweep shows the consequence: seven of the eight slots are not a
fight, they are a resource-accumulation phase with a combat animation, and the run is decided
entirely at slot 8.

### F2 — The Debt axis is decorative. Exactly one death in 38 is a Debt death.

The defeat list is sorted by descending `endDebt`. Only `seed=26` ends above
`EXECUTION_THRESHOLD` (55, with `hp=30` — killed by the debt, at full-ish health). The
second-highest is `endDebt=30`, which is 20 points below the execution line, so no unlisted run
can reach it either. **1 of 38 defeats (2.6%) is an execution; the other 37 are `hp=0`.**

Average peak Debt sits at 0.615 of the execution line and average *end* Debt among defeats is
17. Players run a debt that is permanently, comfortably survivable, and then die to damage.

The thesis in `docs/GDD.md` is that Debt is the central tension. In simulation it is a damage
multiplier with a cap nobody reaches.

### F3 — The shop prices itself out of the run by node 4.

`NodeConfig` sets `BUY_BASE = 8` and `ESCALATION = 1.5`, and `escalatedCost` is
`base * 1.5^(nodeIndex - 1)`. Gold has exactly two sources in `RunManager` — the
combat reward at line 168, credited as `rawGold - garnished`, and a loan at line 253. The reward
half is the flat per-slot figure in `sequence.json` (10, 10, 15, 12, 18, 20, 25, 30), reduced by
garnishment, so realised income is at or below that line. One curve is exponential; neither of
the two income sources is.

Lay the buy price beside the average gold the trace records *before* each decision:

| Node | Buy cost | Avg gold held | |
| --- | --- | --- | --- |
| 1 | 8 | 9 | affordable |
| 2 | 12 | 9 | priced out |
| 3 | 18 | 32 | affordable |
| 4 | 27 | 25 | priced out |
| 5 | 40 | 19 | priced out |
| 6 | 60 | 29 | priced out |
| 7 | 91 | 42 | priced out |

The node trail follows this line exactly. `node1: BUY=80/80` is the one node where the average
player can afford a card. `node2: LOAN=75/80` is the node where they cannot and borrow instead.
From node 4 on, the shop is unreachable and every node reports `FREE_PICK`.

So the 47 gold the average defeated player is holding at death is not hoarding — it is
**below the node-7 asking price of 91**, and it has been below the asking price since node 4.
The economy has a sink; the sink outruns the income by node 4 and the gold has nowhere to go.

Node 3 is the one exception worth noting: at 32 gold against an 18 cost the purchase *is*
affordable and the greedy policy still takes `FREE_PICK` 78 times out of 80. That is a policy
choice, not a price wall, and it is the only node in the run where a real decision exists and
is being made badly.

### F4 — Five of the seven nodes carry no decision at all.

`node1: BUY=80/80`, `node2: LOAN=75/80`, `node3` through `node7`: `FREE_PICK` at 78-80/80.
There is one opening — buy, then borrow — and then five nodes where the policy has, by F3, no
affordable alternative to pick from. Combats average **1.7 turns**, so the in-combat decision
is nearly as shallow as the meta one.

### F5 — The cost curve is not the lever. Measured, not argued.

F3 says the shop is unaffordable. The obvious inference — lower `ESCALATION` — is wrong, and
the sweep says so. Each row is a full 200-seed harness run with one constant changed and
nothing else:

| Variant | `ESCALATION` | Greedy win | Peak Debt | Defeats |
| --- | --- | --- | --- | --- |
| shipped | 1.50 | 54,0% | 30,8 | `{collector=92}` |
| cost only | 1.35 | 35,0% | 41,5 | `{collector=130}` |
| cost only | 1.25 | **20,5%** | 44,5 | `{collector=159}` |
| cost only | 1.15 | 60,5% | 35,0 | `{collector=76, loan_shark=3}` |

Making the shop affordable makes the game **harder**, and not smoothly: 54 → 35 → 20,5 → 60,5
is not a knob, it is a cliff with a rebound. One mechanism is legible in the numbers: cards
bought are cards added, and a wider deck draws its good cards less often. Peak Debt also climbs
as prices fall — 30,8 → 41,5 → 44,5 — and that one is **still unexplained**; the explanation this
section originally gave for it is retracted below.

**A second mechanism was proposed here, and it was wrong.** `NodePolicy` evaluated *"front-load
value while the shop is cheap"* (rule 1b) **before** *"repay when the debt band is hot"* (rule 3),
and this section originally predicted that swapping the two would lift the win rate to 77,5% and
push peak Debt to 63,1 — above the execution line — with `loan_shark` recording its first kill.

**Retracted 2026-08-28. The swap was implemented and measured; it changes nothing.** PR #17
(merged as `c84974d`) moves repay to rung 2, right after UPGRADE. Both predicted rows were then
re-run with `--rerun-tasks`, same build and locale as §1:

| Variant | `ESCALATION` | Predicted here | Actually measured |
| --- | --- | --- | --- |
| repay before shop | 1.50 | 77,5% / 63,1 / `{collector=44, loan_shark=1}` | **54,0% / 30,8 / `{collector=92}`** — identical to the `shipped` row above |
| repay before shop | 1.25 | 61,0% / 41,7 / `{collector=78}` | **20,5% / 44,5 / `{collector=159}`** — identical to the `cost only` 1.25 row above |

**Why it is a no-op.** The repay branch needs `run.debt >= REPAY_BAND` (25) *at the moment of a
node decision*. §2.2 of this same file already recorded node-time debt averaging 7 → 20, never
reaching the band: the ~30 peak is mid-combat, and the reward garnishment lowers it again before
the next node. **REPAY fires in zero of the sweep's node decisions.** The rule is dormant, and
reordering a dormant rule cannot move a number. The evidence refuting this section was already
printed inside it.

**What survives.** The cost cliff above (54 → 35 → 20,5 → 60,5) is untouched, and its 1.25 point
was reproduced during this check. What does not survive is the claim that the decision ladder is
the dominant mechanism, and with it the claim that the reference policy leaves 23pp on the table.
F2 and F4 stand as properties of the game, not of the ruler. **F5 no longer blocks FV.**

**What the reorder is still worth.** `NodePolicy` lives in `app/src/test/` and its own docstring
calls it *"the MEASUREMENT floor for balance tuning, not a design directive."* The merged ladder
is the correct floor for whenever nodes *do* see hot debt — which is exactly what F3's treasury
(monthly payments) would introduce. It is a correct change with a measured delta of zero, not a
fix for a problem that existed.

### F6 — The credit line dies exactly where escalation makes it necessary.

`NodeConfig` sets `LOAN_GOLD_BASE = 12`, and `12 = BUY_BASE (8) x ESCALATION (1.5)`. That is not
a coincidence: a loan at node *n* yields **exactly** the buy price at node *n+1*, for all six
pairs — (12,12), (18,18), (27,27), (40,40), (60,60), (91,91). Borrow here, buy next door. The
late-game shop was never meant to be paid for out of income.

But the loan's Debt cost escalates on the same exponent while `DebtConfig.EXECUTION_THRESHOLD`
is a flat `50`, and `RunManager.takeLoan` rejects any loan that would cross it:

| Node | Loan Debt cost | Max prior Debt allowed |
| --- | --- | --- |
| 4 | 27 | 23 |
| 5 | 40 | 10 |
| 6 | 60 | **impossible at any Debt** |
| 7 | 91 | **impossible at any Debt** |

Observed Debt at those nodes is 20, 19, 16, 13. So the loan is unavailable from node 5 in
practice and from node 6 as arithmetic. `NodePolicy` is stricter still (`SAFE_AFTER_LOAN` = 0.90
of the line = 45), which is why the trace shows no loan after node 4.

An exponential credit instrument is gated by a constant. Whatever is done about F3, this ratio
is the thing that is actually inconsistent, and `NodePolicy` already names the ticket in a
comment: *"LOAN_GOLD_NEED stays absolute on purpose: it is coupled to the GOLD economy, which
has no honest anchor against the execution line — deferred to F3."*

---

## 4. What this means for FV deliverable 1

FV §2 already selects the three verbs, and `openspec/changes/f5-zone-bosses/charter.md` names
them as *"FV deliverable 1 — the hard dependency."* That decision is on `develop` and is not
re-opened here. What the baseline adds is which measured failure each verb has to move, and
therefore what it would mean for one to have landed.

| Verb (FV §2) | The finding it must move | Observable that has to change |
| --- | --- | --- |
| **FORECLOSE** | F2 — Debt kills 1 run in 38 | Execution deaths become a real share of defeats, and the average peak-Debt fraction stops sitting at 0.615 with nothing above it |
| **AUDIT** | F4 — one line, five dead nodes | Node decisions after node 2 stop being `FREE_PICK` at 80/80; a disabled tag has to make a second line worth owning. Blocked behind F3: a player who cannot afford a card cannot build a second line to fall back on |
| **HEDGE** | F2, and F3's idle gold | Debt stops being free upside, so paying down and spending compete for the same gold |

**F1 is the finding no verb fixes on its own.** Giving `collector` new verbs makes slot 8
harder; it does not make slots 1-7 a fight. Whatever else FV does, `sequence.json` and the
`intentPattern` of `thug`/`loan_shark` have to change, or the sweep will report `{collector=N}`
again with a different N.

**F3 is not a verb problem, and F5 says it is not a cost problem either.** AUDIT's premise is
that the player has a second line to fall back on, and F3 says the player stops being able to
buy cards at node 4 — so AUDIT on this economy is a stun, not a redirect. But the sweep in F5
rules out the obvious fix: lowering `ESCALATION` moves the win rate to 35% and then 20,5%
before recovering, so the price wall is currently *load-bearing*.

The order of work that follows from the evidence, rather than from the shape of the complaint:
the reference policy is now settled — F5's proposed reorder was merged and measured at zero
delta — so take the loan-versus-execution-line ratio (F6) next, and only then ask what the shop
should cost. Tuning the price first would move a constant that the sweep shows is holding the
economy up. This is a finding, not a proposal.

---

## 5. The instrumentation E1 needs, which does not exist yet

FV §4, criterion E1: *"add a policy variant that never reacts to FORECLOSE/AUDIT/HEDGE and
require its win rate to sit at least 10pp below the responding policy over 200 seeds."*

Two things must be in place **before** the verbs, or the gate is unmeasurable at the moment it
is supposed to be measured:

1. **The two policy variants.** `ScriptedPolicy` and `LeveragePolicy` are the only
   implementations of `RunPolicy` in `app/src/test/java/com/debtsdecks/core/simulation/`.
   Neither can respond to a verb that does not exist, so E1 needs a *responding* policy written
   alongside the verbs and a *non-responding* control. Note the size of the gap E1 asks for:
   the existing greedy-vs-leverage spread is **-4.5pp**, so 10pp is more than twice the widest
   policy difference the game has ever produced.
2. **Defeat cause, recorded rather than inferred.** `RunObservationTest` records `endHp` and
   `endDebt` per run, so today the cause is *derivable* — I derived F2 from it above — but only
   by reading a sorted top-5 list and reasoning about what cannot be below it. That does not
   scale to a gate. Recording the cause (`EXECUTION` vs `HP_ZERO`) and the slot index directly
   turns F2 into a single number that can be asserted on.

Item 2 is cheap and is worth doing first: it is a pure test-source change with no gameplay
effect, so it can land and be verified against *this* baseline before any verb moves it.

---

## 6. Scope of this document

Not a proposal, not a balance change, not a re-baselining. When FV moves the band, E2 requires
the new band to arrive with its own sim output attached; that output should be diffed against
§2 of this file, and this file should be left as it stands — a record of what the game measured
on `3a18e4c`, before the verbs.

## 7. F5 addendum (2026-08-28) — repay-before-shop is zero-delta; the repay rule is dormant

Intent: fix the measurement floor. The `NodePolicy` priority ladder was reordered so that repaying
hot debt (previously rung 4, after the early-shop and the loan) comes **before any shop** (now rung
2, right after UPGRADE), matching the documented ladder. Branch `fix/policy-repay-before-shop`; no
gameplay files touched. This document's §5.2 claimed the defeat-cause/slot instrumentation "does not
exist yet" — it was delivered meanwhile as PR #16 (`fix/observation-defeat-slot`, merged as `72ffa1c`).

Result: **byte-identical sweep.** Re-ran with `--rerun-tasks`, same build and locale (see §1):

```
Win rate:          54,0%
Avg peak Debt:     30,8
  as fraction:     0,615 of execution line
Avg HP at victory: 12,6
Avg turns/combat:  1,7
Defeats by encounter:  collector: 92
Greedy   -> win 54,0% | peak debt 30,8 | HP@win 12,6
Leverage -> win 49,5% | peak debt 30,3 | HP@win 10,6
Defeats greedy:   {collector=92}
Defeats leverage: {collector=101}
```

The 80-seed observation is unchanged too (wins 42/80, defeats 38, identical node decision counts).

Why the ladder move changed nothing: the repay branch fires only when `run.debt >= 25` *at the
moment of a node decision*. Observed node-time debt averages 7 → 20 and never reaches the band
(the ~30 peak debt is mid-combat; the reward garnishment lowers it before the node). **REPAY
appears in zero node decisions** — the rule is dormant under current conditions. E2 therefore
requires no new band: the gate did not move. The reorder stands as the intended measurement floor
for whenever nodes do see hot debt (e.g. F3 treasury's monthly payments).
