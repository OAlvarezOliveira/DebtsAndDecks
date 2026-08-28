# Gemini prompt sheet — world art, one asset at a time

Everything the game needs that is **not** a card illustration: the two placeholder intent icons,
the three district backgrounds and the enemy portraits.

Each numbered block is a **complete, self-contained prompt**. Copy one block, paste it, generate,
run the post-processing step for its class, save under the filename in the heading. Nothing has to
be assembled and nothing else has to be pasted with it.

The italic line under each heading is what the asset must read as. It is there so you can judge the
result — **never paste it**.

Companion to [`ART-PROMPTS-GEMINI.md`](ART-PROMPTS-GEMINI.md) (the 27 card illustrations).

---

## Read this first: the renderer decides the composition

The card prompts could be written from the fiction alone, because a card illustration is drawn into
a clean rectangular window. These assets are not: they are drawn **underneath and behind live UI**,
at sizes the prompt has to be written for. Every number below is read out of
`CombatRenderer.kt`, not assumed.

The combat screen is a **1280x720 design space**, y measured from the bottom
(`screenHeight = 720f`, line 115). Converted to distance from the *top* of the screen:

| Band | From top | What occupies it |
|---|---|---|
| 0–50 px | very top | enemy intent icon + label (`drawIntent`, `y + h + 6`) |
| 50–270 px | upper third | **the enemy portraits** (`enemyAreaY = 450f`, box 180x220) |
| ~300 px | | enemy HP bars, block, strength readouts |
| 300–500 px | middle band | **nothing — this is the only reliably visible part of the background** |
| 670–720 px | bottom strip | the player's hand and HUD (`playerAreaY = 50f`) |

Two consequences the first version of this sheet got wrong:

1. **The interesting part of a background belongs in the MIDDLE band, not the upper third.** The
   upper third is where the enemies stand. Detail put there is covered up.
2. **Backgrounds are drawn "cover", not stretched.** `drawBackground` (line 204) scales by
   `max(worldWidth / 1280, 1)` on both axes and lets the surplus height fall off the **top and
   bottom equally**. On any screen wider than 16:9, the top and bottom edges are cropped. Nothing
   load-bearing may sit near them.

And for the portraits, the number that governs everything:

> `w = 180f`, and the art is square, so `spriteH = w * (height/width)` = **180**.
> **A 512x512 portrait is displayed at 180x180 px** in a 1280-wide design space — 14% of the screen width.

At that size a full-body figure is about 180 px tall and its head is roughly **22 px**: a smudge.
The three portraits currently in the repo are full-body, which is why they read as figurines. The
prompts below ask for **waist-up** framing instead, which puts the head at roughly 60 px and lets
the silhouette do the work. That is a deliberate change from the existing art — if you want the
full-body convention kept, swap the SUBJECT line and leave everything else alone.

## What art alone does NOT fix

Three of the sections below have a code dependency. Generating the images without it changes
nothing on screen:

- **District backgrounds** — `CombatRenderer.kt` hardcodes `drawBackground(batch, "bg_combat")` at
  lines 154, 834 and 858, and preloads exactly `listOf("bg_combat", "bg_reststop")` at line 73.
  `RunSequence.kt:11-12` says it outright: `districtId` and `role` "are metadata — nothing in" the
  renderer consumes them.
- **New enemies** — `enemyTextures` (line 49) is a hardcoded `mapOf` of exactly three literal ids.
  A fourth enemy needs an entry there as well as in `enemies/all.json` and `run/sequence.json`.
  Sections 9 to 11 are therefore a **proposal**; do not generate them before that roster decision.
- **The shadow behind a portrait** — `shadowRect` (line 181) paints a 35%-black 180x220 rectangle
  offset by (+6, -6) *behind* every enemy. Against a transparent cut-out that reads as a shifted
  dark box, not as a shadow under a figure. Worth fixing when the portraits land.

## About transparency

Sections 1, 2 and 6 to 11 need an alpha channel. Do **not** ask the generator for one: image models
routinely answer "transparent background" with an opaque image that has a grey-and-white
**checkerboard painted into it**, which looks right in the chat window and is broken in the game.

