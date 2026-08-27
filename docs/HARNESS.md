# Engineering & Harness Notes — Debts & Decks

> Practical record of how this repo is developed and orchestrated. Keep it updated when the toolchain or conventions change — it exists so future sessions (human or agent) can pick up fast.

## Dev loop at a glance

- Stack: **Android (compileSdk/targetSdk 36, minSdk 24) · AGP 8.10.1 · Gradle 8.11.1 · Kotlin 2.2.20 · LibGDX 1.14.2 · kotlinx-serialization · Koin** (single `:app` Gradle module). Baseline resolved and justified in `docs/ADR/0002-16kb-page-size-and-platform-baseline.md`.
- Orchestration: SDD (init → explore → proposal → spec → design → tasks → apply → verify → sync → archive) via el-Gentleman on Pi. Artifact store = **Engram** (memory), not `openspec/` — this repo has no `openspec/` directory.
- Persistent memory: **Engram** (HTTP server `engram serve` @ `ENGRAM_URL=http://127.0.0.1:7437`). Project detection is pinned via a repo-local `.engram/config.json` with `{"project_name": "debtsanddecks"}`.

## The agent/skill model (what we learned)

- **Skills are project-scoped** in Pi: load from `.pi/skills/<name>/SKILL.md`. This is the real mechanism for project specialization.
- **Agents (subagents) are global** in this harness (`~/.pi/agent/agents/`): `sdd-*`, `gentle-ai-worker`, `gentle-ai-explore`, etc. There is **no documented per-project agent** concept. So the minimum viable specialization is **one project skill + registry entry**, not bespoke global agents (which would pollute the shared role set).
- Project skill: `.pi/skills/debtsanddecks/SKILL.md` (conventions + harness gotchas). Resolved via `.atl/skill-registry.md` and passed to subagents before repo work.
- **Decided 2026-08-25:** skill + registry + this doc; deliberately NOT creating a global `debtsanddecks-worker` agent — the skill covers specialization and existing agents execute it.

## Reproduce the build/tests (headless)

```bash
# preferred: the committed wrapper works (re-verified 2026-08-27 on Gradle 8.11.1)
./gradlew --no-daemon :app:testDebugUnitTest [--tests "<FQCN>"]

# release artifacts (unsigned until P6 adds signing)
./gradlew --no-daemon :app:bundleRelease      # -> app/build/outputs/bundle/release/app-release.aab
./gradlew --no-daemon :app:assembleRelease    # -> app/build/outputs/apk/release/app-release-unsigned.apk

# fallback only, if the wrapper ever breaks: a standalone distribution
~/.gradle/standalone/gradle-8.11.1/bin/gradle \
  --no-daemon :app:testDebugUnitTest [--tests "<FQCN>"]
```

- **Gradle version pin: 8.11.1** (raised from 8.9 on 2026-08-27). It is not a free choice: AGP
  8.10.1 requires Gradle >= 8.11.1, and AGP 8.10 is the lowest stable line supporting `compileSdk 36`.
  Only the `distributionUrl` text in `gradle/wrapper/gradle-wrapper.properties` changed; the
  wrapper JAR was **not** regenerated.
- The standalone fallback above was installed while resolving that Gradle bump
  (SHA-256 `f397b287…6eee151c6`, verified against the published `.sha256`). It is retained as a
  fallback only — **`./gradlew` is the canonical invocation** and needs no repair.
- Build outputs `*.apk` / `*.aab` are gitignored, so `fd` will not list them without `-I`.
- **On any future LibGDX bump, re-run the 16 KB Check A script** kept in ADR 0002 (it is
  deliberately not committed to the tree). LibGDX 1.12.1 shipped `.so` files with `p_align 0x1000`,
  which Google Play rejects for `targetSdk >= 35`; 1.13.0 is the first aligned release.
