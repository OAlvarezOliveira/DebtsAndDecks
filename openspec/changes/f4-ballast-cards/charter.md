# F4 — Ballast Cards — CHARTER

> Charter, not a spec. Not apply-ready.

## Intent

Make debt something the player holds in his hand, not something he reads off a counter.
Borrowing inserts dead cards — "Interest", "Invoice" — that cost energy and do nothing. The
deck itself gets heavier as the run goes on.

This is the mechanic that turns the debt number into a felt experience. It is also the one
that most easily makes the game miserable, so it goes after the economy is settled.

## Inputs required

- **F3 complete.** Ballast attaches to the act of borrowing, and F3 defines what borrowing
  costs and when it is forced. Building ballast against the current loan economy means
  rebuilding it after F3.
- Per D5: ballast attaches to **node loans and shortfall borrowing only**. Card purchases on
  credit do not generate it. One rule, no exception table.

## Outputs

- One or more ballast card definitions in `all.json` — keyed like everything else.
- Automatic insertion on borrow, and on default (from F3's missed-minimum path).
- A removal story. The node already sells card removal at `REMOVE_BASE = 10` with escalation;
  whether that is sufficient pressure relief is a balance question, not an assumption.

## Open decisions

1. **Is ballast a pure no-op, or does it carry a micro-penalty?** A cost-1 blank is already
   punishing in a 3-energy game. A penalty on top may be redundant cruelty.
2. **Where does it enter — draw pile, discard, or hand?** Discard is gentlest, hand is
   cruellest, draw pile is the one that creates dread without immediate punishment.
3. **Does it exhaust?** Non-exhausting ballast compounds across a long fight in a way that
   may exceed anything the sweep currently produces.
4. **Does the deck have a size ceiling?** `NodePolicy.THIN_DECK = 14` suggests the simulated
   player already has an opinion about deck size. Ballast attacks that directly.

## Risk: MEDIUM-HIGH

The mechanic is thematically perfect and mechanically the most likely in the whole program to
be un-fun. Slot Kruger's law applies: dead cards are the standard way this genre expresses
punishment, and also the standard way it loses players.

The harness can measure whether ballast changes the win rate. It cannot measure whether
drawing a blank card three times in a row makes someone put the phone down. **This phase
should be re-validated against real testers**, reusing FV's protocol, before F5 builds on it.

## Forecast

~300-400 lines. One PR is plausible; two if insertion and removal are separated.
