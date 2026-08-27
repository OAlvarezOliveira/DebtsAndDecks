# ADR 0002: 16 KB Page-Size Compliance and the Android Platform Baseline

## Status
Accepted

## Date
2026-08-27

## Context

Debts & Decks must be publishable on Google Play. Three Play mandates apply, all re-verified
against the live documentation on **2026-08-27** (item 13 — dates are cited, not assumed):

| Mandate | Verbatim source text | URL (fetched 2026-08-27) |
|---------|----------------------|--------------------------|
| Target API level | "Starting August 31 2026: New apps and app updates must target Android 16 (API level 36) or higher to be submitted to Google Play" — and "If you need more time to update your app, you'll be able to request an extension to **November 1, 2026**." | https://developer.android.com/google/play/requirements/target-sdk |
| AAB-only publishing | "Important: From August 2021, new apps are required to publish with the Android App Bundle on Google Play." | https://developer.android.com/guide/app-bundle |
| 16 KB page sizes | "Google Play compatibility requirement — To ensure your app works correctly on the latest versions of Android, all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play." | https://developer.android.com/guide/practices/page-sizes |

The third mandate is the hard one. It is not satisfied by a version number: it is a property of
the **bytes** of every 64-bit `.so` we ship, and our only native code is `libgdx.so`, which we do
not compile — LibGDX ships it prebuilt. So the question "are we 16 KB ready?" had to be answered
by measurement, and the answer determined whether this phase could proceed at all.

The baseline before this phase was AGP 8.4.0 / Gradle 8.9 / Kotlin 1.9.22 / LibGDX 1.12.1 /
compileSdk 34 — every rung below what the mandates require.

### The three-layer verification model

16 KB readiness is three independent properties, and conflating them produces false verdicts:

- **Check A — ELF alignment.** Every `PT_LOAD` segment of each 64-bit `.so` must have
  `p_align >= 0x4000`. Owner: whoever *produced* the library. We cannot fix this ourselves without
  rebuilding the natives. **This is the only check that can produce `BLOCKED`.**
- **Check B — zip packaging.** Each `.so` inside the APK must be `STORED` (uncompressed) and start
  on a 16 KB boundary. Owner: AGP >= 8.5.1 with `useLegacyPackaging = false`.
- **Check C — empirical launch.** The app must actually start on a device whose
  `getconf PAGE_SIZE` is `16384`.

32-bit ABIs (`armeabi-v7a`, `x86`) are exempt; they are reported `SKIP32` and never count as a
failure.

## Decision

**Modernize the platform baseline to compileSdk/targetSdk 36 and reach 16 KB compliance by
bumping LibGDX to 1.14.2 — the documented F1 fallback — not by weakening any check.**

### Item 1 — Resolved toolchain matrix

Each rung was resolved *before* any file was edited, from primary sources, and applied one
variable at a time (decision D-7).

| Component | From | To | Why this exact version | Source (accessed 2026-08-27) |
|-----------|------|----|------------------------|------------------------------|
| AGP | 8.4.0 | **8.10.1** | Lowest *stable* AGP that supports `compileSdk 36` **and** is >= 8.5.1 (the Check B floor). AGP 8.6/8.8/8.9 all cap at API 35. 8.10 is the first line with max API 36. 8.10.1 is the newest patch of that line. | https://developer.android.com/build/releases/past-releases/agp-8-10-0-release-notes + Google Maven metadata |
| Gradle | 8.9 | **8.11.1** | AGP 8.10's *minimum and default* Gradle. Not a free choice. | same AGP release notes |
| Kotlin (+ serialization plugin) | 1.9.22 | **2.2.20** | Lowest KGP whose fully-supported window contains **both** Gradle 8.11.1 and AGP 8.10.1. KGP 2.2.0–2.2.10 caps AGP at 8.10.0; 2.1.x caps at 8.7.2. Note 1.9.22 was *already* outside its supported window against AGP 8.4/Gradle 8.9, so this is a correction, not merely a bump. | https://kotlinlang.org/docs/gradle-configure-project.html (KGP compatibility table) |
| LibGDX | 1.12.1 | **1.14.2** | Trigger **T1**: 1.12.1 fails Check A. See item 7. | measured; see item 2 |
| compileSdk / targetSdk | 34 | **36** | Play target-API mandate. | table above |
| minSdk | 24 | **24 (unchanged)** | P1 must not change device reach. | — |

