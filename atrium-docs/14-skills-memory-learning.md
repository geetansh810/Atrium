# 14 — Agent Skills, Memory & Learning (the depth subsystem)

**This is the differentiating subsystem**: agents are deep, role-specific specialists that *learn per tenant* — grabbing new facts, procedures, and preferences from reviewed work until each agent is that company's best possible employee for its role. Module: `app.atrium.agentmind`. Tables: `15-data-model-delta.md` §4. Endpoints: `16-api-contract-delta.md` §2–3. Provenance: Paperclip skills registry + memory-landscape two-layer model (`notes/paperclip-findings.md` §3–4).

## 0. The layer model — what an agent "knows"

| Layer | Table | Changes | Example |
|---|---|---|---|
| **Identity** | `role_definitions` | rarely; versioned | "You are a research specialist… output contract: markdown report" |
| **Skills** — *how to do things* | `skills` (+ attach tables) | curated; versioned | "Competitive analysis procedure: 1) map competitors… " |
| **Memory** — *what has been learned here* | `memories` | continuously; governed | "This company's tone is informal; CEO rejects passive voice" |
| **Knowledge** — *reference material* | `knowledge_docs/_chunks` | on upload/ingest | product catalog PDF, brand guide |

Prompt-time, ContextAssembler (§6) draws from all four under a token budget. Identity is always fully present; the rest is retrieved. **Structural security rule (inherits 05/08):** these four tables are the ONLY stored-content sources that can reach a prompt; secrets live nowhere near them.

## 1. Skills registry

A skill = a versioned, company-scoped (or global-template) unit of *procedure*, stored as markdown + metadata — taught to agents by inclusion in context, never code. (Paperclip `company_skills` shape, trimmed.)

Semantics (schema in 15 §4.1):
- `skills(company_id NULL=global, key, version, name, description, body_md, kind, tags[], trust_level, source, created_by, …)` — `UNIQUE(company_id, key, version)`, same versioning idiom as `role_definitions`.
- `kind`: `procedure` (how to perform work) | `reference` (domain cheat-sheet) | `tool_guide` (how to use an allowed tool) | `policy` (constraints, e.g. compliance gates for M4.x roles).
- `trust_level`: `platform` (Atrium-authored, global) | `company` (tenant-authored) | `agent_proposed` (drafted by an agent, inert until a human promotes it to `company`). Only `platform`/`company` skills can enter prompts.
- Attachment: `role_definition_skills(role_definition_id, skill_id, position)` = every agent with that role has it. `agent_skills(agent_id, skill_id, source 'hired'|'assigned'|'learned', proficiency smallint 1–5)` = per-agent extras. `agents.skill_tags` remains the ROUTING vocabulary (queue names); skills are the CONTENT behind those tags — tags say *what* an agent claims, skills say *how well/how*.
- Editing = new version row; attachments pin `skill_id` (a specific version) so a role upgrade is an explicit re-attach — same "changing a role never redeploys code" guarantee, now for skills.
- Hire flow addition: hiring from a role template auto-attaches the template's skill set (16 §2).

## 2. Memory model

A memory = one learned item with provenance. Schema (15 §4.2): `memories(company_id, scope, agent_id?, role_key?, task_id?, kind, content, embedding vector, importance smallint, status, provenance jsonb, source_event_id?, use_count, last_used_at, …)`.

