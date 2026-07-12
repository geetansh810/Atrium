# execution

**What:** [[core-api]] module that does the actual AI work: `LlmClient` interface (AnthropicClient / OpenAiClient / GeminiClient, selected per agent row), `AgentRunner` (one loop per active agent, virtual threads), `PromptAssembler`.

**State: NOT STARTED, SPEC'D** — normative SPIs in `atrium-docs/13-llm-and-agent-spi.md` ([[agent-platform]]): `LlmProvider`/`LlmRouter` + `model_catalog` (models are data), `AgentRuntime` (invoke/status/cancel; `llm_loop` built-in, webhook/process later), error-taxonomy→flag-reason table, `ContextAssembler` feeding a still-pure `PromptAssembler.build(roleDef, task, feedback?, bundle?)`. Built at M0.5a/M0.5b + M-CTX1 per doc 17.

**Key rules:**
- `PromptAssembler.build(roleDef, task, feedback?)` is a **pure function that only sees those three inputs** — secrets structurally impossible in prompts. Rejection feedback included on retry (M0.6).
- Every LLM call → `usage_records` insert with idempotency key `taskId:attempt` in the same tx ([[accountability]]) — redelivery never double-bills.
- Provider failure → `TaskService.flag()`, never crash the runner. Completion → artifact row + `pending_review`.
- API keys from env only, never logged.

**Contracts:** `atrium-docs/02-architecture.md §6` · [[data-model]] (usage_records, artifacts) · `05-module-specs.md §execution` · `08-conventions.md §Security`.

Links: [[_Atrium]] · [[core-api]] · [[routing]] · [[accountability]]