`kotlinx-serialization-json` stayed at 1.6.3: it is versioned independently and the Kotlin rung
did not require moving it.

**Convergent evidence.** Rung 4.1 initially failed with
`Dependency 'androidx.core:core-ktx:1.17.0' requires Android Gradle plugin 8.9.1 or higher` and
`requires ... compile against version 36 or later`. Cause: `gdx-backend-android-1.14.2.pom`
declares `androidx.core:core:1.17.0`, while `1.12.1` declares no androidx dependency at all.
So LibGDX 1.14.2 *independently* demands AGP >= 8.9.1 and compileSdk >= 36 — arriving at the same
matrix by a second, unrelated route.

### Item 2 — The 16 KB verdict

**`ALIGNED-AFTER-FALLBACK`.** Justified by the Check A x Check C matrix row *"Check A PASS +
Check C PASS on a 16 KB device = fully verified — verdict stands"*. STOP GATE 1 did **not** fire.

The starting point genuinely failed. LibGDX **1.12.1**:

```
UNALIGNED app/libs/arm64-v8a/libgdx.so PT_LOAD align: 0x1000, 0x1000, 0x1000  (need >= 0x4000)
SKIP32   app/libs/armeabi-v7a/libgdx.so  PT_LOAD align: 0x1000, 0x1000, 0x1000
SKIP32   app/libs/x86/libgdx.so  PT_LOAD align: 0x1000, 0x1000, 0x1000
UNALIGNED app/libs/x86_64/libgdx.so PT_LOAD align: 0x1000, 0x1000, 0x1000  (need >= 0x4000)

VERDICT: FAIL
```

Rather than guess which release fixed it, every candidate was measured from `repo1.maven.org`.
**1.13.0 is the alignment floor** — the first release built with a 16 KB-capable linker:

| LibGDX | arm64-v8a | x86_64 | Verdict |
|--------|-----------|--------|---------|
| 1.12.1 | `0x1000` | `0x1000` | **FAIL** |
| 1.13.0 | `0x4000` | `0x4000` | PASS |
| 1.13.1 | `0x4000` | `0x4000` | PASS |
| 1.13.5 | `0x4000` | `0x4000` | PASS |
| 1.14.0 | `0x4000` | `0x4000` | PASS |
| 1.14.1 | `0x4000` | `0x4000` | PASS |
| 1.14.2 | `0x4000` | `0x4000` | PASS |

**1.14.2 was pinned** (not merely the floor 1.13.0) for stated reasons, not recency: 1.14.1 fixes
Android cutout, NPE and ANR defects, and 1.14.2 reverts 1.14.1's `InputMultiplexer` regression —
which this project does not use anyway. A breaking-API probe over the 22 LibGDX symbols actually
imported by this codebase returned zero matches against the 1.13/1.14 changelogs.

Final Check A, on natives produced by **this repo's own build** at the final toolchain:

```
ALIGNED  app/libs/arm64-v8a/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000
SKIP32   app/libs/armeabi-v7a/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000
SKIP32   app/libs/x86/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000
ALIGNED  app/libs/x86_64/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000

VERDICT: PASS
```

And — the part that actually matters for the store — Check A re-run on the `.so` **extracted from
the shipped release APK**, i.e. on the bytes a user would download:

```
ALIGNED  lib/arm64-v8a/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000
SKIP32   lib/armeabi-v7a/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000
SKIP32   lib/x86/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000
ALIGNED  lib/x86_64/libgdx.so  PT_LOAD align: 0x4000, 0x4000, 0x4000

VERDICT: PASS
```

