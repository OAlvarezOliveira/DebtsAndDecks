# Vision — Debts & Decks: Financial Survival in a Noir City

**Status:** proposed, **deliberately unverified** (see `openspec/VERIFICATION-CHECKLIST.md`).
**Date:** 2026-08-28 · **Branch:** `develop` · **Owner:** project owner.
**Supersedes:** nothing. `docs/GDD.md` remains the description of what the code does today;
this document describes what it is becoming. Where they disagree about the *future*, this
wins. Where they disagree about the *present*, the code wins and both documents are wrong.

---

## 1. The pitch

**Debts & Decks is a deck-builder about financial survival in a noir city.** The player is
**Alistair Vance**, ex-appraiser at Liquidations, working districts of a city that owes him
and collects from him. His life is his **treasury**. His deck is his means: financial
instruments and violence, and the game refuses to tell him which is which. Each district has
a face, a name, and a boss who represents a specific way of being owned by money.

The current build already believes half of this. `STARTING_DEBT = 6` means no run begins
clean, and `+floor(debt / 6)` on every attack means debt is the engine, not the punishment.
What is missing is the world the debt lives in: districts instead of slots, faces instead of
tiers, choices instead of a corridor.

## 2. The seven systems

1. **Life is money (treasury).** The balance is the life resource. You lose when the debt
   hits the execution limit (the system forecloses) or when you cannot meet the **monthly
   minimum** at its due date (technical bankruptcy). See decision D1 and D9 for how this
   coexists with HP and with the current numeric scale.
2. **Districts.** The run advances through named districts, not abstract floors. Each has an
   identity: name, background, NPCs, tone. The existing 8-slot sequence is re-cut into zones
   without changing the number of fights.
3. **Events.** Between combats an NPC offers a deal with teeth: cash now against ballast in
   the deck later. Data-driven, keyed, translatable.
4. **Ballast cards.** Borrowing inserts dead cards ("Interest", "Invoice") into the deck.
   Debt stops being a number you watch and becomes a hand you have to play around.
5. **The market.** Instead of abstract relics, you buy **assets** — shares, real estate —
   that pay passively. The most balance-sensitive system in the vision, and therefore last.
6. **District bosses.** Each district ends in its own antagonist: the Local Godfather, the
   Vulture Fund CEO, the Central Bank. The generic collector is redistributed into them.
7. **Leads and preparation.** Each district hides clues to its boss's weakness. Walking in
   without one is near-certain death — **logically**, never arbitrarily.

**Logical permadeath** is the rule underneath all seven: a run ends because the player did
something, not because the game decided. Every death is announced by the UI before it lands.

## 3. The tone

Neo-noir, cynical, poetic, adult. The interactive-fiction experiment in this setting is
**not a game mode** — it is the mould for the writing and for the ten districts:

> The Slaughterhouse of the Insolvent · The District of Needles · The Palace of Injustice ·
> The Liquidation Zone · The Vulture Funds Casino · Julian's Vault · The Monopoly District ·
> The Corporate Ladder · The Boardroom · Tax Hell

Ten districts is the whole game. A run visits three.

All prose is authored in English in `strings.properties` and translated in
`strings_es.properties`. No literal ever enters a data file or a Kotlin source.

---

## 4. Decisions

Nine decisions were open. Number 3 was closed by the owner. The other eight are resolved
below, each with the alternative that lost and why.

### D1 — Treasury vs HP: **hybrid**

HP stays as **physical integrity**: what enemy attacks damage, what block absorbs, what
healing restores. The treasury is the **life of the run**: what the city takes, what
forecloses, what ends you. Two axes, two failure modes.

*Rejected:* pure treasury (damage hits cash directly). It reads beautifully in one sentence
and collapses on contact with the deck — 23 of the 27 live cards are built on damage and
block, `EnemyIntent` speaks in damage, and the entire harness measures HP at victory.
Collapsing the axes is not a design change, it is a rewrite of the combat layer to buy a
metaphor the hybrid already delivers.

*Cost accepted:* two simultaneous defeat conditions plus HP-0 makes three. **The UI contract
that distinguishes them is a mandatory deliverable of F3**, not a follow-up. Without it the
logical-permadeath rule stops being verifiable, which is the same as it not existing.

### D2 — The "month": **one node = one month**

The due date fires when the player enters a node. Seven nodes, seven months, one per rest
between fights. The reading is clean: you leave a fight, you face the bill.

*Rejected:* per N combats (arbitrary, invisible to the player) and per combat turn (the
`beginTurn` hook fires roughly 30 times per run — see D8 — which would turn the monthly
minimum into a per-turn tax and duplicate the interest that already lives there).

### D3 — Zones per run: **3 zones over the existing 8 slots** *(closed by the owner)*

The split is **3 + 3 + 2**, and the internal shape is resolved here:

| Zone | Slots | Street | Boss | Current enemies (unchanged in F2) |
| --- | --- | --- | --- | --- |
| 1 | 1-3 | 1, 2 | 3 | thug, thug, **loan_shark** |
| 2 | 4-6 | 4, 5 | 6 | thug, loan_shark, **loan_shark** |
| 3 | 7-8 | 7 | 8 | collector, **collector** |

The boss slot is always the last of its zone, which is where the existing sequence already
puts its hardest enemy — so F2 is pure metadata and its balance delta is provably zero.
F5 later replaces the three boss slots with named antagonists; F2 only marks the seats.

Zone 3 is short on purpose: it is the finale, and the run length is fixed.

### D4 — Monthly minimum: **recalibrated interest, charged at the due date**

