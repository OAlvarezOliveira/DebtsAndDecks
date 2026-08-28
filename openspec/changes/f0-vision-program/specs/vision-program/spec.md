# Spec delta — capability: `vision-program`

## ADDED Requirements

### Requirement: R0.1 — The vision is a versioned document

The project SHALL carry the consolidated product vision as `docs/VISION.md`, tracked in git,
containing every resolved design decision together with the alternative that was rejected and
the reason it was rejected.

#### Scenario: A future contributor asks why HP still exists
- **GIVEN** the treasury is described as the life resource
- **WHEN** a contributor reads `docs/VISION.md`
- **THEN** decision D1 states the hybrid choice, names the rejected pure-treasury option, and
  gives the concrete reason (card data, intent model and harness are all HP-shaped)

#### Scenario: A decision is questioned later
- **GIVEN** any of the nine decisions D1-D9
- **WHEN** it is challenged
- **THEN** the document contains a rejected alternative for it, so the challenge can be
  answered with the original tradeoff instead of a fresh argument

### Requirement: R0.2 — The GDD is amended, never overwritten

Changes to `docs/GDD.md` SHALL be additive or explicitly marked as forward-references. A
statement in the GDD that is currently true of the code on `develop` SHALL NOT be replaced by
a statement about planned behaviour.

#### Scenario: The GDD describes the 8-slot sequence
- **GIVEN** `docs/GDD.md` correctly describes 8 slots as they exist in `sequence.json`
- **WHEN** F0 amends the GDD for the district structure
- **THEN** the 8-slot description remains, and the district mapping is added as a forward
  reference pointing at `docs/VISION.md` and the F2 change

#### Scenario: A reviewer checks for regressions in the GDD
- **WHEN** the reviewer runs `git diff develop -- docs/GDD.md`
- **THEN** every removed line is either factually false about `develop`, or moved verbatim
  under a new heading

### Requirement: R0.3 — Dual artifact store, both tracked

Every SDD artifact SHALL exist in two places: as a file under `openspec/changes/<change>/`
tracked in git, and as an Engram observation with topic `sdd/<change>/<artifact>` under
project `debtsanddecks`.

#### Scenario: An apply agent runs in this repo
- **GIVEN** the documented precedent of untracked files under `openspec/changes/` being
  deleted by an apply agent
- **WHEN** F0's first commit lands
- **THEN** `git ls-files openspec/` lists every artifact file, and `.gitignore` contains no
  pattern matching `openspec/`

#### Scenario: The two stores disagree
- **WHEN** a file and its Engram topic differ
- **THEN** the git tree is authoritative, as stated in `openspec/config.yaml`

### Requirement: R0.4 — Documentation-only blast radius

F0 SHALL change no file outside `docs/` and `openspec/`.

#### Scenario: Verifying the blast radius
- **WHEN** a reviewer runs `git diff --stat develop...HEAD`
- **THEN** every path in the output starts with `docs/` or `openspec/`
- **AND** `./gradlew test` passes with the same 180 tests as on `develop`, none added, none
  changed

### Requirement: R0.5 — The program is delivered unverified, with a checklist

F0 SHALL ship `openspec/VERIFICATION-CHECKLIST.md` mapping each factual claim in the program
to a source and a command that checks it, and SHALL NOT assert that any claim has been
verified.

#### Scenario: An independent pass picks up the work
- **GIVEN** a reviewer with no memory of how these documents were produced
- **WHEN** they open the checklist
- **THEN** each row names a claim, a source that is code / `git log -S` / harness output, and
  a runnable command — never this document and never a prior agent's summary

#### Scenario: Searching the program for a false completion claim
- **WHEN** a reviewer searches the F0 output for the words "verified" or "confirmed" as
  assertions about this program's own claims
- **THEN** they find none
