---
name: Waterline Operator Console
description: Evidence-first municipal water telemetry console shaped as a mineral-enamel annunciator board.
colors:
  ink: "#092534"
  ink-muted: "#3f565c"
  enamel: "#edf1eb"
  paper: "#f7f8f4"
  paper-deep: "#e0e8e2"
  line: "#91a29f"
  line-faint: "#c7d1cd"
  live: "#087e78"
  live-deep: "#005e5a"
  live-pale: "#d4e9e3"
  warn: "#a75a0c"
  warn-pale: "#f5e4c8"
  danger: "#a52e27"
  danger-pale: "#f2d9d5"
  navy: "#071f2d"
  focus: "#005fcc"
  white: "#ffffff"
  tank-enamel: "#f4f6f1"
  chart-temperature: "#b96812"
  chart-rssi: "#425d9b"
  rail-text: "#b8cbce"
  rail-active: "#0e3543"
  rail-accent: "#42c0b4"
typography:
  display:
    fontFamily: '"Barlow Condensed", sans-serif'
    fontSize: "clamp(2.1rem, 4vw, 3.3rem)"
    fontWeight: 700
    lineHeight: 0.95
    letterSpacing: "-0.025em"
  title:
    fontFamily: '"Barlow Condensed", sans-serif'
    fontSize: "1.45rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "0.015em"
  body:
    fontFamily: '"Atkinson Hyperlegible", system-ui, sans-serif'
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: '"Barlow Condensed", sans-serif'
    fontSize: "0.72rem"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "0.05em"
  data:
    fontFamily: '"Azeret Mono", monospace'
    fontSize: "0.85rem"
    fontWeight: 400
    lineHeight: 1.25
    letterSpacing: "normal"
rounded:
  square: "0"
  tight: "2px"
  dot: "50%"
  tank-vessel: "44% 44% 8px 8px / 7% 7% 3px 3px"
  tank-cap: "50%"
spacing:
  xs: "0.35rem"
  sm: "0.55rem"
  md: "0.75rem"
  lg: "1rem"
  xl: "1.4rem"
  2xl: "1.8rem"
  page: "clamp(1rem, 2.5vw, 2rem)"
components:
  panel:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    padding: "0"
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.white}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    padding: "0.55rem 0.8rem"
    height: "38px"
  button-secondary:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    padding: "0.55rem 0.8rem"
    height: "38px"
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    padding: "0"
  status-tag:
    textColor: "{colors.live-deep}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
  navigation-rail:
    backgroundColor: "{colors.navy}"
    textColor: "{colors.rail-text}"
    typography: "{typography.label}"
    rounded: "{rounded.square}"
    padding: "1.3rem 0"
    width: "112px"
  tank-station:
    backgroundColor: "{colors.tank-enamel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    padding: "0"
  delivery-chain:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    padding: "1.8rem 1rem"
  telemetry-chart:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    padding: "0.7rem 0.9rem 0"
  ledger-row:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    padding: "0.65rem 0.9rem"
---

# Design System: Waterline Operator Console

## Overview

**Creative North Star: "The Mineral-Enamel Annunciator"**

Waterline is a contemporary municipal water-treatment annunciator board. Mineral enamel and paper-toned surfaces hold a navy ink system; oxidized-copper signals mark live evidence; thin rules and etched process lines make the delivery chain feel inspectable. The interface is one continuous operational record from tank level, through delivery evidence, to attention and history—not a collection of generic smart-home cards.

The visual voice is disciplined, legible, and equipment-adjacent. Condensed industrial headings establish scan order, monospaced values preserve the feel of a register, and squared ledgers keep the UI calm when an abnormal state needs attention. The tank is the signature object: a vessel silhouette with a staff gauge, water fill, and measured-value register that keeps recency and signal quality beside the headline level.

**Key Characteristics:**
- Mineral enamel, paper, and navy surfaces with quiet one-pixel rules.
- Oxidized-copper live evidence, with amber and red reserved for waiting and abnormal states.
- Condensed uppercase headings paired with monospaced telemetry and timestamps.
- Square ledgers, process stages, and responsive operator layouts rather than pill-shaped cards.

## Colors

The palette is a cool mineral field with a dark marine register and a deliberately scarce oxidized-copper signal. The root CSS custom properties are the source of truth; chart and rail accents are extracted from their repeated visual roles.

### Primary
- **Oxidized-copper live** (`colors.live`, `colors.live-deep`, `colors.live-pale`): Use for confirmed telemetry, active transport, process connectors, selected device state, and the pale selected-row wash. Keep it legible as a signal, not a decorative brand fill.

### Secondary
- **Warning amber** (`colors.warn`, `colors.warn-pale`): Use for waiting stages, reconnect or confirmation states, and the safety note.
- **Alert red** (`colors.danger`, `colors.danger-pale`): Use for offline, open, missing, and failed operational states.

