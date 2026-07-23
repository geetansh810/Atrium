-- Seed a built-in admin account so every freshly instantiated DB comes with a
-- working login out of the box (owner-requested). Runs once, on first apply of
-- this migration, on any new database.
--
-- The password hash is produced by pgcrypto's crypt()/gen_salt('bf', 10), which
-- emits a standard $2a$10$ BCrypt hash — byte-for-byte the format Spring's
-- BCryptPasswordEncoder issues at signup and verifies at login (AuthService),
-- so this row logs in through the ordinary /auth/login path with no special
-- casing. cost=10 matches Spring's default strength.
--
-- Flyway's own connection already sets app.bypass_rls='on' (application.yml
-- init-sqls), so these inserts pass V10's RLS WITH CHECK on a managed,
-- non-superuser Postgres. gen_random_uuid()/created_at come from column
-- defaults (V1__core.sql).
--
-- ON CONFLICT DO NOTHING makes this defensive: if the slug/email already exist
-- (e.g. someone signed up with them first), the migration is a no-op instead of
-- failing. The company lookup resolves the id whether it was just inserted or
-- already present.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH inserted AS (
  INSERT INTO companies (name, slug, plan_tier)
  VALUES ('Agrawal Corporations', 'agrawal-corporations', 'solo')
  ON CONFLICT (slug) DO NOTHING
  RETURNING id
),
company AS (
  SELECT id FROM inserted
  UNION ALL
  SELECT id FROM companies WHERE slug = 'agrawal-corporations'
  LIMIT 1
)
INSERT INTO users (company_id, display_name, email, role, password_hash)
SELECT id,
       'Geetansh Agrawal',
       'geetansh@admin.com',
       'admin',
       crypt('Geetu@54321', gen_salt('bf', 10))
FROM company
ON CONFLICT (email) DO NOTHING;