- `scope` — who recalls it: `agent` (one agent's private experience) | `role` (all agents of a role in this company) | `company` (all agents in the tenant). Plus `task` scope for working notes that die with the task (excluded from cross-task recall).
- `kind`: `fact` (durable truth about the tenant's world) | `preference` (how this company wants work done) | `lesson` (distilled from a rejection/approval) | `summary` (compressed episodic record of a completed task).
- `status` — the governance gate: `active` | `pending_review` | `rejected` | `archived`. **Only `active` memories can be recalled into prompts.** Auto-activation policy (§5) decides which writes need a human.
- `provenance` (required, Paperclip rule): `{taskId?, eventId?, artifactId?, extractedBy:'pipeline'|'user', modelUsed?}` — every memory answers "where did you learn that?". Surfaced in the UI inspect view (16 §3).
- Maintenance: `use_count`/`last_used_at` updated on recall; nightly job archives `lesson`/`summary` memories unused for 90 days (config `atrium.memory.ttl-days`); `forget` endpoint sets `archived` (never hard-deletes — audit).

### MemoryStore SPI (portable core — Paperclip landscape contract)

```java
public interface MemoryStore {                     // app.atrium.agentmind.spi
    UUID ingest(MemoryWrite w);                            // embed + insert
    List<MemoryHit> recall(RecallQuery q);                 // scoped semantic + recency search
    Page<MemoryView> browse(BrowseFilter f, Cursor c);     // UI inspect surface
    void forget(UUID companyId, UUID memoryId);            // archive
    void setStatus(UUID companyId, UUID memoryId, MemoryStatus s);  // review actions
}
public record RecallQuery(UUID companyId, UUID agentId, String roleKey,
                          String queryText, int k, Set<MemoryKind> kinds) {}
public record MemoryHit(MemoryView memory, double score) {}
```

v1 implementation: `PgVectorMemoryStore` — pgvector cosine search over `(company_id, scope-set)` with score = `0.75·similarity + 0.15·recency_decay(30d half-life) + 0.10·min(use_count,10)/10`, threshold 0.30. Scope-set for an agent = its `agent` rows ∪ its role's `role` rows ∪ `company` rows. The SPI exists so a hosted provider (mem0-style) or richer engine can bind per company later (config `atrium.memory.provider`), control plane keeps scoping/provenance/governance either way.

## 3. Knowledge (reference retrieval, kept minimal in v1)

`knowledge_docs(company_id, title, source_uri?, mime, status)` + `knowledge_chunks(doc_id, seq, content, embedding)` (15 §4.3). Ingestion: upload/text → chunk (~800 tokens, 100 overlap) → embed → rows; re-ingest = new doc version, old → `archived`. Recall: same cosine search, wired into ContextAssembler slot 4. Attachable to roles (`role_definition_knowledge`) so e.g. only the content-writer role retrieves the brand guide. v1 accepts text/markdown only; PDF/office parsing is a later milestone (M-KN1, 17).

## 4. How an agent is "trained" for its role (the operator story)

1. **Hire** from a role template → identity + platform skill set attached.
2. **Specialize**: attach company skills, upload knowledge docs, set preferences as seed `company`-scope memories via the API (16 §3) — minutes, no code.
3. **Work + review**: every rejection with feedback and every approval feeds the LearningPipeline.
4. **Converge**: lessons and facts accumulate per scope; recall puts them in front of every future task; the review queue keeps humans in control of what generalizes.
Result: two companies hiring the same template get divergent, tenant-shaped specialists — data no competitor can copy, and the moat behind "best agentic employee for the user".

## 5. Learning pipeline (durable outbox consumer — 12 §3)

Trigger events → extraction → governed writes. Idempotency key per side effect: `learn:{eventId}`.

| Trigger | Extraction (cheap `tier='fast'` model from catalog) | Writes |
|---|---|---|
| `task.rejected` | distill feedback into ≤3 imperative lessons ("Do X / Avoid Y"), each tagged fact/preference/lesson | `lesson` scope=`agent` → **auto-active**; anything phrased as company-wide fact/preference → scope=`role`/`company`, `pending_review` |
| `task.approved` | if attempts>1 or feedback existed: 1 "what worked" lesson; extract stated durable facts | same policy |
| `task.completed` | ≤120-token episodic `summary` (what was done, key entities) | `summary` scope=`agent`, auto-active |
| `memory.review_requested` | — | Atrium Bot notice + review queue item (16 §3) |

Governance policy (12 hard rule 3): **auto-active** = `agent`-scope `lesson`/`summary` only (blast radius: that one agent). **pending_review** = every `role`/`company` scope write and every `fact` that asserts external truth — a human approves/rejects in the review queue; approval may also *promote* scope (agent→role) or convert a recurring lesson into a draft skill (`trust_level='agent_proposed'` skill, §1). Extraction calls are metered like any LLM call (`usage_records`, `task_id` = source task) — learning is payroll too.

Duplicate control: before insert, recall top-1 same-scope; similarity ≥0.92 → increment existing memory's `importance` (max 5) instead of inserting.

**M-LN2-fix note (2026-07-16):** extraction no longer gates on `EmbeddingClient.isReady()`. `MemoryStore.ingest` now takes the same degrade posture `recall`/`findDuplicate` already had — a missing or failing embedding provider lands the row with a NULL `embedding` (the schema column is nullable for exactly this) instead of throwing, so the pipeline keeps working (governed writes still land, still metered) with only that memory's *own future semantic recall* deferred until a real embeddings key is configured. `LearningPipeline` no longer holds an `EmbeddingClient` dependency at all — the degrade decision lives solely where the embed call actually happens, in `PgVectorMemoryStore`.

## 6. ContextAssembler (the single doorway into prompts)

```java
public interface ContextAssembler {   // app.atrium.agentmind — M-CTX1 (see note below)
    ContextBundle assemble(Agent agent, Task task);
}
public record ContextBundle(List<SkillExcerpt> skills, List<MemoryHit> memories,
                            List<KnowledgeHit> knowledge, List<UUID> provenanceIds, int tokenCount) {}
```

**M-CTX1 note (supersedes this section's earlier sketch, kept for the next builder):** the interface + `ContextBundle`/`SkillExcerpt`/`MemoryHit`/`KnowledgeHit` all live in `app.atrium.agentmind`, not `app.atrium.execution` as first sketched here — agentmind must never import execution types (12 §2), and `execution → agentmind` is the allowed direction, so `PromptAssembler` (execution) imports `ContextBundle` from agentmind instead. `assemble()` takes the real `registry.domain.Agent`/`routing.domain.Task` entities, not separate `AgentSnapshot`/`TaskSnapshot` DTOs — no snapshot indirection existed elsewhere in the codebase (`PromptAssembler` already took entities directly), so none was introduced here either. `MemoryHit`/`KnowledgeHit` are empty placeholder records at M-CTX1 (skills only); M-MEM1/M-KN1 give them real fields.

Deterministic assembly under a budget (`runtime_config.contextBudgetTokens`, default 4000, hard cap 30% of model context window from catalog):

1. **Skills** (≤50%): role-attached skills in `position` order, then agent skills by proficiency desc. Whole `body_md` if it fits; else name+description line ("skill index" fallback).
2. **Memories** (≤35%): `recall(k=12, queryText = task.title+description)`, ordered score desc; preferences and lessons before facts.
3. **Knowledge** (≤15%): top-3 chunks if score ≥0.35.
4. Truncation is item-granular (drop whole items, never mid-item), so bundles are reproducible; `provenanceIds` land in `task_events(claimed).payload.contextProvenance` — every prompt is auditable ("why did the agent think that?").

**M-CTX1 note — how provenance actually reaches the claimed event:** `task_events` is append-only (no setters, `payload` column `updatable=false`, 03 invariant 1), and `claimed` is written by `WorkBroker.claimNext` (routing) at claim time — before `assemble()` runs, and routing must not import agentmind/execution to call it early. `WorkBroker.claimNext` therefore takes an extra `@Nullable Function<Task, ObjectNode> claimedPayloadEnricher` parameter (plain JDK type — costs routing no cross-module import), invoked *inside* the claim's own transaction, right after the JDBC claim and before the `claimed` event is recorded. `LlmLoopRuntime` (execution) supplies a lambda that calls `contextAssembler.assemble(agent, task)` exactly once, keeps the resulting bundle for building the prompt a few lines later (no second `assemble()` call), and returns `{contextProvenance: [...]}` for the enricher to merge into that event's payload. Any future assembler step reusing this same claim path (M-MEM1's memories, M-KN1's knowledge) should extend the SAME lambda rather than adding a second enricher call — `claimed` gets exactly one write.

`PromptAssembler.build(roleDef, task, feedback?, bundle?)` stays a pure function (05 rule). Normative prompt layout:

```
[system]  role_definitions.system_prompt
          ## Your skills                 ← bundle.skills
          ## What you have learned here  ← bundle.memories   ("Learned context — verify if critical")
          ## Output contract             ← role_definitions.output_contract
[user]    ## Task: title, description, subtasks, priority, eta
          ## Reference material          ← bundle.knowledge
          ## Reviewer feedback (attempt N) ← feedback (rejection retry — unchanged M0.6 behavior)
```

**M-CTX1 note:** only the `## Your skills` section is rendered so far — `## What you have learned here`/`## Reference material` are omitted outright (not emitted-empty) since `bundle.memories()`/`bundle.knowledge()` are structurally always empty until M-MEM1/M-KN1; rendering code for an always-empty list would be dead code. Add those two render branches when those milestones give the lists real content.

Injection posture (08 §Security stands): task descriptions and learned content are untrusted; the system prompt instructs agents to treat quoted task/memory content as data, not instructions; `pending_review` remains the backstop for whatever slips through.

## 7. Observability & metrics (feeds 10 §6)

Per-company metrics: memories by scope/status, recall hit-rate (recalled items per prompt), review-queue depth + age, learning spend (usage_records where idempotency LIKE 'learn:%'), skill coverage per role. Structured logs on every ingest/recall with `companyId, agentId, memoryId`. Analytics surfacing (dashboard "Agent is learning" panel) is M-LN2 in 17.
