# 13 — LLM Provider SPI & Agent Runtime SPI

The two pluggability contracts. Java signatures here are **normative** — code sessions implement them verbatim (renames = contract change, update this file first). Tables referenced: `15-data-model-delta.md` §1. Package: `app.atrium.execution.spi`.

## 1. LLM provider SPI

### 1.1 Interfaces (normative)

```java
/** One conversation turn. role: "system"|"user"|"assistant"|"tool". */
public record LlmMessage(String role, String content, @Nullable String toolCallId) {}

public record LlmToolDef(String name, String description, JsonNode inputSchema) {}

public record LlmToolCall(String id, String name, JsonNode arguments) {}

public record LlmRequest(
    String provider,            // 'anthropic'|'openai'|'google'|… (agents.model_provider)
    String model,               // agents.model_name — must exist in model_catalog
    @Nullable String system,    // system prompt (kept separate; providers map internally)
    List<LlmMessage> messages,
    List<LlmToolDef> tools,     // empty = none
    int maxOutputTokens,
    @Nullable Double temperature,
    @Nullable JsonNode jsonSchema   // non-null => structured output requested
) {}

public record LlmResult(
    @Nullable String content,        // assistant text (null if pure tool call)
    List<LlmToolCall> toolCalls,     // empty if none
    long tokensIn, long tokensOut,   // ALWAYS present — accounting depends on it
    String stopReason,               // 'end'|'max_tokens'|'tool_use'|'filtered'
    String providerRequestId         // provider-side id for tracing; "" if absent
) {}

/** SPI. One bean per provider; discovered via Spring List<LlmProvider>. */
public interface LlmProvider {
    String id();                              // 'anthropic' — matches model_catalog.provider
    LlmResult complete(LlmRequest r) throws LlmException;
}

/** The only entry point business code uses. */
public interface LlmClient {
    LlmResult complete(LlmRequest r) throws LlmException;   // routes by r.provider()
}
```

`LlmRouter implements LlmClient`: looks up the provider bean by `r.provider()`, validates `(provider, model)` is enabled in `model_catalog`, enforces the timeout, translates errors (§1.3). Unknown provider/model → `LlmException(UNKNOWN_MODEL)` — flag, don't crash.

### 1.2 Model catalog (models are DATA)

Table `model_catalog` (15 §1): `provider, model_name, display_name, context_window, max_output_tokens, price_in_micro_usd_per_mtok, price_out_micro_usd_per_mtok, capabilities JSONB ('tools','vision','json_mode','embeddings'), tier ('fast'|'balanced'|'deep'), enabled`. Seeded in migration; updated by rows, never code. `cost_micro_usd` on `usage_records` = tokens × catalog prices at call time. UI "pick a model" list = `GET /model-catalog` (16 §1).

**Adding a provider** (the whole checklist): ① implement `LlmProvider` bean using the official SDK/REST (06 §C), ② env var `<PROVIDER>_API_KEY` documented in `.env.example`, ③ insert `model_catalog` rows, ④ integration test with recorded fixtures. Nothing else changes anywhere.
**Adding a model**: insert one row. Done.

### 1.3 Error taxonomy → flag reasons (normative)

`LlmException.kind` — every provider adapter maps its SDK errors into exactly these:

| kind | Meaning | Runner behavior |
|---|---|---|
| `RATE_LIMITED` | 429/quota | retry same attempt: 3× exponential backoff (2s/8s/30s), then flag `provider_rate_limited` |
| `PROVIDER_DOWN` | 5xx/timeout/connect | retry 2× (5s/20s), then flag `provider_unavailable` |
| `CONTEXT_TOO_LONG` | input over window | no retry → flag `context_too_long` (ContextAssembler bug or oversized task) |
| `CONTENT_FILTERED` | provider refusal | no retry → flag `content_filtered` |
| `AUTH` | bad/missing key | no retry → flag `config_incomplete`, pause the agent's loop (12 §9) |
| `UNKNOWN_MODEL` | not in catalog / provider rejects | no retry → flag `config_incomplete` |
| `INVALID_REQUEST` | our bug | no retry → flag `execution_error`, log at ERROR |

Retries stay within one attempt: usage is recorded once per *successful* call with idempotency key `taskId:attempt` — a retried transport call that never returned usage bills nothing; a redelivered task increments `attempt`.

### 1.4 Non-goals v1

