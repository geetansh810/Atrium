-- V1 — Core (03-data-model.md §V1 verbatim, plus 15 §1 ALTERs folded in below).
-- All IDs UUID DEFAULT gen_random_uuid(). All timestamps TIMESTAMPTZ.
-- Every business table has company_id (tenant key) and an index on it.
-- Enums are TEXT + CHECK constraints.

CREATE TABLE companies (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  slug TEXT UNIQUE NOT NULL,
  plan_tier TEXT NOT NULL DEFAULT 'solo',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (               -- human members (minimal until Phase 3 auth)
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  display_name TEXT NOT NULL,
  email TEXT UNIQUE NOT NULL,
  role TEXT NOT NULL DEFAULT 'admin' CHECK (role IN ('admin','member')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
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

-- ── 15 §1 ALTERs folded into V1 (additive agent-platform columns) ──────────

ALTER TABLE agents
  ADD COLUMN runtime_type  TEXT  NOT NULL DEFAULT 'llm_loop',  -- no CHECK: validated vs RuntimeRegistry (13 §3.1)
  ADD COLUMN runtime_config JSONB NOT NULL DEFAULT '{}',
  ADD COLUMN paused        BOOLEAN NOT NULL DEFAULT false;     -- board/budget pause (distinct from status, which is presence)

ALTER TABLE tasks
  ADD COLUMN attempt          INT  NOT NULL DEFAULT 0,         -- ++ on every claim; usage idempotency = taskId:attempt
  ADD COLUMN billing_task_id  UUID REFERENCES tasks(id),       -- root of the request chain (cost attribution, 12 §6)
  ADD COLUMN request_depth    INT  NOT NULL DEFAULT 0;         -- delegation hops from root

ALTER TABLE budgets
  ADD COLUMN alert_pct SMALLINT NOT NULL DEFAULT 80;           -- soft-alert tier → budget.threshold event (once per period)
