# registry

**What:** [[core-api]] module owning companies, users (dev-mode), role_definitions (versioned, global templates have `company_id NULL`), agents CRUD + roster, manager-hierarchy validation (acyclic).

**State: BUILT (M0.2, 2026-07-12 session 5).** Entities+repos (`registry/domain`, every repo method companyId-scoped), endpoints per 04 §Registry + 16 §1 (hire/patch with runtimeType/runtimeConfig/paused, roster, profile w/ zeroed stats, role-definitions, `/model-catalog` prices omitted), `AgentRuntime` SPI + `RuntimeRegistry` + `llm_loop` descriptor in `registry/runtime` (validateConfig real; start/stop inert until M0.5b replaces the bean), `AgentDirectory.findBySkill` impl, `V2_1__seed.sql` (coder/tester/research templates + 3 anthropic model_catalog rows). 6 ITs green incl. isolation + manager-cycle. NOTE: runtime SPI lives in registry (not execution) to keep dependency direction acyclic — execution implements it at M0.5b.

**Rev C additions ([[agent-platform]]):** owns `model_catalog` (+ `GET /model-catalog`); agents gain `runtime_type`/`runtime_config` (validated against RuntimeRegistry on hire/patch → 400) and `paused`; hire auto-attaches the role template's skill set once M-SK1 lands.

**Key rules:** hire via template key or explicit role_definition; skill_tags non-empty; manager cycles rejected. Exposes `AgentDirectory` interface for [[routing]] to validate skills — routing never imports registry internals. **Roles are data**: adding a role must never touch router code (proven at M1.1).

**Contracts:** `atrium-docs/03-data-model.md` (companies/users/role_definitions/agents) · `04-api-contract.md §Registry` · `05-module-specs.md §registry`.

Links: [[_Atrium]] · [[core-api]] · [[routing]] · [[data-model]]
