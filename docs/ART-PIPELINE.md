# Art Pipeline — Debts & Decks

> What art the game ships, what the renderer demands of it, which assets are defective, and what is missing to debug the render layer. Update when assets or layout contracts change.

---

## 1. Inventory

Audited against `assets/cards/all.json` and `assets/enemies/all.json` on 2026-08-28. No missing
assets, no orphans.

| Class | Path | Count | Size | Format |
| --- | --- | --- | --- | --- |
| Card illustrations | `assets/art/cards/` | 27 / 27 | 512x512 | PNG RGBA |
| Card frames | `assets/art/card_frame_{attack,skill,power}.png` | 3 | 336x480 | PNG RGBA |
| Enemy portraits | `assets/art/enemy_{thug,loan_shark,collector}.png` | 3 / 3 | — | PNG RGBA |
| Intent icons | `assets/art/intent_{attack,buff,debuff,levy,multi}.png` | 5 | 128x128 | PNG RGBA |
| Backgrounds | `assets/art/backgrounds/bg_{combat,reststop}.png` | 2 | 1280x720 | PNG |

Coverage is complete. Every defect below is a quality problem, not a gap.

## 2. Contracts the renderer imposes

### 2.1 Adding an asset is not just dropping a file

A new PNG in `assets/art/cards/` is ignored until its id is added to the hardcoded list in
`CombatRenderer`. Enemy intent icons are resolved by `IntentType` name. Two places, both manual.

### 2.2 Real alpha, no baked transparency pattern

Assets must carry a real alpha channel. A JPEG renamed to `.png` loads, but the generator's
transparency checkerboard is then frozen into opaque pixels and renders as a literal grey grid.
This already happened once and was repaired with a flood fill from the canvas edges — which is
why the surviving damage is exactly the kind a flood fill cannot reach (section 3.2).

### 2.3 Card face geometry

`card_frame_*.png` is expected to have a **fully transparent centre**; the opaque border eats
**13.7% of the width and 12.9% of the height per side** (`FRAME_INSET_X` / `FRAME_INSET_Y`).
Measured opaque coverage inside that window: attack 8.6%, skill 11.5%, **power 96.4%** — the power
frame violates the contract outright (section 3.9). Consequences:

- The frame is drawn **last**, over everything, and the content must live inside that hole.
- The interior splits into an art window (top) and a solid text panel (bottom
  `TEXT_PANEL_FRACTION` = 48%).
- The art window is landscape (~1.35:1) but sources are square, so the art is drawn
  `objectFit: cover` — a centred source crop, not a stretch.
- Descriptions are hard-clamped to `DESCRIPTION_MAX_LINES` = 3 and ellipsised with `"..."`.
  Not `…`: the default `BitmapFont` charset does not contain it.

### 2.4 Layout is a pure function of the live world width

`di/Module.kt` uses `ExtendViewport`, so **there is no fixed 1280 world**. On the 20:9 test device
the world is 1600x720. `HandLayout` and `CombatLayout` derive everything from the width passed in;
side columns are sized first and the hand takes the leftover band, which is what guarantees the
hand can never collide with the END TURN button at any aspect ratio. Never hardcode a screen
coordinate outside those two objects.

### 2.5 Draw-order traps

`ShapeRenderer` and `SpriteBatch` cannot interleave their `begin`/`end` pairs, so any panel that
mixes bars and text has to compute all row positions in one pass before drawing anything. Also
`BitmapFont.draw(batch, text, x, y)` takes `y` as the **top of the line, not the baseline** — an
8px gap does not clear the ~12px descent, which is exactly how the HP bar ended up riding into the
`PS:` label.

## 3. Known art defects

### 3.1 Baked-in rule text, and it is wrong

**15 of 27** illustrations have the card's rules painted into the image in English. The card face
already renders name, cost and description, so this is duplicated at best. It is worse than
duplicated — the baked text contradicts `all.json` on every card checked:

| Card | Baked into the art | Actual data |
| --- | --- | --- |
| `foreclosure_express` | `€10 + €5/discard` | cost 1, damage 6, gold +4 |
| `asset_auction` | `€10 + €5/discard` | cost 1, gold +9, exhausts hand |
| `subprime_loan` | `€15 +20%` | cost 0, credit +3, debt +3 |
| `zombie_debt` | `1 HP … ×2 DEBT` | cost 0, debt +2, credit +1 |

Two different cards carry the identical string, which alone proves the text is decorative filler
rather than generated from the data. It also defeats i18n: the text is English, burned into pixels.

