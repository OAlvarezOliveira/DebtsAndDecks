# Gemini prompt sheet — world art, one asset at a time

Everything the game needs that is **not** a card illustration: enemy portraits, district
backgrounds and the two missing intent icons.

Each numbered block below is a **complete, self-contained prompt**. Copy one block, paste it into
Gemini, generate, save the PNG under the filename in the heading, move to the next. Nothing has to
be assembled and nothing else has to be pasted with it.

The italic line under each heading is why the asset exists and what it must read as. It is there so
you can check the result does its job — **never paste it**.

Companion to [`ART-PROMPTS-GEMINI.md`](ART-PROMPTS-GEMINI.md) (the 27 card illustrations) and
[`ART-PROMPTS.md`](ART-PROMPTS.md) (why each asset is being regenerated).

## Why this sheet exists

The 27 card illustrations were regenerated in the neo-noir painterly style. The rest of the art was
not, and the gap is now visible in three places:

1. **The three enemy portraits are in a different art style.** `enemy_thug.png`,
   `enemy_loan_shark.png` and `enemy_collector.png` are cel-shaded comic-book art — heavy black
   outlines, saturated red and teal — next to painterly restricted-palette cards. They also break
   two rules the card sheet enforces: `enemy_collector.png` has the English word `COLLECTIONS`
   painted onto the jacket, which cannot be localized, and `enemy_loan_shark.png` carries an
   occult pentagram on the ledger that belongs to no part of the fiction. These need **replacing**,
   not supplementing.
2. **Two intent icons are placeholders.** `intent_foreclose.png` and `intent_hedge.png` on
   `feat/fv-verbs-foreclose-hedge` are 395-byte flat orange squares. They exist only so
   `IntentTypeCoverageTest` passes — the enum declares an `iconName` per constant and the test
   asserts the file is on disk. The build is green; the screen is not.
3. **Two districts have no background.** `districts/all.json` declares three districts; only
   `bg_combat.png` and `bg_reststop.png` exist.

## What art alone does NOT fix

Two of the sections below have a code dependency. Generating the images without it changes nothing
on screen:

- **District backgrounds** — `CombatRenderer.kt` hardcodes `drawBackground(batch, "bg_combat")` at
  lines 154, 834 and 858, and preloads exactly `listOf("bg_combat", "bg_reststop")` at line 73.
  `RunSequence.kt:11-12` says it outright: `districtId` and `role` "are metadata — nothing in" the
  renderer consumes them. The wiring is a separate change.
- **New enemies** — sections 6 to 8 are a *proposal*, not a settled roster. Nothing exists for them
  in `enemies/all.json` or `run/sequence.json`. Generate them only once that roster decision is
  made, or the art will sit unused.

---

# Part A — intent icons

Two icons, to replace the orange-square placeholders. Save to
`app/src/main/assets/art/<filename>`. The filename is load-bearing: `IntentType` resolves the asset
by its declared `iconName`.

These are **not** neo-noir paintings. They match the five icons already in the game
(`intent_attack`, `intent_buff`, `intent_debuff`, `intent_levy`, `intent_multi`): flat black
pictograms on transparency, read at roughly 24 pixels on a phone.

## 1. `intent_foreclose.png` — FORECLOSE

*The Collector's verb: it takes the thing itself, not the money. Must read as seizure of property at a glance, and must not be confusable with the plain sword of ATTACK.*

```
A single flat pictogram icon. Pure solid black silhouette on a fully transparent background. No colour of any kind, no grey, no gradient, no shading, no outline stroke of a second colour, no background shape, no circle or badge behind the glyph. Bold chunky forms with thick strokes and generous negative space, in the style of a mobile game status icon that stays legible when shrunk to 24x24 pixels.

SUBJECT: A door with a heavy padlock and a crossed pair of boards nailed across it — a property sealed shut. The padlock is the largest single mass and sits at the visual centre.

COMPOSITION: square, 128x128 pixels. The glyph is centred with a clear even margin on all four sides and does not touch any edge. Transparent background, alpha channel required.

DO NOT INCLUDE: any text of any kind - no lettering, words, letters, numbers, digits, currency symbols, labels, captions, watermarks or signatures. No colour, no grey tones, no gradients, no drop shadow, no glow, no bevel, no 3D, no perspective, no photorealism, no painterly texture, no sketch lines, no rounded app-icon square behind the glyph, no frame, no border.
```

## 2. `intent_hedge.png` — HEDGE

*The enemy is protecting itself against what you are about to do. Must read as a defensive counter-position, and must not be confusable with the upward chevron of BUFF.*

