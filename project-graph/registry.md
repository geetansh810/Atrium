# registry

**What:** [[core-api]] module owning companies, users (dev-mode), role_definitions (versioned, global templates have `company_id NULL`), agents CRUD + roster, manager-hierarchy validation (acyclic).

**State: NOT STARTED.** Milestone M0.2 (amended card in `atrium-docs/17-backend-execution-plan.md`).

**Rev C additions ([[agent-platform]]):** owns `model_catalog` (+ `GET /model-catalog`); agents gain `runtime_type`/`runtime_config` (validated against RuntimeRegistry on hire/patch → 400) and `paused`; hire auto-attaches the role template's skill set once M-SK1 lands.

**Key rules:** hire via template key or explicit role_definition; skill_tags non-empty; manager cycles rejected. Exposes `AgentDirectory` interface for [[routing]] to validate skills — routing never imports registry internals. **Roles are data**: adding a role must never touch router code (proven at M1.1).

**Contracts:** `atrium-docs/03-data-model.md` (companies/users/role_definitions/agents) · `04-api-contract.md §Registry` · `05-module-specs.md §registry`.

Links: [[_Atrium]] · [[core-api]] · [[routing]] · [[data-model]]
