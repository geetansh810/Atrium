-- V2 — Agent platform (15-data-model-delta.md §§2–4 verbatim).
-- Requires the pgvector extension → compose/test image is pgvector/pgvector:pg16.

CREATE EXTENSION IF NOT EXISTS vector;

-- ── §2 Event backbone (12 §3) ──────────────────────────────────────────────

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

-- ── §3 Orchestration safety (12 §6) ────────────────────────────────────────

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

-- ── §4.0 Model catalog (13 §1.2) ───────────────────────────────────────────

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
-- Seed lands at M0.2 (V2_1__seed.sql): anthropic claude-fable-5 (deep) / claude-sonnet-5 (balanced)
-- / claude-haiku-4-5 (fast); openai + google rows when those providers land. Global table: no company_id.

-- ── §4.1 Skills (14 §1) ────────────────────────────────────────────────────

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

-- ── §4.2 Memories (14 §2) ──────────────────────────────────────────────────

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

-- ── §4.3 Knowledge (14 §3) ─────────────────────────────────────────────────

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