Every prompt below therefore asks for a flat, uniform background in a colour that appears **nowhere**
in the game's palette, and the alpha is cut locally afterwards with the scripts at the end of this
sheet. Icons get pure white (no colour spill on antialiased edges); portraits get chroma-key green.

Resolution works the same way. Asking for "128x128" or "512x512" wastes the instruction — the model
generates at its own size. Ask for the **aspect ratio** and the largest image it will produce, then
downscale with LANCZOS in the post-processing step.

---

# Part A — intent icons

Two icons, to replace the 395-byte flat orange placeholders on `feat/fv-verbs-foreclose-hedge`.
Final files: `app/src/main/assets/art/intent_foreclose.png` and `intent_hedge.png`, 128x128 RGBA.

These are **not** noir paintings. They join five existing pictograms (`intent_attack`,
`intent_buff`, `intent_debuff`, `intent_levy`, `intent_multi`): flat solid black, no colour, read at
about 24 px on a phone. The two new glyphs must be unmistakable against the five that exist —
`intent_attack` is a dagger, `intent_buff` is an upward chevron, so a blade shape and a chevron
shape are both already taken.

## 1. `intent_foreclose.png` — FORECLOSE

*The Collector's verb: it takes the thing itself, not the money. Must not be confusable with the dagger of ATTACK.*

```
A single icon in the visual language of a filled Material Symbols glyph or an airport wayfinding pictogram: one solid black shape, stencil-simple, with no interior detail beyond a few thick cut-out gaps.

THE GLYPH: a heavy closed padlock, seen straight on, with the simple silhouette of a small house cut cleanly out of the middle of its body as negative space. The padlock is one single connected mass and fills most of the frame; the house is a plain gable shape, a square with a triangular roof, nothing more.

Rendered as pure black #000000 on a completely flat, uniform, pure white #FFFFFF field that fills the entire frame. The image contains only two values, black and white, with clean antialiasing between them and nothing else. Every stroke and every gap is at least one twelfth of the icon's width, so the shape still reads when it is shrunk to 24 pixels. Square framing, the glyph centred, with an even white margin on all four sides; the glyph does not touch any edge.

Flat vector-style artwork: no gradient, no shading, no texture, no second colour, no outline stroke, no drop shadow, no glow, no bevel, no 3D, no perspective, no highlight, no keyhole detail, no rivets. No lettering, words, digits or symbols of any kind. No circle, square, rounded app-icon tile or badge behind the glyph. No frame, no border.

Generate at the largest square resolution available.
```

## 2. `intent_hedge.png` — HEDGE

*The enemy is covering itself against what you are about to do. Must not be confusable with the upward chevron of BUFF — so: not a shield, not a chevron. An umbrella is the insurance glyph, and it is exact here.*

```
A single icon in the visual language of a filled Material Symbols glyph or an airport wayfinding pictogram: one solid black shape, stencil-simple, with no interior detail beyond a few thick cut-out gaps.

THE GLYPH: an open umbrella seen straight on, a bold solid dome with a short straight shaft and a simple hooked handle below it. The dome is a single unbroken mass with two or three wide scallops along its lower edge, no more. The whole glyph is one connected shape.

Rendered as pure black #000000 on a completely flat, uniform, pure white #FFFFFF field that fills the entire frame. The image contains only two values, black and white, with clean antialiasing between them and nothing else. Every stroke and every gap is at least one twelfth of the icon's width, so the shape still reads when it is shrunk to 24 pixels. Square framing, the glyph centred, with an even white margin on all four sides; the glyph does not touch any edge.

Flat vector-style artwork: no gradient, no shading, no texture, no second colour, no outline stroke, no drop shadow, no glow, no bevel, no 3D, no perspective, no highlight, no ribs drawn as thin lines, no raindrops. No lettering, words, digits or symbols of any kind. No circle, square, rounded app-icon tile or badge behind the glyph. No frame, no border.

Generate at the largest square resolution available.
```

---

# Part B — district backgrounds

