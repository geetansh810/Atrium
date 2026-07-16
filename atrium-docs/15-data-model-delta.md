# 15 — Data Model Delta (agent platform)

Additive companion to `03-data-model.md` — V1 SQL there is untouched and still canonical. Nothing is applied yet (no DB until M0.1), so migration numbers are **reassigned by application order** (§0). Same conventions: UUID pks, TIMESTAMPTZ, TEXT+CHECK (except where noted why), `company_id` + index on every business table.

## 0. Migration plan (supersedes the V-numbers in 03 §headings)

| Flyway file | Contents | Applied at |
|---|---|---|
| `V1__core.sql` | 03 §V1 verbatim + §1 ALTERs below (same file — V1 has never been applied) | M0.1 |
| `V2__agent_platform.sql` | §§2–4 below: outbox, orchestration, model catalog, skills, memories, knowledge. Requires `CREATE EXTENSION IF NOT EXISTS vector;` → compose image becomes `pgvector/pgvector:pg16` | M0.1 |
| `V2_1__seed.sql` | seed global role templates + model catalog | M0.2 |
| `V3__budget_alerted_at.sql` | `budgets.alerted_at` addendum | M0.7 |
| `V4__google_model_catalog.sql` | Google/Gemini model_catalog rows | session 6i |
| `V5__seed_platform_skills.sql` | seeded platform skills + role attach | M-SK1 |
| `V6__agent_stats_daily.sql` | 03 §V2's `agent_stats_daily` only (the `skill` column M2.3 added) — split out of the old "V3__communication_office.sql" bundle since channels/messages/announcements/office_layout aren't needed until M2.4c/M2.5 | M2.3 |
| `V7__communication.sql` | 03 §V2's channels, messages, announcements only — **`office_layout` dropped** (office track retired 2026-07-15, M2.4a; the Team view derives zones from live task state, no desk map). Renamed from the old "V7__communication_office" bundle | M2.5 |
| `V8__users_auth.sql` | `users.password_hash` (03 §V1 amendment, M3.1 signup/login — BCrypt, `NOT NULL DEFAULT ''` then default dropped so pre-M3.1 dev rows don't block the migration) | M3.1 |
| `V9__seed_starter_roster_templates.sql` | 3 more global `role_definitions` (`lead`/`product`/`content`, generic prompts — company_id NULL, don't collide with the company-owned same-key rows from sessions 18/20) for the onboarding wizard's starter packs; `lead`/`product` get the M2.2 `create_child_tasks` tool | M3.4 |
| `V10__multitenant_billing.sql` (future) | 03 §V3 (RLS, billing_accounts) | M3.x |

This table is reassigned by actual application order each session (real Flyway files always win over what's written here — see `core-api/src/main/resources/db/migration/`). Rule unchanged: never edit an applied migration.

## 1. ALTERs folded into V1 (additive columns on 03 tables)

```sql
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
```

**M0.7 addendum (`V3__budget_alerted_at.sql`, applied after V1/V2):** `alert_pct` above shipped in V1 ahead of the enforcement code; M0.7 adds `budgets.alerted_at TIMESTAMPTZ` (nullable) for the once-per-period dedupe named above — the natural key is already `(company_id, agent_id, period)` UNIQUE, so a new period gets a new row and `alerted_at` resets for free.

Semantics: `billing_task_id` = self for user-created roots, copied from parent on child creation. `paused=true` ⇒ runtime stopped + claims refused (checked in the claim query's WHERE via join, see 17 M0.4 card). Frontend `types.ts` gains these fields (optional, defaulted — mock data unaffected).

## 2. Event backbone (12 §3)

```sql
CREATE TABLE outbox_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,          -- ordered cursor key (deliberately not UUID)
  company_id UUID NOT NULL,
  topic TEXT NOT NULL,                                         -- taxonomy 12 §4
  event_type TEXT NOT NULL,                                    -- dot.case
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ                                     -- NULL = pending relay
);
CREATE INDEX idx_outbox_pending ON outbox_events(id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_company ON outbox_events(company_id, id);

CREATE TABLE event_consumers (
  consumer_name TEXT PRIMARY KEY,                              -- 'learning_pipeline','stats_rollup'
  last_event_id BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Retention: nightly delete of published rows older than 14 days AND below every consumer cursor (`task_events` remains the permanent audit; outbox is transport).

## 3. Orchestration safety (12 §6)

```sql
CREATE TABLE task_decompositions (               -- exact-once child fan-out per accepted plan
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  parent_task_id UUID NOT NULL REFERENCES tasks(id),
  plan_artifact_id UUID NOT NULL REFERENCES artifacts(id),
  created_by TEXT NOT NULL,                      -- 'agent:<id>'|'user:<id>'
  child_task_ids UUID[] NOT NULL DEFAULT '{}',   -- durable result
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (parent_task_id, plan_artifact_id)      -- the fingerprint
);
```

**M2.2 build note:** `plan_artifact_id` is the parent task's own completion artifact (`TaskService#complete` already writes one) — not a separate "decomposition_plan" artifact kind. `TaskService#decompose` does a raw `INSERT ... ON CONFLICT (parent_task_id, plan_artifact_id) DO NOTHING`; `rows==0` means the fingerprint already existed (some earlier call processed this exact artifact) and the call becomes a no-op returning the existing `child_task_ids`, never a second fan-out. The one built-in tool this enables, `create_child_tasks` (execution.ChildTaskTool), is offered to a role only when its `role_definitions.allowed_tools` JSON array lists it — a data-driven gate, no role-key branching in routing/execution. It's single-shot: the model either returns text or one tool call per attempt, never a multi-turn back-and-forth (that would need the assistant's `tool_use` content block replayed into history, which `execution.spi.LlmMessage`'s plain-string content can't carry — deferred until a real multi-turn use case needs it).

`task_blockers(task_id, blocked_by_task_id)` for cross-tree dependencies remains a sketch, NOT built at M2.2 — the milestone's Done-when only needed parent/child fan-out plus the approval gate (already covering open child tasks since M0.6) and the `/tasks/{id}/flow` graph, none of which need cross-tree blockers. Parent/child stays structural (Paperclip separation); build `task_blockers` only when a real cross-tree dependency use case shows up.

## 4. Agent platform tables

### 4.0 Model catalog (13 §1.2)

```sql
CREATE TABLE model_catalog (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider TEXT NOT NULL, model_name TEXT NOT NULL,
  display_name TEXT NOT NULL,
  context_window INT NOT NULL, max_output_tokens INT NOT NULL,
  price_in_micro_usd_per_mtok  BIGINT NOT NULL,
  price_out_micro_usd_per_mtok BIGINT NOT NULL,
  capabilities JSONB NOT NULL DEFAULT '[]',      -- ["tools","vision","json_mode","embeddings"]
  tier TEXT NOT NULL CHECK (tier IN ('fast','balanced','deep')),
  enabled BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, model_name)
);
-- Seed (V2, verify prices at implementation): anthropic claude-fable-5 (deep) / claude-sonnet-5 (balanced)
-- / claude-haiku-4-5 (fast); openai + google rows when those providers land. Global table: no company_id.
```

### 4.1 Skills (14 §1)

```sql
CREATE TABLE skills (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID REFERENCES companies(id),      -- NULL = global platform skill
  key TEXT NOT NULL, version INT NOT NULL DEFAULT 1,
  name TEXT NOT NULL, description TEXT NOT NULL,
  body_md TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('procedure','reference','tool_guide','policy')),
  tags TEXT[] NOT NULL DEFAULT '{}',
  trust_level TEXT NOT NULL DEFAULT 'company'
    CHECK (trust_level IN ('platform','company','agent_proposed')),
  source TEXT NOT NULL DEFAULT 'authored',       -- 'authored'|'template'|'promoted_memory'
  created_by TEXT NOT NULL,                      -- 'user:<id>'|'agent:<id>'|'system'
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, key, version)
);
CREATE INDEX idx_skills_company ON skills(company_id);

CREATE TABLE role_definition_skills (
  role_definition_id UUID NOT NULL REFERENCES role_definitions(id),
  skill_id UUID NOT NULL REFERENCES skills(id),  -- pins a specific version
  position INT NOT NULL DEFAULT 0,
  PRIMARY KEY (role_definition_id, skill_id)
);

CREATE TABLE agent_skills (
  agent_id UUID NOT NULL REFERENCES agents(id),
  skill_id UUID NOT NULL REFERENCES skills(id),
  source TEXT NOT NULL DEFAULT 'assigned' CHECK (source IN ('hired','assigned','learned')),
  proficiency SMALLINT NOT NULL DEFAULT 3 CHECK (proficiency BETWEEN 1 AND 5),
  attached_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (agent_id, skill_id)
);
```

### 4.2 Memories (14 §2)

```sql
CREATE TABLE memories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  scope TEXT NOT NULL CHECK (scope IN ('agent','role','company','task')),
  agent_id UUID REFERENCES agents(id),           -- required when scope='agent'
  role_key TEXT,                                 -- required when scope='role'
  task_id UUID REFERENCES tasks(id),             -- required when scope='task'; provenance otherwise
  kind TEXT NOT NULL CHECK (kind IN ('fact','preference','lesson','summary')),
  content TEXT NOT NULL,
  embedding vector(1536),                        -- dim locked to atrium.embeddings.model (13 §2)
  importance SMALLINT NOT NULL DEFAULT 1 CHECK (importance BETWEEN 1 AND 5),
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','pending_review','rejected','archived')),
  provenance JSONB NOT NULL DEFAULT '{}',
  source_event_id BIGINT,                        -- outbox_events.id that triggered extraction
  use_count INT NOT NULL DEFAULT 0,
  last_used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (scope <> 'agent' OR agent_id IS NOT NULL),
  CHECK (scope <> 'role'  OR role_key IS NOT NULL),
  CHECK (scope <> 'task'  OR task_id  IS NOT NULL)
);
CREATE INDEX idx_memories_company_scope ON memories(company_id, scope, status);
CREATE INDEX idx_memories_agent ON memories(agent_id) WHERE agent_id IS NOT NULL;
CREATE INDEX idx_memories_embedding ON memories
  USING hnsw (embedding vector_cosine_ops);      -- pgvector HNSW; fine at v1 scale
```

### 4.3 Knowledge (14 §3)

```sql
CREATE TABLE knowledge_docs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  title TEXT NOT NULL, source_uri TEXT, mime TEXT NOT NULL DEFAULT 'text/markdown',
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('ingesting','active','archived','failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kdocs_company ON knowledge_docs(company_id);

CREATE TABLE knowledge_chunks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  doc_id UUID NOT NULL REFERENCES knowledge_docs(id) ON DELETE CASCADE,
  seq INT NOT NULL,
  content TEXT NOT NULL,
  embedding vector(1536),
  UNIQUE (doc_id, seq)
);
CREATE INDEX idx_kchunks_embedding ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE role_definition_knowledge (
  role_definition_id UUID NOT NULL REFERENCES role_definitions(id),
  doc_id UUID NOT NULL REFERENCES knowledge_docs(id),
  PRIMARY KEY (role_definition_id, doc_id)
);
```

## 5. New/updated invariants (extends 03 §Invariants — enforce in code + tests)

6. Every outbox row is written in the same transaction as the change it describes.
7. Only `status='active'` memories and `trust_level IN ('platform','company')` skills may be read by ContextAssembler (12 hard rule 4).
8. `usage_records.idempotency_key` namespaces: `‹taskId›:‹attempt›` (task work), `learn:‹eventId›` (extraction), `embed:‹sha256›:‹model›` (embeddings). One namespace per writer.
9. `tasks.attempt` increments exactly and only in the claim UPDATE (the canonical query gains `attempt = attempt + 1` — see 17 M0.4 card; 03's query text is amended there).
10. A `task_decompositions` insert precedes child-task creation in the same transaction (exact-once fan-out).
11. Memories/skills/knowledge never contain secret material — same grep-gate as prompts (09 checklist).