### Item 6 — Cross-validation with a second tool

The verdict does not rest on one home-made parser. `/usr/bin/readelf -lW` agrees exactly, before
and after the bump:

```
# LibGDX 1.12.1 (before)                    # LibGDX 1.14.2 (after)
== app/libs/arm64-v8a/libgdx.so             == app/libs/arm64-v8a/libgdx.so
  LOAD ... R E 0x1000                         LOAD ... R E 0x4000
  LOAD ... RW  0x1000                         LOAD ... RW  0x4000
  LOAD ... RW  0x1000                         LOAD ... RW  0x4000
== app/libs/x86_64/libgdx.so                == app/libs/x86_64/libgdx.so
  LOAD ... R E 0x1000                         LOAD ... R E 0x4000
  LOAD ... RW  0x1000                         LOAD ... RW  0x4000
  LOAD ... RW  0x1000                         LOAD ... RW  0x4000
```

### Item 4 — Check B result

Tool: `$ANDROID_SDK/build-tools/36.1.0/zipalign -c -P 16 -v 4 app-release-unsigned.apk`,
exit code **0**, `Verification successful`:

```
 3571712 lib/arm64-v8a/libgdx.so (OK)      3571712 = 218 * 16384
 3751936 lib/armeabi-v7a/libgdx.so (OK)    3751936 = 229 * 16384
 3915776 lib/x86/libgdx.so (OK)            3915776 = 239 * 16384
 4112384 lib/x86_64/libgdx.so (OK)         4112384 = 251 * 16384
```

All four are `Stored` (cmpr 0%), i.e. uncompressed. The merged manifest carries
`android:extractNativeLibs="false"` — this is **AGP's default**, which we inherited; we did not
set it, and we did not set `useLegacyPackaging` anywhere.

### Item 5 — Check C result

**RAN, and PASSED** on 2026-08-27 — on a genuine 16 KB kernel, not asserted from a version number.

| Property | Value |
|----------|-------|
| Device | AVD `p1_ps16k_api36`, image `system-images;android-36;google_apis_ps16k;x86_64` |
| `getconf PAGE_SIZE` | **16384** |
| `ro.build.version.sdk` | **36** (equals our `targetSdk`) |
| Build installed | **debug** (decision D-9 — `assembleRelease` emits `app-release-unsigned.apk`, which cannot be installed; signing is P6) |
| Install | `Success` |
| Launch | `Status: ok`, `LaunchState: COLD`, `TotalTime: 3502 ms`, `Complete` |
| Stability | process alive at 00:23 and 00:59; `logcat -b crash` empty; no `FATAL EXCEPTION` |

The decisive line — the native library loading straight out of the uncompressed APK entry on a
16 KB kernel, which is precisely what the mandate is about:

```
D nativeloader: Load /data/app/~~nh_Tl3gZNxJy-CGDljfuKg==/com.debtsdecks-.../base.apk!/lib/x86_64/libgdx.so
                using class loader ns clns-9 (...): ok
```

LibGDX genuinely came up (not just the process):

```
W GL2JNIView    : creating OpenGL ES 2.0 context
I AndroidGraphics: OGL renderer: Android Emulator OpenGL ES Translator (ANGLE ... SwiftShader ...)
I AndroidGraphics: OGL version: OpenGL ES 3.1 (ANGLE 2.1.17841)
```

Guard against a "process alive but black screen" false pass: a screenshot was captured and
analysed — 2400x1080, **942 distinct colours** sampled across the full luminance range 0–255.
Real content was on screen.

### Item 7 — Which LibGDX bump trigger fired

**T1** (decision D-4): Check A failed on the pinned version, which is the named trigger permitting
a LibGDX bump. T2 and T3 did not fire. LibGDX is otherwise not bumped speculatively, because this
project has **no headless GL harness** that could catch a rendering regression.

### Item 8 — STOP GATE 2

**It fired.** AGP 8.10.1 requires Gradle >= 8.11.1, above the pinned 8.9.