```
A single flat pictogram icon. Pure solid black silhouette on a fully transparent background. No colour of any kind, no grey, no gradient, no shading, no outline stroke of a second colour, no background shape, no circle or badge behind the glyph. Bold chunky forms with thick strokes and generous negative space, in the style of a mobile game status icon that stays legible when shrunk to 24x24 pixels.

SUBJECT: A shield split down its vertical axis into two offset halves that overlap slightly, one half stepped forward of the other — a position covered from both sides. The split is a clean straight gap, wide enough to survive being shrunk.

COMPOSITION: square, 128x128 pixels. The glyph is centred with a clear even margin on all four sides and does not touch any edge. Transparent background, alpha channel required.

DO NOT INCLUDE: any text of any kind - no lettering, words, letters, numbers, digits, currency symbols, labels, captions, watermarks or signatures. No colour, no grey tones, no gradients, no drop shadow, no glow, no bevel, no 3D, no perspective, no photorealism, no painterly texture, no sketch lines, no rounded app-icon square behind the glyph, no frame, no border.
```

---

# Part B — district backgrounds

Three backgrounds, one per district declared in `districts/all.json`. Save to
`app/src/main/assets/art/backgrounds/<filename>`.

These are the room the fight happens in: the enemy portrait and the whole combat HUD are drawn on
top, so the centre of the frame must stay **quiet and dark**. Do not put the focal point in the
middle. Detail belongs at the edges and in the upper third.

## 3. `bg_slaughterhouse.png` — The Slaughterhouse of the Insolvent

*Where a first missed payment earns you a name. The debts are small here. So is the mercy. The cheapest district: street-level, wet, unceremonious.*

```
Neo-noir environment background for a card game about debt and financial survival. A rain-slicked 1940s city rendered as modern digital painting: deep shadow, hard single-source light, wet reflections, cigarette haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the focal highlight. Cynical, adult, poetic. Painterly, not photographic. No cartoon or chibi styling.

SUBJECT: A meat-packing yard at night, repurposed as a place to settle debts. Empty steel hooks hang on a rail across the upper third of the frame, catching a single cold light. Tiled walls streaked with damp, a floor drain, standing water reflecting the one warm bulb at the far end. Utterly still, recently used, nobody present.

COMPOSITION: 16:9 landscape, 1280x720 pixels. This is a BACKGROUND: characters and interface elements will be drawn on top of the centre of the image. Keep the centre of the frame dark, quiet and low in detail, and push the structure, the light sources and the interesting shapes to the edges and the upper third. Deep atmospheric perspective. Fully opaque artwork, edge to edge. No transparency, no alpha channel.

DO NOT INCLUDE: any text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, neon signs, labels, captions, watermarks or signatures. No people, no figures, no silhouettes of characters. No blood, no gore, no animal carcasses. No crop marks, dashed guides, corner brackets, dimension arrows, rulers, blueprint annotations or mockup framing. No border, no rounded corners. The painting must reach all four edges of the image, full bleed: no mount, no mat, no white or pale margin, no paper border, no framed print, no canvas edge, no drop shadow around the artwork.
```

## 4. `bg_casino.png` — The Vulture Funds Casino

*They wager on which debtors fold. You are the table. Mid-run: money is visible here, and it is being bet on you.*

```
Neo-noir environment background for a card game about debt and financial survival. A rain-slicked 1940s city rendered as modern digital painting: deep shadow, hard single-source light, wet reflections, cigarette haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the focal highlight. Cynical, adult, poetic. Painterly, not photographic. No cartoon or chibi styling.

SUBJECT: A private gaming room after the last hand. A long baize table running out of frame, chips left in uneven stacks at the edges, a low brass lamp over one end, smoke still hanging in the beam. Heavy curtains, a mirrored back wall reflecting almost nothing. The room is dressed for an audience that has already placed its bets.

COMPOSITION: 16:9 landscape, 1280x720 pixels. This is a BACKGROUND: characters and interface elements will be drawn on top of the centre of the image. Keep the centre of the frame dark, quiet and low in detail, and push the structure, the light sources and the interesting shapes to the edges and the upper third. Deep atmospheric perspective. Fully opaque artwork, edge to edge. No transparency, no alpha channel.

DO NOT INCLUDE: any text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, neon signs, labels, captions, watermarks or signatures. No card faces, no pips, no suits, no dice numbers, no roulette numerals. No people, no figures, no silhouettes of characters. No crop marks, dashed guides, corner brackets, dimension arrows, rulers, blueprint annotations or mockup framing. No border, no rounded corners. The painting must reach all four edges of the image, full bleed: no mount, no mat, no white or pale margin, no paper border, no framed print, no canvas edge, no drop shadow around the artwork.
```

