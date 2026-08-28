# Spec delta — capability: `run-structure`

## ADDED Requirements

### Requirement: R2.1 — The run is partitioned into districts

The run sequence SHALL assign every encounter slot to exactly one district, and districts
SHALL be contiguous over slot order.

#### Scenario: The partition covers the run
- **WHEN** the sequence is loaded
- **THEN** all 8 slots carry a `districtId`
- **AND** the districts are `[1,2,3]`, `[4,5,6]`, `[7,8]` in slot order
- **AND** no slot belongs to two districts and none belongs to none

#### Scenario: A district id has no catalog entry
- **GIVEN** a slot naming a `districtId` absent from `districts/all.json`
- **WHEN** the sequence loads
- **THEN** loading fails with a message naming the missing id
- **AND** it fails at load, not at render — the same way an unknown `enemyId` already does

### Requirement: R2.2 — Each district has exactly one boss seat, and it is its last slot

Each district SHALL mark exactly one slot with `role: BOSS`, and that slot SHALL be the last
slot of the district.

#### Scenario: Boss seats
- **WHEN** the sequence is loaded
- **THEN** slots 3, 6 and 8 have `role: BOSS` and every other slot has `role: STREET`

#### Scenario: A district declares two bosses
- **THEN** loading fails, naming the district

#### Scenario: F5 later replaces a boss
- **GIVEN** F5 swaps slot 3's enemy for a named antagonist
- **THEN** no change to the district partition is required — the seat already exists

### Requirement: R2.3 — Districts are a catalog; the sequence is the authority

District identity SHALL live in `app/src/main/assets/districts/all.json` as a catalog, and
the sequence SHALL reference it by id, mirroring the existing relationship between
`enemies/all.json` and `run/sequence.json`.

#### Scenario: A district exists in the catalog but is unused
- **GIVEN** the catalog carries a district no slot references
- **THEN** loading succeeds — the catalog is a library, exactly as the enemy roster is

### Requirement: R2.4 — District prose is keyed, never literal

The district catalog SHALL store i18n keys for every human-readable field, and the prose
SHALL exist only in `strings.properties` with a translation in `strings_es.properties`.

#### Scenario: Reading the catalog
- **WHEN** `districts/all.json` is inspected
- **THEN** the name field holds `district.<id>.name`, not "The Boardroom"
- **AND** no field in the file contains a sentence in any language

#### Scenario: Locale parity
- **WHEN** the district keys in `strings.properties` are compared to `strings_es.properties`
- **THEN** every key exists in both, with no untranslated English left in the Spanish bundle

### Requirement: R2.5 — Zero balance delta

F2 SHALL NOT change any simulated outcome.

#### Scenario: The sweep is compared across the change
- **GIVEN** the 200-seed report from the fork point
- **WHEN** the sweep runs on the F2 branch
- **THEN** win rates, average peak debts, HP at victory and defeat breakdowns are identical
- **AND** any difference fails the change

#### Scenario: Enemies and rewards are untouched
- **WHEN** `git diff` is read for `run/sequence.json`
- **THEN** every `enemyId` and every `rewards` object is byte-identical; only added fields
  appear

### Requirement: R2.6 — No new run phase

F2 SHALL NOT add a value to `RunManager.Phase`.

#### Scenario: The phase machine is unchanged
- **WHEN** `RunManager.Phase` is read after F2
- **THEN** it is still `{ COMBAT, NODE, VICTORY, DEFEAT }`
- **AND** none of the four exhaustive `when` sites over it required a new branch

### Requirement: R2.7 — District identity is visible in play

The player SHALL be told which district they are in, on entering it and on the node screen.

#### Scenario: Entering a district
- **WHEN** the first combat of a district begins
- **THEN** the district name and descriptor are shown, and its background is drawn

#### Scenario: The layout is resolution-independent
- **WHEN** the district name is drawn
- **THEN** its position derives from the viewport width through the existing layout helpers,
  with no fixed 1280-space coordinate

### Requirement: R2.8 — New art declares its debt

Any asset generated for F2 SHALL be produced with the explicit "no text, no lettering, no
numbers" instruction recorded in `docs/ART-PIPELINE.md`, and the change SHALL state which
pipeline defects it pays and which it carries.

#### Scenario: A district background is generated
- **THEN** the generation prompt carries the no-text instruction
- **AND** the resulting asset contains no lettering
- **AND** it is produced at the corrected resolution, not the undersized one

#### Scenario: The card-art debt
- **THEN** F2 states explicitly that it carries the 15-of-27 baked-text debt and assigns it
  to F5, rather than leaving the reader to assume it was handled

### Requirement: R2.9 — The design system stops being invisible

Before F2 adds a UI surface, the design tokens it depends on SHALL exist as tracked text in
`docs/`.

#### Scenario: Another agent needs the palette
- **GIVEN** `Arts/` is gitignored and `git ls-files Arts/` returns nothing
- **WHEN** a contributor or agent needs the district-card treatment
- **THEN** `docs/DESIGN-SYSTEM.md` is tracked in git and states the palette, type scale and
  spacing used
- **AND** the ZIP is cited as the origin, not required as a dependency