The operator pre-selected **option (a)**: install a newer standalone Gradle and document it,
explicitly *not* pulling the wrapper repair forward from P7. That was done —
`~/.gradle/standalone/gradle-8.11.1`, SHA-256
`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`, verified against the published
`.sha256`. The whole toolchain ladder was executed with it.

**Correction, recorded honestly.** The premise behind the gate — that the committed wrapper is
broken — turned out to be **false**. Verified directly: `gradle/wrapper/gradle-wrapper.jar` is a
valid 43,504-byte archive containing `GradleWrapperMain.class`, and after this phase bumps
`distributionUrl` to `gradle-8.11.1-bin.zip`, `./gradlew --version` reports Gradle 8.11.1 and
`./gradlew :app:testDebugUnitTest --rerun` builds and runs the full suite green. The wrapper never
needed repairing; it only ever pointed at a Gradle too old for AGP 8.10.1. Editing
`distributionUrl` is plain text and is the first rung of the D-7 ladder, squarely inside P1.

So: **no wrapper-JAR regeneration was pulled forward from P7** (risk R11 did not materialise), the
standalone distribution is retained as a documented fallback rather than a requirement, and
`./gradlew` is the canonical invocation from now on.

### Item 9 — Manifest and configuration verdicts

| Setting | Verdict | Reasoning |
|---------|---------|-----------|
| `package="com.debtsdecks"` (manifest) | **Removed** | Deprecated; `namespace` in `build.gradle.kts` is the single source of truth. The `Setting the namespace via the package attribute ... is no longer supported` warning went from >= 1 occurrence to **0**. The merged manifest still shows `package=` because AGP *generates* it from `namespace` — that is correct, not a leftover. |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` | **Removed (all three)** | The app makes no network calls; BD-1 fixes v1 as free with no billing/ads/analytics. Fewer permissions is a smaller Play data-safety surface. |
| `android:allowBackup="true"` | **KEPT — decision deferred to P4** | Backup policy belongs with the Play Data safety form. P1 records the verdict only; it makes no change. (Open question OQ-2.) |
| `android:largeHeap="true"` | **KEPT** | **Artifact discrepancy recorded:** the spec says remove it, design §6.4 says keep it. Design was followed per the executor contract. Rationale: LibGDX loads the card-art textures; shrinking the heap is a runtime-risk change with no submission benefit, and P1 must not change behaviour. |
| `multiDexEnabled = true` | **KEPT, but now redundant** | minSdk 24 (>= 21) has native multidex, and LibGDX 1.13.5 release notes state multidex config is no longer required. Removing it is a behaviour-adjacent change with zero P1 benefit. Flagged as safe cleanup for a later phase. |
| `numSamples = 2` | **UNTOUCHED** | Owned by P5 (rendering/MSAA). |
| Sensors | **Disabled** | `useAccelerometer`, `useCompass`, `useGyroscope` set to `false`. A card game reads no sensors; leaving them on costs battery and implies capabilities we do not use. |

One permission survives on the **merged** manifest, and we did not declare it:
`com.debtsdecks.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (`protectionLevel="signature"`),
injected by `androidx.core` 1.17.0 which `gdx-backend-android` 1.14.2 pulls in. It is app-private,
signature-level, not user-visible on the store listing, and cannot be removed without dropping
`androidx.core`. Recorded so P6 does not mistake it for a stray permission.

### Item 10 — Cutout decision and device observation

**A defect in the phase instruction was found and corrected.** The task said to add
`android:layoutInDisplayCutoutMode="shortEdges"` to `<activity>`. That attribute **does not
exist**; AAPT rejected the build with
`error: attribute android:layoutInDisplayCutoutMode not found`. Confirmed against the platform
resource table (`javap` on `android.R$attr` from `android-36.jar`): the only public attribute
matching `/layoutInDisplay/` is **`windowLayoutInDisplayCutoutMode`**, and it is a *theme/window*
attribute, not a manifest attribute. `layoutInDisplayCutoutMode` is the
`WindowManager.LayoutParams` **field** name — the likely source of the confusion.

