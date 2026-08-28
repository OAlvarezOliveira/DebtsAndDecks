# Art Prompt Book — Debts & Decks

> One prompt per PNG the game needs, ready to paste into Gemini / Imagen / Veo.
> Companion to [`ART-PIPELINE.md`](ART-PIPELINE.md), which records *why* each asset below is
> being regenerated. Update both when the roster changes.

**Scope.** Card illustration coverage is already 27/27 — nothing here is a gap. §4 re-cuts the
whole deck to one standard; §5–6 replace assets with recorded defects. §7 is the only genuinely
new art, and it is gated on a design decision that has not been taken yet.

---

## 0. How to use this file

1. Paste **§1 (style block)**, then the per-asset prompt, then **§2 (negatives)**. In that order.
2. Generate, then run the post-processing in **§3**. Do not skip it: every alpha defect in
   `ART-PIPELINE.md` §3.2–§3.4 came from hand-repairing a generator background after the fact.
3. Drop the file at the path given in the prompt heading. Filenames are load-bearing —
   `CombatRenderer` resolves card art by card `id` and intent icons by `IntentType.iconName`.

---

## 1. Shared style block

> Neo-noir illustration for a card game about debt and financial survival. Rain-slicked 1940s
> city rendered with modern digital painting: deep shadow, hard single-source light, wet
> reflections, cigarette haze. Restricted palette — near-black indigo (#0c0c18), desaturated
> navy (#14142a), cold bone white (#eef0fb), and a single warm brass accent (#c9a227) used
> sparingly as the focal highlight. Cynical, adult, poetic. Painterly, not photographic; no
> cartoon or chibi styling. Subject fills the frame, composed for a centred horizontal crop.

The palette values are the renderer's own constants (`CombatRenderer.kt:133–137`), so art
generated against them sits inside the card face instead of fighting it.

---

## 2. Hard negatives — paste after every prompt

> **No text of any kind.** No lettering, no words, no letters, no numbers, no digits, no
> currency symbols, no signage, no labels, no captions, no watermark, no signature.
> No crop marks, no dashed guides, no corner brackets, no dimension arrows, no rulers,
> no blueprint annotations, no mockup framing, no drop shadow outside the subject.
> No transparency checkerboard pattern anywhere, including inside enclosed shapes.
> No border, no card frame, no rounded corners — the frame is drawn by the engine.

The first line is the whole reason this file exists. **15 of 27** shipped illustrations have
rules text painted into the pixels, and it contradicts `all.json` on every card checked
(`ART-PIPELINE.md` §3.1). Two different cards carry the identical baked string. Burned-in
English also defeats i18n outright: the game ships EN and ES from `strings.properties`.

---

## 3. Output and post-processing contract

### 3.1 Ask for opaque art, not transparent art

**Recommendation: full-bleed opaque illustrations, no alpha channel at all.**

`CombatRenderer.kt:512–528` draws card art into a bounded window with `objectFit: cover` and
paints the card interior underneath it. Nothing composites the illustration over an arbitrary
background, so the alpha channel buys the game nothing — and it has cost it four defects:

| Defect | Cause | Gone with opaque art |
| --- | --- | --- |
| §3.2 checkerboard inside `asset_bubble`'s dome | flood fill could not reach an enclosed region | yes |
| §3.3 guide marks forcing a 100%-opaque bbox | background removal keyed on edge-touching marks | yes |
| §3.4 holes punched through `ejecucion`'s gavel | background removal bit into the subject | yes |
| §3.5 75% average transparent padding | subject authored small on a large canvas | yes |

If a future asset genuinely needs alpha, generate it on **flat magenta `#FF00FF`** — a colour
absent from the palette above — and chroma-key it. Never ask a generator for transparency
directly; it returns an opaque checkerboard.

### 3.2 Dimensions

| Class | Path | Generate at | Why |
| --- | --- | --- | --- |
| Card illustration | `app/src/main/assets/art/cards/<id>.png` | **1024×768 (4:3)** | The art window is `innerW / artH` = 244.0 / 185.2 = **1.317:1**. A 4:3 source crops 1.2% horizontally; today's 512×512 square loses 24.1% vertically to the cover-crop. |
| Card frame | `app/src/main/assets/art/card_frame_<type>.png` | 336×480 | Fixed by the renderer's inset constants. See §5. |
| Background | `app/src/main/assets/art/backgrounds/bg_<name>.png` | **1920×720** | Current 1280×720 is cropped ~12% top and bottom on a 20:9 phone, where the world is 1600 wide (`ART-PIPELINE.md` §3.6). |
| Intent icon | `app/src/main/assets/art/intent_<name>.png` | 128×128 | Matches the five shipped icons. |

### 3.3 Verification before commit

No ImageMagick and no numpy on this machine (`ART-PIPELINE.md` §4.4), so checks are pure PIL:

```python
from PIL import Image
im = Image.open(path)
print(im.size, im.mode)
if im.mode == "RGBA":
    a = im.getchannel("A")
    print("min alpha", a.getextrema()[0])   # 255 => fully opaque, which is what we want
```

Read the card face on device before merging. A prompt that obeys §2 in the thumbnail can still
have smuggled a number into a ledger column at full size.

---

## 4. Card illustrations — all 27

Regenerate the whole set, not only the defective ones. Mixing 1024×768 opaque plates with
512×512 alpha plates that average 75% transparent padding gives a deck that reads as two
different games.

Each entry states what the card **actually does**, taken from `app/src/main/assets/cards/all.json`
and `strings.properties`. That is context for the illustrator — **never text to render.** The
engine draws name, cost, description and type tag live from the bundle at every frame, in the
player's language, with the upgraded values substituted. Painting any of it into the pixels
freezes what has to move.

### 4.0 Working checklist

Priority **1** = a recorded defect, the art is wrong today. **2** = correct but off-standard,
re-cut for consistency. Work 1s first; the deck is playable at any point in the list.

| # | File | Pri | Why |
| --- | --- | --- | --- |
| 1 | `asset_auction.png` | 1 | baked text §3.1, guide marks §3.3 |
| 2 | `asset_bubble.png` | 1 | opaque checkerboard inside the dome §3.2 |
| 3 | `bash.png` | 2 | consistency |
| 4 | `bounced_check.png` | 1 | baked text §3.1 |
| 5 | `collateral_hold.png` | 2 | consistency |
| 6 | `compound_interest.png` | 1 | baked text §3.1 |
| 7 | `debt_forgiveness.png` | 1 | baked text §3.1 |
| 8 | `defend.png` | 2 | consistency |
| 9 | `ejecucion.png` | 1 | alpha cut into the artwork §3.4 |
| 10 | `emergency_fund.png` | 2 | 84% transparent padding — the worst in the set §3.5 |
| 11 | `eternal_debt.png` | 1 | baked text §3.1 |
| 12 | `foreclosure_express.png` | 1 | baked text §3.1, guide marks §3.3 |
| 13 | `ghost_collector.png` | 1 | baked text §3.1 |
| 14 | `golden_credit.png` | 1 | baked text §3.1, guide marks §3.3 |
| 15 | `leverage_strike.png` | 2 | consistency |
| 16 | `mortgage_collateral.png` | 1 | baked text §3.1 |
| 17 | `overdraft.png` | 2 | consistency |
| 18 | `partial_forgiveness.png` | 1 | baked text §3.1 |
| 19 | `refinanciar.png` | 2 | consistency |
| 20 | `repo_expert.png` | 2 | consistency |
| 21 | `reverse_mortgage.png` | 1 | baked text §3.1 |
| 22 | `risky_investment.png` | 1 | baked text §3.1 |
| 23 | `strike.png` | 2 | consistency |
| 24 | `subprime_loan.png` | 1 | baked text §3.1 |
| 25 | `survive.png` | 2 | consistency |
| 26 | `tactical_bankruptcy.png` | 1 | baked text §3.1 |
| 27 | `zombie_debt.png` | 1 | baked text §3.1 |

Path for every row: `app/src/main/assets/art/cards/<file>`. The filename **is** the card `id` —
`CombatRenderer` resolves art by id, so a rename silently drops the illustration.

---

### 1. `asset_auction.png` — Asset Auction
*SKILL, cost 1. Exhaust a card from hand. Gain 9 Gold.*

> An auction room after hours. A gavel mid-fall over a lot of seized domestic possessions — a
> mantel clock, a rolled rug, a child's bicycle — arranged under one hanging bulb. Bidders are
> silhouettes at the edge of the light, faceless. Brass accent on the gavel head.

### 2. `asset_bubble.png` — Asset Bubble
*ATTACK, cost 1. Damage equal to half your Debt. Debt is kept.*

> A soap-thin glass dome the size of a room, containing a miniature financial district in
> perfect detail, lit from within. A hairline fracture crosses the dome and catches the light.
> **The interior of the dome is fully painted city — never empty, never patterned, never grey.**

### 3. `bash.png` — Bash
*ATTACK, cost 2, starter. Deal 8 damage. Apply 1 Vulnerable.*

> A man swinging a heavy leather briefcase like a club, caught at the top of the arc, the case
> already split and papers bursting out of the seam. Rain-lit alley behind him. Blunt and
> unglamorous — this is a starting card, not a finisher.

### 4. `bounced_check.png` — Bounced Check
*ATTACK, cost 1. Deal 5 damage. Add 4 Debt.*

> A cheque torn clean in half mid-air over a bank counter, the two halves tumbling apart, ink
> smearing in the rain coming through an open door. Blank paper — no writing, no figures.

### 5. `collateral_hold.png` — Collateral Hold
*SKILL, cost 1. Gain Block equal to half your Debt. Debt is kept.*

> A pawnshop counter seen from the customer's side: a wristwatch and a wedding ring locked
> behind thick grilled glass, a heavy chain and padlock across the cabinet. A hand rests flat
> on the counter, not reaching. Brass on the padlock.

### 6. `compound_interest.png` — Compound Interest
*SKILL, cost 1, exhaust. Gain 1 Strength per 10 Debt.*

> A spiral staircase of stacked ledger books descending into darkness, each tier wider than the
> one above, receding past the reach of the light. Blank spines and blank pages.

### 7. `debt_forgiveness.png` — Debt Forgiveness
*SKILL, cost 2. Wipe all Debt to 0.*

> A single ledger page held over a brass ashtray, one corner already alight, the flame the only
> warm colour in the frame. A gloved hand lets it fall. Ash suspended in the air.

### 8. `defend.png` — Defend
*SKILL, cost 1, starter. Gain 5 Block.*

> A forearm raised across the face behind an overturned office desk, the desk taking the impact,
> splinters and dust in the beam of a streetlight through a broken window. Plain, reflexive,
> nothing heroic.

### 9. `ejecucion.png` — Foreclosure
*ATTACK, cost 2, exhaust. Damage equal to half your Debt, then wipe all Debt.*

> A gavel already struck, splitting a courtroom desk, documents blown outward in the shock. Low
> angle, hard light from behind, dust and paper in the air. **The gavel and every document are
> solid and unbroken.**

### 10. `emergency_fund.png` — Emergency Fund
*SKILL, cost 1. Gain 6 Block. Draw 1 card.*

> A tin cash box lifted out from under a prised-up floorboard, lid open, held in both hands in
> a dark room. The only light comes from inside the box. Subject fills the frame — this asset
> is currently 84% empty canvas and reads as a speck on the card.

### 11. `eternal_debt.png` — Eternal Debt
*SKILL, cost 1. Add 3 Debt. Add a copy to your discard pile. Gain 1 Strength per 10 Debt.*

> A ribbon of ledger paper knotted into an ouroboros, feeding back into a typewriter carriage
> that is printing the same ribbon it is being fed. Blank paper throughout.

### 12. `foreclosure_express.png` — Foreclosure Express
*ATTACK, cost 1. Deal 6 damage. Gain 4 Gold.*

> A repossession crew forcing an apartment door at dawn, moving fast and rehearsed, furniture
> already halfway to the van idling in the street. Headlights raking wet asphalt. Brass on the
> crowbar.

### 13. `ghost_collector.png` — Ghost Collector
*ATTACK, cost 1. Deal 5 damage. Apply 2 Weak.*

> A debt collector in a long coat standing in a tenement hallway, faintly translucent — the
> wallpaper pattern reads through his chest. His shadow on the wall is solid and cast wrong.
> Hat brim hiding the eyes.

### 14. `golden_credit.png` — Golden Credit
*SKILL, cost 2. Gain 4 Credit this turn.*

> A letter of credit on heavy stock, embossed and gold-leafed at the edge, lying on black
> lacquer under a single beam. The gold is the brightest thing in the frame. Blank stock — the
> embossing is a pattern, never characters.

### 15. `leverage_strike.png` — Leverage Strike
*ATTACK, cost 1. Deal 5 damage. Deal 1 extra damage per 10 Debt.*

> A crowbar driven under a steel shutter, a boot on its end, the fulcrum resting on a stacked
> column of ledgers — the taller the stack, the more the bar bites. The shutter is buckling.
> Brass along the bar.

### 16. `mortgage_collateral.png` — Mortgage Collateral
*SKILL, cost 1. Gain 12 Block.*

> A rolled property deed held up like a shield, unfurling into the outline of a house that
> hardens into a barricade of stone and shutters. Rain breaking against it. Blank parchment.

### 17. `overdraft.png` — Overdraft
*SKILL, cost 1. Draw 1 card, plus 1 per 10 Debt.*

> A brass pneumatic tube station in a bank's back office, every canister fired at once,
> documents erupting from the open ports faster than the clerk's hands can take them. Blank
> paper. Motion, not violence.

### 18. `partial_forgiveness.png` — Partial Forgiveness
*SKILL, cost 0. Repay 8 Debt.*

> An open ledger, one column struck through with a single heavy pen stroke, the rest untouched.
> The pen still resting where it stopped. Ruled columns are visible as lines only — no figures.

### 19. `refinanciar.png` — Refinance
*SKILL, cost 1. Halve your Debt. Gain Block equal to the amount cancelled.*

> Two contracts sliding past each other across a lawyer's desk in opposite directions, one
> being folded in half as it goes, the fold line catching the lamp. Two pairs of hands, no
> faces. Blank paper.

### 20. `repo_expert.png` — Repossession Expert
*ATTACK, cost 1. Deal 7 damage. Apply 1 Weak.*

> A repo man crouched at the door of a parked car in the rain, slim jim already in the window
> seal, tool roll open on the wet kerb beside him. Unhurried, professional, done this a
> thousand times. Brass on the tools.

### 21. `reverse_mortgage.png` — Reverse Mortgage
*SKILL, cost 1. Gain 4 Gold per 10 Debt.*

> A narrow row house tilted and pouring coins out of its own front door into a storm drain,
> the structure visibly thinning as it empties. Streetlight above, everything else in shadow.

### 22. `risky_investment.png` — Risky Investment
*SKILL, cost 0. Gain 12 Gold. Lose 6 HP.*

> A bare hand gripping a rising line of a stock chart rendered as a blade edge, the line
> climbing steeply, blood running down the wrist and beading on the glass. The chart is pure
> geometry — a line and a grid, no axis marks.

### 23. `strike.png` — Strike
*ATTACK, cost 1, starter. Deal 6 damage.*

> A bare fist landing, seen close and side-on in the rain, knuckles split, water thrown off the
> impact. No weapon, no flourish. The most ordinary violence in the game.

### 24. `subprime_loan.png` — Subprime Loan
*SKILL, cost 0. Gain 3 Credit this turn. Add 3 Debt.*

> A loan contract on a desk, the lower half of the page dissolving into a dense grey texture
> like insects. A fountain pen bleeding a slow pool across it. The texture reads as pattern,
> never as legible writing.

### 25. `survive.png` — Survive
*SKILL, cost 1, starter. Gain 8 Block.*

> A man braced hard in a doorframe with both arms as the room behind him comes apart — plaster
> dust, a light fixture swinging, the floor at a wrong angle. Holding, not winning.

### 26. `tactical_bankruptcy.png` — Tactical Bankruptcy
*SKILL, cost 1. Lose 8 HP. Wipe all Debt to 0.*

> A man in shirtsleeves pulling a fire alarm in his own office as the room behind him folds
> inward, filing cabinets toppling toward him. He is not running. Deliberate, calm, ruined.

### 27. `zombie_debt.png` — Zombie Debt
*SKILL, cost 0. Add 2 Debt. Gain 1 Credit. Add a copy to your discard pile.*

> A hand of grey paper — fingers made of layered invoices — pushing up through a cemetery of
> filing drawers, soil and shredded documents falling away. Blank paper throughout.

---

## 5. Card frames (2) — read this before generating

**A generator cannot satisfy this contract.** The frame is drawn *last, over everything*, and
the renderer requires a **fully transparent rectangular window** at exact coordinates. Ask an
image model for that and you get an opaque slab, which is precisely defect §3.9.

Required geometry, from `FRAME_INSET_X = 0.137` / `FRAME_INSET_Y = 0.129` on a 336×480 canvas:

| | px |
| --- | --- |
| Canvas | 336 × 480 |
| Transparent window | x **46 → 290**, y **62 → 418** (244 × 356) |
| Opaque border | everything outside that rectangle |

**Procedure:** generate the ornament on the border band only, then knock the window out in PIL:

```python
from PIL import Image
im = Image.open(src).convert("RGBA")
im = im.resize((336, 480), Image.LANCZOS)
px = im.load()
for x in range(46, 290):
    for y in range(62, 418):
        px[x, y] = (0, 0, 0, 0)
im.save(dst)
```

### 5.1 `card_frame_attack.png`
Defect §3.8: lettering stamped into the lower moulding, reading as `...TSTOPPED`. It is on
**every ATTACK card in the game**.

> An ornate rectangular border in tarnished gunmetal and oxblood enamel, art-deco chevrons
> running the long edges, a heavier moulding across the bottom band. The centre is empty flat
> magenta #FF00FF. The moulding carries geometric relief only.

### 5.2 `card_frame_power.png`
Defect §3.9: **96.4% opaque** inside the window, against 8.6% and 11.5% for attack and skill. A
POWER card would render as a solid slab — no art, no name, no description. `all.json` holds 18
SKILL and 9 ATTACK and **zero POWER**, so nothing triggers it today. It breaks on the first one.

> An ornate rectangular border in blackened brass with a cold violet enamel inlay, art-deco
> rays radiating from the top centre, a heavier moulding across the bottom band. The centre is
> empty flat magenta #FF00FF. The moulding carries geometric relief only.

---

## 6. Backgrounds (2) — 1920×720

Both currently ship at 1280×720 and lose ~12% top and bottom on a 20:9 device. Note that the
vignette is **baked into these files on purpose** (§3.7): a true overlay also dims the HUD and
the combat log.

### 6.1 `backgrounds/bg_combat.png`
> A rain-blackened alley between two financial-district towers at night, seen straight on and
> deep. Fire escapes, a flooded gutter throwing back neon, steam from a grate. Composition is
> empty through the centre third — combatants stand there. Heavy vignette baked into the
> corners. Wide panoramic framing.

### 6.2 `backgrounds/bg_reststop.png`
> The interior of a shuttered all-night diner, stools empty, one bulb still lit over the
> counter, rain on the plate glass and the street beyond dissolved into wet light. Warmer than
> the combat plate but still cold. Composition empty through the centre third. Heavy vignette
> baked into the corners. Wide panoramic framing.

---

## 7. Intent icons — conditional, 128×128

`IntentType` has exactly five values today (`EnemyDefinition.kt:40–46`), each with a shipped
icon. `openspec/changes/fv-core-validation/proposal.md` introduces three enemy verbs. **If** they
land as `IntentType` values, `IntentTypeCoverageTest` turns red until each one has both an icon
on disk and a display key in *both* bundles — the test walks `IntentType.entries` and reads the
`.properties` files directly, so a missing asset is caught rather than silently rendering a
blank bar.

**Do not generate these yet.** Whether the verbs become intent types or resolve elsewhere is
undecided. Prompts are drafted so they are ready the moment it is.

Shared icon style: *flat single-colour pictogram, bone white #eef0fb on transparent, heavy
even stroke weight, no gradient, no perspective, readable at 32px, matching a set of five
existing icons for attack / buff / debuff / multi-attack / levy.*

| File | Verb | Prompt subject |
| --- | --- | --- |
| `intent_foreclose.png` | **FORECLOSE** — seizure once Debt crosses the threshold | A padlock closing over a simple house silhouette |
| `intent_audit.png` | **AUDIT** — disables a card tag for N turns | A magnifying glass over a document, the lens crossed by a bar |
| `intent_hedge.png` | **HEDGE** — enemy block scaled by the player's Debt | A shield whose lower half is built from stacked coins |

---

## 8. Video intro

Veo generates in short takes, so this is four shots to cut together, not one prompt. Keep the
title card out of the generator — burn it in afterwards, for the same reason §2 exists.

**Shared:** *Neo-noir, 1940s city, modern cinematic grade. Anamorphic, shallow depth of field,
practical light only — streetlamps, neon, a desk lamp. Palette near-black indigo, navy, bone
white, one brass accent. Rain throughout. No text, no subtitles, no watermark, no on-screen
lettering.*

| # | ~sec | Prompt |
| --- | --- | --- |
| 1 | 8 | Slow crane down the face of a financial-district tower at night, rain streaking the glass, hundreds of lit windows going dark floor by floor as the camera descends toward the street. |
| 2 | 8 | Close on a man's hands at a desk — Alistair Vance, ex-appraiser — sorting a stack of blank documents into two piles under a single desk lamp. One pile is far taller. He stops. No face. |
| 3 | 8 | He steps out of a doorway into the rain, coat collar up, face still unseen, and the street ahead resolves into a row of district gates receding into fog, each one lit differently. |
| 4 | 8 | Static wide of the empty street behind him, the rain still falling, one brass streetlamp holding the frame as everything else drops to black. Hold on black. |

Title card in the editor: **DEBTS & DECKS**, bone white on black, brass rule beneath.
Voice-over, if used, is authored in `strings.properties` like all prose — never burned into
the video, or the Spanish build ships an English intro.

---

## 9. Not specifiable yet

The vision names ten districts and district bosses — the Local Godfather, the Vulture Fund CEO,
the Central Bank — that replace the generic `collector`. That art cannot be briefed until the
districts exist as data. `sequence.json` still holds eight abstract slots.

Screens still on placeholder art (`ART-PIPELINE.md` §5) — node selection, rest stop, reward
header, `renderRunEnd` — need layout decisions before illustration, not prompts.
