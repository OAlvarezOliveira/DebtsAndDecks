# F2 Districts — Design System (extracted subset)

This document extracts the design tokens that **F2 (districts)** actually needs, and nothing more.
F2's two visual surfaces are:

1. **District backdrop rendering** — the per-district background image selected from
   `RunManager.currentDistrict` (F2 tasks 7.1–7.2). The backdrop art itself is generated in 5.x;
   this document only fixes the *color/type/spacing language* the district UI draws against.
2. **District name + descriptor title display** — the title card painted on entering a district
   and on the node screen (F2 tasks 7.1–7.3).

The full kit is much larger; transcribing it would be a phase of its own and inventing tokens F2
does not use would be worse than leaving them in the ZIP. Everything below is a subset chosen for
these two surfaces.

> **Provenance.** Every value in the Palette, Type, and Spacing sections comes from the source
> design-system kit at:
>
> ```
> /home/oscardev/DebtsAndDecks/Arts/Debts & Decks Design System.zip
> ```
>
> mapped to its internal token files: `tokens/colors.css` (palette), `tokens/typography.css`
> (type scale), `tokens/effects.css` (spacing / radius / effects). The district title-card
> treatment in the last section is **not** a kit pattern — see that section for why.

---

## Palette (CSS custom properties, from `tokens/colors.css`)

> Source: `/home/oscardev/DebtsAndDecks/Arts/Debts & Decks Design System.zip` → `tokens/colors.css`

```css
/* Base noir scale */
--navy-950: #0c0c18;
--navy-900: #14142a;
--navy-800: #1a1a2e;
--navy-700: #242440;
--navy-600: #2f2f52;
--navy-500: #3f3f68;

/* Ink (text) scale */
--ink-100: #eef0fb;
--ink-300: #c7c9e0;
--ink-500: #9294b8;

/* Card-type accents: rust (attack) */
--rust-300: #e3906a;
--rust-500: #b5502e;
--rust-700: #7a3319;
/* Card-type accents: steel (skill) */
--steel-300: #a9c0d1;
--steel-500: #5b7284;
--steel-700: #374a58;
/* Card-type accents: brass (power) */
--brass-300: #e8cf87;
--brass-500: #c9a227;
--brass-700: #8a6c15;

/* Accent + semantic */
--gold-400: #f2c230;
--gold-500: #e0aa14;
--neon-green: #39ff6a;
--neon-green-dim: #1c8f3f;
--blood-red: #c0392b;
--blood-red-dim: #7a231b;
```

### Semantic aliases

```css
--surface-app: var(--navy-800);            /* navy-800 */
--surface-app-deep: var(--navy-950);       /* navy-950 */
--surface-panel: var(--navy-700);          /* navy-700 */
--surface-panel-raised: var(--navy-600);   /* navy-600 */
--surface-overlay: rgba(12, 12, 24, 0.86);

--text-primary: var(--ink-100);            /* ink-100 */
--text-secondary: var(--ink-300);          /* ink-300 */
--text-muted: var(--ink-500);              /* ink-500 */
--text-on-accent: var(--navy-950);         /* navy-950 */

--accent-primary: var(--gold-400);         /* gold-400 */
--accent-primary-hover: var(--gold-500);   /* gold-500 (hover) */

--border-hairline: rgba(238, 240, 251, 0.12);
--border-strong: rgba(238, 240, 251, 0.24);
```

---

## Type scale (from `tokens/typography.css`)

> Source: `/home/oscardev/DebtsAndDecks/Arts/Debts & Decks Design System.zip` → `tokens/typography.css`

```css
/* Fonts */
--font-display: 'Oswald';   /* HUD labels, headers, buttons, card type tags — a district title uses this */
--font-body: 'Inter';       /* body copy */
--font-terminal: 'VT323';   /* combat log, victory/defeat, ledger read-outs */

/* Sizes */
--text-2xs: 11px;
--text-xs: 13px;
--text-sm: 15px;
--text-base: 17px;
--text-md: 20px;
--text-lg: 26px;
--text-xl: 34px;
--text-2xl: 48px;

/* Letter-spacing */
--tracking-label: 0.06em;
--tracking-wide: 0.12em;
```

---

## Spacing / radius / effects (from `tokens/effects.css`)

> Source: `/home/oscardev/DebtsAndDecks/Arts/Debts & Decks Design System.zip` → `tokens/effects.css`

```css
/* Space scale */
--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
--space-5: 24px;
--space-6: 32px;
--space-7: 48px;
--space-8: 64px;

/* Radius */
--radius-sm: 4px;
--radius-md: 8px;
--radius-lg: 14px;
--radius-pill: 999px;

/* Shadows */
--shadow-panel: 0 8px 24px rgba(0, 0, 0, 0.45);
--shadow-card: 0 6px 16px rgba(0, 0, 0, 0.55);
--shadow-card-hover: 0 10px 26px rgba(0, 0, 0, 0.6);
--glow-gold: 0 0 12px rgba(242, 194, 48, 0.5);

/* Motion */
--ease-out: cubic-bezier(0.2, 0.8, 0.3, 1);
--duration-fast: 120ms;
--duration-base: 200ms;
```

---

## District title-card treatment

**Plain statement of fact:** the source design-system ZIP contains **no** district-specific
component or pattern. Its component/card inventory — verified via the kit's `_ds_manifest.json` —
covers only `GameCard`, `CombatLog`, `HUDPanel`, `IntentBadge`, `StatBar`, `Button`, and the
brand/color guideline cards. A content search for `district` or `title-card` returns **zero hits**
anywhere in the kit.

Therefore F2's district name + descriptor title display (tasks 7.1–7.3) must be **composed from the
general tokens above**, not drawn from a dedicated pattern. The following is **this document's own
recommendation**, not something the ZIP specifies (the ZIP specifies nothing district-related):

> Render the district title card with:
> - **Typeface:** `--font-display` (Oswald) — the same face used for HUD labels, headers, buttons,
>   and card type tags.
> - **Size:** `--text-xl` (34px) for the district name, or `--text-2xl` (48px) on wide viewports;
>   the descriptor one step down.
> - **Tracking:** `--tracking-wide` (0.12em).
> - **Surface:** `--surface-overlay` (`rgba(12,12,24,0.86)`) as the card backing.
> - **Text color:** `--text-primary` (ink-100).
> - **Elevation:** `--shadow-panel` (0 8px 24px rgba(0,0,0,.45)).

This is offered as guidance for the renderer (already wired in `CombatRenderer.drawDistrictTitle`,
task 7.3) and as the canonical token reference for any future district UI. It is intentionally
**not** promoted to a kit component, because the kit has none and F2 should not invent one beyond
what these general tokens give it.