Implemented correctly in `res/values/themes.xml` on `Theme.DebtsAndDecks.Fullscreen`:

```xml
<item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
```

Honoured from API 27 and silently ignored on API 24–26, so it is safe at minSdk 24 without a
`values-v27` duplicate (duplicating the style would mean maintaining every item twice). The
motivation is concrete: the LibGDX 1.14.1 changelog records *"Don't set Android
LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES on Android 35+"* — the engine stopped setting it for us,
so declaring it in the theme restores deterministic behaviour across versions.

**Device observation.** On the API 36 device, `dumpsys window windows` reports our window as
`layoutInDisplayCutoutMode=always`, not `shortEdges`. That is expected: from Android 15 (API 35+),
`DEFAULT`/`SHORT_EDGES`/`NEVER` are all interpreted as `ALWAYS` for apps targeting SDK 35+, and
every system window in the dump reads `always` too. **Verdict: keep `shortEdges`.** It remains
load-bearing for API 27–34 devices (in range at minSdk 24) and is simply widened on API 35+; the
desired outcome — content extending into the cutout — holds on both sides of the split. No
clipping was observed and nothing is deferred to P5 on this point.

**Card art (P0 handoff).** 19 of 27 cards have art in `assets/art/cards/`, which is **exactly the
expected count**; 8 cards are deliberately art-less. The zero-case (which would have meant the
`.png` files hold non-PNG bytes and the decoder rejects them) did **not** occur, so there is
nothing to defer to P5 here.

### Item 11 — Rollback pins (verbatim, as measured at base `9afd532`)

```
AGP                = 8.4.0            build.gradle.kts  id("com.android.application") version "8.4.0" apply false
Kotlin plugin      = 1.9.22           build.gradle.kts  id("org.jetbrains.kotlin.android") version "1.9.22" apply false
Kotlin serialization plugin = 1.9.22  build.gradle.kts  id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
kotlinVersion (app)= 1.9.22           app/build.gradle.kts  val kotlinVersion = "1.9.22"
LibGDX             = 1.12.1           app/build.gradle.kts  val gdxVersion = "1.12.1"
compileSdk         = 34               app/build.gradle.kts  compileSdk = 34
targetSdk          = 34               app/build.gradle.kts  targetSdk = 34
minSdk             = 24               app/build.gradle.kts  minSdk = 24   (UNCHANGED by P1)
applicationId      = com.debtsdecks   namespace = com.debtsdecks
versionCode/Name   = 1 / "1.0.0-mvp"
JVM target         = 17
Gradle wrapper     = https\://services.gradle.org/distributions/gradle-8.9-bin.zip
Manifest (deleted, verbatim):
  package="com.debtsdecks"
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <uses-permission android:name="android.permission.WAKE_LOCK" />
gradle.properties (deleted): android.enableJetifier=true
```

Baseline test count **N = 124** (failures 0, errors 0, skipped 0, 10 classes).

## Consequences

### Positive
- Publishable against the 2026-08-31 target-API mandate with no further platform work.
- 16 KB compliance proven on the shipping bytes at all three layers, plus an empirical launch.
- Kotlin moves back **inside** its supported compatibility window (1.9.22 was already outside it).
- `copyAndroidNatives` became a real `Sync` task: correctly up-to-date-checkable and
  configuration-cache safe, where the previous `doFirst { copy { } }` could never be up to date.
- Sensor subsystems no longer initialise.

### Negative
- Minimum toolchain for contributors rises to JDK 17+ and Gradle 8.11.1.
- LibGDX moved 1.12.1 -> 1.14.2 with **no headless GL harness** to catch rendering regressions.
  Mitigated by the empirical device run, but this is a genuine, disclosed gap (owner: P7).
- `androidx.core` is transitively upgraded to 1.17.0 by `gdx-backend-android` 1.14.2, which is what
  injects the signature-level permission noted above.