No streaming to end users (progress events cover UX); no provider-side tool execution loops beyond single-turn tool calls (the runner owns the loop); no BYO-keys (platform keys per 10 §5). All three are additive later.

## 2. Embeddings SPI (used by agentmind, same registry style)

```java
public interface EmbeddingClient {
    float[] embed(String text);           // dimension fixed by config: atrium.embeddings.model
    List<float[]> embed(List<String> texts);
}
```
One bean selected by config (`atrium.embeddings.provider/model`, default `openai/text-embedding-3-small`, 1536-dim, matching `vector(1536)` columns in 15 §4 — changing dimension = new migration, so pick once). Usage metered into `usage_records` with `task_id NULL`, idempotency `embed:{sha256(text)}:{model}` (dedupes re-embeddings for free).

## 3. Agent runtime SPI (any agent type)

### 3.1 The contract (Paperclip-derived, normative)

```java
public interface AgentRuntime {
    String type();                                   // 'llm_loop'|'webhook'|…  = agents.runtime_type
    void start(AgentHandle agent);                   // begin/resume the agent's work loop
    void stop(AgentHandle agent);                    // graceful: finish current task step, release
    RuntimeHealth health(AgentHandle agent);         // RUNNING|IDLE|STOPPED|ERROR(reason)
    void validateConfig(JsonNode runtimeConfig);     // throws ConfigException → hire/patch 400s
}
public record AgentHandle(UUID agentId, UUID companyId) {}  // runtimes re-read the roster row; never cache role/model
```

`RuntimeRegistry` (Spring `List<AgentRuntime>` → map by `type()`) validates `agents.runtime_type` on hire/update — TEXT column + code validation, deliberately NOT a DB CHECK, so a new runtime is zero-migration. `AgentLifecycleService` calls start/stop on hire, pause/resume, budget auto-pause, shutdown.

### 3.2 Built-in runtime `llm_loop` (v1, the only one built in Phase 0)

The AgentRunner from 05 §execution, formalized. Per active agent, one virtual thread:

```
loop every poll_interval (default 15s, runtime_config.pollSeconds):
  0. pre-dispatch gate: provider key present? budget not hard-exceeded? else flag/park (12 §9)
  1. claim next task for any of agent.skill_tags (canonical query, 03) — none? continue
  2. bundle  = ContextAssembler.assemble(agent, task)              (14 §6)
     — M-CTX1: runs INSIDE step 1's claim transaction (a WorkBroker.claimNext
       enricher hook), not after, so provenanceIds land in that SAME claimed
       task_event's payload (14 §6 note) — the bundle is then reused here.
  3. prompt  = PromptAssembler.build(roleDef, task, feedback?, bundle)   — pure (05 rule stands)
  4. result  = llmClient.complete(...)  [tool-loop ≤ runtime_config.maxToolTurns, default 4]
  5. UsageRecorder.record(taskId:attempt, tokens, cost)  — same tx as step 6 write
  6. progress/subtasks via TaskService; long work renews lease every 5min
  7. complete → artifact + pending_review   |   error → flag per 13 §1.3
```

`runtime_config` schema for `llm_loop` (validated by `validateConfig`): `{pollSeconds?, maxToolTurns?, maxAttemptsPerTask? (default 3 → flag 'max_attempts'), contextBudgetTokens?}`.

### 3.3 Future runtimes (specified now so the schema is proven, built later)

- **`webhook`** (integration level 1–3, Paperclip): `runtime_config = {url, secretRef, contextMode:'fat'|'thin'}`. `start` = deliver signed task notifications; the external agent works through the **Worker API gateway** — `POST /tasks/{id}/claim | progress | complete | flag` with `X-Agent-Id` auth (04 already defines these; that's the point). Fat mode posts task+context bundle; thin mode posts `{taskId}` only.
- **`process`**: local subprocess per invocation — dev/CI utility agent, `runtime_config = {command, args[]}`.

Both arrive as: new `AgentRuntime` bean + docs row in this file. **No migration, no routing change, no API change** — this is the "any agent type without breaking base schema" proof, and M-AR1 in 17 tests it with a stub runtime.

### 3.4 What runtimes may and may not do

May: read roster/task via service interfaces, call LlmClient, write through TaskService/UsageRecorder. May not: touch task status columns directly, read secrets other than their own provider key binding, bypass BudgetGuard, or write memories directly (learning flows only through LearningPipeline, 14 §5).
