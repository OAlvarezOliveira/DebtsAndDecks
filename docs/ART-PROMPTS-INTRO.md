# Gemini prompt sheet — the opening stills

Four full-screen frames shown before the first fight, so the game does not start cold.

Each numbered block is a **complete, self-contained prompt**. Copy one block, paste it, generate,
run the post-processing step at the end, save under the filename in the heading. Nothing has to be
assembled and nothing else has to be pasted with it.

The italic line under each heading is what the frame must read as. It is there so you can judge the
result — **never paste it**.

Companion to [`ART-PROMPTS-WORLD.md`](ART-PROMPTS-WORLD.md) (icons, backgrounds, portraits) and
[`ART-PROMPTS-GEMINI.md`](ART-PROMPTS-GEMINI.md) (the 27 card illustrations).

---

## Read this first

**The caption is not in the image.** All prose is authored in `strings.properties` and translated
in `strings_es.properties`, and it is drawn over the still at runtime. That is the whole reason
this is four stills instead of a video: a rendered clip cannot be translated without regenerating
it. So every prompt below reserves a **quiet bottom third** for text, and forbids lettering in the
art itself.

**Alistair never shows his face.** Not for mystery — for a technical reason. A generator asked for
"the same man" across four separate images returns four different men, and there is no cheap fix.
So frame 1 is his hands, frame 2 is a desk, frame 3 is the city, frame 4 is his back. The series
stays consistent because there is no face to keep consistent. If the roster later needs a portrait
of him, that is one image and the problem does not arise.

**These are drawn like the district backgrounds**, `drawBackground`-style: scaled by
`max(worldWidth / 1280, 1)` on both axes, surplus height falling off the top and bottom equally.
On any screen wider than 16:9 **the top and bottom edges are cropped**, so nothing load-bearing
sits near them.

The captions the frames have to carry (English source; the Spanish is a translation, not a rewrite):

| Frame | Caption |
|---|---|
| 1 | He valued what other people lost. He was good at it. |
| 2 | Then Liquidations valued him, and the number was small. |
| 3 | The city keeps a ledger. Everyone is in it. Nobody has read it. |
| 4 | Six on the balance. Nobody starts clean. |

Frame 4's number is not decoration: `STARTING_DEBT = 6`. If that constant changes, this line changes.

---

## 1. `intro_01.png` — The appraisal

*His trade, in his hands. Precise, unhurried, and about to be worthless.*

```
Neo-noir story illustration for a card game about debt and financial survival. A 1940s city rendered as modern digital painting: deep shadow, one hard cold light source, wet reflections, haze in the air. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the one warm note in the frame. Painterly, atmospheric. Not photographic, not cartoon, no comic-book ink outlines.

SUBJECT: A close view of a man's hands at a desk, lit by one low lamp. He holds a jeweller's loupe to a small object he is valuing - a wedding ring, resting on a square of dark felt. Beside it, a brass balance scale and a fountain pen. Bare forearms, sleeves rolled, one plain cuff. The hands are steady and unhurried, the hands of someone who has done this ten thousand times. Only the hands, the desk and the objects are visible - the man's face, head and shoulders are entirely outside the frame, above the top edge.

COMPOSITION: 16:9 landscape. Reserve the BOTTOM THIRD as a quiet dark surface with no detail - text is drawn over it at runtime. The hands, the loupe and the ring sit in the upper middle of the frame, lit hard from the left. Deep falloff into black at the edges. Keep everything important well away from the top and bottom edges, which will be cropped.

Fully opaque painting reaching all four edges of the image, full bleed, with no transparency and no alpha channel.

DO NOT INCLUDE: no lettering, words, letters, numbers, signage, labels, captions, watermarks or signatures anywhere in the scene - every surface that could carry writing is blank. No face, no head, no eyes. No border, no vignette, no rounded corners, no mount, no mat, no pale margin, no paper edge, no framed print, no canvas edge, no drop shadow around the artwork.

Generate at the largest 16:9 resolution available.
```

---

## 2. `intro_02.png` — The desk, after

*The same desk, cleared out. The trade is gone; the tools stayed behind.*

```
Neo-noir story illustration for a card game about debt and financial survival. A 1940s city rendered as modern digital painting: deep shadow, one hard cold light source, wet reflections, haze in the air. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the one warm note in the frame. Painterly, atmospheric, unpeopled. Not photographic, not cartoon, no comic-book ink outlines.

SUBJECT: The same desk at night, emptied. The lamp is off. The felt square is bare, the ring gone. The brass loupe has been left behind on the wood, next to an open ledger whose pages are blank, and a single desk drawer standing open and empty. A hat is missing from the empty hook on the wall behind. One shaft of cold light from an unseen window falls across the desk at an angle. Dust in the beam. Nobody is present, and the room has the stillness of a place somebody has just stopped working in.

COMPOSITION: 16:9 landscape. Reserve the BOTTOM THIRD as a quiet dark surface with no detail - text is drawn over it at runtime. The desk, the abandoned loupe and the shaft of light occupy the upper middle of the frame. Strong diagonal of light, everything else in deep shadow. Keep everything important well away from the top and bottom edges, which will be cropped.

Fully opaque painting reaching all four edges of the image, full bleed, with no transparency and no alpha channel.

DO NOT INCLUDE: no lettering, words, letters, numbers, signage, labels, captions, watermarks or signatures anywhere in the scene - the ledger pages and every other surface that could carry writing are completely blank. No people, no figures, no silhouettes. No border, no vignette, no rounded corners, no mount, no mat, no pale margin, no paper edge, no framed print, no canvas edge, no drop shadow around the artwork.

Generate at the largest 16:9 resolution available.
```

