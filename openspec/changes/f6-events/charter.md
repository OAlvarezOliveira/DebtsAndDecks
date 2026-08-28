# F6 — Events — CHARTER

> Charter, not a spec. Not apply-ready.

## Intent

Between combats, someone offers a deal. Cash now for interest later; a favour for a mark on
your record. Data-driven, keyed, translatable, and written in the register of the interactive
fiction that this setting already produced.

## Inputs required

- **F3 and F4 complete.** An event's consequences are its whole point, and the vocabulary of
  consequences — cash, debt, ballast, health — only exists once the treasury and ballast do.
  Writing events first means writing options whose costs are placeholders.

## The cost that is easy to underestimate

**This phase changes the run phase machine.** `RunManager.Phase` is
`{ COMBAT, NODE, VICTORY, DEFEAT }`, and `when` dispatches over it in **five** places, of
which only **three** are exhaustive:

| Site | Exhaustive? |
| --- | --- |
| `CombatInputHandler.kt:34` | yes |
| `GameScreen.kt:43` | yes |
| `RunSimulator.kt:71` | yes |
| `CombatInputHandler.kt:213` | **no** — `else -> Unit` |
| `NodePolicyTest.kt:32` | **no** — `else -> error(...)` |

*Corrected 2026-08-28. This charter used to say "four places" and list `NodePolicyTest.kt:32`
among the exhaustive ones. **F6 is the phase that adds `EVENT`, so this is the file the error
would actually have hurt.** Adding a `Phase` value gets you a compile error from the three
exhaustive sites and **silence** from `CombatInputHandler.kt:213`, whose `else -> Unit`
swallows the new phase: an event node would simply play no sound, and nothing would tell you.
`NodePolicyTest.kt:32` fails at runtime with a readable message, which is second best but is
not the compiler. **Open `CombatInputHandler.kt:213` deliberately — it is the one site that
will not ask.** Command:*
`git grep -n "when *( *\(run\|runManager\)\.phase" develop -- 'app/src/**/*.kt'`

Adding `EVENT` is not additive content. It touches input, render, **the simulator**, and a
test — and the simulator matters most, because a phase the sim cannot drive is a phase the
balance gate is blind to. An event system the harness skips is an event system with no
balance coverage at all.

Alternative worth costing before committing: model events as a **node action** rather than a
phase. The node already exists, already has a screen, already has a policy in the sim.
Cheaper, less structurally honest. Decide it deliberately.

## Outputs

- `app/src/main/assets/events/all.json` — structure and **keys only**.
- Prose in `strings.properties` with a Spanish translation. **The vision's §2.3 says
  `events.json` will hold noir text. That is wrong and F6 must not implement it as written:**
  it breaks the repo's convention and would leave the most translation-heavy layer in the game
  untranslatable. `all.json` already stores `card.strike.description`; events do the same.
- A simulator policy for the new decision point. Non-optional — see above.

## Open decisions

1. Phase or node action (see above). Biggest call in the phase.
2. How many events for the MVP, and are they per-district or global? Per-district is better
   writing and triple the content.
3. Are events guaranteed, or randomised? Randomised events add variance the sweep must absorb.
4. Can an event be refused for free? A dilemma with a free exit is not a dilemma.

## Risk: MEDIUM-HIGH — structural, not conceptual

The content is easy and pleasant to write. The phase machine change is neither, and the sim
policy for a decision node is real design work, not plumbing.

## Forecast

~400-600 lines depending on the phase-vs-node call. **Chained PRs: mechanism first (with the
sim policy and zero content), content second.** That way the structural risk is reviewed
without prose in the diff.