## 5. `bg_boardroom.png` — The Boardroom

*Nobody raises their voice. The paperwork was signed years ago. The last district: the violence here is administrative, and it already happened.*

```
Neo-noir environment background for a card game about debt and financial survival. A rain-slicked 1940s city rendered as modern digital painting: deep shadow, hard single-source light, wet reflections, cigarette haze. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the focal highlight. Cynical, adult, poetic. Painterly, not photographic. No cartoon or chibi styling.

SUBJECT: A corporate boardroom high above the city at night, seen from the foot of the table. A long polished slab of dark wood, empty high-backed chairs turned at slight angles, one brass desk lamp still burning at the far head of the table. Floor-to-ceiling windows behind, the city below reduced to a cold scatter of light through rain on the glass. Immaculate, expensive, and entirely without menace on the surface.

COMPOSITION: 16:9 landscape, 1280x720 pixels. This is a BACKGROUND: characters and interface elements will be drawn on top of the centre of the image. Keep the centre of the frame dark, quiet and low in detail, and push the structure, the light sources and the interesting shapes to the edges and the upper third. Deep atmospheric perspective. Fully opaque artwork, edge to edge. No transparency, no alpha channel.

DO NOT INCLUDE: any text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, neon signs, labels, captions, watermarks or signatures. No charts, no graphs, no diagrams, no readable documents. No people, no figures, no silhouettes of characters. No crop marks, dashed guides, corner brackets, dimension arrows, rulers, blueprint annotations or mockup framing. No border, no rounded corners. The painting must reach all four edges of the image, full bleed: no mount, no mat, no white or pale margin, no paper border, no framed print, no canvas edge, no drop shadow around the artwork.
```

---

# Part C — enemy portraits

Save to `app/src/main/assets/art/<filename>`. The filename is load-bearing: the renderer resolves
the portrait from the enemy `id` in `enemies/all.json`.

Portraits are **cut-out figures on transparency**, unlike the cards and the backgrounds: the
district background is drawn behind them. They must therefore carry no background of their own and
no ground shadow.

Sections 6 to 8 replace what is already on disk. **Sections 9 to 11 are a proposal** — those three
enemies do not exist in `enemies/all.json` or `run/sequence.json` yet. The roster today is three
enemies across eight slots, and all three announce from the same five verbs; the proposal below
gives each district its own street enemy and its own boss. Do not generate them before that
decision is taken.

## 6. `enemy_thug.png` — Thug *(replaces the existing cel-shaded portrait)*

*22 HP, the first thing that happens to you. Attacks twice, then buffs. Cheap muscle: no plan, no leverage, just present.*

```
Neo-noir character portrait for a card game about debt and financial survival, rendered as modern digital painting: deep shadow, hard single-source light from above and slightly to one side, wet highlights, cigarette haze clinging to the figure. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly on one small detail only. Cynical, adult, poetic. Painterly, not photographic. No cartoon styling, no comic-book ink outlines, no cel shading, no flat black contour lines around the figure.

SUBJECT: A young leg-breaker in a soaked overcoat too big for him, standing square to the viewer with a length of pipe held low at his side, not raised. Collar up, face half in shadow under a cheap hat, jaw set but eyes uncertain — he is doing a job he was told to do. Full figure, head to just below the knee. Brass accent on a signet ring, nothing else.

COMPOSITION: square, 512x512 pixels. This is a CUT-OUT character on a fully transparent background - the game draws a scene behind him. The figure is centred, faces the viewer, and has a clear even margin on all four sides so nothing is clipped. Transparent background, alpha channel required.

DO NOT INCLUDE: any background, environment, room, street, wall, floor, ground, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image, including inside enclosed shapes. No text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, labels, patches, badges, jacket lettering, captions, watermarks or signatures. No occult, religious or mystical symbols. No blood, no gore. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.
```

## 7. `enemy_loan_shark.png` — Loan Shark *(replaces the existing cel-shaded portrait)*

*36 HP, elite, enrages below half. Attacks, buffs, debuffs and levies Debt onto you. The only one of the three who is genuinely enjoying this.*

