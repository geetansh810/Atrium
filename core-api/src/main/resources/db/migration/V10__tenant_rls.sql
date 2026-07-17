-- M3.2: Postgres Row Level Security, the second tenant-isolation enforcement
-- layer (application-level company_id scoping, present since M0.1, is the
-- first). billing_accounts is deliberately NOT created here — split out and
-- deferred post-pilot with M3.3 (07 Rev D, 03 §V3).
--
-- Two session GUCs drive every policy below, both set once per transaction by
-- app.atrium.common.TenantAwareJpaTransactionManager (08 §Security rule 6):
--   app.company_id  — the bound tenant; current_setting(..., true) returns ''
--                      (NULLIF'd to NULL below) when nothing is bound, so an
--                      unbound transaction sees zero rows — fail-closed.
--   app.bypass_rls  — 'on' only inside TenantContext.runWithBypass(...), used
--                      by the small set of components that are genuinely
--                      cross-tenant by design (background jobs sweeping every
--                      company's queue/outbox, and the pre-auth signup/login
--                      path). See 08 §Security for the exact list.

-- A least-privilege runtime role for the app's own connection. RLS never
-- applies to a superuser regardless of FORCE, and the official postgres
-- image's POSTGRES_USER (this migration's own connecting role, both in
-- docker-compose and Testcontainers) IS the cluster's bootstrap role — and
-- Postgres refuses to ever strip SUPERUSER from the bootstrap role, even by
-- its own hand ("the bootstrap user must have the SUPERUSER attribute"),
-- confirmed against this exact image. So demoting the connecting role isn't
-- possible; instead, Flyway/migrations keep running as the bootstrap role
-- (DDL, extensions, and this GRANT all need it), and the app's own
-- spring.datasource connects as this new atrium_app role instead — wired in
-- application.yml (spring.datasource.*) and IntegrationTestBase (tests).
-- AWS RDS's master user is never a true superuser, so this split matters for
-- local dev/CI correctness, not RDS — M3.5's real deployment enforces RLS
-- for its master user with zero extra role plumbing.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'atrium_app') THEN
    -- Dev-only fixed password, same posture as the atrium/atrium bootstrap
    -- creds already in docker-compose.yml — ATRIUM_APP_DB_PASSWORD MUST be
    -- set to a real secret in any shared/staging/prod environment (08 §Config).
    CREATE ROLE atrium_app LOGIN PASSWORD 'atrium_app'
      NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO atrium_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO atrium_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO atrium_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO atrium_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO atrium_app;

-- ── Strict: company_id NOT NULL, direct equality ───────────────────────────
CREATE OR REPLACE FUNCTION _rls_strict_using() RETURNS TEXT AS $$
  SELECT $q$(
    current_setting('app.bypass_rls', true) = 'on'
    OR company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
  )$q$;
$$ LANGUAGE sql IMMUTABLE;

DO $$
DECLARE
  t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'users', 'agents', 'tasks', 'task_events', 'budgets', 'usage_records',
    'artifacts', 'outbox_events', 'task_decompositions', 'memories',
    'knowledge_docs', 'knowledge_chunks', 'agent_stats_daily', 'channels',
    'messages', 'announcements'
  ]
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON %I USING (%s) WITH CHECK (%s)',
      t, _rls_strict_using(), _rls_strict_using());
  END LOOP;
END $$;

DROP FUNCTION _rls_strict_using();

-- ── Nullable: company_id NULL = global template, visible to every tenant ──
DO $$
DECLARE
  t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['role_definitions', 'skills']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    -- Read: bypass, OR a global row, OR this tenant's own row.
    -- Write: bypass, OR this tenant's own row — never company_id IS NULL, so
    -- an ordinary tenant transaction can never insert/update a global template
    -- (only migrations/bypass can); this tightens the existing app-level rule
    -- (SkillService/attachToRole et al. already reject attaching to a global
    -- template from a single company's request) with a DB-enforced backstop.
    EXECUTE format($f$
      CREATE POLICY tenant_isolation ON %I
        USING (
          current_setting('app.bypass_rls', true) = 'on'
          OR company_id IS NULL
          OR company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
        )
        WITH CHECK (
          current_setting('app.bypass_rls', true) = 'on'
          OR company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
        )
    $f$, t);
  END LOOP;
END $$;

-- ── companies: policy on id (the tenant's own PK), not a company_id column ─
ALTER TABLE companies ENABLE ROW LEVEL SECURITY;
ALTER TABLE companies FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON companies
  USING (
    current_setting('app.bypass_rls', true) = 'on'
    OR id = NULLIF(current_setting('app.company_id', true), '')::uuid
  )
  WITH CHECK (
    current_setting('app.bypass_rls', true) = 'on'
    OR id = NULLIF(current_setting('app.company_id', true), '')::uuid
  );

-- ── subtasks: no company_id column — scope via the owning task ────────────
ALTER TABLE subtasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE subtasks FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON subtasks
  USING (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM tasks t
      WHERE t.id = subtasks.task_id
        AND t.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
    )
  )
  WITH CHECK (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM tasks t
      WHERE t.id = subtasks.task_id
        AND t.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
    )
  );

-- ── Join tables: no company_id column — scope via the owning row ──────────
ALTER TABLE role_definition_skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_definition_skills FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role_definition_skills
  USING (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM role_definitions rd
      WHERE rd.id = role_definition_skills.role_definition_id
        AND (rd.company_id IS NULL
             OR rd.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid)
    )
  )
  WITH CHECK (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM role_definitions rd
      WHERE rd.id = role_definition_skills.role_definition_id
        AND (rd.company_id IS NULL
             OR rd.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid)
    )
  );

ALTER TABLE agent_skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_skills FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON agent_skills
  USING (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM agents a
      WHERE a.id = agent_skills.agent_id
        AND a.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
    )
  )
  WITH CHECK (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM agents a
      WHERE a.id = agent_skills.agent_id
        AND a.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid
    )
  );

ALTER TABLE role_definition_knowledge ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_definition_knowledge FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role_definition_knowledge
  USING (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM role_definitions rd
      WHERE rd.id = role_definition_knowledge.role_definition_id
        AND (rd.company_id IS NULL
             OR rd.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid)
    )
  )
  WITH CHECK (
    current_setting('app.bypass_rls', true) = 'on'
    OR EXISTS (
      SELECT 1 FROM role_definitions rd
      WHERE rd.id = role_definition_knowledge.role_definition_id
        AND (rd.company_id IS NULL
             OR rd.company_id = NULLIF(current_setting('app.company_id', true), '')::uuid)
    )
  );

-- Deliberately NOT under RLS: model_catalog (fully global, no company_id at
-- all) and event_consumers (a system cursor table, no company_id).
