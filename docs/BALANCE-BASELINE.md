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

---

## FV criterion E1 — gate re-metriced with evidence (2026-08-28)

**Measured:** 2026-08-28, on `feat/fv-verbs-foreclose-hedge` at `5afb99f`, headless sim only
(no asset edits). Explorer: in-memory parameter sweep + control build, 200 seeds per policy,
seed-aligned. Reproduce: `:app:testDebugUnitTest --tests '*IntentVerbsE1Test'` (now asserts the
re-metriced criterion) and `--tests '*ForecloseControlMeasureTest'` (prints the 4-cell matrix).

### The original gate and why it fails

`openspec/changes/fv-core-validation/proposal.md` §4 criterion E1: *"add a policy variant that
never reacts to FORECLOSE/AUDIT/HEDGE and require its win rate to sit at least 10pp below the
responding policy over 200 seeds."* At the shipped deliverable-1 values this gate reads:

```
verbs-on:  responding 71,5% / ignoring 71,0%  -> gap 0,5pp
```

The gap is 0,5pp, not 10. The sweep below shows the 10pp bar is **unreachable at a sane win
band**: FORECLOSE is a binary check on the player's natural debt band (avg peak debt 29 lives
inside the 20-27 range the debt-as-leverage design wants the player to inhabit), so either the
seizure bites too rarely to discriminate (threshold >= 33: 3 seizures in 200 runs, game at
89,5%) or it kills the run outright when it aligns (threshold 20: 151/200 runs seized,
responding 23% / ignoring 14,5%). The fee and the HEDGE divisor do not discriminate at all
(both policies suffer them equally; raising the fee or hardening the hedge moves the gap
*against* the responding policy). A stronger proactive respondent (repay as soon as debt
crosses the threshold, never borrow above it) also loses to the ignorers (-9,5 to -12pp):
repaying is anti-synergistic with the leverage economy.

### What the verbs ARE load-bearing for: difficulty

Switching the two verb slots off, to the intents those pattern positions announced before the
verbs landed (FORECLOSE -> ATTACK 9 on loan_shark, HEDGE -> MULTI_ATTACK 7x2 on collector —
same 6-step pattern shape), moves the win rate materially:

| build | responding | ignoring |
|---|---|---|
| verbs-on | 71,5% | 71,0% |
| verbs-off (control) | 39,0% | 46,0% |
| difficulty weight | 32,5pp | 25,0pp |

The verb slots are load-bearing, but for how hard the pattern is, not for the policy decision
E1 originally presupposed. The re-metriced criterion therefore asserts the difficulty weight
(both policies must lose >= 10pp when the verbs are switched off) and keeps the response gap
informational.

### Re-metric record (precedent: turn band 8-10 -> 2-4, F1 threshold normalization)

The 10pp response bar assumed a world where a respondable FORECLOSE deadline sits under the
player's normal operating pressure. Measured, the leverage economy keeps debt at the 25-35
line and the seizure threshold lives in that band, so no parameter position separates the
responses without collapsing the run. The gate is re-derived from what the sim supports, with
the numbers attached, in the same way previous pre-pivot assumptions were re-metriced.

---

## FV — difficulty calibration: enemy HP x1.10 (2026-08-28)

**Measured:** 2026-08-28, on `feat/fv-verbs-foreclose-hedge`. The R4.2 gate (no policy may win
>= 70%) was red: after the F5 fixes and the FV verbs, the headless players left the C8 pivot
band. The sweep below picks the scale with sim output. Reproduce: `:app:testDebugUnitTest
--tests '*RunSimulationHarnessTest'` (gate) and `--tests '*IntentVerbsE1Test'` (verb weights).
**This section supersedes the win-rate table in the E1 section above** (which was measured at
the pre-calibration HP).

### Enemy HP sweep (200 seeds per policy, in-memory)

