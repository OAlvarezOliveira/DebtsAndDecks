# Work plan — handoff to Pi

**Written 2026-08-28** against `feat/card-art-1024` @ `0d8ba32`. Suite at the time: **217 tests,
0 failures, 0 errors, 0 skipped**.

Every block below is self-contained: the defect, the line that causes it, the test to write
first, and what "done" means. Pick one, do it, stop. Do not assemble context from other blocks.

**Provenance matters and is marked per block.** `VERIFIED` means the line was read in the
working tree on 2026-08-28 and still shows the defect. `REPORTED` means it comes from the
2026-08-27 device playtest (`docs/TRACKING.md`, Playtest Notes) and the code was only located,
not proven defective — reproduce it before you fix it.

House rules that apply to every block: strict TDD (RED first, and the RED must fail for the
stated reason, not a compile error you shrug at); 4-space indent; conventional commits with no
AI attribution; branch off `develop`; keep the PR under 400 changed lines.

---

## Order

The 2026-08-27 playtest verdict is explicit about the first one: **fix B1 before any further
balance work, because it contaminates every measurement taken after it.** Then B3, which is the
one that kills runs without warning. The rest are independent — take them in any order.

`B1 -> B3 -> (B2 | B4 | B5 | B6 | B7)`

---

## B1 — The node sub-mode outlives everything (P1) · VERIFIED

**Branch:** `fix/node-mode-reset`

`CombatRenderer` holds the node sub-mode (`CHOICES` / `SHOP` / `REMOVE` / `UPGRADE` / `LOAN`) and
nothing puts it back. In `app/src/main/java/com/debtsdecks/gdx/input/CombatInputHandler.kt`:

- line 117, the `LOAN` branch, is the **only** one that calls
  `renderer.setNodeMode(CombatRenderer.NodeMode.CHOICES)` after acting;
- the `SHOP` (line 90), `REMOVE` (98) and `UPGRADE` (107) branches do not;
- `runManager.restartRun()` (line 45) does not touch the renderer's mode either.

So the next node opens already inside the previous node's sub-screen, and a brand-new run
inherits the sub-mode of the run before it. Observed **10 times** across four device runs; in the
worst case node 2 of run 4 opened in the `REMOVE` mode left behind by run 3.

This is the block that has to go first. Every balance number measured while a node can open in
the wrong mode is measuring the bug as much as the economy.

**Test first.** `CombatInputHandler` is GDX-coupled, so drive it through the renderer's own
accessor rather than a headless GL harness: act on a node in `SHOP` mode, then assert
`renderer.getNodeMode() == NodeMode.CHOICES`. Repeat per branch. Add one more for
`restartRun()`.

**Done when:** every branch that completes an action returns to `CHOICES`, `restartRun()` resets
it, and each has a test that fails on today's code.

---

## B2 — The ghost sub-screen swallows taps in silence (P1) · REPORTED

**Branch:** `fix/ghost-subscreen-feedback`

A node sub-screen that is drawn but has no live offers still accepts taps and does nothing at
all: no sound, no message, no visible state change. The player cannot tell the difference
between "this button does nothing" and "the game froze".

Very likely the same root as **B1** — a stale mode drawing a panel whose offers are empty.
**Do B1 first, then re-check whether this still reproduces.** If it does not, close it citing B1
and say so; do not invent a second fix for one cause.

**Done when:** either it is closed as a duplicate of B1 with the reproduction attempt written
down, or a tap on an inert offer produces a visible refusal.

---

## B3 — The loan guard is measured before interest, so Execution looks inert (P1) · VERIFIED

**Branch:** `fix/loan-guard-post-interest`

`app/src/main/java/com/debtsdecks/core/combat/RunManager.kt:252`:

```kotlin
if (debt + loanDebt > DebtConfig.EXECUTION_THRESHOLD) return false
```

The guard compares the **pre-interest** debt against the threshold, so the loan is allowed to
land at 49 against a threshold of 50. Turn-1 interest then pushes it to 57. Because the interest
tick is exempt from Execution by design ("Decision B"), the player survives several fights at
57-66, concludes the threshold is inert — and then any card adding a single point of debt is an
instant loss with no signal anywhere.

Two separable defects, and they are worth separating:

1. **The guard.** It should refuse a loan that the next interest tick will carry over the line.
2. **The signal.** Nothing in the HUD says the player is above the Execution line. Being over
   the line is currently indistinguishable from being under it right up to the moment it kills.

Fix 1 here. If 2 grows past a HUD warning, split it into its own block rather than inflating
this PR.

**Test first.** Pure core, no GDX: take a loan that lands just under the threshold, run one
interest tick, assert the run is not silently over the line. Then assert `takeLoan()` refuses
the loan that would get there.

**Done when:** the guard accounts for the interest that immediately follows, and a test pins the
exact boundary with concrete numbers — not `assertTrue(debt > 0)`.

---

## B4 — HP numbers are unreadable (P2) · REPORTED

**Branch:** `fix/hp-readability`