Final files: `app/src/main/assets/art/backgrounds/bg_slaughterhouse.png`, `bg_casino.png`,
`bg_boardroom.png`, 1280x720 RGB, fully opaque.

The composition clause in each block is written from the layout table above and is the same in all
three: quiet top third (enemies stand there), quiet bottom strip (the hand is there), the payload in
the middle band, and nothing load-bearing near the top and bottom edges (they get cropped).

## 3. `bg_slaughterhouse.png` — The Slaughterhouse of the Insolvent

*Where a first missed payment earns you a name. The debts are small here. So is the mercy. Street level: cold, wet, unceremonious, municipal.*

```
Neo-noir environment background for a card game about debt and financial survival. A rain-slicked 1940s city rendered as modern digital painting: deep shadow, one hard cold light source, wet reflections, haze in the air. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the one warm note in the frame. Painterly, atmospheric, unpeopled. Not photographic, not cartoon, no comic-book ink outlines.

SUBJECT: A municipal meat-packing hall at night, long since repurposed as a place where debts get settled. A row of empty steel hooks on an overhead rail recedes into the depth of the room. White tile walls streaked with damp, a floor drain, standing water holding the reflection of one bulb burning at the far end. The room is spotless, cold and recently used. Nobody is present.

COMPOSITION: 16:9 landscape. This image is a BACKGROUND for a game screen and most of it will be covered by interface. Compose it in three horizontal bands. The TOP THIRD must be simple, dark and almost empty - characters are drawn over it. The BOTTOM SIXTH must be simple and dark - the player's cards are drawn over it. The MIDDLE BAND is the only part that stays visible, so the depth, the receding perspective, the light source and the reflections all belong there. Keep everything important well away from the top and bottom edges, which will be cropped. Deep atmospheric perspective, strong sense of a long room.

Fully opaque painting reaching all four edges of the image, full bleed, with no transparency and no alpha channel.

DO NOT INCLUDE: no lettering, words, letters, numbers, signage, neon signs, labels, captions, watermarks or signatures anywhere in the scene - every surface that could carry writing is blank. No people, no figures, no silhouettes, no animals, no carcasses, no blood. No border, no vignette, no rounded corners, no mount, no mat, no pale margin, no paper edge, no framed print, no canvas edge, no drop shadow around the artwork.

Generate at the largest 16:9 resolution available.
```

## 4. `bg_casino.png` — The Vulture Funds Casino

*They wager on which debtors fold. You are the table. Mid-run: there is money here, and it is being bet on you.*

```
Neo-noir environment background for a card game about debt and financial survival. A rain-slicked 1940s city rendered as modern digital painting: deep shadow, one hard warm light source, wet reflections, cigarette smoke hanging in the beam. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the one warm note in the frame. Painterly, atmospheric, unpeopled. Not photographic, not cartoon, no comic-book ink outlines.

SUBJECT: A private gaming room a few minutes after the last hand. A long baize table runs away from the viewer into the dark, chips left standing in uneven towers along its edges. A low brass lamp hangs over the far end and the smoke still holds the shape of the people who left. Heavy curtains, a mirrored back wall that reflects almost nothing. Dressed for an audience that has already placed its bets.

COMPOSITION: 16:9 landscape. This image is a BACKGROUND for a game screen and most of it will be covered by interface. Compose it in three horizontal bands. The TOP THIRD must be simple, dark and almost empty - characters are drawn over it. The BOTTOM SIXTH must be simple and dark - the player's cards are drawn over it. The MIDDLE BAND is the only part that stays visible, so the table, the lamp, the smoke and the depth all belong there. Keep everything important well away from the top and bottom edges, which will be cropped. Deep atmospheric perspective.

Fully opaque painting reaching all four edges of the image, full bleed, with no transparency and no alpha channel.

DO NOT INCLUDE: no lettering, words, letters, numbers, signage, neon signs, labels, captions, watermarks or signatures anywhere in the scene - every surface that could carry writing is blank. No playing card faces, no pips, no suits, no dice, no roulette wheel numerals - chips are plain unmarked discs. No people, no figures, no silhouettes. No border, no vignette, no rounded corners, no mount, no mat, no pale margin, no paper edge, no framed print, no canvas edge, no drop shadow around the artwork.

Generate at the largest 16:9 resolution available.
```

