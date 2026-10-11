---
name: web-design-guidelines
description: Review Atrium web UI code against Vercel's Web Interface Guidelines (accessibility, focus, forms, animation, typography, theming). Use for "review my UI", "audit accessibility", "check UX".
argument-hint: <file-or-pattern>
---

# Web Interface Guidelines (pinned snapshot)

Source: vercel-labs/agent-skills `web-design-guidelines` + vercel-labs/web-interface-guidelines `command.md`,
snapshotted 2026-10-11 and read in full before vendoring (docs/07 Phase 5 rule: internet skills are untrusted).
The upstream skill fetches its rules from a URL on every run; this copy deliberately does NOT. Refresh by
re-reading the upstream file and updating `guidelines.md` by hand.

## Usage

1. Read `guidelines.md` in this directory.
2. Read the files the user named (or ask which).
3. Report findings as terse `file:line - issue`, grouped by file, per the output format in `guidelines.md`.

## Atrium overrides (these win over guidelines.md)

- **Sentence case** for headings and buttons, not Title Case (Atrium voice; see UX1 direction in 07-milestones.md).
- Colors only from `index.css` tokens; flag any hardcoded hex.
- `<meta name="theme-color">` / `color-scheme` must follow the app theme toggle, not a fixed value.
- Next.js/hydration rules do not apply (Vite SPA).
