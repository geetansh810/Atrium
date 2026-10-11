# 1 · What Atrium is

Atrium is a multi-tenant SaaS where a company **hires AI agents as employees**. Each agent has a role, skills, a manager, a token budget (its "payroll"), and a human sign-off gate in front of everything it produces. Humans create tasks; agents claim the ones that match their skills, do the work through an LLM, and hand it back for review. Nothing an agent produces counts as done until a person approves it.

## Who it is for

- **A small company or team piloting agent labour** — an owner/admin who wants work done by agents but needs control: who did what, with which model, at what cost, approved by whom.
- **Engineers extending the platform** — new capabilities are data (a role, a skill, a catalog row), not new branches of code.

It is currently **piloted, not sold**: there is no billing, plans gate nothing, and the one live pilot tenant is a small food business. See [`07-milestones.md`](../07-milestones.md) (Rev D, "DEFERRED — post-pilot backlog").

## The five promises (and where each is enforced)

| Promise | Mechanism | Detail |
|---|---|---|
| Companies never see each other's data | App-level company scoping **plus** Postgres Row Level Security | [08 Security & tenancy](08-security-tenancy.md) |
| Nothing ships unreviewed | `pending_review` is a hard gate; roles marked `review_required` need a named human | [05](05-life-of-a-task.md), [`compliance/review-process.md`](../compliance/review-process.md) |
| Every state change is auditable | Append-only `task_events`, written in the same transaction as the change | [06 Data model](06-data-model.md) |
| Spend is capped and attributable | Per-agent and company-wide token budgets, one usage row per LLM call, idempotent | [05](05-life-of-a-task.md) |
| Agents get better with feedback | Rejections become governed memories that reach later prompts | [07](07-skills-memory-learning.md) |

## What is built, and what is not

**Built:** signup/login (JWT), agent hiring from role templates, task routing with leases, LLM execution through Anthropic and Google Gemini, review inbox, budgets and analytics, task decomposition into child tasks, skills/memory/knowledge/learning, chat and announcements, a derived live Team view, LLM request logs, RLS, rate limiting, structured logs and alerting, Terraform for AWS.

**Not built / deliberately open:**

- Billing and plan entitlements (deferred; `companies.plan_tier` is display-only).
- A live AWS deployment — Terraform is written and validated but never applied. The pilot prod shape is Render + Netlify ([09](09-run-and-deploy.md)).
- Account deletion / right-to-erasure ([`compliance/retention-policy.md`](../compliance/retention-policy.md) names this gap).
- A multi-turn tool loop for agents (tool use is single-shot) and external `webhook`/`process` agent runtimes — the SPI exists, only `llm_loop` and a test `echo` runtime are implemented.
- `agent.status_changed` events have no publisher; the Team view derives status from tasks instead.

*Verified against:* [`07-milestones.md`](../07-milestones.md), [`core-api/src/main/java/app/atrium/`](../../core-api/src/main/java/app/atrium/), [`web/src/shell/routes.tsx`](../../web/src/shell/routes.tsx).