## 5. `bg_boardroom.png` — The Boardroom

*Nobody raises their voice. The paperwork was signed years ago. The last district: the violence here is administrative, and it already happened.*

```
Neo-noir environment background for a card game about debt and financial survival. A rain-slicked 1940s city rendered as modern digital painting: deep shadow, one hard light source, cold reflections on polished surfaces, rain on glass. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the one warm note in the frame. Painterly, atmospheric, unpeopled. Not photographic, not cartoon, no comic-book ink outlines.

SUBJECT: A corporate boardroom high above the city at night, seen from the foot of the table. A long slab of black polished wood runs away from the viewer, empty high-backed chairs turned at slight careless angles along it, one brass desk lamp still burning at the far head. Floor-to-ceiling windows behind reduce the city to a cold scatter of light through rain on the glass. Immaculate, expensive, and on the surface entirely without menace.

COMPOSITION: 16:9 landscape. This image is a BACKGROUND for a game screen and most of it will be covered by interface. Compose it in three horizontal bands. The TOP THIRD must be simple, dark and almost empty - characters are drawn over it. The BOTTOM SIXTH must be simple and dark - the player's cards are drawn over it. The MIDDLE BAND is the only part that stays visible, so the table, the chairs, the lamp and the rain-lit glass all belong there. Keep everything important well away from the top and bottom edges, which will be cropped. Deep atmospheric perspective.

Fully opaque painting reaching all four edges of the image, full bleed, with no transparency and no alpha channel.

DO NOT INCLUDE: no lettering, words, letters, numbers, signage, labels, captions, watermarks or signatures anywhere in the scene - every surface that could carry writing is blank. No charts, no graphs, no diagrams, no readable documents, no framed pictures on the walls. No people, no figures, no silhouettes. No border, no vignette, no rounded corners, no mount, no mat, no pale margin, no paper edge, no framed print, no canvas edge, no drop shadow around the artwork.

Generate at the largest 16:9 resolution available.
```

---

# Part C — enemy portraits

Final files: `app/src/main/assets/art/enemy_<id>.png`, 512x512 RGBA. The filename is load-bearing —
`enemyTextures` resolves the portrait from the enemy `id`.

Read the two constraints in the header before generating: these render at **180x180 px**, and they
need a real alpha channel that the generator will not give you directly.

**Generate `enemy_collector.png` first.** It is the hardest and it sets the series. Once you have
one you are happy with, attach it to the next prompt as a style reference and add the line *"Match
the painting style, lighting, palette and framing of the attached reference image exactly; this is
the same artist and the same series."* Three independently generated portraits will not match each
other otherwise — different faces, different light, different scale — and the mismatch is far more
visible on screen than any single portrait's flaws.

Sections 6 to 8 replace what is on disk today. **Sections 9 to 11 are a proposal** and need a roster
decision plus a `enemyTextures` entry before they are worth generating.

Why the existing three are being replaced, not kept: they are cel-shaded comic-book art with heavy
black outlines and saturated red and teal, next to painterly restricted-palette cards. They also
carry two things the card sheet forbids outright — `enemy_collector.png` has the English word
`COLLECTIONS` painted onto the jacket, which cannot be localized, and `enemy_loan_shark.png` has an
occult pentagram on the ledger that belongs to no part of this fiction.

## 6. `enemy_collector.png` — Collector *(generate this one FIRST; it sets the series)*

*52 HP, boss, resists debuffs. Hits hardest, hits twice, levies the most Debt. The one the run is named after. The horror is that he is polite.*

