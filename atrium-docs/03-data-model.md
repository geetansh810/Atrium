# 03 — Data Model (Postgres, Flyway-managed)
> **Office track removed 2026-07-15** — the SkyOffice virtual office (M2.4a–c, office-realtime, office_layout, /office-state) was retired and replaced by the live Team view (`/team`) in the dashboard; see `07-milestones.md`. Office references below are historical.

> **Rev C (2026-07-12):** `15-data-model-delta.md` adds the agent-platform tables and **reassigns migration numbers by application order** (V1 core+ALTERs, V2 agent platform, V3 communication/office, V4 multi-tenant) — the V2/V3 section headings below keep their content but ship as V3/V4. The canonical claim query below is amended at 17 §M0.4 (`attempt = attempt + 1`, paused-agent guard). Read 15 alongside this file for any schema work.

All IDs `UUID DEFAULT gen_random_uuid()`. All timestamps `TIMESTAMPTZ`. Every business table has `company_id` (tenant key) and an index on it. Enums are `TEXT` + CHECK constraints (simpler migrations than native enums).

## V1 — Core (Phase 0)

```sql
CREATE TABLE companies (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  slug TEXT UNIQUE NOT NULL,
  plan_tier TEXT NOT NULL DEFAULT 'solo',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (               -- human members
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  display_name TEXT NOT NULL,
  email TEXT UNIQUE NOT NULL,
  role TEXT NOT NULL DEFAULT 'admin' CHECK (role IN ('admin','member')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  password_hash TEXT NOT NULL       -- BCrypt, added M3.1 (V8) — signup/login, no plaintext ever stored
);

CREATE TABLE role_definitions (    -- what makes an agent "deep", versioned
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID REFERENCES companies(id),   -- NULL = global template
  key TEXT NOT NULL,                           -- 'coder','tester','research',…
  version INT NOT NULL DEFAULT 1,
  title TEXT NOT NULL,
  system_prompt TEXT NOT NULL,
  allowed_tools JSONB NOT NULL DEFAULT '[]',
  output_contract TEXT,
  UNIQUE (company_id, key, version)
);

CREATE TABLE agents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  name TEXT NOT NULL,                          -- 'ResearchAgent'
  sprite_key TEXT NOT NULL DEFAULT 'adam',     -- avatar sprite id
  role_definition_id UUID NOT NULL REFERENCES role_definitions(id),
  role_title TEXT NOT NULL,                    -- 'Research Specialist'
  skill_tags TEXT[] NOT NULL,
  model_provider TEXT NOT NULL,                -- 'anthropic'|'openai'|'google'
  model_name TEXT NOT NULL,
  manager_agent_id UUID REFERENCES agents(id), -- org hierarchy
  status TEXT NOT NULL DEFAULT 'offline'
    CHECK (status IN ('online','working','in_meeting','in_focus','away','offline')),
  about TEXT,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_agents_company ON agents(company_id);

CREATE TABLE tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  parent_task_id UUID REFERENCES tasks(id),
  required_skill TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  priority INT NOT NULL DEFAULT 3,             -- 1 high … 5 low
  status TEXT NOT NULL DEFAULT 'queued'
    CHECK (status IN ('queued','claimed','in_progress','flagged',
                      'pending_review','approved','rejected','cancelled')),
  progress INT NOT NULL DEFAULT 0,             -- 0–100
  eta_minutes INT,
  assigned_agent_id UUID REFERENCES agents(id),
  created_by_user_id UUID REFERENCES users(id),
  claimed_at TIMESTAMPTZ,
  lease_expires_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_queue ON tasks(company_id, required_skill, status, created_at);

CREATE TABLE subtasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  label TEXT NOT NULL,
  position INT NOT NULL,
  state TEXT NOT NULL DEFAULT 'todo' CHECK (state IN ('todo','doing','done'))
);

CREATE TABLE task_events (          -- APPEND-ONLY. Never UPDATE/DELETE.
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  task_id UUID NOT NULL REFERENCES tasks(id),
  event_type TEXT NOT NULL,         -- created|claimed|progress|note|flagged|
                                    -- completed|approved|rejected|requeued|cancelled
  actor TEXT NOT NULL,              -- 'user:<id>' | 'agent:<id>' | 'system'
  payload JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_task ON task_events(task_id, created_at);

CREATE TABLE budgets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  agent_id UUID REFERENCES agents(id),         -- NULL = company-wide cap
  period TEXT NOT NULL,                        -- 'YYYY-MM'
  cap_tokens BIGINT NOT NULL,
  spent_tokens BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, agent_id, period)
);

CREATE TABLE usage_records (        -- one row per LLM call
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  agent_id UUID NOT NULL REFERENCES agents(id),
  task_id UUID REFERENCES tasks(id),
  provider TEXT NOT NULL, model TEXT NOT NULL,
  tokens_in BIGINT NOT NULL, tokens_out BIGINT NOT NULL,
  cost_micro_usd BIGINT,                       -- provider price × tokens, micro-USD
  idempotency_key TEXT UNIQUE,                 -- taskId:attempt — dup-call guard
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE artifacts (            -- agent output
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  task_id UUID NOT NULL REFERENCES tasks(id),
  kind TEXT NOT NULL,               -- 'text'|'diff'|'file'|'report'|'json'
  content TEXT,                     -- inline for small; S3 key for large (Phase 3)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## V2 — Communication & office (Phase 2)

```sql
CREATE TABLE channels (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  name TEXT NOT NULL,                          -- 'general','announcements',…
  kind TEXT NOT NULL DEFAULT 'channel' CHECK (kind IN ('channel','dm')),
  UNIQUE (company_id, name)
);

CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  channel_id UUID NOT NULL REFERENCES channels(id),
  sender TEXT NOT NULL,                        -- 'user:<id>'|'agent:<id>'|'bot'
  text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_channel ON messages(channel_id, created_at DESC);

CREATE TABLE announcements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  title TEXT NOT NULL, body TEXT,
  category TEXT NOT NULL DEFAULT 'company',    -- company|update|maintenance
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE office_layout (        -- status→location map + desk assignments (data, not code)
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  location_key TEXT NOT NULL,       -- 'desk_1','meeting_room_alpha','focus_pod_2','cafe','help_desk'
  tile_x INT NOT NULL, tile_y INT NOT NULL,
  assigned_agent_id UUID REFERENCES agents(id),  -- for desks
  UNIQUE (company_id, location_key)
);

CREATE TABLE agent_stats_daily (    -- analytics rollup (nightly + on-approve)
  company_id UUID NOT NULL,
  agent_id UUID NOT NULL REFERENCES agents(id),
  skill TEXT NOT NULL,               -- M2.3: task.required_skill at completion time —
                                      -- added to the PK so "Top Skills Used" is a plain
                                      -- GROUP BY on this table, no cross-module read of tasks
  day DATE NOT NULL,
  tasks_completed INT NOT NULL DEFAULT 0,
  tasks_approved INT NOT NULL DEFAULT 0,
  tasks_rejected INT NOT NULL DEFAULT 0,
  focus_minutes INT NOT NULL DEFAULT 0,   -- always 0 until a presence source exists (M2.4c+)
  tokens_spent BIGINT NOT NULL DEFAULT 0,
  cost_micro_usd BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (company_id, agent_id, skill, day)
);
CREATE INDEX idx_agent_stats_daily_company_day ON agent_stats_daily(company_id, day);
-- success_rate = approved / (approved+rejected); Focus Time = Σ focus_minutes (unpopulated for now).
-- Written two ways (M2.3, 17 §M2.3): StatsRollupWorker (durable outbox consumer on
-- task.completed|approved|rejected) increments counts+tokens+cost incrementally as
-- events arrive; a nightly StatsRollupReconciliationJob fully recomputes the trailing
-- 2 days from raw outbox_events (+ usage_records for cost) as a drift-correcting
-- safety net, same spirit as OutboxRetentionJob/MemoryTtlArchiver's nightly jobs.
-- Per-task cost (the Budget Ledger's "cost per task" table) is NOT derived from this
-- rollup — it queries usage_records directly (grouped by task_id), since a per-task
-- number can't come from a table keyed one row per (agent, skill, day).
```

## V3 — Multi-tenant (Phase 3)

> **Split 2026-07-17 (07 Rev D):** this section used to be "Multi-tenant & billing" and shipped RLS + `billing_accounts` as one migration. Monetization is deferred until after the pilot, so the two halves are now separate: **RLS is live work (M3.2, next up)**; **`billing_accounts` is post-pilot backlog (M3.3)** and must not be created by M3.2's migration. See 15 §0 for the migration rows.

```sql
-- auth: credentials/OAuth identities on users; sessions or JWT (no table if stateless JWT)
--   → shipped at M3.1 (V8__users_auth.sql): users.password_hash, stateless JWT, no session table.

-- M3.2 (V10__tenant_rls.sql, done) — the whole of V3 as it stands today:
-- ALTER tables to enable + FORCE Postgres ROW LEVEL SECURITY with a
-- company_id = current_setting('app.company_id', true)::uuid policy (plus a
-- current_setting('app.bypass_rls', true)='on' escape hatch for the handful
-- of genuinely cross-tenant system components — see 08 §Security rule 6 for
-- the full mechanism and the dev/test superuser-bypass caveat).
--   Strict equality (company_id NOT NULL): users, agents, tasks, task_events,
--     budgets, usage_records, artifacts, outbox_events, task_decompositions,
--     memories, knowledge_docs, knowledge_chunks, agent_stats_daily, channels,
--     messages, announcements.
--   NULL-or-match (company_id nullable = global template): role_definitions, skills.
--   Special: companies (policy on id, not company_id); subtasks (no company_id
--     column — policy is an EXISTS join through tasks); role_definition_skills/
--     agent_skills/role_definition_knowledge (join tables, no company_id column —
--     EXISTS join through the owning agents/role_definitions row).
--   Deliberately NOT under RLS: model_catalog (fully global, no company_id at
--     all), event_consumers (a system cursor table, no company_id).
```

### Deferred — billing (M3.3, post-pilot)

Not part of V3's migration. Reproduced here so the shape isn't lost; create it only when M3.3 is picked up.

```sql
CREATE TABLE billing_accounts (
  company_id UUID PRIMARY KEY REFERENCES companies(id),
  stripe_customer_id TEXT UNIQUE NOT NULL,
  stripe_subscription_id TEXT,
  status TEXT NOT NULL DEFAULT 'active'
);
```

`companies.plan_tier` (V1, applied) is **not** part of this deferral — it exists, defaults to `'solo'`, is displayed read-only on the Settings page, and gates nothing. Wiring it to entitlements is M3.3's job. Likewise `budgets`/`usage_records`/`agent_stats_daily` are **not** billing: they meter tokens for control and accountability, which the pilot needs, and they stay live.

## The claim query (canonical — copy exactly; amended at M0.4 per 17 §M0.4)

```sql
UPDATE tasks SET status='claimed', assigned_agent_id=$agentId,
  claimed_at=now(), lease_expires_at=now() + interval '10 minutes',
  attempt = attempt + 1                          -- M0.4: usage idempotency = taskId:attempt
WHERE id = (
  SELECT id FROM tasks
  WHERE company_id=$companyId AND required_skill=$skill AND status='queued'
    AND NOT EXISTS (SELECT 1 FROM agents a
                    WHERE a.id=$agentId AND a.paused)  -- M0.4: paused agents never claim
  ORDER BY priority, created_at
  FOR UPDATE SKIP LOCKED
  LIMIT 1
)
RETURNING *;
```

The Worker API endpoint `POST /tasks/{id}/claim` (04) claims one **named** task:
the inner SELECT swaps `required_skill=$skill … ORDER BY … LIMIT 1` for
`id=$taskId`, keeping every other clause (status guard, paused guard,
`FOR UPDATE SKIP LOCKED`, the SET list) verbatim. The skill-ordered form is the
runner's claim-next (M0.5b). `BudgetGuard.canSpend` runs in the same service
transaction as either form. **M-CTX1:** the skill-ordered form also runs the
`ContextAssembler` (14 §6) inside this same transaction, right after the claim
and before its `task_events(claimed)` row is written, so `contextProvenance`
lands in that row's payload — see 14 §6's M-CTX1 note for why (append-only
`task_events`, no retrofit possible after the fact).

**Lease reclaim job** (every minute): `UPDATE tasks SET status='queued', assigned_agent_id=NULL WHERE status IN ('claimed','in_progress') AND lease_expires_at < now()` + `task_events(requeued)`. Workers renew the lease while actively working. The `usage_records.idempotency_key` (`taskId:attemptNo`) guarantees a redelivered task can't double-bill.

## Invariants (enforce in code + tests)
1. `task_events` is append-only.
2. `tasks.status='approved'` requires an `approved` event with a `user:` or supervising `agent:` actor.
3. `spent_tokens` only increases via usage_records inserts (same transaction).
4. No repository method exists without a `company_id` parameter or tenant filter.
5. A task with open subtasks or open child tasks cannot be approved.
