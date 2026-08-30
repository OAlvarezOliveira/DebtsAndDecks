# sdd-sync — `fv-e1-arrears-lock`, Phase 8

## Outcome: not applicable, and the reason is checkable

Canonical sync would copy verified delta specs into `openspec/specs/`. **That directory does not
exist in this repository.**

```
$ find openspec/specs -type f
find: 'openspec/specs': No such file or directory
$ ls openspec
changes  config.yaml  project.md  VERIFICATION-CHECKLIST.md
```

`openspec/config.yaml` declares `artifact_store.git: openspec/` and artifact store **both**, but no
canonical spec tree has ever been established here: no change in `openspec/changes/` (F0, F2-F8,
FV, and all six FV.E1 sub-changes) has a synced counterpart, and the config itself records that this
repo has **no OpenSpec CLI and no `openspec validate` gate** — the layout is maintained by hand.

Creating `openspec/specs/` would therefore be a new project-wide convention, decided by a
documentation-cleanup phase, on a branch, unreviewed. That is a structural decision for the owner,
not a side effect of tasks 8.1-8.3.

## What exists instead, and stays active

| Delta spec | State |
|---|---|
| `openspec/changes/fv-e1-arrears-lock/specs/arrears-lock/spec.md` | active, unsynced |
| `openspec/changes/fv-e1-arrears-lock/specs/debt-economy/spec.md` | active, unsynced. Already carries the D1 amendment: it explicitly supersedes its own earlier "every reference MUST use 40" blanket rule (`:29-31`), which is the resolution `design.md`'s Open Question asks for |

Both remain in place. Nothing was moved, copied or deleted.

## Blocker to record, not to resolve here

If the owner wants canonical sync as a project convention, it needs its own change: it would have to
seed `openspec/specs/` for **every** shipped change, not just this one, or the tree would claim that
FV.E1's arrears lock is the only specified behaviour in the project. Note also that
`openspec/` must stay tracked in git from its first commit — the config warns of a documented
precedent in this project of an apply agent deleting untracked files under `openspec/changes/`.