### Tertiary
- **Signal blue** (`colors.chart-rssi`): Reserved for the RSSI series in telemetry charts.
- **Signal amber** (`colors.chart-temperature`): Reserved for the temperature series so it remains distinct from warning-state amber.

### Neutral
- **Navy ink** (`colors.ink`, `colors.navy`): Primary reading text and the fixed navigation rail.
- **Muted ink** (`colors.ink-muted`): Supporting copy, labels, chart text, and quiet metadata.
- **Mineral enamel** (`colors.enamel`, `colors.tank-enamel`): The main application field and the tank station surface.
- **Paper surfaces** (`colors.paper`, `colors.paper-deep`): Panels, stage tiles, controls, table treatments, and tonal hover layers.
- **Rules** (`colors.line`, `colors.line-faint`): Structural borders and low-contrast ledger dividers.
- **Rail tones** (`colors.rail-text`, `colors.rail-active`, `colors.rail-accent`): Navigation labels, active rail background, and the active edge marker.
- **Focus blue** (`colors.focus`): The keyboard-visible focus outline and skip link.
- **White** (`colors.white`): High-contrast text on navy ink and live-filled vessel states.

### Named Rules

**The Signal Scarcity Rule.** Live copper carries evidence; amber and red carry state. Do not spend status colors on decoration.

## Typography

**Display Font:** Barlow Condensed (with `sans-serif` fallback)
**Body Font:** Atkinson Hyperlegible (with `system-ui, sans-serif` fallback)
**Label/Mono Font:** Barlow Condensed for labels; Azeret Mono for telemetry, sequence, time, and other data values.

**Character:** The pairing feels like a maintained control board: condensed headings create a strong scan line, while the body face stays readable in operational copy and the mono register makes evidence feel measured rather than promotional. The current implementation declares these stacks and lets the browser use their stated fallbacks when a family is unavailable.

### Hierarchy
- **Display** (700, `clamp(2.1rem, 4vw, 3.3rem)`, 0.95 line-height, -0.025em): Page titles such as “Operations overview”; uppercase and tightly tracked for the first scan.
- **Title** (700, `1.45rem`, 1 line-height, 0.015em): Panel headings, tank identifiers, and section titles; uppercase.
- **Body** (400, inherited 1rem, 1.45 line-height): Explanatory copy, evidence notes, and state descriptions.
- **Label** (600, `0.72rem`, 1 line-height, 0.05em): Table heads, navigation, status labels, and metadata captions; uppercase.
- **Data** (400, `0.85rem` baseline, 1.25 line-height): Timestamps, sequence numbers, percentages, RSSI, and other measured values in Azeret Mono. Local components tune the size between `0.68rem` and `0.94rem` when density requires it.

### Named Rules

**The Two-Register Rule.** Let Barlow Condensed carry the operator’s eye and Azeret Mono carry the evidence. Do not replace either register with an undifferentiated UI sans.

## Layout

The desktop shell is a fixed `112px` navigation rail beside a sticky `66px` utility bar. The content surface is capped at `1840px` and uses `clamp(1rem, 2.5vw, 2rem)` page padding. The overview is a three-column grid with the tank and delivery chain as the dominant objects, the alert and device ledgers at right, and chart plus recent readings spanning the lower row; the default gap is `1rem`.

At `1320px` the overview collapses to two columns. At `980px` the rail becomes a left drawer and the operational grids become one column. At `680px` the page uses `0.85rem` padding, reserves `63px` for the mobile bottom navigation, turns the delivery chain into a horizontally scrollable six-stage strip, and changes chart summaries to two columns. The same tank-first hierarchy remains intact on mobile.

Device detail uses a two-column tank/chain/history composition on wide screens and follows the same one-column collapse. Tables keep their semantic structure and scroll horizontally when their evidence columns cannot fit; loading, empty, stale, offline, and failure states remain explicit in the flow.

## Elevation & Depth

This is a flat, layered board. Panels, ledgers, stage tiles, and controls rely on mineral tonal shifts and one-pixel rules rather than drop shadows. Depth is reserved for the illustrated tank vessel (inset shading plus a restrained ambient shadow) and for the mobile navigation drawer when it opens over the work surface.

### Shadow Vocabulary
- **Tank vessel depth** (`inset 12px 0 22px rgba(9, 37, 52, 0.06), inset -12px 0 22px rgba(9, 37, 52, 0.06), 0 10px 20px rgba(9, 37, 52, 0.08)`): Gives the signature vessel a physical enamel-and-water presence.
- **Drawer lift** (`12px 0 28px rgba(4, 20, 29, 0.24)`): Separates the open navigation drawer from the dimmed work surface at mobile widths.

### Named Rules

**The Flat Ledger Rule.** Panels are flat and squared at rest; depth belongs to the tank vessel and the open navigation drawer, not to every card.

## Shapes

