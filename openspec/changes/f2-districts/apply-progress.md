# sdd-verify — apply-progress

**Change:** `f2-districts` (PR2, section 7 — Render)
**Tasks:** 7.1, 7.2, 7.3, 7.4 — strict TDD, RED written before GREEN.
**Branch:** `feat/f2-districts-runmanager` (not yet merged)

## TDD evidence

### 7.1 — `RunManager.currentDistrict` derived from the current slot
- **RED:** `RunManagerTest` gained `currentDistrict is derived from the slot the run is on` and
  `isDistrictEntrance is true only on the first slot of each district`. They failed to compile
  (`Unresolved reference 'currentDistrict'` / `'isDistrictEntrance'`) — the getters did not exist.
- **GREEN:** added `private val districts: List<District> = emptyList()` (`RunManager.kt:32`),
  `val currentDistrict` (`RunManager.kt:71`) and `val isDistrictEntrance` (`RunManager.kt:79`),
  both derived over `slotIndex` (no stored state). Wired the catalog in `Module.kt:38` + `Module.kt:40`.
- **Proof:** `./gradlew testDebugUnitTest --tests '*RunManagerTest*'` → GREEN.

### 7.2 — combat + node renderers select the background from the district
- **RED:** `DistrictTest.district exposes a backdrop texture key derived from its id` failed
  (`Unresolved reference 'backgroundKey'`).
- **GREEN:** `District.backgroundKey()` (`District.kt:25`) → `bg_district_<id>`, matching design.md's
  catalog ids with **no new data field**. `CombatRenderer` draws it for combat (`CombatRenderer.kt:155`)
  and node (`CombatRenderer.kt:718`), replacing the old single `bg_combat` / `bg_reststop`. Because no
  catalog field was added, PR1's background-less fake catalog test (`DataLoaderDistrictTest`) is untouched.
- **Proof:** `./gradlew testDebugUnitTest --tests '*DistrictTest*'` → GREEN.
- **Note:** the backdrop PNGs are task 5 (PR2 5.x). Until they land, the renderer's existing
  missing-file fallback paints the gradient — identical behaviour to today, just keyed per district.

### 7.3 — district name + descriptor on entering a district
- **No headless pixel test** (LibGDX GL). Proven by: `isDistrictEntrance` (unit-tested in 7.1) as the
  trigger, and `CombatRenderer.drawDistrictTitle` (`CombatRenderer.kt:781`) painting the name +
  descriptor on entrance (`CombatRenderer.kt:171`) and on the node screen (`CombatRenderer.kt:719`),
  into bounds from the 7.4 helper.

### 7.4 — RED then GREEN layout test for the title position
- **RED:** `DistrictTitleLayoutTest` written first; failed to compile (`Unresolved reference 'districtTitle'`).
- **GREEN:** `CombatLayout.districtTitle(worldWidth)` (`CombatLayout.kt:51`) returns a card centred on
  `worldWidth` (`x = (worldWidth - WIDTH) / 2`), no 1280 constant. The test asserts the title is centred
  at 1280 and at 2133 (a 20:9 phone), that a wider world shifts `x` right, and that the two widths never
  coincide — i.e. the position derives from the viewport, not a fixed coordinate.
- **Proof:** `./gradlew testDebugUnitTest --tests 'com.debtsdecks.gdx.render.DistrictTitleLayoutTest'` → GREEN.

## Constraints honoured
- No `RunManager.Phase` change; the five `when (phase)` sites unchanged (R2.6).
- No combat/economy code touched → zero balance delta by construction (R2.5); confirmed by the harness below.
- No PR1 test or 5-arg `RunManager(...)` call site edited; `districts` defaults to `emptyList()`.
- `GameScreen` passes `runManager` into `renderer.render(...)` so the combat backdrop + title use the
  live district.
