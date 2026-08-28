# F7 — The Market (passive assets) — CHARTER

> Charter, not a spec. Not apply-ready.

## Intent

Replace abstract relics with **assets**: shares, real estate, things that pay while you are
busy being hit. The player stops merely surviving his debt and starts owning something.

## Inputs required

- **F3 complete** — the currency has to be settled before anything generates it passively.
- **F4 complete** — borrowing to buy an asset is exactly the kind of leverage that should
  generate ballast, and F4 owns that rule.

## The correction this charter exists to carry

The vision says the hook already exists: `applyInterest` in `startPlayerTurn`.

**There is no `startPlayerTurn` anywhere in this repository.** Claim, carried by checklist
row A7:
`applyInterest` is called from `CombatEngine.kt:319`, inside `private fun beginTurn()`
(declared at line 304). It fires **once per combat turn**.

The consequence is not cosmetic. Three to five turns per combat across eight combats is
roughly **thirty ticks per run**, not eight. An asset advertised as "pays $X per month"
attached to that hook pays thirty times, not seven. Any yield designed against a per-node
mental model will be off by a factor of four.

And the hook is `private`. Exposing it is a change to the most balance-sensitive method in
the codebase — the one that applies compounding interest, the mechanism the entire
debt-as-leverage thesis rests on.

This is why F7 is last. It is the only phase whose core hook is inside the combat loop.

## Outputs

- An asset inventory on the run state.
- A market surface — probably the node, possibly its own screen.
- Passive yield resolution, on a tick whose frequency is **decided and written down**, not
  inherited by accident.
- Sim policy coverage: a simulated player who never buys assets and one who does. If they
  perform identically, the system is decoration.

## Open decisions

1. **Which tick?** Per combat turn (the existing hook, ~30/run), per node (7/run, matches
   D2's "month"), or per combat (8/run). D2 already chose the node as the month, which argues
   for per node — and against reusing `beginTurn` at all.
2. **Do assets stack, and is there an inventory cap?** Uncapped passive income in a
   deck-builder is how a run becomes unloseable by node 6.
3. **Can assets be seized?** Thematically irresistible: the collector taking your building.
   Mechanically, it is a fourth way to lose things and needs its own UI communication.
4. **Do assets appear in the deck?** If not, they are a second resource system with no
   interaction with the first.

## Risk: HIGH

Compounding passive income against compounding interest is the single most dangerous balance
interaction available in this design. Two exponentials pointing at each other.

Mitigation: linear yields only for the first delivery, an explicit cap, and a sweep before
and after every single asset added. Not a batch of six.

## Forecast

~400-500 lines. **Chained PRs**: inventory + tick + one asset, then the catalog.
