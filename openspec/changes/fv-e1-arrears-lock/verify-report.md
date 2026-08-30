# sdd-verify — `fv-e1-arrears-lock`, Phase 8 (Documentation Cleanup)

**Scope: tasks 8.1-8.3.** Phase 8 changed only Markdown, so "verification" has two halves: prove the
suite is still green (regression control), and prove each new documentation claim against the source
it describes. The second half is the substantive one — a docs phase's characteristic failure is a
confident sentence nobody re-derived.

## 1. Full-suite regression control — re-run, not reported

`--rerun-tasks` was used deliberately: the first invocation returned `UP-TO-DATE` (correct — docs do
not invalidate the test task), and an `UP-TO-DATE` build is not evidence of a green suite.

```
$ LANG=C <cached-gradle-8.11.1>/bin/gradle --no-daemon :app:testDebugUnitTest --rerun-tasks
BUILD SUCCESSFUL in 31s
23 actionable tasks: 23 executed

$ python3 -c "import glob,xml.etree.ElementTree as ET; r=[ET.parse(p).getroot() for p in glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml')]; print('classes',len(r),'tests',sum(int(x.get('tests')) for x in r),'failures',sum(int(x.get('failures')) for x in r),'errors',sum(int(x.get('errors')) for x in r),'skipped',sum(int(x.get('skipped')) for x in r))"
classes 29 tests 251 failures 0 errors 0 skipped 2
```

XML mtimes confirm re-execution (`16:41`, against `16:20` for the previous run), so the counts are
this pass's own measurement and not a stale artifact. The 2 skipped are the pre-existing `@Disabled`
F2 gates in `DebtPressureTest.kt`, whose symbols this change renamed to keep the package compiling
and which were correctly left disabled. Matches the 251/2/0 state Phase 7 recorded — Phase 8 changed
no behaviour, as intended.

## 2. Documentation claims re-derived against source

Each new claim, and the command that produced it. Nothing here was taken from `design.md`.

| Claim written in 8.1/8.2 | Command | Result |
|---|---|---|
| Execution is deleted, not bypassed | `rg -n "EXECUTION_THRESHOLD" app/src` | no output, exit 1 |
| Arms at 40; anchor is 50; distinct | `rg -n "const val ARREARS_THRESHOLD\|const val DEBT_SCALE_ANCHOR" DebtConfig.kt` | `ARREARS_THRESHOLD: Int = 40`, `DEBT_SCALE_ANCHOR: Int = 50` |
| Split is test-enforced | read `DebtConfigTest.kt` | `assertEquals(40, ARREARS_THRESHOLD)`; `assertEquals(50, DEBT_SCALE_ANCHOR)`; `DEBT_SCALE_ANCHOR > BREAK_THRESHOLD`; both `> 0`; `!=` between them |
| Behavioral sites read 40 | `rg -n "ARREARS_THRESHOLD" app/src` | `CombatEngine:93` (`armArrearsIfCrossed`), `CombatRenderer:365` (red colour), `:388` (warning) |
| Scale sites read 50 and stay lock-blind | `rg -n "DEBT_SCALE_ANCHOR" app/src` | `HarnessBands` (all band ratios), `ScriptedPolicy`, `LeveragePolicy`, `RespondingPolicy`, `RunManager.takeLoan`, `CombatRenderer:742`, `DebtConfigTest`. **No behavioral site reads the anchor and no policy reads 40** — D3 holds |
| Arm paths are `addDebt` only | `rg -n "addDebt\(" app/src/main/java` | `:252` borrow, `:316` `LEVY`, `:489` card effect. The interest tick at `:391` reads `!inArrears`, it does not arm — D2 holds |
| Interest frozen while armed | read `CombatEngine:386-391` | tick wrapped in `if (!inArrears)` |
| Single exit rule at `debt == 0` | read `clearArrearsIfEscaped` (`:102`) and its call sites (`:474`, `:481`) | `inArrears = inArrears && debt > 0`, invoked after `RepayDebt` and `WipeDebt` |
| Once-per-combat immunity; reset per combat | read `:93`, `:169-174` | `armArrearsIfCrossed` returns early on `arrearsUsedThisCombat`; both flags reset in `startCombat`; `arrearsArmedCount` intentionally not reset |
| Gatillo B position | `rg -n -B4 -A4 "state.inArrears" RunManager.kt` | `:183-187`, after `allEnemiesDead`, before garnishment |
| Unit contract exists as described | read test names | `CombatEngineTest`: crossing-first-time arms, 39 does not, no re-arm after the charge, dip keeps it, exactly-zero clears it, interest frozen but active debt applies, 38→42 non-uniform jump. `RunManagerTest`: in-arrears kill → defeat, escaped kill → victory, arming mid-combat resolves nothing. `CombatStateTest`: both flags default false |
| Harness numbers in AL5-AL8 | none re-run | cited verbatim from `docs/BALANCE-BASELINE.md` §"Post fv-e1-arrears-lock", which Phase 7 captured from `TEST-*.xml` `system-out`. **Not independently re-measured by this pass** — see Limitations |

