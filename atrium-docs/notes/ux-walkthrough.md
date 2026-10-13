# UX walkthrough — findings log

**Status: not yet run.** UX5's Done-when requires findings from at least one real person using the product
unassisted. That cannot be simulated. Everything below the line is a template; fill it in after a session.

## What is already verified without a person (2026-10-12, automated)

Run from a scratch harness (playwright-core driving local Chrome + axe-core 4.10.2) against the Vite dev servers,
mock mode for app pages, API mode for landing/auth.

- **axe-core:** 0 violations across landing, signup, login, Mission Control, Tasks, task detail, Review inbox,
  Employees, profile, Workflow, Organization, /design; dark and light; 1280px and 400px.
- **Overflow:** no horizontal page scroll at 400px on any of those pages.
- **Keyboard, Review inbox:** Tab reaches Approve; visible 2px accent focus ring; Enter opens the confirm dialog,
  focus lands on the confirm button; Escape closes it and returns focus to Approve; Enter on confirm approves
  (3 → 2 cards).
- **Keyboard, profile:** Pause agent → Cancel leaves the agent running; Confirm pauses it (button becomes Resume).
- **Keyboard, Workflow:** a graph node is focusable and Enter selects it, updating the inspector.
- **Landing → signup:** the Sign up link navigates to `/signup` at 400px.

Not covered: the signup → onboarding → hire → assign → watch → review journey against a live backend.

## Session script (15 minutes, observer says nothing unless the person is stuck for 60 seconds)

1. Start on the landing page. "What do you think this product does?" (record in their words)
2. "Sign up and get one agent doing one real task for you."
3. When a task reaches review: "Decide whether this is good enough to ship."
4. "Find out how much that task cost and which model produced it."
5. "Pause the agent so it does nothing further."

## Findings

| # | Step | What happened (observed, not interpreted) | Severity | Fix / ticket |
|---|------|-------------------------------------------|----------|--------------|
|   |      |                                           |          |              |

Participant: _role, familiarity with AI tools_ · Date: _ · Build: _ · Observer: _
