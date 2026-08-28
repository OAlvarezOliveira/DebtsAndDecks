# F2 — Design

## Data shape

Two files, following the catalog/authority split the repo already uses.

**`app/src/main/assets/districts/all.json`** (new — the catalog):

```json
[
  { "id": "slaughterhouse", "name": "district.slaughterhouse.name",
    "descriptor": "district.slaughterhouse.descriptor", "background": "bg_district_slaughterhouse" },
  { "id": "vulture_casino", "name": "district.vulture_casino.name",
    "descriptor": "district.vulture_casino.descriptor", "background": "bg_district_vulture_casino" },
  { "id": "boardroom", "name": "district.boardroom.name",
    "descriptor": "district.boardroom.descriptor", "background": "bg_district_boardroom" }
]
```

**`app/src/main/assets/run/sequence.json`** (amended — two added fields per slot, nothing
removed):

```json
{ "enemyId": "thug", "districtId": "slaughterhouse", "role": "STREET",
  "rewards": { "gold": 10, "cardChoices": 1 } }
```

**Model** (`core/model/RunSequence.kt`):

```kotlin
enum class SlotRole { STREET, BOSS }

@Serializable
data class EncounterSlot(
    val enemyId: String,
    val districtId: String,
    val role: SlotRole = SlotRole.STREET,
    val rewards: EnemyRewards,
)
```

`role` gets a default so a malformed hand-edit degrades to STREET rather than failing;
`districtId` deliberately does not, because a slot with no district is a bug that should
surface at load. This mirrors the existing rule that slot rewards are mandatory.

## Why not put the districts inside `sequence.json`

Because the repo already decided this question once. `RunSequence.kt`'s own KDoc says the
enemy roster "is only a catalog" and the sequence is authoritative. Districts are the same
kind of thing: identity that outlives any particular run layout. When F5 adds bosses and F8
adds leads, both will want to hang content off a district id without editing the run
structure. One shape, learned once.

## Rendering

`RunManager` gains a read-only `currentDistrict: DistrictDefinition`, derived from
`runSequence.slots[slotIndex].districtId`. `CombatRenderer` and the node renderer select the
background from it instead of the single `bg_combat`.

Nothing about the phase machine changes. This is the load-bearing constraint of the phase:
adding a `Phase` value would cost four exhaustive `when` sites plus a test, and F2 has no
reason to pay that. F6 and F7 do, and their charters say so.

Text placement goes through the existing `HandLayout` / `CombatLayout` helpers. There is no
fixed 1280 world — the viewport is `ExtendViewport` and layout is a pure function of width.
A hardcoded coordinate would look correct on the development device and wrong on a tester's,
which is exactly the population FV is trying to measure.

## Districts chosen, and why these three

The mould offers ten. Three are needed. The selection is not aesthetic — it is that the
existing enemy escalation already tells this story:

| District | Slots | Enemies | What it is |
| --- | --- | --- | --- |
| The Slaughterhouse of the Insolvent | 1-3 | thug ×2, loan_shark | Street predation. Violence with no paperwork. |
| The Vulture Funds Casino | 4-6 | thug, loan_shark ×2 | Professional predation. The paperwork arrives. |
| The Boardroom | 7-8 | collector ×2 | The institution. Nothing personal, which is worse. |

The arc lands on the room where D6's fixed finale — the Central Bank — belongs. The other
seven districts stay in `docs/VISION.md` as content for later runs, not as dead data.

## Zero delta: how it is actually guaranteed

`RunSimulator` reads the sequence through `TestAssetLoader.loadSequence()` and drives combat
off `enemyId` and `rewards`. Added fields are invisible to it. So the guarantee is structural,
not hopeful — but it is still **proved by diffing the sweep**, because "structurally
impossible" is a claim, and this program does not accept claims as evidence.

One trap worth naming: `RunSequenceTest` asserts the gold list
`[10, 10, 15, 12, 18, 20, 25, 30]` and eight slots. Those assertions must survive untouched.
If a task ends up editing them, the phase acquired a balance delta and the acceptance gate
has already failed.

## Design system extraction

`Arts/Debts & Decks Design System.zip` is the vigent UI reference and `Arts/` is gitignored
with zero tracked files. Committing a ZIP is not the fix — the fix is that the *decisions*
inside it become text: palette hex values, type scale, spacing rhythm, and the treatment for
the district title card. That goes in `docs/DESIGN-SYSTEM.md`, tracked, with the ZIP cited as
provenance.

This is scoped to what F2 actually needs. Transcribing the whole system would be a phase of
its own, and inventing tokens F2 does not use would be worse than leaving them in the ZIP.

## Test plan (TDD order)

1. **RED** `DistrictCatalogTest` — the catalog parses, has three entries, every field is a key
   (no whitespace, matches `^[a-z_]+(\.[a-z_]+)+$`).
2. **RED** `RunSequenceTest` additions — all 8 slots carry a district; partition is
   `[1,2,3][4,5,6][7,8]`; exactly one BOSS per district and it is the last slot; every
   `districtId` resolves in the catalog.
3. **RED** unknown-district test — a slot with a bogus id fails at load with the id in the
   message.
4. **GREEN** — model, catalog, loader, sequence data.
5. **RED** i18n parity test — every `district.*` key present in both bundles.
6. **GREEN** — strings.
7. **GATE** — sweep report diff. Identical.
8. Renderer wiring + a layout test asserting the district title derives from viewport width.

Step 5 is worth having permanently: it is the first automated check in this project that a
data-file key actually resolves in both bundles, and the same test generalises to F6's events.
