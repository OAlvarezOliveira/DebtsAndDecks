# sdd-sync — delta-spec sync

## Finding
- This change has **no file-backed delta specs**. Proposal §8 and §11 state no `openspec/specs/` change in
  either phase; phase one is a measurement probe that ships as a test, not a capability change.
- `openspec/specs/` does **not** exist in this repo (confirmed: `ls openspec/specs/` → none). There is no
  delta to merge into `openspec/specs/`.
- The only changed source is test files (`app/src/test/.../simulation/`), already committed in `c4ae993`,
  plus `docs/BALANCE-BASELINE.md` (uncommitted).

## Conclusion
- Canonical file-backed sync is **not applicable** (no spec deltas). Per the chain, in no-spec-delta mode the
  sync step reports "canonical sync not applicable" rather than fabricating a spec.
- The change dir (`openspec/changes/fv-e1-node-response-channel/`) remains the artifact store for this probe.
- Engram mirror (`sdd/fv-e1-node-response-channel/<artifact>`, project `debtsanddecks`) per config — optional,
  not required for the phase-one gate.