```
Neo-noir character portrait for a card game about debt and financial survival, rendered as modern digital painting: deep shadow, hard single-source light from above and slightly to one side, wet highlights, cigarette haze clinging to the figure. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly on one small detail only. Cynical, adult, poetic. Painterly, not photographic. No cartoon styling, no comic-book ink outlines, no cel shading, no flat black contour lines around the figure.

SUBJECT: A lean, elegant lender in a well-cut pinstripe suit, one hand open in a small welcoming gesture, the other holding a slim closed ledger against his hip. Amused, entirely relaxed, head tilted slightly — the posture of a man who already knows how this ends. Full figure, head to just below the knee. Brass accent on the ledger's corner clasp, nothing else.

COMPOSITION: square, 512x512 pixels. This is a CUT-OUT character on a fully transparent background - the game draws a scene behind him. The figure is centred, faces the viewer, and has a clear even margin on all four sides so nothing is clipped. Transparent background, alpha channel required.

DO NOT INCLUDE: any background, environment, room, street, wall, floor, ground, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image, including inside enclosed shapes. No text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, labels, patches, badges, jacket lettering, captions, watermarks or signatures. No occult, religious or mystical symbols - no pentagram, no sigil, no rune. No fangs, no shark motif, no monster features: he is an ordinary man. No blood, no gore. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.
```

## 8. `enemy_collector.png` — Collector *(replaces the existing cel-shaded portrait)*

*52 HP, boss, resists debuffs. Hits hardest, hits twice, levies the most Debt. The one the run is named after.*

```
Neo-noir character portrait for a card game about debt and financial survival, rendered as modern digital painting: deep shadow, hard single-source light from above and slightly to one side, wet highlights, cigarette haze clinging to the figure. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly on one small detail only. Cynical, adult, poetic. Painterly, not photographic. No cartoon styling, no comic-book ink outlines, no cel shading, no flat black contour lines around the figure.

SUBJECT: An enormous, immaculately dressed man in a heavy dark coat, filling the frame with sheer mass, standing perfectly still with both hands folded in front of him. A length of chain hangs from one fist, not brandished, simply held. The face is calm, bureaucratic, almost kind — the light catches the eyes and there is nothing behind them. Full figure, head to just below the knee. Brass accent on a single coat button, nothing else.

COMPOSITION: square, 512x512 pixels. This is a CUT-OUT character on a fully transparent background - the game draws a scene behind him. The figure is centred, faces the viewer, and has a clear even margin on all four sides so nothing is clipped. Transparent background, alpha channel required.

DO NOT INCLUDE: any background, environment, room, street, wall, floor, ground, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image, including inside enclosed shapes. No text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, labels, patches, badges, jacket lettering, clipboard writing, captions, watermarks or signatures. No occult, religious or mystical symbols. No sunglasses. No blood, no gore. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.
```

## 9. `enemy_appraiser.png` — Appraiser *(PROPOSED: casino street enemy)*

*Proposed. Does not exist in `enemies/all.json`. The casino's street-level enemy: it prices you before anything else happens.*

```
Neo-noir character portrait for a card game about debt and financial survival, rendered as modern digital painting: deep shadow, hard single-source light from above and slightly to one side, wet highlights, cigarette haze clinging to the figure. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly on one small detail only. Cynical, adult, poetic. Painterly, not photographic. No cartoon styling, no comic-book ink outlines, no cel shading, no flat black contour lines around the figure.

SUBJECT: A meticulous, narrow-shouldered valuer in a buttoned waistcoat and shirtsleeves, holding a jeweller's loupe to one eye and looking directly through it at the viewer. The other hand is raised, palm up and empty, waiting to be handed something. Neat, dry, faintly bored. Full figure, head to just below the knee. Brass accent on the loupe barrel, nothing else.

COMPOSITION: square, 512x512 pixels. This is a CUT-OUT character on a fully transparent background - the game draws a scene behind him. The figure is centred, faces the viewer, and has a clear even margin on all four sides so nothing is clipped. Transparent background, alpha channel required.

DO NOT INCLUDE: any background, environment, room, street, wall, floor, ground, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image, including inside enclosed shapes. No text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, labels, patches, badges, captions, watermarks or signatures. No occult, religious or mystical symbols. No blood, no gore. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.
```

## 10. `enemy_auditor.png` — Auditor *(PROPOSED: boardroom street enemy)*

*Proposed. Does not exist in `enemies/all.json`. The boardroom's street-level enemy: it finds what you did wrong and makes it your debt.*