```
Neo-noir character portrait for a card game about debt and financial survival, painted in the style of a 1940s noir film poster: deep shadow, one hard light source from above and slightly to one side, cold rim light along the edges of the figure, haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 on one small detail only. Painterly and adult. Not photographic. No cartoon styling, no cel shading, no comic-book ink outlines, no black contour line drawn around the figure.

SUBJECT: An enormous, immaculately dressed man in a heavy dark overcoat, so broad that he fills the frame edge to edge. He stands perfectly still and square to the viewer with both hands folded in front of him, a short length of heavy chain hanging slack from one fist - held, never brandished. His face is calm, bureaucratic, almost kind; the light catches his eyes and there is nothing behind them. Brass accent on a single coat button. Nothing else in the image.

FRAMING: waist-up, the figure centred and facing the viewer, filling the frame from side to side. Square format. This portrait is displayed very small in game, so it must work as a SILHOUETTE first: the outline alone should read as "an immense, immovable man", the shapes must be big and simple, the contrast between the figure and its own interior detail high, and there should be no fine detail that dissolves at small size. Leave an even margin on all four sides so nothing is clipped.

BACKGROUND: the figure stands against a completely flat, uniform, evenly lit chroma-key green screen, pure saturated green #00B140, filling the entire frame behind him. The green is a solid unlit colour with no gradient, no shading, no texture, no vignette and no objects in it. No green anywhere on the figure himself, and no green rim light or reflected green on his clothes or skin.

DO NOT INCLUDE: no room, environment, wall, floor, ground, horizon, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image. No lettering, words, letters, numbers, signage, labels, patches, badges, jacket lettering, clipboard writing, captions, watermarks or signatures - every surface on him is blank. No occult, religious or mystical symbols. No sunglasses, no visible weapon other than the chain, no blood. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.

Generate at the largest square resolution available.
```

## 7. `enemy_thug.png` — Thug

*22 HP, the first thing that happens to you. Attacks twice, then buffs. Cheap muscle: no plan, no leverage, just present. He must read as SMALL next to the Collector.*

```
Neo-noir character portrait for a card game about debt and financial survival, painted in the style of a 1940s noir film poster: deep shadow, one hard light source from above and slightly to one side, cold rim light along the edges of the figure, haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 on one small detail only. Painterly and adult. Not photographic. No cartoon styling, no cel shading, no comic-book ink outlines, no black contour line drawn around the figure.

SUBJECT: A young leg-breaker in a soaked overcoat two sizes too big for him, narrow through the shoulders, collar turned up, a cheap hat throwing half his face into shadow. He holds a short length of pipe low and across his body, not raised. His jaw is set and his eyes are not: he is doing a job he was told to do and would rather be elsewhere. Brass accent on a signet ring. Nothing else in the image.

FRAMING: waist-up, the figure centred and facing the viewer. He is slight, and the frame should show that - he does not fill it the way a larger man would, and there is visible green either side of his shoulders. Square format. This portrait is displayed very small in game, so it must work as a SILHOUETTE first: the outline alone should read as "a thin young man in a coat too big, holding something", the shapes must be big and simple, and there should be no fine detail that dissolves at small size. Leave an even margin on all four sides so nothing is clipped.

BACKGROUND: the figure stands against a completely flat, uniform, evenly lit chroma-key green screen, pure saturated green #00B140, filling the entire frame behind him. The green is a solid unlit colour with no gradient, no shading, no texture, no vignette and no objects in it. No green anywhere on the figure himself, and no green rim light or reflected green on his clothes or skin.

DO NOT INCLUDE: no room, environment, wall, floor, ground, horizon, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image. No lettering, words, letters, numbers, signage, labels, patches, badges, jacket lettering, captions, watermarks or signatures - every surface on him is blank. No occult, religious or mystical symbols. No blood, no wounds. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.

Generate at the largest square resolution available.
```

## 8. `enemy_loan_shark.png` — Loan Shark

*36 HP, elite, enrages below half. Attacks, buffs, debuffs and levies Debt onto you. The only one of the three who is genuinely enjoying this. He is a man, not a monster — no fangs, no shark.*

