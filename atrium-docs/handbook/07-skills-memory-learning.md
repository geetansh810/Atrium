# 7 · Skills, memory & learning

This is the subsystem that makes an agent more than a prompt. Design: [`14-skills-memory-learning.md`](../14-skills-memory-learning.md); as-built: [`18-technical-architecture.md`](../18-technical-architecture.md) §10. Code lives in [`agentmind/`](../../core-api/src/main/java/app/atrium/agentmind/).

## What an agent knows, in layers

| Layer | Source | How it gets into a prompt | Who can change it |
|---|---|---|---|
| **Identity** | Role definition (system prompt + output contract) | Always | Company admins, as new versions |
| **Skills** | Versioned markdown procedures attached to the role (`hired`) or the agent (`assigned`/`learned`) | "## Your skills" — role skills first in position order, then the agent's own by proficiency | Anyone with API access; `agent_proposed` skills are inert until promoted |
| **Memory** | Facts, lessons, preferences, summaries, scoped to agent / role / company | "## What you have learned here" — semantic recall for *this* task | Created by the learning pipeline or seeded by a human; governed by review |
| **Knowledge** | Reference documents, chunked and embedded | "## Reference material" — top 3 chunks | Ingested by a human, attached to a company-owned role |

All of it reaches the prompt through one door, [`ContextAssembler`](../../core-api/src/main/java/app/atrium/agentmind/ContextAssembler.java) → [`SkillContextAssembler`](../../core-api/src/main/java/app/atrium/agentmind/SkillContextAssembler.java), and the ids it used are recorded in the task's `claimed` event.

## Context assembly under a budget

Deterministic and a pure local-database read except for one query embedding:

- Budget = `runtime_config.contextBudgetTokens` (default 4000), hard-capped at 30% of the model's context window. Tokens are estimated as characters ÷ 4.
- Split: skills ≤ 50%, memories ≤ 35%, knowledge ≤ 15%.
- Truncation is **item-granular**: a skill's full body if it fits, else a one-line name + description, else dropped — and the loop keeps trying smaller items rather than stopping.
- Only `platform`/`company` trust-level skills are considered.

## Memory

- **Store:** [`PgVectorMemoryStore`](../../core-api/src/main/java/app/atrium/agentmind/PgVectorMemoryStore.java) behind the [`MemoryStore`](../../core-api/src/main/java/app/atrium/agentmind/MemoryStore.java) interface, raw JDBC with pgvector.
- **Scope set for recall:** this agent's `agent` rows ∪ its role's `role` rows ∪ all `company` rows. Never another company's.
- **Score:** `0.75·similarity + 0.15·recency (30-day half-life) + 0.10·min(use_count,10)/10`, threshold 0.30, top 12; preferences and lessons are ordered ahead of facts and summaries. Only `status='active'` memories are eligible.
- **Expiry:** `lesson`/`summary` memories unused for `atrium.memory.ttl-days` (90) are archived nightly; `fact`/`preference` never expire this way.
- **No embeddings key?** Memories still land (with a NULL embedding) and prompts still build; those memories just can't be *recalled* semantically until a key is configured. Setup note: [09](09-run-and-deploy.md).

## The learning pipeline

[`LearningPipeline`](../../core-api/src/main/java/app/atrium/agentmind/LearningPipeline.java) is a durable consumer of the outbox ([`EventCursorWorker`](../../core-api/src/main/java/app/atrium/eventbus/EventCursorWorker.java)):

| Event | Extraction | Notes |
|---|---|---|
| `task.rejected` | up to 3 items `{content, kind, scope}` from the reviewer's feedback | The main source of lessons |
| `task.approved` | same, but only if it took more than one attempt | A clean first-try approval costs nothing |
| `task.completed` | one ≤120-token episodic summary, forced `kind=summary`, `scope=agent` | |

It uses the agent's **own provider**, `fast`-tier model, and the call is metered into `usage_records` like any other ("learning is payroll too").

**Governance — the one decision point** ([`decideStatus`](../../core-api/src/main/java/app/atrium/agentmind/LearningPipeline.java)): a memory is auto-`active` **only** if it is `agent`-scope and a `lesson` or `summary`. Everything `role`/`company`-scoped, and every `fact` at any scope, lands `pending_review` and waits for a human in the Review Inbox → Memories tab. The extraction model's own opinion of how widely a memory should apply is never trusted. Approving can optionally widen the scope or spin off an `agent_proposed` draft skill.

Duplicates (same scope, cosine ≥ 0.92) bump the existing memory's `importance` (max 5) instead of inserting.

## The loop, end to end

```
reject "never use passive voice; our brand is X-Corp"
   → task.rejected event (same tx)
   → LearningPipeline extracts: [lesson/agent → active], [fact/company → pending_review]
   → human approves the fact in Review Inbox → Memories
   → next task, any agent in the company: recall surfaces "Brand is X-Corp"
   → the claimed event's contextProvenance lists that memory id
```

Pinned by [`LearningPipelineTest`](../../core-api/src/test/java/app/atrium/agentmind/LearningPipelineTest.java) and [`SkillContextAssemblerTest`](../../core-api/src/test/java/app/atrium/agentmind/SkillContextAssemblerTest.java).

## Knowledge

Ingest (`POST /companies/{id}/knowledge`) chunks content (~800 tokens, 100 overlap), batch-embeds, and stores chunks; the document row itself returns no content. A document only surfaces for a role it has been **attached to**, and attachment is restricted to company-owned role definitions — attaching to a global template would leak the reference into every tenant that hires from it. Practical consequence: to give an agent company knowledge, hire it from a custom role definition, not a bare template. Recall: top 3 chunks by cosine similarity ≥ 0.35, no recency blend.

## Known limits

- Skills have no promotion path from `agent_proposed` yet — they stay inert.
- Learning calls made under an RLS bypass (no bound company) are not written to the LLM log.
- The single-shot tool model means an agent cannot iterate with tools inside one attempt.

*Verified against:* the classes linked above, [`application.yml`](../../core-api/src/main/resources/application.yml) (`atrium.memory`, `atrium.learning`), [`KnowledgeChunker`](../../core-api/src/main/java/app/atrium/agentmind/KnowledgeChunker.java).