```
Neo-noir character portrait for a card game about debt and financial survival, rendered as modern digital painting: deep shadow, hard single-source light from above and slightly to one side, wet highlights, cigarette haze clinging to the figure. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly on one small detail only. Cynical, adult, poetic. Painterly, not photographic. No cartoon styling, no comic-book ink outlines, no cel shading, no flat black contour lines around the figure.

SUBJECT: A grey, patient woman in a plain dark suit, standing with a thick closed folder held flat across both forearms like something being presented. Reading glasses pushed up onto her head, expression mild and unhurried. She has been here since before you arrived and will be here after. Full figure, head to just below the knee. Brass accent on the folder's corner clip, nothing else.

COMPOSITION: square, 512x512 pixels. This is a CUT-OUT character on a fully transparent background - the game draws a scene behind her. The figure is centred, faces the viewer, and has a clear even margin on all four sides so nothing is clipped. Transparent background, alpha channel required.

DO NOT INCLUDE: any background, environment, room, street, wall, floor, ground, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image, including inside enclosed shapes. No text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, labels, folder writing, captions, watermarks or signatures. No occult, religious or mystical symbols. No blood, no gore. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.
```

## 11. `enemy_underwriter.png` — Underwriter *(PROPOSED: casino boss)*

*Proposed. Does not exist in `enemies/all.json`. The casino's boss seat: it does not fight you, it insures against you.*

```
Neo-noir character portrait for a card game about debt and financial survival, rendered as modern digital painting: deep shadow, hard single-source light from above and slightly to one side, wet highlights, cigarette haze clinging to the figure. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly on one small detail only. Cynical, adult, poetic. Painterly, not photographic. No cartoon styling, no comic-book ink outlines, no cel shading, no flat black contour lines around the figure.

SUBJECT: A heavy-set man in evening dress with the bow tie undone, seated on the arm of a chair, leaning forward with his elbows on his knees and his hands loosely clasped. He is looking slightly past the viewer, calculating, not threatening. A cigar burns forgotten between two fingers. Full figure, head to foot, seated pose. Brass accent on a cufflink, nothing else.

COMPOSITION: square, 512x512 pixels. This is a CUT-OUT character on a fully transparent background - the game draws a scene behind him. The chair arm he sits on is part of the cut-out figure and must be painted; nothing else is. The figure is centred and has a clear even margin on all four sides so nothing is clipped. Transparent background, alpha channel required.

DO NOT INCLUDE: any background, environment, room, street, wall, floor, ground, ground shadow, cast shadow or contact shadow of any kind. No transparency checkerboard pattern painted into the image, including inside enclosed shapes. No text of any kind - no lettering, words, letters, numbers, digits, currency symbols, signage, labels, patches, badges, captions, watermarks or signatures. No occult, religious or mystical symbols. No blood, no gore. No frame, no border, no rounded corners, no vignette, no circle or badge behind the figure.
```

---

## Checklist after generating

| # | File | Size | Mode | Notes |
|---|---|---|---|---|
| 1 | `art/intent_foreclose.png` | 128x128 | RGBA | replaces a placeholder on `feat/fv-verbs-foreclose-hedge` |
| 2 | `art/intent_hedge.png` | 128x128 | RGBA | replaces a placeholder on `feat/fv-verbs-foreclose-hedge` |
| 3 | `art/backgrounds/bg_slaughterhouse.png` | 1280x720 | RGB | needs renderer wiring |
| 4 | `art/backgrounds/bg_casino.png` | 1280x720 | RGB | needs renderer wiring |
| 5 | `art/backgrounds/bg_boardroom.png` | 1280x720 | RGB | needs renderer wiring |
| 6 | `art/enemy_thug.png` | 512x512 | RGBA | replaces existing |
| 7 | `art/enemy_loan_shark.png` | 512x512 | RGBA | replaces existing |
| 8 | `art/enemy_collector.png` | 512x512 | RGBA | replaces existing |
| 9 | `art/enemy_appraiser.png` | 512x512 | RGBA | proposed, needs a roster decision first |
| 10 | `art/enemy_auditor.png` | 512x512 | RGBA | proposed, needs a roster decision first |
| 11 | `art/enemy_underwriter.png` | 512x512 | RGBA | proposed, needs a roster decision first |

Portraits and icons **must keep their alpha channel** — the generator will happily return an opaque
image with a painted grey-and-white checkerboard where the transparency should be. Check before
committing:

```
python3 -c "from PIL import Image; im=Image.open('app/src/main/assets/art/enemy_thug.png'); \
print(im.size, im.mode, 'has_alpha=', im.mode.endswith('A'))"
```

Backgrounds are the opposite: they must be fully opaque, with no alpha channel and no pale border.