```
Neo-noir character portrait for a card game about debt and financial survival, painted in the style of a 1940s noir film poster: deep shadow, one hard light source from above and slightly to one side, cold rim light along the edges of the figure, haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 on one small detail only. Painterly and adult. Not photographic. No cartoon styling, no cel shading, no comic-book ink outlines, no black contour line drawn around the figure.

SUBJECT: A lean, elegant lender in a well-cut dark pinstripe suit, entirely relaxed, head tilted very slightly. One hand is open in a small welcoming gesture; the other holds a slim closed ledger flat against his hip. He is amused. It is the posture of a man who already knows how this ends and is in no hurry to get there. Brass accent on the ledger's corner clasp. Nothing else in the image.

FRAMING: waist-up, the figure centred and facing the viewer, angled a few degrees off square so the open hand reads clearly. Square format. This portrait is displayed very small in game, so it must work as a SILHOUETTE first: the outline alone should read as "a narrow, poised man with one hand out", the shapes must be big and simple, and there should be no fine detail that dissolves at small size. Leave an even margin on all four sides so nothing is clipped.

BACKGROUND: the figure stands against a completely flat, uniform, evenly lit chroma-key green screen, pure saturated green #00B140, filling the entire frame behind him. The green is a solid unlit colour with no gradient, no shading, no texture, no vignette and no objects in it. No green anywhere on the figure himself, and no green rim light or reflected green on his clothes or skin.

DO NOT INCLUDE: no room, environment, wall, floor, ground, horizon, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image. No lettering, words, letters, numbers, signage, labels, patches, badges, ledger writing, captions, watermarks or signatures - every surface on him is blank. No occult, religious or mystical symbols - no pentagram, no sigil, no rune. No fangs, no pointed teeth, no shark motif, no monstrous or inhuman features of any kind: he is an ordinary man. No blood. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.

Generate at the largest square resolution available.
```

## 9. `enemy_appraiser.png` — Appraiser *(PROPOSED — needs a roster decision and an `enemyTextures` entry)*

*Proposed casino street enemy: it prices you before anything else happens.*

```
Neo-noir character portrait for a card game about debt and financial survival, painted in the style of a 1940s noir film poster: deep shadow, one hard light source from above and slightly to one side, cold rim light along the edges of the figure, haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 on one small detail only. Painterly and adult. Not photographic. No cartoon styling, no cel shading, no comic-book ink outlines, no black contour line drawn around the figure.

SUBJECT: A meticulous, narrow-shouldered valuer in shirtsleeves and a buttoned waistcoat. He holds a jeweller's loupe to one eye and is looking straight through it at the viewer, one eye hugely magnified and the other narrowed. His free hand is raised, palm up and empty, waiting to be handed something. Neat, dry, faintly bored. Brass accent on the loupe barrel. Nothing else in the image.

FRAMING: waist-up, the figure centred and facing the viewer. Square format. This portrait is displayed very small in game, so it must work as a SILHOUETTE first: the outline alone should read as "a man with something held to his eye and an open hand out", those two gestures must be the largest shapes in the frame, and there should be no fine detail that dissolves at small size. Leave an even margin on all four sides so nothing is clipped.

BACKGROUND: the figure stands against a completely flat, uniform, evenly lit chroma-key green screen, pure saturated green #00B140, filling the entire frame behind him. The green is a solid unlit colour with no gradient, no shading, no texture, no vignette and no objects in it. No green anywhere on the figure himself, and no green rim light or reflected green on his clothes or skin.

DO NOT INCLUDE: no room, environment, wall, floor, ground, horizon, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image. No lettering, words, letters, numbers, signage, labels, patches, badges, captions, watermarks or signatures - every surface on him is blank. No occult, religious or mystical symbols. No blood. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.

Generate at the largest square resolution available.
```

## 10. `enemy_auditor.png` — Auditor *(PROPOSED — needs a roster decision and an `enemyTextures` entry)*

*Proposed boardroom street enemy: it finds what you did wrong and makes it your debt.*