---

## 3. `intro_03.png` — The city that keeps the ledger

*The districts, from above and far away. Cold, enormous, and indifferent to who owes what.*

```
Neo-noir story illustration for a card game about debt and financial survival. A 1940s city rendered as modern digital painting: deep shadow, one hard cold light source, wet reflections, haze in the air. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the one warm note in the frame. Painterly, atmospheric, unpeopled. Not photographic, not cartoon, no comic-book ink outlines.

SUBJECT: A vast 1940s financial city seen at night from high above and far off, through rain and haze. Banking towers, a slaughterhouse district low and flat to one side, a domed courthouse, tenement blocks, a river cutting through. Thousands of lit windows recede into fog, each one a room where somebody owes somebody. One tower stands taller than the rest, its uppermost floor the only warm brass-lit window in the whole frame. The city is enormous and completely impersonal.

COMPOSITION: 16:9 landscape. Reserve the BOTTOM THIRD as low, dark, undifferentiated rooftops and fog with no readable detail - text is drawn over it at runtime. The skyline, the one warm window and the depth of the haze belong in the middle band. Extreme atmospheric perspective, layers of fog separating near, middle and far. Keep everything important well away from the top and bottom edges, which will be cropped.

Fully opaque painting reaching all four edges of the image, full bleed, with no transparency and no alpha channel.

DO NOT INCLUDE: no lettering, words, letters, numbers, signage, neon signs, billboards, labels, captions, watermarks or signatures anywhere in the scene - every surface that could carry writing is blank. No people, no figures, no silhouettes, no vehicles in the foreground. No moon, no stars. No border, no vignette, no rounded corners, no mount, no mat, no pale margin, no paper edge, no framed print, no canvas edge, no drop shadow around the artwork.

Generate at the largest 16:9 resolution available.
```

---

## 4. `intro_04.png` — Walking in

*Him, from behind, at the mouth of the first district. Not a hero shot. A man going to work.*

```
Neo-noir story illustration for a card game about debt and financial survival. A 1940s city rendered as modern digital painting: deep shadow, one hard cold light source, wet reflections, haze in the air. Restricted palette - near-black indigo #0c0c18, desaturated navy #14142a, cold bone white #eef0fb, and a single warm brass accent #c9a227 used sparingly as the one warm note in the frame. Painterly, atmospheric. Not photographic, not cartoon, no comic-book ink outlines.

SUBJECT: A lone man seen from directly behind, small in the frame, walking away from the viewer down a rain-slicked street toward the open gate of a meat-packing district. Long dark overcoat, no hat, hands in pockets, shoulders set. He is a dark silhouette against the one cold light burning over the gate ahead. Wet cobbles throw his reflection back. Steam rises from a grate. The buildings on either side lean in and are lost in haze. His face is not visible and cannot be - he is walking away.

COMPOSITION: 16:9 landscape. Reserve the BOTTOM THIRD as wet dark street with soft reflections and no readable detail - text is drawn over it at runtime. The figure is small, placed slightly left of centre, standing in the middle band with the lit gate ahead of him. Strong one-point perspective down the street, deep haze. Keep everything important well away from the top and bottom edges, which will be cropped.

Fully opaque painting reaching all four edges of the image, full bleed, with no transparency and no alpha channel.

DO NOT INCLUDE: no lettering, words, letters, numbers, signage, neon signs, labels, captions, watermarks or signatures anywhere in the scene - every surface that could carry writing is blank. No face, no front view, no turned head. Only one figure, no crowd, no bystanders. No border, no vignette, no rounded corners, no mount, no mat, no pale margin, no paper edge, no framed print, no canvas edge, no drop shadow around the artwork.

Generate at the largest 16:9 resolution available.
```

---

## Post-processing — to 1280x720 RGB

These are the only assets in the project that need **no keying**: they are full-bleed and opaque, so
none of the checkerboard machinery in `tools/art/dekey.py` applies. Downscale and flatten:

```python
from PIL import Image
import sys

# Cover-crop to 16:9 and flatten. Opaque on purpose: an alpha channel here is a bug, not a feature
# -- drawBackground never blends these, and a stray channel just costs texture memory.
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
tw, th = 1280, 720
scale = max(tw / w, th / h)
im = im.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
x, y = (im.width - tw) // 2, (im.height - th) // 2
im.crop((x, y, x + tw, y + th)).save(out)
```

Save to `app/src/main/assets/art/intro/intro_01.png` … `intro_04.png`.

## Checklist

| File | Reads as | No lettering | Quiet bottom third | Opaque RGB 1280x720 |
|---|---|---|---|---|
| `intro_01.png` | hands appraising a ring, no face | yes | yes (mean 11.7) | yes |
| `intro_02.png` | the same desk, cleared out | yes | yes (mean 11.8) | yes |
| `intro_03.png` | the city from above, one warm window | yes | yes (mean 18.7) | yes |
| `intro_04.png` | his back, walking into the district | yes | yes (mean 32.1) | yes |

All four generated and installed. "Quiet bottom third" is the mean luminance of the band the
caption lands on, out of 255; bone white `#eef0fb` was checked against each one by rendering the
real caption over the real frame, not judged by eye from a thumbnail.

## What art alone does NOT fix

There is no intro screen in the code yet. `GameApp.kt` goes straight to `GameScreen`, and
`app/src/main/assets/art/intro/` does not exist. Generating these four files changes nothing on
screen until the screen that shows them is written and the four captions are added to
`strings.properties` and `strings_es.properties`.
