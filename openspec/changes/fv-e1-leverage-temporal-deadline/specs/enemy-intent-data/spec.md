# Delta for Enemy Intent Data — FORECLOSE Cancel Threshold

No prior spec file exists for this domain; written as ADDED requirements.

## ADDED Requirements

### Requirement: FORECLOSE Cancel-Threshold Field on Intent Data
Each FORECLOSE intent entry in `app/src/main/assets/enemies/all.json` MUST carry a new
per-intent field for the cancel threshold, structured the same way `param`/`damage` already are
today (per-enemy, data-driven, no code constant). This field MUST be independent of the existing
`param` field (which continues to drive the window's expiry-check axis, i.e. the level compared
at window close) and MUST NOT be `DebtConfig` constant.

#### Scenario: New field present per FORECLOSE slot
- GIVEN an enemy definition with a FORECLOSE intent step
- WHEN the intent data is loaded
- THEN the FORECLOSE step MUST expose a cancel-threshold value distinct from `param`

### Requirement: Cancel Threshold Value Is a Measured Tuning Parameter
The numeric value of the cancel threshold is NOT fixed by this spec. It MUST be determined by
measurement against the exit criterion (response gap >= 10pp/200 seeds, E2 green in the same
run, results recorded in `docs/BALANCE-BASELINE.md` with the exact command), the same way the
shipped `param: 27` was reached by sweep rather than picked in advance.

#### Scenario: Threshold tuned via measurement, not chosen in the spec
- GIVEN the new cancel-threshold field exists on a FORECLOSE intent
- WHEN `sdd-design`/`sdd-tasks` selects candidate values to test
- THEN each candidate MUST be run through the full measurement plan (§ below) before being
  accepted, and the chosen value MUST be recorded with its measured numbers

## Constraints

- MUST NOT change any `damage` value on existing FORECLOSE/ATTACK/MULTI_ATTACK/BUFF/DEBUFF/LEVY
  intent steps (non-negotiable, proposal §5).
- Adding the field MUST remain a single-commit, no-schema-migration change (proposal §10
  rollback: `git revert` of the change commit restores today's snapshot FORECLOSE, 27/fee 9).
