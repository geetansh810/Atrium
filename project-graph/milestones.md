# milestones

**What:** the 26-milestone / 5-phase plan. Full cards + Done-when checks: `atrium-docs/07-milestones.md`; ready-made session prompts: `11-session-prompts.md`. One milestone per session.

**Phases:** 0 Foundation (M0.0–M0.8: OSS spike → scaffold → registry → routing → claim/lease → first agent → approval → budgets → task board) · 1 Validate (M1.1–1.3: content role, real pilot, fixes) · 2 Expand (M2.1–2.6: roles, dependencies, analytics, **SkyOffice fork M2.4a–c**, chat, panels) · 3 Multi-tenant (M3.1–3.5: auth, RLS, Stripe, onboarding, prod) · 4 Compliance (M4.1–4.3).

**Actual progress (2026-07-12) — note we deviated from doc order, frontend-first per user decision:**
- ✅ Dashboard shell + all panels ([[web-dashboard]] + [[frontend-shared]]) — the UI half of M0.8/M2.3/M2.5/M2.6, built early on mock data.
- ✅ [[web-office]] — SkyOffice client vendored (client half of M2.4a), stripped (M2.4b scope), agent avatars driven by the mock store (M2.4c preview). LimeZu license verified: paid tier required before commercial launch (06 §D).
- ✅ MB-0 (2026-07-12, session 5) — git repo initialized on `main`, root commit with web/ + docs + graph; root `.gitignore`/`.env.example`/`README`/`Makefile` (`make dev|check|test`, graceful no-ops for absent services).
- ✅ M0.1 (2026-07-12, session 5) — [[core-api]] scaffolded: Boot 3.5.6/Java 17, 8 module packages + boundary docs, common/ (TenantContext/problem+json/utils), Flyway V1+V2 applied on pgvector, compose stack boots health-UP, Testcontainers smoke green. Done-when verified in full.
- ☐ Everything else. Next: M0.2 registry (entities/repos/endpoints, RuntimeRegistry stub, V2_1 seed). Real M2.4a–c server halves (Colyseus vendor, room tokens, Redis presence) still pending.

**Rev C (2026-07-12):** backend sequence now lives in `atrium-docs/17-backend-execution-plan.md` ([[agent-platform]]) — amends M0.1/M0.2/M0.4, splits M0.5a/b, redefines M0.8 (mock→API swap), adds MB-0, M0.75 and the M-SK/CTX/MEM/LN/KN/AR agent-depth series before/around Phase 1. M0.0's Paperclip half ✅ (findings in `atrium-docs/notes/`).

**Likely next:** M0.2 (registry module + seed migration) per doc 17.

Links: [[_Atrium]] · [[core-api]] · [[web-dashboard]] · [[web-office]] · [[office-realtime]]
