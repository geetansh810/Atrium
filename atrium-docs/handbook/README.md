# Atrium handbook

The newcomer's guide to Atrium — for a human engineer, a pilot-company admin, or an AI agent loading context cold. It is written against the code **as it exists** and deliberately short: each page explains the idea, then points at the real file or contract doc for the detail. When a page and the code disagree, the code wins — [fix the page](#keeping-this-honest).

## Reading paths

| You want to… | Read, in order |
|---|---|
| Understand the product in 10 minutes | [01 What Atrium is](01-what-is-atrium.md) → [02 Concepts & glossary](02-concepts-glossary.md) → [03 UI tour](03-ui-tour.md) |
| Run it and watch a task go through | [09 Run & deploy](09-run-and-deploy.md) → [05 Life of a task](05-life-of-a-task.md) |
| Change backend code | [04 Architecture map](04-architecture-map.md) → [05 Life of a task](05-life-of-a-task.md) → [06 Data model](06-data-model.md) → [08 Security & tenancy](08-security-tenancy.md) |
| Understand what agents "know" | [07 Skills, memory & learning](07-skills-memory-learning.md) |
| Answer a "why/how come…" question | [10 FAQ](10-faq.md) |

## Pages

1. [What Atrium is](01-what-is-atrium.md) — product, audience, current status
2. [Concepts & glossary](02-concepts-glossary.md) — the vocabulary every other page uses
3. [UI tour](03-ui-tour.md) — every screen, what it is for, what feeds it
4. [Architecture map](04-architecture-map.md) — repo layout, backend modules, who may call whom
5. [Life of a task](05-life-of-a-task.md) — create → route → claim → LLM → review → audit → cost
6. [Data model](06-data-model.md) — tables grouped by concern, invariants
7. [Skills, memory & learning](07-skills-memory-learning.md) — the differentiator subsystem
8. [Security & tenancy](08-security-tenancy.md) — two isolation layers, auth, gates, rate limits
9. [Run & deploy](09-run-and-deploy.md) — local stack, config, tests, prod shapes
10. [FAQ](10-faq.md)

## Where the deeper docs are

| Doc | Role |
|---|---|
| [`18-technical-architecture.md`](../18-technical-architecture.md) | The as-built engineering deep dive (patterns, tradeoffs, bugs that taught us something). The handbook is the on-ramp; this is the destination. |
| [`03-data-model.md`](../03-data-model.md), [`15-data-model-delta.md`](../15-data-model-delta.md) | Schema contract and the later additions |
| [`04-api-contract.md`](../04-api-contract.md), [`16-api-contract-delta.md`](../16-api-contract-delta.md) | REST contract |
| [`12-backend-architecture.md`](../12-backend-architecture.md), [`13-llm-and-agent-spi.md`](../13-llm-and-agent-spi.md), [`14-skills-memory-learning.md`](../14-skills-memory-learning.md) | Event backbone, provider/runtime SPIs, the agentmind design |
| [`08-conventions.md`](../08-conventions.md) | Code style, security rules, definition of done |
| [`07-milestones.md`](../07-milestones.md) | What was built, in what order, and what is next |
| [`compliance/`](../compliance/) | Retention policy, review process, EU AI Act posture |

## Keeping this honest

Every page ends with the files it was verified against. If you change behavior a page describes, change the page in the same commit. The contract docs 00–17 describe the *plan*; where they differ from the code, these pages and doc 18 follow the code.