Most surfaces use a square silhouette (`0` radius): panels, buttons, stage boxes, tabs, rules, and ledger rows. Small status indicators are circular (`50%`), while the tank uses a custom vessel silhouette (`44% 44% 8px 8px / 7% 7% 3px 3px`) with a separate elliptical cap. The chart tooltip is the only tight rounded surface (`2px`).

Borders are structural: panels and ledgers use `1px` mineral rules, stage boxes use `1px` ink-muted rules, and the tank assembly uses `2–4px` navy strokes for the vessel, cap, bands, and feet. Preserve the etched, squared geometry; do not turn operational states into generic pills.

## Components

### Buttons
- **Shape:** Square controls with no radius (`0`), a `1px` ink border, and a `38px` minimum height.
- **Primary:** Navy ink background, white text, condensed uppercase label, and `0.55rem 0.8rem` padding. Hover shifts to deep live copper; disabled controls use the existing reduced-opacity treatment.
- **Hover / Focus:** Hover is a tonal or live-color shift; keyboard focus uses the global `3px` focus-blue outline with `3px` offset. Active range tabs invert to navy ink with white text.
- **Secondary / Ghost / Tertiary:** Secondary buttons use paper on a navy border; text buttons are transparent, borderless, and underlined; the reconnect utility control is transparent until hover.

### Chips
- **Style:** Status tags are text-plus-dot, not pills: condensed uppercase text, an `8px` circular dot, and no enclosing background.
- **State:** Good/online/live/resolved use deep live copper; suspect/stale/acknowledged use warning amber; open/offline/missing/critical use alert red; other values use muted ink. Labels remain textual so state is never color-only.

### Cards / Containers
- **Corner Style:** Square (`0`) for panels, tank station, chain panel, and ledger rows.
- **Background:** Paper for reusable panels and chain stages; mineral enamel for the page; a slightly softer enamel for the tank station.
- **Shadow Strategy:** Flat by default; follow the Elevation & Depth section for the vessel and mobile drawer exceptions.
- **Border:** `1px` line around panels and `1px` rules between headings, rows, and metadata cells.
- **Internal Padding:** Heading bands use `0.8rem 1rem`; compact rows use `0.65rem 0.9rem`; tables use `0.58rem 0.7rem` cells.

### Navigation
- **Desktop:** Fixed `112px` navy rail with a centered Waterline lockup, stacked condensed links, and a `4px` live-accent active edge. Links are `76px` tall with icon above label; hover uses a darker navy wash.
- **Utility bar:** Sticky `66px` paper-toned bar split into local time, transport, last live event, and reconnect cells by faint vertical rules.
- **Mobile:** At `980px`, the rail becomes a keyboard-contained drawer with a dimmed scrim. At `680px`, the primary mobile treatment is a fixed `63px` bottom bar with three icon-over-label links.

### Tank Station
The signature tank gauge pairs a staff scale with a custom vessel, a bottom-anchored live-water fill, a large measured percentage, and a two-by-two register for measured age, distance, temperature, and signal. The vessel is a reading surface only; it must not imply pump control or an infrastructure command.

### Reading Delivery Chain
Six square stages—Device, Ingest, Kafka, Calibrate, Store, Live—are laid in a horizontal process line with copper connectors and small status dots. Stage labels describe evidence carried by the latest persisted reading and browser subscription, not direct infrastructure health probes.

### Telemetry Chart
The chart uses a copper level line with a quiet min/max band, a separate amber temperature line, and a blue RSSI line over a dotted grid. A dashed LIVE reference marks the latest sample, and a four-cell summary beneath the plot preserves the latest level, min, max, and sample count. The hidden semantic table is part of the accessible chart contract.

### Ledgers and Tables
Alert, device, gap, and reading records use semantic tables or list ledgers with uppercase condensed headers, monospaced values, one-pixel dividers, and restrained tonal hover. Keep action links and state tags visible in the row; do not bury evidence in decorative cards.

## Do's and Don'ts

### Do:
- **Do** use the mineral enamel, paper, navy, and copper roles consistently; keep live copper scarce and meaningful.
- **Do** preserve the Barlow Condensed heading/label register and Azeret Mono data register.
- **Do** keep panels, stage boxes, controls, and ledgers square with one-pixel structural rules.
- **Do** keep recency, quality, provenance, and explicit state labels beside headline values.
- **Do** preserve loading, empty, stale, offline, and failure treatments, visible focus, reduced motion, and non-color-only state communication.
- **Do** keep the tank-first hierarchy and responsive rail/drawer/bottom-nav behavior at the documented breakpoints.

### Don't:
- **Don't** rebuild this surface as a generic smart-home card grid or a collection of pill-shaped status cards.
- **Don't** use color as the only state signal, or spend warning/red colors on decorative emphasis.
- **Don't** add pump controls, command affordances, or claims of direct infrastructure health that the product contract does not support.
- **Don't** remove the chain caveat, recency evidence, or textual chart summary in the name of visual simplicity.
- **Don't** add heavy global shadows, glossy gradients, or arbitrary new typefaces that fight the mineral-enamel board.