- **The "wrapper jar is broken" note is obsolete.** It was true before `7cc7bf1 build(gradle): commit
  wrapper so public repo clones are buildable`. Re-verified 2026-08-27: `gradle/wrapper/gradle-wrapper.jar`
  is a valid 43 KB archive containing `GradleWrapperMain.class`, and `./gradlew --version` reports
  Gradle 8.11.1 successfully after the `distributionUrl` bump. No wrapper repair is outstanding.
  Older SDD artifacts that mandate the cached-binary path as the *only* working invocation are
  stale on this point.
- Ignore harmless daemon-socket stderr noise ("Unexpected type tag 71").
- Baseline suite: **153/153 green** across **13 test classes** (measured 2026-08-27 via
  `:app:testDebugUnitTest --rerun-tasks`, after C7 `between-fight-node` landed on `develop`; two
  prior figures recorded here — 124/10 and 131/11 — were both already stale by the time they were
  written, since `develop` kept advancing under a concurrent change stream. Re-measure with the
  command above rather than trusting any fixed number in this file, including this one.
  Render/art has no headless GL harness — verified by build + manual playtest (disclosed gap).

## Non-obvious gotchas (learned the hard way)

1. **`core/` must stay pure Kotlin.** The combat-progression chain originally imported `I18NBundle` into three core classes, breaking `docs/CONVENTIONS.md` rule #1 and failing sdd-verify as a CRITICAL. Fix: extract a pure `Localizer` interface in `core/i18n/` and a GDX adapter `gdx/i18n/BundleLocalizer.kt` outside core. `grep -rn "com.badlogic" core/` must be empty.
2. **4-space indent.** The `edit` tool can fail to match text (read output may render 8 spaces); use an anchored python replace when `edit` oldText won't match.
3. **Git commit is lifecycle-gated** in this harness. A `git commit` in a compound command "fail-closes" with `Compound or wrapped lifecycle command detection is ambiguous and must fail closed.` This is the review/RDD lifecycle, not a repo problem — route commits through the sanctioned review path, never force them.
4. **`I18NBundle.format` uses `MessageFormat`** (`{0}` placeholders), not `simpleFormatter`. `.properties` VALUES don't need colon-escaping (only keys do).
5. **`I18nBundleTest` builds `I18NBundle` directly** (per-key EN+ES). Domain fixtures use `TestI18n.testLocalizer()` (returns `Localizer`); locale pinned to US around creation.
6. **Reward pool** excludes `starter`-tagged cards via `RunManager`; economy mechanics keep player state (Debt/Gold/Credit) owned by `CombatEngine`.

## In-flight / recent work

- **`combat-progression-and-i18n`** (7-PR chain, all local branches, unpushed): HP persistence, enemy tiers, full i18n. Verify flagged a core-purity CRITICAL → resolved by the Localizer refactor (committed as `fix(core)`).
- **`debt-economy-cards-and-boss-interest`** (SDD change, artifacts in Engram `sdd/debt-economy-cards-and-boss-interest/*`): 15 economy cards, new economy effects (add-debt / gain-credit / hand-exhaust / gold-scaled), boss `LEVY` squeeze, per-card art pipeline, EN/ES. Card art is **imported and in-tree** (state verified 2026-08-27): `app/src/main/assets/art/cards/` holds **19 files, all genuine PNG by magic bytes** under `.png` names — no WebP, no JPEG, no byte/extension mismatch — and `CombatRenderer.kt` loads `art/cards/$id.png`, so loader and assets agree. Two open items, both handed to P1: the restored PNGs run ~355–470 KB each (the `fe281e1` revert of the WebP pass), which pressures the `< 20 MB` APK target; and **8 of the 27 cards have no art file at all** (the C2/C4 additions).

## Cross-machine memory sync

Local SQLite (`~/.engram/engram.db`) is the source of truth; the private git repo `~/engram-memory-sync` is transport only (`engram sync --all` on the working machine, `git pull && engram sync --import` elsewhere). Never make it public (contains plaintext secrets). Not `engram cloud` (different feature).