Interest keeps accruing per combat turn as it does today. At the node, the accrued interest
becomes the **minimum due**. Pay it in cash or default. Defaulting is not instant death: it
adds the unpaid amount to the principal *and* inserts ballast (see D5), which is how a
missed payment actually works. Technical bankruptcy — the second defeat condition — fires
when the player cannot cover the minimum **and** has no borrowing headroom left, i.e. the
system has already refused him.

*Rejected:* a fixed percentage of debt (a second interest rate with no narrative), and a
scaling minimum (two escalation curves interacting with `NodeConfig.ESCALATION`, which is a
balance surface nobody can hold in their head).

### D5 — Ballast: **node loans and shortfall borrowing only**

Taking the node loan and borrowing to cover an energy shortfall insert ballast. Buying a
card on credit does not. Reason: ballast must attach to the act of *borrowing*, so the
player learns one rule instead of an exception table. Card purchases already cost gold and
already escalate; taxing them twice makes the shop unusable by node 5.

### D6 — Bosses: **fixed final boss, rotating zone bosses**

Zones 1 and 2 draw their boss from a pool. Zone 3 always ends in **The Central Bank**. The
narrative needs a last name — the vision's arc is toward the institution, not toward a
random face — and a fixed finale is what makes leads (D7) worth investing in.

### D7 — Leads: **permanent knowledge, paid for with time and cash**

Once investigated, the weakness is known for good. A consumable clue turns preparation into
inventory management; permanent knowledge turns it into a decision made once and lived with.

### D8 — Scope of that permanence: **within the run**

"Permanent" means until the run ends. Across runs would require a **persistence layer that
does not exist anywhere in this project today** — no save file, no profile, no meta-currency,
nothing — and it puts meta-progression in tension with the permadeath rule in §2.

*Rejected with a caveat:* cross-run leads are a genuinely good idea and this is not a
permanent no. It is a statement that they are **their own change**, with their own storage
format, migration story and cost, and that hiding them inside F8 would be dishonest about
what F8 costs.

### D9 — Renumbering the 27 cards: **no. Presentation scale for money, engine untouched.**

This is the decision with the widest blast radius, so it is stated precisely.

- The engine keeps Debt and Gold as small integers. `STARTING_DEBT`, `EXECUTION_THRESHOLD`,
  `LOAN_GOLD_BASE`, every leverage divisor: unchanged units.
- A single formatting scale renders those units as money in the UI. One constant, one
  formatter, one place.
- **Damage, block, HP and energy are not money and are never scaled.** A card deals 6 damage
  and always will. The player sees dollars for the city's ledger and points for what a
  crowbar does to a man. Those are two different things, not two dialects of one thing.
- Therefore: no renumbering of `all.json`, no re-illustration of 15 cards, no invalidation
  of the balance corpus.

*Rejected:* renumbering to the $58,000 scale. It would touch card data, the i18n strings in
two languages, 15 illustrations and every balance test, and — this is the part that matters
— it would put `floor(debt / 2)` cards at 29,000 damage against a 52 HP boss. The scale is
not decorative; the divisors are load-bearing.

*A consequence the vision should own:* §2.1's "-$58,000, limit -$100,000" was written as an
example, and the ratio it implies is not the ratio the game has. At those numbers the player
starts 58% of the way to execution; today he starts at 12% (6 of 50). **That gap is a real
design question, not a formatting one**, and it belongs to F3's balance work, measured
against the harness — not to a display constant. F3 must answer it explicitly: does the run
start under pressure or does the pressure build?

*Still mandatory regardless:* parametrizing the card descriptions
(`card.strike.description=Deal {0} damage.`) is prep work for F3. Not because of the scale,
but because the number lives in four places today and is already provably out of sync in one
of them. The pattern already exists in this same bundle for intents (`intent.levy=Levy {0}
Debt`); the cards simply never adopted it.

---

## 5. Program

| Phase | What | Why here |
| --- | --- | --- |
| **FV** | Core validation: new intent verbs + the external playtest the GDD already specifies | Tests the most expensive assumption with the cheapest work |
| **F0** | This document, the GDD delta, the program | Documentation only |
| **F1** | Normalize the harness invariants to ratios | The instrument before the work |
| **F2** | Districts: 8 slots -> 3 zones, identity, backgrounds | Provably zero balance delta |
| **F3** | Treasury | The economic re-thesis, before anyone spends the currency |
| **F4** | Ballast cards | Direct consequence of F3's loan economy |
| **F5** | District bosses | Needs zones (F2), currency (F3), verbs (FV) |
| **F6** | Events | New run phase; its vocabulary of consequences exists after F3+F4 |
| **F7** | Market | The `beginTurn` hook is the most balance-sensitive surface there is |
| **F8** | Leads and preparation | Needs bosses (F5) and events (F6) |

The ordering principle: **instrument, then structure, then currency, then the things that
spend it.** The alternative — treasury last — forces F4 through F7 to be calibrated against
an economy that is then replaced, and recalibrated wholesale afterwards.

## 6. What this vision does not do

- It does not change the run length. Eight combats, decided by the owner. Re-length is a
  later, isolated change measured against a normalized harness.
- It does not add a mode. The interactive fiction is a mould, not a product.
- It does not add enemies for their own sake. Content budget is not the constraint;
  **intent vocabulary is**. Three enemies today are not a roster, they are a staircase:
  `loan_shark` is `thug` plus two verbs, `collector` is `loan_shark` plus one. A fourth head
  on the same five verbs is a fourth reskin.
- It does not claim to be verified. It is a proposal. The checklist that would verify it is
  in `openspec/VERIFICATION-CHECKLIST.md`, and it must be run by someone who did not write
  this.
