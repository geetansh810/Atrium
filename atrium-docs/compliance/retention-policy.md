# Data retention policy

**Status:** internal engineering policy note, written 2026-07-17 (M4.3, `07-milestones.md` Phase 4). Describes what Atrium's own code and infrastructure actually do today — not a customer-facing legal document, and not reviewed by counsel. Where nothing enforces a stated period, that is said plainly rather than implied.

## What's retained, and for how long

| Data | Retained | Mechanism |
|---|---|---|
| `task_events` (the audit trail — created/claimed/progress/completed/approved/rejected/…) | Indefinitely. Never deleted, never updated (append-only by construction — `03-data-model.md` invariant 1). | No job purges this table. This is deliberate: it's the record M4.2's compliance audit trail depends on. |
| `artifacts` (task output) | Indefinitely, alongside their task. | No archival/purge job exists. |
| `usage_records` (token spend, billing/accountability ledger) | Indefinitely. | No purge job; `spent_tokens` only ever increases (invariant 3). |
| `outbox_events` (internal event backbone) | 14 days after publish, and only once every durable consumer's cursor has passed them. | `eventbus.OutboxRetentionJob`, nightly. This is infrastructure plumbing, not a compliance-relevant record — the durable consumers that read it (`LearningPipeline`, `StatsRollupWorker`, `ChatNoticePipeline`) have already turned anything that matters into a `task_events` row, a memory, a rollup, or a chat message by the time it's purged. |
| `memories` — `lesson`/`summary` scope | 90 days from last use (`COALESCE(last_used_at, created_at)`), then archived (not deleted). | `agentmind.MemoryTtlArchiver`, nightly. Config: `atrium.memory.ttl-days` / `ATRIUM_MEMORY_TTL_DAYS`, default 90. |
| `memories` — `fact`/`preference` scope | Indefinitely. | No TTL — these are treated as durable knowledge about how the company/role/agent should behave, not transient episodic notes. |
| `knowledge_docs`/`knowledge_chunks` | Indefinitely until a human archives them (`DELETE /knowledge/{id}` — archives, does not hard-delete). | `KnowledgeService.archive`. |
| CloudWatch application logs (structured JSON, M3.5) | 30 days in `prod`, 7 days elsewhere. | `infra/terraform/ecs.tf`'s `aws_cloudwatch_log_group.retention_in_days` — **written and validated, not yet applied to a live environment** (`CLAUDE.md` session 32: no AWS deploy exists yet). |
| RDS Postgres backups | 7-day automated backups with point-in-time recovery. | `infra/terraform/rds.tf`'s `backup_retention_period = 7` — same not-yet-applied caveat as above. |
| Passwords | Never stored in plaintext. BCrypt hash only (`users.password_hash`), never logged (`08-conventions.md` security rule 1). | `spring-security-crypto`'s `BCryptPasswordEncoder`. |

## What is explicitly NOT retained or protected today — real gaps, not glossed over

- **No account/company deletion path exists.** There is no `DELETE /companies/{id}` or equivalent. A company that stops using Atrium has no way to have its data erased by any in-product action — this is a genuine gap against any "right to erasure" expectation (see the EU AI Act / GDPR-adjacent note below) and would need to be built (likely a manual/support-driven process at minimum, ideally a self-serve one) before Atrium could honestly claim erasure-on-request support.
- **`DELETE /memories/{id}` and `DELETE /knowledge/{id}` archive, they do not hard-delete.** An archived memory or knowledge doc still exists in Postgres; it's just excluded from recall/prompt assembly. This is intentional for audit-safety (you can see what an agent used to know), but it means "forget" in the product UI does not mean "erased from the database."
- **No row-level encryption at rest beyond whatever the hosting Postgres instance provides** (RDS's own storage encryption, once M3.5's Terraform is actually applied — see `infra/terraform/rds.tf`). Nothing in application code encrypts `task_events.payload`, `artifacts.content`, or `memories.content` beyond that.
- **The alerting webhook (`common.AlertingAppender`, M3.5) forwards ERROR-level log lines to an external Slack-shaped webhook.** If a log line's exception message or context ever contained sensitive task content (it shouldn't, by convention — logs carry ids, not payloads — but nothing structurally prevents a future bug from doing so), that would leave the retention boundaries described above. Worth a periodic grep of log statements for payload/content leakage, not something this policy alone guarantees.

## Why these specific periods

- `task_events`/`usage_records`/`artifacts` are kept forever because they're simultaneously the product's core value (the audit trail a human reviews) and the compliance mechanism M4.2 depends on ("full reconstructable audit on any HR task"). Deleting them would undermine both.
- `outbox_events`'s 14-day window exists purely so the table doesn't grow unbounded; it is not a compliance-driven period, and there's no reason it couldn't be shorter or longer without affecting any Done-when in this codebase.
- Memory TTL (90 days for episodic `lesson`/`summary`, indefinite for `fact`/`preference`) mirrors the distinction 14-agent-platform.md's memory model already draws: episodic notes decay, durable facts and stated preferences don't, on the theory that a stale "the agent noticed X once, three months ago" is less trustworthy than a standing instruction a human explicitly approved.

## Open items for a real launch

1. Build an account/company deletion path (self-serve or support-driven) before this policy can honestly claim erasure-on-request.
2. Decide and document a retention period for `task_events`/`usage_records`/`artifacts` if legal/compliance later requires a maximum (today: indefinite, by omission rather than by decision).
3. Apply the M3.5 Terraform for real (`infra/terraform/`) so the CloudWatch/RDS retention settings above are live, not just validated on disk.