Affected: `asset_auction`, `bounced_check`, `compound_interest`, `debt_forgiveness`,
`eternal_debt`, `foreclosure_express`, `ghost_collector`, `golden_credit`, `mortgage_collateral`,
`partial_forgiveness`, `reverse_mortgage`, `risky_investment`, `subprime_loan`,
`tactical_bankruptcy`, `zombie_debt`.

**Fix:** regenerate with an explicit "no text, no lettering, no numbers" instruction.

### 3.2 Residual transparency checkerboard

`asset_bubble` still shows an opaque grey checkerboard filling the inside of the glass dome. The
repair pass flood-filled from the canvas edges, and that region is enclosed by artwork, so the
fill never reached it. Any enclosed background region in a future asset will have the same fate.

### 3.3 Stray guide marks baked into the canvas

Several illustrations carry the generator's blueprint annotations — dashed crop guides, corner
brackets, red dimension arrows labelled `54px`. `asset_auction`, `foreclosure_express` and
`golden_credit` have an opaque bounding box covering **100% of the canvas** despite being ~30%
transparent, purely because those marks touch all four edges.

### 3.4 The alpha cut bit into the artwork

`ejecucion` has holes punched through the gavel and the papers by the same background-removal
pass. Visible when composited over a non-dark colour.

### 3.5 Subject padding wastes the art window

Transparent margin ranges from 27% (`risky_investment`) to 84% (`emergency_fund`); the opaque
bounding box averages **75%** of the canvas. Because the renderer crops a centred slice of the
full 512 square, that padding is carried into the card and the subject reads small and
inconsistently sized card to card. **Recommended fix (not implemented):** crop to the opaque
bounding box instead of the full canvas, either at build time or by caching the bbox per texture.

### 3.6 Backgrounds are undersized

`bg_combat` / `bg_reststop` are 1280x720. On a 20:9 phone the world is 1600 wide and the cover
scale crops ~12% off the top and bottom. Authoring at 1920x720 or wider would remove the crop.

### 3.7 Vignette deviation from the art brief

Brief item A7 specified a separate vignette overlay PNG drawn at ~50% alpha above everything. It
was instead baked into the two background PNGs, because a true overlay also dims the HUD and the
combat log. Deliberate, and reversible in minutes if the literal spec is wanted.

### 3.8 Baked text in the ATTACK frame

`card_frame_attack.png` has lettering stamped into the lower moulding (reads as `...TSTOPPED`).
Because the frame is shared, this appears on **every ATTACK card in the game**, under the type tag.

### 3.9 The POWER frame has no window

`card_frame_power.png` is **96.4% opaque inside the area the renderer treats as the transparent
window**, against 8.6% and 11.5% for attack and skill. The frame is drawn last, over everything,
so a POWER card would render as a solid slab: no art, no name, no description, no type tag. It
also carries a residual transparency checkerboard in its upper right.

`all.json` currently holds 18 SKILL and 9 ATTACK cards and **zero POWER**, so nothing triggers it
today. It will break the moment the first POWER card is added.

## 4. What is missing to debug the render layer

Honest list, in order of how much time each one costs today.

1. **No screenshot or UI test.** `app/src/` has `main` and `test` only — no `androidTest`. Every
   layout check is `installDebug` → `am start` → `adb exec-out screencap`, a ~40s round trip that
   can only ever show turn 1 of the first fight.
2. **No way to reach the later screens.** Rest stop, shop, reward, victory and defeat need a real
   run played by hand. A debug entry point — a Gradle property or a long-press that boots straight
   into a given `RunManager` phase with a seeded state — would turn minutes into seconds. This is
   the single highest-value item here.
3. **No geometry test.** `HandLayout` and `CombatLayout` are now pure functions of `worldWidth`
   and need no GL context, but nothing exercises them: the only test that mentions them does so in
   a comment. Asserting that the player panel, log panel, END TURN button and every card slot stay
   disjoint at 1280 / 1600 / 2133 would lock down the exact class of bug that keeps recurring.
4. **No ImageMagick on this machine.** All asset inspection goes through Python + Pillow, and
   `numpy` is absent too, so scans are pure-PIL loops.
5. **Concurrent sessions in this checkout.** Two agents editing the same working tree have already
   produced mixed commits and test failures that looked like regressions but were someone else's
   in-flight edit. One session per checkout, or a worktree per task.

## 5. Screens still on placeholder art

The noir pass covered combat and the card face. Untouched, still flat rectangles and default
font at 3x scale: node selection (`nodeChoiceBounds`, `drawNodeButton`), rest stop, the reward
screen header, and `renderRunEnd`. Enemy portraits have no hit flash and no intent animation.