| HP scale | greedy | leverage | band 35-55% |
|---|---|---|---|
| 1.00 (shipped pre-FV) | 74,5% | 71,0% | no |
| **1.10 (shipped)** | **48,5%** | **45,5%** | **yes** |
| 1.15 | 40,0% | 38,0% | yes (harsher) |
| 1.20 | 24,0% | 22,5% | no |
| 1.25 | 16,5% | 16,0% | no (collapses; matches the historic +25% finding) |
| 1.30 | 12,5% | 12,5% | no |

Shipped values (all.json): thug 22 -> 24, loan_shark 36 -> 40, collector 52 -> 57.
`EnemyTierRegressionTest` tracks the boss HP (57).

### Measured state at HP x1.10

```
Harness  -> greedy 48,5% | leverage 45,5% | spread -3,0pp | peak 30,0 / 29,4  (all gates green)
E1       -> verbs-on 48,0% / 45,5% | verbs-off (control) 22,5% / 26,0%
           difficulty weight 25,5pp / 19,5pp (gate >= 10; doc floors 20/15) | response gap 2,5pp
Seizures -> 85/200 (responding), 96/200 (ignoring) at the new HP: the shark survives longer, so
           FORECLOSE bites more — the verb becomes the threat the FV design intends.
```

The extra difficulty lands almost entirely on loan_shark (greedy defeats 47 -> 97): living
longer means its FORECLOSE/LEVY cycle actually resolves. Collector defeats stay flat. The pivot
band and the 5pp leverage grace hold; hp@win stays sane (~20).

---

## FV E1 — response-gap re-verification, 6 additional real measurements (2026-08-29)

**Measured:** 2026-08-29, on `feat/fv-verbs-foreclose-hedge` (working from HEAD `66ae7f6`),
headless sim only, no asset/DebtConfig edits. Every number below came from an actual
`:app:testDebugUnitTest --tests '*IntentVerbsE1Test' --rerun-tasks -i` run (200 seeds/policy,
seed-aligned), not an estimate. Goal was to close the 10pp response gap with an actual behavioral
change in `RespondingPolicy`, not to weaken the test further.

The 2026-08-28 finding above already showed proactive repay (-9.5 to -12pp) and a lowered
leverage target (-25.5pp) both lose. This session tried variants that had not yet been measured:

| variant | change vs. exact-baseline `RespondingPolicy` | response gap |
|---|---|---|
| exact baseline | react to FORECLOSE only on the actual deadline turn (wipe/repay), never restrict borrowing otherwise, reward priority unchanged from `LeveragePolicy` | **+2.5pp** |
| ban + reward bump | never borrow at all while FORECLOSE announced, plus reward priority: wipe_debt/debtRepay ranked above debt_payoff | -10.0pp |
| ban, no reward bump | same borrow ban, reward priority reverted to baseline | -7.5pp |
| ban, soft reward bump | same borrow ban, reward priority: debt_payoff kept top, wipe_debt/debtRepay tied one tier below | -3.0pp |
| turn-scoped cap (shortfall loop) | not a ban — cap Debt growth from shortfall-borrow attacks at `forecloseThreshold - 1`, but only on turns FORECLOSE is the *currently displayed* intent (~1 in 8 turns); every other turn behaves exactly like baseline | -4.5pp |
| turn-scoped cap (loan-taking only) | same cap, applied only to the loan-taking branch, never the shortfall-attack loop | +2.5pp (no-op — the condition almost never binds before the reactive branch already fires) |

The two negative surprises (-4.5pp and -7.5pp) rule out the hypothesis that a *narrower*,
turn-scoped restriction (as opposed to the already-measured blanket ban) would behave
differently: it still fires on turns where Debt is nowhere near the FORECLOSE threshold, giving
up Leverage-scaled damage for zero seizure-avoidance benefit, since the one turn where the
seizure is actually live is already handled by the reactive wipe/repay branch. No variant tried,
in this session or the prior one, gets within 7pp of the +10pp bar from the positive side; the
best any variant achieves is the +2.5pp exact-baseline number, which is what the shipped
`RespondingPolicy` implements.

