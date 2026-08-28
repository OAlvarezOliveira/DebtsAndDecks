# F8 — Leads and Preparation — CHARTER

> Charter, not a spec. Not apply-ready. This phase was **missing from the earlier draft of
> the program** and is included here deliberately.

## Intent

Each district hides clues to its boss's weakness — in dialogue, on a sign, in an object.
Investigating costs time and money. Walking into a boss without a lead should be near-certain
death, and the player should be able to see afterwards exactly why it was his fault.

This is the phase that makes decision D7 and the logical-permadeath rule do real work.
Everything else in the program is about pressure; this is the only system that is about
*knowing*.

## Inputs required

- **F5 complete** — a weakness needs a boss to be a weakness of.
- **F6 complete** — investigation is an event, or something shaped exactly like one.
- Per D7: once investigated, the knowledge is **permanent**.
- Per D8: "permanent" means **within the run**. Across runs would need a persistence layer
  this project does not have — no save file, no profile, nothing — and it puts
  meta-progression in tension with permadeath. Cross-run leads are a good idea and are
  **their own change**, with their own storage format and migration cost.

## Outputs

- Per-boss weakness definitions, hung off the district id F2 established.
- An investigation action with a real cost.
- A mechanical expression of the lead: an "Audit" card that doubles damage against that boss,
  or a way to negate its signature intent.
- **Communication.** The player who dies without a lead must be able to see that the lead
  existed and that he skipped it. A logical death that reads as arbitrary is an arbitrary
  death.

## Open decisions

1. **Is the lead information or an item?** A card in the deck is legible and takes a slot.
   A flag on the run state is invisible and free. Legibility argues for the card; the card
   argues with ballast (F4) for deck space.
2. **How near is "near-certain death"?** A boss unbeatable without preparation is a gate; a
   boss 15% harder is a suggestion. This number decides whether the system exists.
3. **One lead per district, or several partial ones?** Partial leads reward exploration and
   multiply content.
4. **What happens on a second run through the same district?** Under D8 the player re-learns
   it — which is either honest roguelike design or tedious repetition, depending entirely on
   how expensive investigating is.

## Risk: MEDIUM

The mechanic is well-understood and low on structural risk. Two real hazards:

- **The difficulty cliff.** A boss balanced around having the lead is unwinnable without it;
  the harness's simulated policies will not investigate unless taught to, so the sweep may
  report a collapsed win rate that is a policy artefact, not a design failure. The sim policy
  must learn to investigate, or F8's harness numbers mean nothing.
- **The pull toward cross-run persistence.** D8 says no, and the pressure to say yes will be
  strongest here, at the end of the program, when it is cheapest to hand-wave. It is not a
  detail of F8. It is a change.

## Forecast

~350-450 lines plus content. One or two PRs.