In `app/src/main/java/com/debtsdecks/gdx/render/CombatRenderer.kt`: the player's HP text is drawn
at line 368 and its bar at line 349, and on device the bar covers the number. The enemy has a bar
(`drawHPBar`, line 252) and **no number at all**.

Note the comment already at line 337 — "clear the glyph descent or the HP bar rides up into the
text" — someone met this before and solved it by hand. Read it before you move anything.

**Done when:** the player's number is legible over its bar on a real device, and the enemy has
one too.

---

## B5 — An upgraded card still describes its unupgraded self (P2) · REPORTED

**Branch:** `fix/upgraded-card-description`

`CombatRenderer.kt:453` renders `description = bundle.get(card.description)` — the static bundle
string — while `upgraded = card.upgraded` travels separately (line 455). The cost updates on
screen; the damage and block figures in the description text do not. A player who bought
`Golpe 6 -> 9` still reads "6".

Mind the i18n rule while fixing this: `CardDefinition.description` holds a **bundle key**, never
literal text (see the header comment at lines 31-32), and any new string needs a key in **both**
`strings.properties` and `strings_es.properties`.

**Done when:** an upgraded card's description shows its effective values, in both locales.

---

## B6 — Free rewards are debt-biased and poison a zero-debt run (P2) · REPORTED

**Branch:** `fix/reward-bias-zero-debt`

The reward pool skews toward debt-scaled cards. A player running at debt 0 is offered cards whose
effect is a function of debt, which is to say cards that do nothing. In the 2026-08-27 control
run the player reached the final boss holding **two** copies of "Burbuja de Activos" — damage =
half of debt = 0.

Start at `RunManager.kt:182` (`rewardChoices = cardRegistry.all()...`).

Read the design intent before changing the pool: `docs/VISION.md` treats debt as the engine, so
"debt-scaled cards are common" may well be deliberate and the real defect is that they are
offered to a player who has no debt to scale. Decide which one you are fixing and say so in the
PR body.

**Done when:** a zero-debt run stops being offered cards that are arithmetically inert for it,
with a seeded test pinning the behaviour.

---

## B7 — The leverage divisor is documented as 5 and implemented as 6 (P3) · VERIFIED

**Branch:** `fix/leverage-divisor-doc`

`app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt:45` says `floor(debt / 5)`.
Line 50 says `const val LEVERAGE_DIVISOR: Int = 6`, and line 49's own comment says `floor(debt / N)`.
Device measurement agrees with the code: debt 57 gave +9.

Comment-only change. Check `CardResolver.kt` for the same drift while you are in there.

**Done when:** no comment in the tree claims a divisor the code does not use.

---

## Not blocks — things that need a human decision

**The intent art collides at merge.** `feat/card-art-1024` carries real illustrations for
`intent_foreclose.png` (2990 B) and `intent_hedge.png` (1491 B).
`feat/fv-verbs-foreclose-hedge` carries 395-byte placeholders at the same two paths. Whichever
merges second will conflict, and **the real art must win**. Do not resolve this by taking
"theirs" out of habit.

**PR #18 is CONFLICTING.** `docs/refute-f5-ladder`. It carries the correct hunk for the
`docs/BALANCE-BASELINE.md` §2.2 mislabel: the doc reads "reached final boss (won >=7 fights):
80/80" but `encountersWon` counts **nodes, not slots**, so the true figure is 72/80. Rebase it,
keep the hunk.

**Do not touch `/home/oscardev/DebtsAndDecks`.** As of 2026-08-28 23:07 the main checkout had
uncommitted work on `feat/fv-verbs-foreclose-hedge` (`IntentVerbTest.kt`,
`ForecloseControlMeasureTest.kt`, `IntentVerbsE1Test.kt`, a staged `VerbControl.kt`, and
`docs/BALANCE-BASELINE.md`) belonging to a live session. Use a worktree of your own. Never run a
wildcard restore (`git checkout -- .`, `git restore .`, `git clean`, `git reset --hard`) in a
shared checkout — it has already destroyed uncommitted work in this project once.

---

## State of play

| Branch | Ahead of origin | PR | Note |
| --- | --- | --- | --- |
| `feat/card-art-1024` | 2 | #23 | 27 card illustrations, world art, six enemy portraits, the four opening stills, the intro screen |
| `feat/fv-verbs-foreclose-hedge` | 2 + uncommitted | #22 | live session, hands off |
| `test/debt-pressure-door` | 0 | #24 | mergeable |
| `docs/refute-f5-ladder` | 0 | #18 | conflicting, see above |

The debug APK builds from a clean tree: `./gradlew assembleDebug` produced a 45.5 MB
`app/build/outputs/apk/debug/app-debug.apk` on 2026-08-28. Release is **unsigned** —
`app/build.gradle.kts` declares no `signingConfig`, so only the debug build installs as-is.

## What this plan does not cover

The seven blocks above are defects. The **feature** programme is in `docs/VISION.md` section 5
(F2 districts through F8 leads), and its own note warns that the phase numbers are not the order
of execution. Do not read this file as the roadmap; it is a defect queue that should be drained
before the next measured balance pass, because five of the seven distort what a playtest would
tell you.
