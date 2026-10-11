# 6 · Data model

Postgres 16 with the `vector` extension. Flyway owns the schema ([`db/migration/`](../../core-api/src/main/resources/db/migration/), `Hibernate ddl-auto: validate`). The contract is [`03-data-model.md`](../03-data-model.md) plus the later additions in [`15-data-model-delta.md`](../15-data-model-delta.md); this page groups the tables by concern. Conventions: snake_case columns, UUID keys, `company_id` on every tenant row.

## Tables by concern

| Concern | Tables | Notes |
|---|---|---|
| **Tenancy & people** | `companies`, `users` | `users.password_hash` is BCrypt; `companies.plan_tier` is display-only |
| **Agents** | `agents`, `role_definitions`, `model_catalog` | `agents.skill_tags text[]`, `runtime_type`, `runtime_config jsonb`, `paused`; `role_definitions` are versioned `UNIQUE(company_id,key,version)`, `company_id NULL` = global template, `review_required`, `allowed_tools` |
| **Work** | `tasks`, `subtasks`, `artifacts`, `task_decompositions` | `tasks` carries lease/attempt/parent/billing-chain columns; `task_decompositions(parent_task_id, plan_artifact_id)` is the exact-once fan-out fingerprint; `subtasks` has no `company_id` (scoped through its task) |
| **Audit** | `task_events` | **Append-only.** One row per state change, same transaction. |
| **Money** | `budgets`, `usage_records`, `agent_stats_daily` | `budgets` UNIQUE `(company, agent, period)`, `agent_id NULL` = company-wide; `usage_records.idempotency_key` UNIQUE; `agent_stats_daily` PK `(company, agent, skill, day)` |
| **Debugging** | `llm_request_logs` | Full prompt/response trace, includes failures; purged by `LlmLogRetentionJob` (default 30 days). Not billing. |
| **Event backbone** | `outbox_events`, `event_consumers` | Outbox rows have a BIGINT identity cursor; `event_consumers` holds each durable consumer's position |
| **Mind** | `skills`, `role_definition_skills`, `agent_skills`, `memories`, `knowledge_docs`, `knowledge_chunks`, `role_definition_knowledge` | `memories.embedding` and `knowledge_chunks.embedding` are `vector(1536)`, nullable |
| **Communication** | `channels`, `messages`, `announcements` | `channels` UNIQUE `(company, name)` |

## Migrations

| Version | Adds |
|---|---|
| V1 | Core tables (companies … artifacts) |
| V2 / V2_1 | Agent-platform tables (outbox, skills, memories, knowledge, model catalog) / seed: coder, tester, research templates and Anthropic models |
| V3 | `budgets.alerted_at` |
| V4, V13 | Google Gemini model catalog rows |
| V5 | Platform skills seed |
| V6 | `agent_stats_daily` |
| V7 | Communication tables |
| V8 | `users.password_hash` (real auth) |
| V9 | `lead`, `product`, `content` templates |
| V10 | Row Level Security + the `atrium_app` runtime role |
| V11 / V12 | `role_definitions.review_required` / `legal`, `hr` templates |
| V14 | `llm_request_logs` |
| V15 | A seeded admin account for a fresh database |

Never edit an applied migration; add a new one. Version numbers are reassigned by actual application order — the contract docs' planned numbers have drifted.

## Invariants (enforced in code and tests)

From [`03-data-model.md`](../03-data-model.md) §Invariants:

1. `task_events` is append-only.
2. `approved` requires an `approved` event with a user or supervising-agent actor.
3. `spent_tokens` only increases through `usage_records` inserts, in the same transaction.
4. No repository method exists without a `company_id` parameter or tenant filter.
5. A task with open subtasks or child tasks cannot be approved.
6. A task whose assigned agent's role is `review_required` cannot be approved by anything but a named human.

## Two traps worth knowing

- **Raw JDBC and JPA don't share a cache.** Memory/knowledge writes go through raw SQL (the vector column has no Hibernate mapping). Reading the same row back through JPA in the same transaction returns the stale snapshot unless you `refresh` it, and a raw insert referencing a JPA-saved parent needs an explicit `flush()` first.
- **Tables without a `company_id` column** (`subtasks`, the join tables) are scoped by joining to their parent, and their RLS policies are `EXISTS`-joins ([08](08-security-tenancy.md)).

*Verified against:* the migration files, [`routing/domain/`](../../core-api/src/main/java/app/atrium/routing/domain/), [`agentmind/domain/`](../../core-api/src/main/java/app/atrium/agentmind/domain/).
