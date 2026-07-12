# execution

**What:** [[core-api]] module that does the actual AI work: `LlmClient` interface (AnthropicClient / OpenAiClient / GeminiClient, selected per agent row), `AgentRunner` (one loop per active agent, virtual threads), `PromptAssembler`.

**State: LLM SPI LIVE (M0.5a done, 2026-07-13 session 6).** `execution/spi/` verbatim 13 §1.1 (LlmMessage/ToolDef/ToolCall/Request/Result records, LlmProvider/LlmClient interfaces, LlmException w/ 7-kind taxonomy). `LlmRouter implements LlmClient`: provider bean map, catalog validation via registry's `ModelCatalogLookup` (new interface, AgentDirectory pattern), Future-based timeout (`atrium.llm.timeout` 120s), in-attempt retries (RATE_LIMITED 3× / PROVIDER_DOWN 2×, backoffs configurable). `AnthropicClient` on official SDK `com.anthropic:anthropic-java:2.34.0` (maxRetries 0 — router owns retries; missing key → AUTH at call time, not boot; refusal stop_reason → CONTENT_FILTERED; jsonSchema → OutputConfig structured output). `LlmCostCalculator` (µUSD from catalog prices). Remaining: M0.5b (`LlmLoopRuntime`/AgentLifecycleService/PromptAssembler/UsageRecorder), M-CTX1 (ContextAssembler).

**Key rules:**
- `PromptAssembler.build(roleDef, task, feedback?)` is a **pure function that only sees those three inputs** — secrets structurally impossible in prompts. Rejection feedback included on retry (M0.6).
- Every LLM call → `usage_records` insert with idempotency key `taskId:attempt` in the same tx ([[accountability]]) — redelivery never double-bills.
- Provider failure → `TaskService.flag()`, never crash the runner. Completion → artifact row + `pending_review`.
- API keys from env only, never logged.

**Contracts:** `atrium-docs/02-architecture.md §6` · [[data-model]] (usage_records, artifacts) · `05-module-specs.md §execution` · `08-conventions.md §Security`.

Links: [[_Atrium]] · [[core-api]] · [[routing]] · [[accountability]]