```
Neo-noir character portrait for a card game about debt and financial survival, painted in the style of a 1940s noir film poster: deep shadow, one hard light source from above and slightly to one side, cold rim light along the edges of the figure, haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 on one small detail only. Painterly and adult. Not photographic. No cartoon styling, no cel shading, no comic-book ink outlines, no black contour line drawn around the figure.

SUBJECT: A grey, patient woman in a plain dark suit, standing square to the viewer with a thick closed folder held flat across both forearms, presented rather than carried. Reading glasses pushed up onto her head. Her expression is mild and entirely unhurried: she was here before you arrived and will be here afterwards. Brass accent on the folder's corner clip. Nothing else in the image.

FRAMING: waist-up, the figure centred and facing the viewer. Square format. This portrait is displayed very small in game, so it must work as a SILHOUETTE first: the outline alone should read as "a woman holding a heavy folder out in front of her", the folder must be a large clear horizontal mass across the lower half, and there should be no fine detail that dissolves at small size. Leave an even margin on all four sides so nothing is clipped.

BACKGROUND: the figure stands against a completely flat, uniform, evenly lit chroma-key green screen, pure saturated green #00B140, filling the entire frame behind her. The green is a solid unlit colour with no gradient, no shading, no texture, no vignette and no objects in it. No green anywhere on the figure herself, and no green rim light or reflected green on her clothes or skin.

DO NOT INCLUDE: no room, environment, wall, floor, ground, horizon, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image. No lettering, words, letters, numbers, signage, labels, folder writing, captions, watermarks or signatures - every surface on her is blank. No occult, religious or mystical symbols. No blood. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.

Generate at the largest square resolution available.
```

## 11. `enemy_underwriter.png` — Underwriter *(PROPOSED — needs a roster decision and an `enemyTextures` entry)*

*Proposed casino boss: it does not fight you, it insures against you.*

```
Neo-noir character portrait for a card game about debt and financial survival, painted in the style of a 1940s noir film poster: deep shadow, one hard light source from above and slightly to one side, cold rim light along the edges of the figure, haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 on one small detail only. Painterly and adult. Not photographic. No cartoon styling, no cel shading, no comic-book ink outlines, no black contour line drawn around the figure.

SUBJECT: A heavy-set man in evening dress with the bow tie hanging undone around his collar, leaning forward with his elbows on his knees and his hands loosely clasped, as though he has just been told a number. He is looking slightly past the viewer, calculating rather than threatening. A cigar burns forgotten between two fingers, one thin line of smoke rising. Brass accent on a cufflink. Nothing else in the image.

FRAMING: waist-up, the figure centred, leaning towards the viewer so the shoulders and clasped hands are the dominant mass. Square format. This portrait is displayed very small in game, so it must work as a SILHOUETTE first: the outline alone should read as "a big man leaning in, hands together", the forward lean must be unmistakable in outline, and there should be no fine detail that dissolves at small size. Leave an even margin on all four sides so nothing is clipped.

BACKGROUND: the figure sits against a completely flat, uniform, evenly lit chroma-key green screen, pure saturated green #00B140, filling the entire frame behind him. The green is a solid unlit colour with no gradient, no shading, no texture, no vignette and no objects in it. No chair, no furniture, nothing but the man and the green. No green anywhere on the figure himself, and no green rim light or reflected green on his clothes or skin.

DO NOT INCLUDE: no room, environment, wall, floor, ground, horizon, chair, table, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image. No lettering, words, letters, numbers, signage, labels, patches, badges, captions, watermarks or signatures - every surface on him is blank. No occult, religious or mystical symbols. No blood. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.

Generate at the largest square resolution available.
```

---

# Post-processing

Save whatever the generator gives you into a scratch folder first. These two scripts produce the
files that actually go into `app/src/main/assets/art/`.

## Icons — black glyph on white, to 128x128 RGBA

Luminance becomes alpha, so the antialiased edges survive for free.

```python
# python3 icon.py raw_foreclose.png app/src/main/assets/art/intent_foreclose.png
import sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
g = Image.open(src).convert("L")
alpha = g.point(lambda v: 255 - v)                  # white -> transparent, black -> opaque
black = Image.new("L", g.size, 0)
out = Image.merge("RGBA", (black, black, black, alpha))
out.resize((128, 128), Image.LANCZOS).save(dst)
print(dst, out.size, "-> 128x128")
```

