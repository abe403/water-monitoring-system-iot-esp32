---
version: 1
slug: "console-src-app-tsx"
primary_target: "console/src/App.tsx"
related_targets: ["console/src/pages/OverviewPage.tsx","console/src/pages/DevicesPage.tsx","console/src/pages/DeviceDetailPage.tsx","console/src/pages/AlertsPage.tsx"]
---

# Operator Console Surface Brief

## Purpose

Replace the Home Assistant dashboard with a trustworthy, task-focused operator surface for monitoring tank state, delivery evidence, device health, telemetry history, gaps, and anomaly alerts.

## Visual direction

Use a contemporary municipal water-treatment annunciator board: mineral enamel surfaces, navy ink, oxidized-copper live signals, etched process lines, squared ledgers, and industrial condensed headings. One continuous operational record links the tank to its delivery chain. Avoid a generic smart-home card grid.

## Information hierarchy

The first viewport answers, in order: what the selected tank holds; whether the reading is trustworthy; whether anything needs attention; and where to inspect history or act. Desktop uses a narrow navigation rail with tank, delivery chain, alerts, roster, chart, and recent readings. Mobile collapses navigation to a bottom bar and preserves the tank as the dominant first object.

## Product truth

REST supplies devices, telemetry, aggregates, gaps, and alert lifecycle actions. STOMP/SockJS supplies live telemetry. Delivery stages describe evidence carried by persisted readings and the browser subscription, not direct infrastructure health probes. Pump controls remain absent until an authenticated, interlocked backend command API exists.

## Accessibility and behavior

Target WCAG 2.2 AA. Preserve keyboard navigation, visible focus, semantic tables, non-color state labels, reduced motion, readable error/loading/empty states, and confirmation before resolving alerts. Use self-hosted fonts and responsive semantic SVG/CSS rather than rasterized UI.
