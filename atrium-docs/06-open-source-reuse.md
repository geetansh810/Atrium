# 06 — Open-Source Reuse Strategy

Two upstream projects cut months off this build. Rule of thumb: **fork SkyOffice's code, mine Paperclip's patterns.**

## A. SkyOffice — fork and adapt (the office surface)

> **Removed 2026-07-15.** The vendored SkyOffice client (`web/src/office/`, vendored session 3) was deleted with the office-view retirement — replaced by the live Team view (`/team`); `office-realtime` (the server half) was never vendored. M2.4a–c are retired in 07. This section is kept as a historical record of the plan.

**Repo:** `github.com/kevinshen56714/SkyOffice` · **License:** MIT · ~1.2k stars, TypeScript ≈98%.
**Stack (verified):** Phaser 3 (game engine) + Colyseus (WebSocket server) + React/Redux (client shell) + PeerJS (WebRTC — we strip this). Repo layout: `client/` (Phaser+React), `server/` (Colyseus), `types/` (shared).
**Built-in features we inherit free:** tile-map office with rooms, WASD/arrow movement, `E` to sit, avatar name tags, real-time text chat with dialog bubbles, custom/private rooms, multi-user presence.

### Fork plan (maps to milestones M2.4a–M2.4c)
1. **M2.4a — Vendor & boot:** fork into monorepo as `office-realtime/` (their `server/`) and `web/src/office/` (their `client/`), plus `types/` merged into `shared-types/`. Get it running unmodified inside our docker-compose. Deliverable: stock SkyOffice reachable at `/office`. Also: verify LimeZu asset licensing for commercial use (see 05 §web/office).
2. **M2.4b — Strip & wire identity:** remove PeerJS/webcam/screen-share/whiteboard code paths and UI; replace room-selection lobby with auto-join `office:{companyId}` gated by a core-api room token; user avatar named from the logged-in user.
3. **M2.4c — Agent avatars:** add server-driven avatars: Redis subscriber in `office-realtime` consumes `agent.status_changed` etc., sets each agent avatar's target location (from `office_layout`), status dot color, and activity bubble. Bootstrap from `GET /office-state`. Straight-line walk v1; pathfinding later.

### Why fork rather than depend
SkyOffice isn't a library — it's an app. Forking is the intended reuse mode (MIT, "PRs welcome"). We take the ~80% (rendering, movement, presence, chat) and own the 20% that is our actual product (agents as avatars, state-driven positions).

## B. Paperclip — mine, don't fork (the orchestration patterns)

**What it is (from market research earlier in this project):** the dominant MIT-licensed open-source "AI company" orchestrator — org-chart roles, per-agent budgets, approval gates, multi-company isolation, check-in/heartbeat agent scheduling. Node.js, self-hosted, developer-facing, no hosted version.

**Why not fork it wholesale:** our core is deliberately Java/Spring (owner's strength, and the differentiation is the hosted/verified/visual layer, not the orchestration engine). Forking a fast-moving Node core we'd diverge from immediately buys risk, not speed.

**What to mine (milestone M0.0 — evaluation spike, timeboxed to one session):**
1. Clone it; read the docs and the modules for: budget metering, approval gating, agent check-in scheduling, org/role modeling, and multi-tenant isolation.
2. Write `docs/notes/paperclip-findings.md` answering: How do they model roles vs. agents? How is budget enforced (pre-call, post-call, both)? What's their approval-gate UX? How do they isolate tenants? What failure modes do their issues/discussions reveal (hallucinated output, approval fatigue, prompt-injection reports)?
3. Adopt **patterns** into our specs (03/04/05) where they're better than ours; note deliberate divergences.
4. If any of their standalone MIT utilities (e.g. provider price tables for cost calculation) are cleanly importable, list them for reuse with attribution.

> ⚠️ Exact repo URL, module names, and APIs must be verified at spike time — the space is consolidating fast and details may have changed since this doc was written (July 2026). Treat this section's claims about Paperclip as research-era snapshots, not gospel.

## C. Other ready-made pieces (use, don't build)
- **Colyseus** (comes with SkyOffice) — realtime rooms. **Flyway** — migrations. **springdoc-openapi** — serves 04 as live Swagger.
- **LLM SDKs:** official Anthropic/OpenAI/Google Java or REST clients behind our `LlmClient` interface.
- **Langfuse or Helicone (self-host/free tier)** — optional LLM tracing in dev; our `usage_records` remains the billing source of truth.
- ~~**Stripe metered billing** (Phase 3)~~ — **deferred post-pilot 2026-07-17** (07 Rev D; M3.3 moved to the backlog — Atrium is being piloted, not sold). The advice still stands for whoever picks it up: never hand-roll invoicing.

## D. License & attribution ledger (keep updated)
| Component | License | Obligation |
|---|---|---|
| SkyOffice (code) | MIT | ~~Keep copyright notice; credit in-app~~ — **removed 2026-07-15** (vendored client deleted, nothing shipped); obligation no longer applies |
| LimeZu art assets | itch.io license — verified 2026-07-12 | ~~Must buy the paid tier before commercial launch (M3.5 blocker)~~ — **removed 2026-07-15** (all assets deleted with the office retirement); launch blocker VOID |
| Paperclip (patterns/utils) | MIT (verify) | Attribution for any imported code |
| React (Phaser 3 / Colyseus no longer shipped — office removed 2026-07-15) | MIT | Notices in a THIRD-PARTY-LICENSES file |