## Portraits — green screen, to 512x512 RGBA

Hard-keys the flat green, feathers the boundary, and pulls green spill down to the second-highest
channel so haloes do not survive.

```python
# python3 portrait.py raw_collector.png app/src/main/assets/art/enemy_collector.png
import sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
out = Image.new("RGBA", (w, h))
sp, op = im.load(), out.load()

HARD, SOFT = 40, 10          # green excess: >HARD is key, <SOFT is subject
for y in range(h):
    for x in range(w):
        r, g, b = sp[x, y]
        excess = g - max(r, b)
        if excess > HARD:
            op[x, y] = (0, 0, 0, 0)
        elif excess > SOFT:
            a = int(255 * (HARD - excess) / (HARD - SOFT))
            op[x, y] = (r, min(g, max(r, b)), b, a)   # despill
        else:
            op[x, y] = (r, g, b, 255)

out.resize((512, 512), Image.LANCZOS).save(dst)
opaque = sum(1 for y in range(0, h, 8) for x in range(0, w, 8) if op[x, y][3] > 0)
print(dst, "-> 512x512, %.0f%% of the frame is subject" % (100 * opaque / ((h // 8) * (w // 8))))
```

If that last percentage comes out near 100, the key did not fire: the generator returned an opaque
image without a green field, and the raw file has to be regenerated rather than patched.

## Backgrounds — to 1280x720 RGB

```python
# python3 bg.py raw_casino.png app/src/main/assets/art/backgrounds/bg_casino.png
import sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
side = min(w, h * 16 // 9)                          # centred 16:9 crop
left, top = (w - side) // 2, (h - side * 9 // 16) // 2
im.crop((left, top, left + side, top + side * 9 // 16)) \
  .resize((1280, 720), Image.LANCZOS).save(dst, optimize=True)
print(dst, "-> 1280x720")
```

---

# Checklist

| # | File | Final | Mode | Post-step | Blocked on |
|---|---|---|---|---|---|
| 1 | `art/intent_foreclose.png` | 128x128 | RGBA | `icon.py` | — |
| 2 | `art/intent_hedge.png` | 128x128 | RGBA | `icon.py` | — |
| 3 | `art/backgrounds/bg_slaughterhouse.png` | 1280x720 | RGB | `bg.py` | renderer wiring |
| 4 | `art/backgrounds/bg_casino.png` | 1280x720 | RGB | `bg.py` | renderer wiring |
| 5 | `art/backgrounds/bg_boardroom.png` | 1280x720 | RGB | `bg.py` | renderer wiring |
| 6 | `art/enemy_collector.png` | 512x512 | RGBA | `portrait.py` | — *(do this one first)* |
| 7 | `art/enemy_thug.png` | 512x512 | RGBA | `portrait.py` | — |
| 8 | `art/enemy_loan_shark.png` | 512x512 | RGBA | `portrait.py` | — |
| 9 | `art/enemy_appraiser.png` | 512x512 | RGBA | `portrait.py` | roster + `enemyTextures` |
| 10 | `art/enemy_auditor.png` | 512x512 | RGBA | `portrait.py` | roster + `enemyTextures` |
| 11 | `art/enemy_underwriter.png` | 512x512 | RGBA | `portrait.py` | roster + `enemyTextures` |

Before committing, verify rather than eyeball — a painted checkerboard and a real alpha channel look
identical in a file browser:

```
python3 -c "
from PIL import Image; import glob
for p in sorted(glob.glob('app/src/main/assets/art/enemy_*.png')) + sorted(glob.glob('app/src/main/assets/art/intent_*.png')):
    im = Image.open(p); a = im.convert('RGBA').getchannel('A')
    print('%-44s %-10s %-5s alpha min=%d max=%d' % (p, im.size, im.mode, a.getextrema()[0], a.getextrema()[1]))
"
```

`alpha min` must be 0 and `max` 255. A file reporting `min=255` is opaque: the transparency is
painted on, and it will render as a grey checkerboard box in game.