### Risks & Mitigations
| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| LibGDX 1.14.2 rendering regression unseen by unit tests | Medium | High | Empirical device launch + screenshot colour analysis; rollback pins above; P7 owns the GL harness |
| A future LibGDX release regresses `p_align` | Low | High | Check A script is preserved in this ADR; re-run it on any LibGDX bump |
| Emulator is not a physical 16 KB device | Medium | Low | Kernel `PAGE_SIZE` is genuinely 16384 and `nativeloader` really mapped the `.so`; a physical run remains desirable (OQ-1) |

## Alternatives Rejected

Four shortcuts would each have turned a check green while leaving the app broken or unpublishable.
**All four were forbidden up front, and none was used** — verified by an audit showing
`useLegacyPackaging`, `extractNativeLibs`, `abiFilters` and `signingConfig` absent from the source
tree, with `isMinifyEnabled = false` in both build types.

1. **Drop the 64-bit ABIs.** Would make Check A vacuously pass by shipping nothing to check.
   Rejected: Play requires 64-bit support, and it abandons every modern device.
2. **`useLegacyPackaging = true` / `extractNativeLibs="true"`.** Would extract libraries to disk at
   install time, side-stepping Check B. Rejected: it is the exact anti-pattern the 16 KB mandate
   exists to eliminate; it inflates install size and slows startup.
3. **Suppress the AGP warnings.** Rejected: hiding a compatibility signal is not resolving it.
4. **Lower `targetSdk`.** Rejected outright: it directly violates the mandate this phase exists to
   satisfy.

Also rejected: **rebuilding the natives ourselves** with NDK r27+ and
`-Wl,-z,max-page-size=16384` (the F2 fallback). Unnecessary — F1 (bumping LibGDX) resolved it, and
F2 would have meant maintaining a private fork of the engine's native build.

## Implementation Notes

### Item 3 — The Check A script (decision D-6: it lives here, never in the repo tree)

```python
#!/usr/bin/env python3
"""16 KB page-size readiness: check PT_LOAD p_align of ELF shared objects.

Usage: python3 check_elf_align.py <file-or-dir> [...]
Exit 0 = every 64-bit ELF has all PT_LOAD p_align >= 16384. Exit 1 = at least one FAIL.
32-bit ELFs are SKIPped: the Android 16 KB requirement applies to 64-bit ABIs only.
"""
import os
import struct
import sys

PT_LOAD = 1
REQUIRED_ALIGN = 16 * 1024


def read_pt_load_aligns(path):
    with open(path, "rb") as fh:
        data = fh.read()
    if len(data) < 52 or data[:4] != b"\x7fELF":
        return None, None
    ei_class = data[4]                       # 1 = ELF32, 2 = ELF64
    endian = "<" if data[5] == 1 else ">"    # 1 = little endian
    if ei_class == 2:
        e_phoff, = struct.unpack_from(endian + "Q", data, 0x20)
        e_phentsize, = struct.unpack_from(endian + "H", data, 0x36)
        e_phnum, = struct.unpack_from(endian + "H", data, 0x38)
    elif ei_class == 1:
        e_phoff, = struct.unpack_from(endian + "I", data, 0x1C)
        e_phentsize, = struct.unpack_from(endian + "H", data, 0x2A)
        e_phnum, = struct.unpack_from(endian + "H", data, 0x2C)
    else:
        return None, None
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, = struct.unpack_from(endian + "I", data, off)
        if p_type != PT_LOAD:
            continue
        if ei_class == 2:
            p_align, = struct.unpack_from(endian + "Q", data, off + 0x30)
        else:
            p_align, = struct.unpack_from(endian + "I", data, off + 0x1C)
        aligns.append(p_align)
    return ei_class, aligns


def collect(targets):
    out = []
    for t in targets:
        if os.path.isdir(t):
            for root, _dirs, files in os.walk(t):
                for name in sorted(files):
                    if name.endswith(".so"):
                        out.append(os.path.join(root, name))
        else:
            out.append(t)
    return sorted(out)


def main(argv):
    files = collect(argv[1:] or ["app/libs"])
    if not files:
        print("no .so files found", file=sys.stderr)
        return 2
    failed = False
    for path in files:
        ei_class, aligns = read_pt_load_aligns(path)
        if ei_class is None:
            print("NOT-ELF  {}".format(path))
            continue
        bits = 64 if ei_class == 2 else 32
        pretty = ", ".join(hex(a) for a in aligns) or "(none)"
        if bits == 32:
            print("SKIP32   {}  PT_LOAD align: {}".format(path, pretty))
            continue
        if aligns and all(a >= REQUIRED_ALIGN for a in aligns):
            print("ALIGNED  {}  PT_LOAD align: {}".format(path, pretty))
        else:
            failed = True
            print("UNALIGNED {} PT_LOAD align: {}  (need >= 0x4000)".format(path, pretty))
    print("\nVERDICT: {}".format("FAIL" if failed else "PASS"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
```