### Claim that did not survive re-derivation

`docs/GDD.md`'s constant-table note asserted it listed "the complete set of `const val` … verified
2026-08-27". `rg -n 'const val' DebtConfig.kt` returns **14**; the table lists 11. Three constants
are absent (`STARTING_DEBT`, `LEVERAGE_DIVISOR`, `EXECUTION_DAMAGE_DIVISOR`) and two listed values
are wrong (`MAX_GARNISH_RATE` is 0.6 not 0.75; `DEBT_SCALING_ATTACK_DIVISOR` is 8 not 10). All five
predate this change and were left unfixed on purpose; the false certification above them was
withdrawn, because this task edits that table and would otherwise have re-asserted it.

## 3. Markdown integrity (this repo has been bitten twice — E13, §C6)

GFM ends a table when a blockquote, blank line or fenced block appears between the delimiter row and
the body. Both edited files were checked mechanically, not by eye:

- New `C.AL` table: 11 rows, every one with exactly 5 unescaped `|` = 4 cells. Its preamble
  blockquote sits **above** the header row. No fenced block inside any cell; alternation in `rg`
  commands uses the file's existing `\|` escaping.
- New GDD tables: behaviour table 5 rows × 2 cells, constant-job table 3 rows × 4 cells, main
  constant table 11 rows × 3 cells, Change Log 5 rows × 3 cells — all internally consistent.

## 4. Blockers and findings for the reviewer

Verification **passes** for Phase 8. Five things are recorded rather than fixed, and the first is
the one that needs a human decision:

0. **The game states a falsehood on screen, at the moment the mechanic fires.**
   `hud.execution_warning` still reads `DEBT OVER EXECUTION — ANY NEW DEBT KILLS` (EN) and
   `DEUDA SOBRE EJECUCIÓN — CUALQUIER DEUDA NUEVA MATA` (ES), while `CombatRenderer:388` now shows
   it when `debt >= ARREARS_THRESHOLD`. New debt does not kill any more — that path was deleted.
   Command: `rg -n "hud.execution_warning" app/src/main/assets/i18n/ app/src/main/java/com/debtsdecks/gdx/render/CombatRenderer.kt`.
   **Why it was not fixed here:** design D6 scoped UI/i18n out except the two `log.*` keys (which
   were renamed), `tasks.md` 6.2 lists only those two, and 8.1-8.3 are documentation tasks — an
   i18n bundle is neither. A Phase 5-6 pass already flagged it as a Phase 8 follow-up gap; this
   pass confirms it is still live and records it as **AL10** in the checklist, the only AL row
   entered as an open defect rather than a check. It has no test coverage by construction: this
   repo has no headless GL harness, so only a manual playtest or a human reading the bundle sees
   it. This one is a two-string fix and a real player-facing lie; it wants an explicit decision,
   not a backlog entry.

The other four:

1. **Review workload — the 400-line budget is exceeded for the change as a whole.** `git diff --stat`
   on the branch: **692 insertions / 159 deletions across 18 files** (Phase 8 contributes ~137 doc
   lines plus tracking). `tasks.md`'s own forecast said "Medium risk, chained PRs recommended, chain
   strategy: pending" — that decision is still **pending** and is now due. The suggested split
   (PR1 config, PR2 engine+policies, PR3 render/i18n/docs) still matches the diff.
2. **`design.md`'s Open Question is stale but unticked.** It says "D1 contradicts
   `specs/debt-economy/spec.md` ('every reference MUST use 40'). Spec amendment required before
   apply." The amendment **is** in the delta spec (`specs/debt-economy/spec.md:29-31` explicitly
   supersedes the blanket rule), so the question is resolved in substance while its checkbox in
   `design.md` remains `[ ]`. Not ticked here: `design.md` is outside 8.1-8.3's scope and the fix
   belongs to whoever owns that artifact.
3. **Four live documents still name `EXECUTION_THRESHOLD`** — `openspec/config.yaml`
   (`balance_gate.status`), `docs/VISION.md`, `docs/TRACKING.md`, `docs/PLAN-PI.md`. Caused by this
   change's rename, outside its documented scope, now recorded as unowned in `C.AL`.
4. **AL6's fragility is real.** `LeveragePolicy`'s arrears fire rate is 0.5% — about one run in 200.
   The fire-rate-greater-than-zero gate is one unlucky tuning change away from flapping, and peak
   debt (~30) sits 10 below the 40 line by design. Worth a human decision, not a silent tolerance.

## 5. Limitations of this pass

- The harness numbers in AL5-AL8 were **not re-measured**; they are Phase 7's, cited. Re-running the
  200-seed sweeps was outside this phase and would not have been independent anyway.
- Per `VERIFICATION-CHECKLIST.md`'s own rule, the `C.AL` rows this pass wrote are **not closed** by
  this pass. They carry commands so a later, memory-free pass can close them.
- Rendered Markdown was verified structurally (cell counts, block placement), not visually on GitHub.
