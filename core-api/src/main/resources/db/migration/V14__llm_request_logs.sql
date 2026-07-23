-- LLM request log: one row per LLM call that leaves the app, captured at the
-- single doorway every provider goes through (LlmRouter.complete). Distinct
-- from usage_records, which is the billing ledger (one row per SUCCESSFUL,
-- non-deduped attempt, keyed taskId:attempt); this table is the full-fidelity
-- request/response trace — the exact system prompt, message history, tools,
-- and returned text — for debugging and observability, including calls that
-- errored (which usage_records never records) and the background
-- learning-extraction calls that carry no task at all.
--
-- Written best-effort in its own REQUIRES_NEW transaction: a logging failure
-- must never break the LLM call it describes, and this table is deliberately
-- NOT part of the billing/audit invariant chain.

CREATE TABLE llm_request_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  agent_id UUID,                              -- null for calls not tied to an agent (e.g. learning extraction)
  task_id UUID,                               -- null when no task is in scope
  provider TEXT NOT NULL,
  model TEXT NOT NULL,
  system_prompt TEXT,                         -- the assembled system prompt (secrets never reach here — 05 §PromptAssembler)
  messages JSONB NOT NULL,                    -- full LlmMessage[] sent to the provider
  tools JSONB,                                -- LlmToolDef[] offered, if any
  response_text TEXT,                         -- assistant text returned (null on error / pure tool call)
  tool_calls JSONB,                           -- LlmToolCall[] returned, if any
  tokens_in BIGINT,
  tokens_out BIGINT,
  stop_reason TEXT,                           -- 'end'|'max_tokens'|'tool_use'|'filtered' (null on error)
  status TEXT NOT NULL,                       -- 'ok' | 'error'
  error_kind TEXT,                            -- LlmException.Kind name when status='error'
  error_message TEXT,
  latency_ms INTEGER NOT NULL,                -- wall time of the whole complete() call, incl. retries
  provider_request_id TEXT,                   -- provider-side trace id, "" if absent
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Newest-first keyset listing, company-scoped (matches the (created_at, id) cursor).
CREATE INDEX idx_llm_request_logs_company_created
  ON llm_request_logs (company_id, created_at DESC, id DESC);

-- Row Level Security, strict company_id equality — same shape as every
-- NOT-NULL-company_id table set up in V10 (M3.2). The app connects as the
-- NOBYPASSRLS atrium_app role; the write happens inside the agent loop's
-- TenantContext.runAsSystem, which binds app.company_id, so the WITH CHECK
-- passes for the acting tenant and no other.
ALTER TABLE llm_request_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE llm_request_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON llm_request_logs
  USING (
    current_setting('app.bypass_rls', true) = 'on'
    OR company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
  )
  WITH CHECK (
    current_setting('app.bypass_rls', true) = 'on'
    OR company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
  );