Re-run it on **any** future LibGDX bump:

```bash
python3 check_elf_align.py app/libs
```

### Kotlin DSL migration

Kotlin 2.x removed `-Xopt-in=kotlin.RequiresOptIn`, and `kotlinOptions` is superseded. The
`android { kotlinOptions { } }` block and the separate `tasks.withType<KotlinCompile>` block were
replaced by one top-level declaration:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
```

`packagingOptions` was renamed to `packaging` (AGP 8.10 deprecation), and
`android.enableJetifier=true` was deleted — no dependency needs Jetifier.

### Verification ladder actually executed

One variable per rung, full suite after each (baseline N = 124, all green at 124/0/0):

| Rung | Change | Result |
|------|--------|--------|
| 4.1 | Gradle 8.9 -> 8.11.1 | PASS 124/0/0 |
| 4.2 | Kotlin 1.9.22 -> 2.2.20 | PASS 124/0/0 |
| 4.3a | AGP 8.4.0 -> 8.10.1 | PASS 124/0/0 |
| 4.3b | `packagingOptions` -> `packaging`; drop Jetifier | PASS 124/0/0 |
| 4.4 | `copyAndroidNatives` -> `Sync` task | PASS 124/0/0 |
| 4.5 | compileSdk/targetSdk 34 -> 36 | PASS 124/0/0 |
| 4.6 | LibGDX 1.12.1 -> 1.14.2 (trigger T1) | PASS 124/0/0 |

### Compiler warning triage

All **four** pre-P1 warnings are gone: `-Xopt-in is deprecated` (the flag was removed), plus three
`RunSimulator`/`RunSimulationHarnessTest` warnings that Kotlin 2.2 no longer emits.

**Nine new warnings**, all one kind and all in test code:
`I18nBundleTest.kt` — `'constructor(p0: String!): Locale' is deprecated`. `java.util.Locale(String)`
is deprecated in favour of `Locale.of(...)`, which the newer JDK now surfaces.
**Disposition: accepted, deferred to P7** (test-harness ownership). It is test-only with no runtime
or packaging impact, and rule 0.1.8 keeps P1 out of the test sources.

## References
- [Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk) (accessed 2026-08-27)
- [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) (accessed 2026-08-27)
- [About Android App Bundles](https://developer.android.com/guide/app-bundle) (accessed 2026-08-27)
- [AGP 8.10 release notes](https://developer.android.com/build/releases/past-releases/agp-8-10-0-release-notes) (accessed 2026-08-27)
- [Kotlin Gradle plugin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html) (accessed 2026-08-27)
- [LibGDX changelog](https://github.com/libgdx/libgdx/blob/master/CHANGES) (accessed 2026-08-27)
- ADR 0001 — Use LibGDX for Rendering (unchanged by this ADR)

## Review
- **Author:** Oscar
- **Reviewed by:** (self — solo project)
- **Next review:** Before the first Play submission (P6 signing / P8 store listing)
