# F5 — District Bosses — CHARTER

> Charter, not a spec. Not apply-ready.

## Intent

Give each district a face. The Local Godfather closes the Slaughterhouse; the Vulture Fund
CEO closes the Casino; **the Central Bank** always closes the Boardroom (D6 — a fixed finale,
because leads are only worth investing in if you know what you are preparing for).

The generic `collector` is not deleted, it is redistributed: it was always three enemies
wearing one name.

## Inputs required

- **FV deliverable 1 — the new intent verbs. This is the hard dependency, and it is the
  reason F5 is not earlier.** With five verbs, a boss is a retexture. The current roster
  proves it: `loan_shark` is `thug` plus two verbs, `collector` is `loan_shark` plus one.
  Three more heads on the same vocabulary is three more staircases.
- **F2 complete.** The boss seats (slots 3, 6, 8) already exist as `role: BOSS`.
- **F3 complete.** A boss whose threat is financial needs the financial layer to exist.

## Outputs

- Three boss definitions with distinct intent patterns built from **different verbs**, not
  from bigger numbers on the same verbs.
- Portraits and, for the finale, an appropriate backdrop.
- Redistribution of `collector` into the new roster.
- **Payment of the card-art debt.** F2 explicitly carried it here: 15 of 27 illustrations have
  rules text baked in, in English, contradicting `all.json` in every card checked
  (`docs/ART-PIPELINE.md` §3.1), plus the ATTACK frame (§3.8). F5 regenerates art anyway, so
  the marginal cost of regenerating those fifteen with the no-text instruction is the lowest
  it will ever be. If F5 does not pay it, name the phase that will.

## Open decisions

1. **Do the zone-1 and zone-2 bosses rotate from a pool, or are they fixed too?** D6 says
   rotating. Rotation multiplies content and multiplies the balance surface; the sweep would
   need to cover the pool, not a fixed line.
2. **Does each boss get a signature verb, or share the expanded vocabulary?** Signature verbs
   read better and cost more.
3. **How do bosses interact with leads (F8)?** F8 needs a per-boss weakness to reveal. That
   weakness has to be designed *with* the boss, not bolted on after, which argues for F5
   knowing F8's shape before it ships.
4. **Does the Central Bank get a mechanic no other enemy has?** A finale that is only
   statistically harder is the staircase again, one floor higher.

## Risk: MEDIUM

Content-heavy, and the owner has confirmed content budget is not the constraint. The genuine
risk is that F5 ships three portraits over the same five verbs and calls it a roster —
exactly the failure FV exists to prevent. **The FV dependency is what makes this phase worth
doing at all.** If FV's verbs slipped, F5 must slip with them.

## Forecast

Data + intents + art + balance. **~500+ lines plus assets. Expect two or three chained PRs**,
naturally split one per boss, since each is independently revertible and independently
measurable.