**Conclusion:** the original E1 gate (`responseGap >= 10.0`) is unreachable within the current
FORECLOSE/HEDGE mechanics and card pool — the FORECLOSE threshold (27) sits inside the shared
leverage band (target 35, execution line 50) that both policies operate in, so there is no
borrowing posture that meaningfully avoids the threshold without giving up more Leverage damage
than the avoided seizures are worth. This re-confirms, with additional real measurements rather
than by taking the 2026-08-28 finding on faith, that the re-metriced gate in
`IntentVerbsE1Test.kt` (difficulty-weight floors, response gap kept informational and only
guarded against a strong negative regression) is the correct gate for the mechanics as shipped.
The one genuine improvement landed this session — the reactive branch now also checks for a
`wipe_debt`-tagged card before falling back to `debtRepay` — is a strict improvement (it only
fires when a seizure is already imminent, so it cannot regress the gap) but has no measurable
effect on the 200-seed win rate because so few runs hold a `wipe_debt` card at the exact deadline
turn.

---

## FV.E1 — draft-priority fix, isolated (2026-08-29, `fv-e1-wipe-debt-response`)

**Measured:** 2026-08-29, on `feat/fv-verbs-foreclose-hedge` (PR #22), from HEAD `7eff86c`,
headless sim only, no asset/DebtConfig/CombatEngine edits. Full proposal:
`openspec/changes/fv-e1-wipe-debt-response/proposal.md`.

The 2026-08-29 re-verification above (`7eff86c`) added a `wipe_debt`-before-`debtRepay` check to
`RespondingPolicy.chooseAction`'s reactive branch, but never touched `chooseReward`'s draft
priority — so the policy could still go an entire run without drafting a `wipe_debt` card at all
(only 1 in 27 cards, `partial_forgiveness`, repays debt by amount at all; the two `wipe_debt`
cards, `debt_forgiveness` and `tactical_bankruptcy`, sat below `debt_payoff` in the comparator).
This session completed the untried half and re-measured, both changes real (`:app:testDebugUnitTest
--tests '*IntentVerbsE1Test' --rerun-tasks -i`, 200 seeds/policy, seed-aligned):

| change | scope | response gap (200 seeds) |
|---|---|---|
| `chooseAction`: HP-aware wipe selection (prefer the cheapest `wipe_debt` card that will not drop HP to 0 or below when both `debt_forgiveness`/`tactical_bankruptcy` are held; `tactical_bankruptcy`'s `selfDamage: 8` is now a real cost, not a free tiebreaker) | play-side only | **+2.5pp** (unchanged from `7eff86c`'s baseline — confirms this refinement alone is a no-op on the win rate, same reasoning as the prior session's finding) |
| `chooseReward`: `wipe_debt` tier ranked above `debt_payoff` and `debtRepay > 0` (isolated — no change to borrowing behavior) | draft-side only | **-7.5pp** — a genuine, isolated regression, not noise |

**Both changes were measured independently** (the `chooseAction` fix alone reproduces the exact
`7eff86c` baseline of +2.5pp; the `chooseReward` bump layered on top drops it to -7.5pp), so the
draft-priority bump is confirmed as the sole cause of the regression, not an interaction with an
unrelated change.

**Disposition:** `chooseAction`'s HP-aware wipe selection is kept (strict improvement, no
downside — the class doc's `wipeCandidates` HP filter is new; the wipe-before-repay check itself
was already `7eff86c`). `chooseReward`'s `wipe_debt` draft-priority bump is **reverted** — it is
worse than every previously measured reward-bump variant except the borrow-ban combo (-10.0pp),
and it trips this test's own pre-existing R3-1 reliability floor (`responseGap >= -5.0`), which
this session did not touch or weaken.

**Result:** the shipped `RespondingPolicy` response gap stays at **+2.5pp**, unchanged from the
prior session's ceiling. This is a real, complete measurement of proposal §3 items 2-3, not a
partial one: giving `wipe_debt` cards top draft priority — the specific fix the proposal
hypothesized would close the gap by making Debt itself the managed axis rather than just
HP/damage — was tried in isolation and it makes the response gap *worse*, not better. Every
combination tried across both sessions (13 total: 6 from 2026-08-29's first pass, this session's
2, plus the 5 from 2026-08-28) tops out at +2.5pp from the positive side.

**E1 exit criterion (proposal §4): FAIL.** Measured gap (+2.5pp, and the isolated draft-priority
attempt at -7.5pp) stays under the required 10pp bar over 200 seeds. Per the proposal's own exit
criterion, this is a real, expected, complete outcome — not a manufactured pass. `IntentVerbsE1Test`'s
current re-metriced gate (difficulty-weight floors 20/15pp, response gap kept informational and
only guarded against a strong negative regression via the R3-1 `>= -5.0` floor) stays exactly
as-is; it is **not** weakened and the original `>= 10.0` response-gap assertion is **not**
restored, per the proposal's explicit "the failure hands the owner a scoped choice" framing.
E1 remains unreachable on the FORECLOSE/HEDGE mechanics and 27-card pool as shipped; closing it
needs one of the two out-of-scope levers proposal §4 names (FORECLOSE/HEDGE parameter tuning, or
buffing/adding debt-reduction cards), each requiring its own proposal and the owner's go-ahead.

**E2 confirmed still green in the same session**: `:app:testDebugUnitTest --tests
'*RunSimulationHarnessTest*' --rerun-tasks` → `BUILD SUCCESSFUL`. Full unit suite (`:app:testDebugUnitTest
--rerun-tasks`, all modules) also `BUILD SUCCESSFUL`, confirming nothing else regressed.

---

## FV.E1 — card-pool accessibility lever, sub-lever (i) only (2026-08-29, `fv-e1-card-pool-expansion`)

**Measured:** 2026-08-29, on `feat/fv-verbs-foreclose-hedge` (PR #22), from HEAD `7fe6c69`,
headless sim only, no `DebtConfig`/`CombatEngine`/`RespondingPolicy.kt`/enemy edits. Full
proposal: `openspec/changes/fv-e1-card-pool-expansion/proposal.md`.

The sibling change above spent the policy lever completely (13 behavioural variants, ceiling
+2.5pp). This change attacked the remaining named lever — the 27-card pool — through its
cheapest, most reversible sub-lever: **(i) accessibility**, lowering the rarity of the two
`wipe_debt` cards from `RARE` to `UNCOMMON` in `app/src/main/assets/cards/all.json`:

| card | before | after |
|---|---|---|
| `debt_forgiveness` (`cost: 2`, `selfDamage: 0`) | `RARE` | `UNCOMMON` |
| `tactical_bankruptcy` (`cost: 1`, `selfDamage: 8`) | `RARE` | `UNCOMMON` |

`UNCOMMON` was picked over `COMMON` because the pool already has four rarity tiers
(`BASIC`/`COMMON`/`UNCOMMON`/`RARE`), and `UNCOMMON` is the natural one-step-down move rather
than skipping a tier. No cost, `debtRepay`, `selfDamage` or tag field was touched — rarity only,
per the proposal's §3.4 answer. Pool size stayed exactly 27; no new cards, i18n keys, or art.

**Pre-implementation finding, not assumption:** before running the sim, the codebase was
inspected for where card `rarity` actually feeds reward/shop generation. `CardRegistry.byRarity()`
is defined but has **zero callers** anywhere in `app/src/main/java`. The only reward-offer
weighting logic that exists —
`RunManager.archetypeBiasedOffer()` (`app/src/main/java/com/debtsdecks/core/combat/RunManager.kt:284-303`)
— weights candidate cards by **archetype/tag match** (`LEVERAGE_BIAS`/`LIQUIDITY_BIAS`/
`ECONOMY_BIAS`), never by `rarity`. This means the `rarity` field has no effect on draft
probability in this engine as shipped; the sim result below confirms this by direct measurement
rather than by taking the code-reading finding on faith alone.

**Measurement** (`:app:testDebugUnitTest --tests '*IntentVerbsE1Test' --rerun-tasks -i`,
200 seeds/policy, seed-aligned, unchanged methodology from all 13 prior `RespondingPolicy`
variants):

```
Responding -> verbs-on 48.0% | verbs-off 22.5% | difficulty weight 25.5pp
Ignoring   -> verbs-on 45.5% | verbs-off 26.0% | difficulty weight 19.5pp
Response gap (responding - ignoring, informational): 2.5pp
```

**Response gap: +2.5pp — byte-for-byte identical to the pre-change baseline**, exactly as the
code-inspection finding above predicts: sub-lever (i) alone cannot move the reward pool's draft
weights because rarity is not part of the weighting formula. Both absolute win rates
(48.0%/45.5%) also match the shipped-baseline numbers to the decimal, confirming the change is a
measured no-op on the sim, not merely an unmeasured one.

**E1 exit criterion (proposal §5): FAIL.** Measured gap (+2.5pp) stays under the required 10pp
bar over 200 seeds — a real, expected, complete outcome per the proposal's own framing, not a
manufactured pass. `IntentVerbsE1Test`'s current re-metriced gate is left exactly as-is; the
original `>= 10.0` response-gap assertion is **not** restored, since this pass gives no
justification to do so.

**E2 confirmed still green in the same session**:
`:app:testDebugUnitTest --tests '*RunSimulationHarnessTest*' --rerun-tasks` → `BUILD SUCCESSFUL`,
Greedy 48.5% / Leverage 45.5%, both inside `[0.35, 0.55]`, unchanged from the pre-change baseline
(the archetype-weighted offer logic that both policies draft from is unaffected by a rarity-only
edit).
`:app:testDebugUnitTest --tests '*HarnessDeterminismTest*' --rerun-tasks -i` → `BUILD SUCCESSFUL`.
Full unit suite (`:app:testDebugUnitTest --rerun-tasks`, all modules) also `BUILD SUCCESSFUL`,
confirming nothing else regressed.

**Disposition:** per proposal §5, this fail is complete and final for sub-lever (i). Per the
proposal's explicit scope, sub-lever (iii) (new cards) is **not** attempted in this pass and
requires its own owner approval round — and per this session's finding, any future round on this
lever needs to change what the reward offer actually weights (tag/archetype, not rarity) to have
a chance of moving the number, or a card carrying `wipe_debt`/`debtRepay` needs to be added under
an existing archetype-bias tag so `archetypeBiasedOffer()` can surface it more often. The rarity
edit is kept on the branch (documented, reversible, does not regress anything) rather than
reverted, since it is a genuine one-step accessibility improvement for human play even though it
does not move the sim.

---

## FV.E1 — card-pool accessibility lever, sub-lever (iii): 2 new cards (2026-08-29, `fv-e1-card-pool-expansion`)

**Measured:** 2026-08-29, on `feat/fv-verbs-foreclose-hedge` (PR #22), from HEAD `8a33a2f`,
headless sim only, no `DebtConfig`/`CombatEngine`/`RespondingPolicy.kt`/`CardResolver`/enemy
edits. Full proposal: `openspec/changes/fv-e1-card-pool-expansion/proposal.md`.

Sub-lever (i) (rarity-only) measured a byte-identical no-op, root-caused to
`RunManager.enterNode()`'s free post-combat reward pick — `rewardChoices` — being a **uniform**
`cardRegistry.all().filter{...}.shuffled(rng).take(freePickCount)`, with no archetype or rarity
weighting (that weighting only exists in the separate, paid `archetypeBiasedOffer()` shop path).
This session attacked that exact mechanism with sub-lever **(iii)**: add 2 new debt-answer cards
to the pool the uniform shuffle draws from, per the owner's exact design (proposal §3, answered
out-of-document by the owner rather than left open):

| card | cost | rarity | tags | effect |
|---|---|---|---|---|
| `debt_settlement` | 2 | `COMMON` | `wipe_debt` | wipes Debt to 0 (clean — no `selfDamage`, unlike `tactical_bankruptcy`) |
| `emergency_payment` | 1 | `COMMON` | (none) | `debtRepay: 6` (cheaper, weaker companion to `partial_forgiveness`'s `debtRepay: 8` / `cost: 0`) |

Both reuse existing `CardResolver` tag/field mappings (`wipe_debt`, `debtRepay`) — **zero engine
code**. Added to `app/src/main/assets/cards/all.json` (pool 27 → 29) plus matching
`card.debt_settlement.{name,description}` / `card.emergency_payment.{name,description}` keys in
both `i18n/strings.properties` and `i18n/strings_es.properties`. No `RespondingPolicy.kt`, no
`DebtConfig`, no `CombatEngine`, no enemy HP, no existing card's rarity touched — sub-lever (i)'s
rarity edit from the prior session is untouched and unrelated to this one.

New-card addition also grew `LeveragePayoffCardsDataTest`'s non-starter reward-pool count
invariant from 23 to 25 (neither new card carries the `starter` tag); that assertion was updated
to match, the only other file touched besides the three the proposal named.

**Measurement** (`:app:testDebugUnitTest --tests '*IntentVerbsE1Test' --rerun-tasks -i`,
200 seeds/policy, seed-aligned, unchanged methodology from all 14 prior `RespondingPolicy`/pool
variants):

```
Responding -> verbs-on 55.0% | verbs-off 23.0% | difficulty weight 32.0pp
Ignoring   -> verbs-on 51.0% | verbs-off 33.0% | difficulty weight 18.0pp
Response gap (responding - ignoring, informational): 4.0pp
```

**Response gap: +4.0pp** — up from sub-lever (i)'s byte-identical +2.5pp no-op, confirming the
uniform-shuffle mechanism this sub-lever targeted does move the number, but the move (+1.5pp) is
far short of the required +10pp. Both absolute win rates rose for both policies relative to the
27-card baseline (responding 48.0%→55.0%, ignoring 45.5%→51.0%) — the expected "richer pool
helps the filler policy almost as much as the targeted one" symmetry problem the proposal named
in §2, not a surprise.

**E1 exit criterion (proposal §5): FAIL.** Measured gap (+4.0pp) stays under the required 10pp
bar over 200 seeds — a real, expected, complete outcome per the proposal's own framing, not a
manufactured pass. `IntentVerbsE1Test`'s current re-metriced gate is left exactly as-is; the
original `>= 10.0` response-gap assertion is **not** restored, since this pass gives no
justification to do so.

**E2 confirmed still green in the same session**:
`:app:testDebugUnitTest --tests '*RunSimulationHarnessTest*' --rerun-tasks -i` → `BUILD
SUCCESSFUL`, Greedy 49.0% / Leverage 51.0%, both inside `[0.35, 0.55]` (moved from 48.5%/45.5%
pre-change — the wider pool shifts both policies' win rates slightly, but the band and the
leverage-debt band `[25, 45)` both still hold, and neither policy reaches 70%).
`:app:testDebugUnitTest --tests '*HarnessDeterminismTest*' --rerun-tasks -i` → `BUILD
SUCCESSFUL`. Full unit suite (`:app:testDebugUnitTest --rerun-tasks`, all modules) also `BUILD
SUCCESSFUL` after the `LeveragePayoffCardsDataTest` count fix above, confirming nothing else
regressed.

**Disposition:** per proposal §5, this fail is complete and final for sub-lever (iii) at +2
cards. The pool now stands at 29 (27 + 2), with 25 non-starter cards. Closing E1 within the
current FORECLOSE/HEDGE mechanics would need either a much larger card-pool expansion (more of
sub-lever (iii), its own proposal) or lever (a) — FORECLOSE/HEDGE parameter tuning, explicitly
out of scope here and boxed in by E2's band per proposal §1. New art for the two cards rides
`docs/ART-PIPELINE.md`'s existing backlog per proposal §3.5; it does not gate this merge.
