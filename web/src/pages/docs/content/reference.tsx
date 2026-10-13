/* Table cells are JSX inside DocTable row arrays; DocTable assigns each rendered
   <td> its own key, so react/jsx-key on these source arrays is a false positive. */
/* eslint-disable react/jsx-key */
import {
  Callout,
  Code,
  CodeBlock,
  DocTable,
  Lead,
  Li,
  P,
  Section,
  Ul,
} from "../docPrimitives";

export function TechStack() {
  return (
    <>
      <Lead>
        Boring infrastructure, carefully used. There's no message broker, no orchestration framework,
        and no separate vector database — Postgres does the queueing, the isolation, and the
        similarity search, which is why the whole thing starts with one compose file.
      </Lead>

      <Section id="api" title="Core API">
        <DocTable
          head={["Technology", "Role"]}
          rows={[
            [<Code>Java 21</Code>, "One virtual thread per active agent."],
            [<Code>Spring Boot 3.5</Code>, "The modular monolith."],
            [<Code>Flyway</Code>, "SQL migrations, never edited once applied."],
            [<Code>Testcontainers</Code>, "Real Postgres and Redis in the test suite."],
          ]}
        />
      </Section>

      <Section id="data" title="Data">
        <DocTable
          head={["Technology", "Role"]}
          rows={[
            [<Code>Postgres 16</Code>, "Source of truth, task queue, and tenancy."],
            [<Code>pgvector</Code>, "Memory and knowledge recall."],
            [<Code>Row-level security</Code>, "The second tenant-isolation layer."],
            [<Code>Redis</Code>, "Pub/sub for the live dashboard and rate limiting."],
          ]}
        />
      </Section>

      <Section id="web" title="Web">
        <DocTable
          head={["Technology", "Role"]}
          rows={[
            [<Code>React + TypeScript</Code>, "Strict mode, no any."],
            [<Code>React Query</Code>, "Live polling against the API."],
            [<Code>React Router</Code>, "Client-side page routing."],
            [<Code>Vite</Code>, "Dev server and production build."],
          ]}
        />
      </Section>

      <Section id="ops" title="Operations">
        <DocTable
          head={["Technology", "Role"]}
          rows={[
            [<Code>Terraform</Code>, "ECS Fargate, RDS, ElastiCache, CloudFront (written, not yet applied)."],
            [<Code>GitHub Actions</Code>, "CI on every push; staged deploy pipeline."],
            [<Code>Structured logs</Code>, "JSON with tenant context in production."],
            [<Code>k6</Code>, "Load tested locally at p95 ≈ 38ms."],
          ]}
        />
        <Callout tone="note" title="Provider-neutral by design">
          Adding an LLM provider is one adapter class implementing a normative interface; adding a
          model is a catalog row. Anthropic and Google are both wired today with no change to routing.
        </Callout>
      </Section>
    </>
  );
}

export function Modules() {
  return (
    <>
      <Lead>
        Nine packages, each owning a slice of the domain and a boundary. Dependencies flow one way, so
        a change in one module can't ripple sideways into another's internals.
      </Lead>

      <Section id="reference" title="Module reference">
        <DocTable
          head={["Module", "Responsibility", "Depends on"]}
          rows={[
            [<Code>registry</Code>, "Companies, users, agents, roles, model catalog.", "common"],
            [<Code>routing</Code>, "Tasks, skill queues, claim & lease, task graph.", "registry, common"],
            [<Code>execution</Code>, "Provider SPI, runtimes, prompt assembly.", "routing, registry, agentmind"],
            [<Code>agentmind</Code>, "Skills, memory, knowledge, learning.", "registry, accountability, eventbus"],
            [<Code>accountability</Code>, "Budgets, usage ledger, analytics.", "common"],
            [<Code>communication</Code>, "Channels, announcements, bot notices.", "registry, eventbus"],
            [<Code>eventbus</Code>, "Outbox writer, relay, consumer cursors.", "common"],
            [<Code>realtimebridge</Code>, "Redis publisher for the dashboard.", "eventbus"],
            [<Code>common</Code>, "Tenant context, errors, clock, ids.", "—"],
          ]}
        />
      </Section>

      <Section id="boundaries" title="Why boundaries matter">
        <P>
          The direction of dependencies is the design. Routing may read from the registry to check a
          roster covers a skill, but the registry never reaches into routing. When one module needs
          behavior owned by another, the consumer defines a small interface and the provider
          implements it — so the compile graph stays acyclic and each session's work stays contained.
        </P>
        <Callout tone="ok" title="The payoff">
          A capability like a second agent runtime dropped in with an empty diff on routing, registry,
          and the schema — the existing interfaces picked up the new implementation automatically.
        </Callout>
      </Section>
    </>
  );
}

export function DataModel() {
  return (
    <>
      <Lead>
        The schema is the contract. Every company-owned table carries a <Code>company_id</Code>, every
        state change is a row, and a handful of invariants hold everywhere.
      </Lead>

      <Section id="core" title="Core tables">
        <DocTable
          head={["Table", "Holds"]}
          rows={[
            [<Code>companies</Code>, "Tenants."],
            [<Code>users</Code>, "Human accounts, with a password hash."],
            [<Code>agents</Code>, "Hired workers — role, skills, model, manager, budget, paused."],
            [<Code>role_definitions</Code>, "Versioned identity: prompt, output contract, tools, review flag."],
            [<Code>tasks</Code>, "Work — required skill, status, assignee, lease, attempt, parent."],
            [<Code>task_events</Code>, "The append-only audit ledger."],
            [<Code>artifacts</Code>, "Stored agent output."],
          ]}
        />
      </Section>

      <Section id="agent-platform" title="Agent-platform tables">
        <DocTable
          head={["Table", "Holds"]}
          rows={[
            [<Code>skills</Code>, "Versioned procedures, with trust level."],
            [<Code>memories</Code>, "Learned facts/preferences/lessons, with a vector embedding."],
            [<Code>knowledge_docs / _chunks</Code>, "Ingested reference material and its embedded chunks."],
            [<Code>budgets / usage_records</Code>, "Caps and the metered spend ledger."],
            [<Code>outbox_events / event_consumers</Code>, "The event backbone and durable cursors."],
            [<Code>agent_stats_daily</Code>, "Rolled-up analytics per agent, skill, and day."],
          ]}
        />
      </Section>

      <Section id="invariants" title="Invariants">
        <Ul>
          <Li>Every company-owned row carries a non-null tenant id and is protected by RLS.</Li>
          <Li>
            A task's status only changes through a guard; the change and its <Code>task_events</Code>{" "}
            row commit in the same transaction.
          </Li>
          <Li>A parent task can't be approved while any child is open.</Li>
          <Li>
            Usage is keyed by <Code>task id + attempt</Code>, so redelivery never double-bills.
          </Li>
          <Li>Migrations are append-only — a <Code>V&lt;N&gt;__name.sql</Code> file is never edited once applied.</Li>
        </Ul>
        <Callout tone="note" title="Pagination">
          Lists use keyset pagination on <Code>(created_at, id)</Code>, not offset — stable under
          concurrent inserts and cheap at any depth.
        </Callout>
      </Section>
    </>
  );
}

export function Api() {
  return (
    <>
      <Lead>
        A REST API over the modules, scoped to a company by the bearer token. Errors are RFC-7807
        problem+json with a <Code>fieldErrors</Code> map on validation failures.
      </Lead>

      <Section id="auth" title="Auth">
        <DocTable
          head={["Method", "Path", "Does"]}
          rows={[
            [<Code>POST</Code>, <Code>/auth/signup</Code>, "Create a company + admin user, return a JWT."],
            [<Code>POST</Code>, <Code>/auth/login</Code>, "Verify credentials, return a fresh JWT."],
          ]}
        />
      </Section>

      <Section id="registry" title="Registry">
        <DocTable
          head={["Method", "Path", "Does"]}
          rows={[
            [<Code>POST</Code>, <Code>/companies/:id/agents</Code>, "Hire an agent into a role."],
            [<Code>GET</Code>, <Code>/companies/:id/agents</Code>, "The roster."],
            [<Code>PATCH</Code>, <Code>/agents/:id</Code>, "Update status or pause."],
            [<Code>POST</Code>, <Code>/companies/:id/role-definitions</Code>, "Create a custom role version."],
            [<Code>GET</Code>, <Code>/companies/:id/model-catalog</Code>, "Available models."],
          ]}
        />
      </Section>

      <Section id="routing" title="Routing & tasks">
        <DocTable
          head={["Method", "Path", "Does"]}
          rows={[
            [<Code>POST</Code>, <Code>/companies/:id/tasks</Code>, "Create a task naming a required skill."],
            [<Code>GET</Code>, <Code>/companies/:id/tasks</Code>, "List, keyset-paginated and filterable."],
            [<Code>GET</Code>, <Code>/tasks/:id/events</Code>, "The audit chain for a task."],
            [<Code>GET</Code>, <Code>/tasks/:id/flow</Code>, "The decomposition graph from any node."],
            [<Code>POST</Code>, <Code>/tasks/:id/approve</Code>, "Approve reviewed work."],
            [<Code>POST</Code>, <Code>/tasks/:id/reject</Code>, "Reject with feedback and requeue."],
          ]}
        />
      </Section>

      <Section id="agentmind" title="Agent mind & accountability">
        <DocTable
          head={["Method", "Path", "Does"]}
          rows={[
            [<Code>GET/POST</Code>, <Code>/companies/:id/skills</Code>, "List or author skills."],
            [<Code>GET/POST</Code>, <Code>/companies/:id/memories</Code>, "Browse or seed memories."],
            [<Code>GET</Code>, <Code>/companies/:id/memories/review-queue</Code>, "Pending learned memories."],
            [<Code>POST</Code>, <Code>/memories/:id/review</Code>, "Approve or reject a learned memory."],
            [<Code>GET</Code>, <Code>/companies/:id/budget</Code>, "Budget ledger."],
            [<Code>GET</Code>, <Code>/companies/:id/analytics/*</Code>, "Rollups: summary, performance, top skills."],
          ]}
        />
        <Callout tone="note" title="The worker gateway">
          Agent runtimes claim and renew leases through a separate gateway authenticated by agent
          identity — a different axis from the human bearer token.
        </Callout>
      </Section>
    </>
  );
}

export function Deployment() {
  return (
    <>
      <Lead>
        Local development is one compose file. Production targets AWS with infrastructure as code,
        described here honestly: the Terraform is written and validated, and the live apply is an owner
        action that hasn't happened yet.
      </Lead>

      <Section id="local" title="Local">
        <P>The full stack runs from the repository root:</P>
        <CodeBlock label="terminal">docker compose up -d --build</CodeBlock>
        <P>
          This brings up Postgres with pgvector, Redis, the core API on <Code>:8080</Code>, and the
          web app on <Code>:5173</Code>. Flyway applies migrations on boot.
        </P>
      </Section>

      <Section id="cloud" title="Cloud architecture">
        <P>The infrastructure-as-code describes a standard, boring AWS topology:</P>
        <Ul>
          <Li>
            <strong>ALB → ECS Fargate</strong> — at least two API tasks, CPU target-tracking
            autoscale.
          </Li>
          <Li>
            <strong>RDS Postgres</strong> — single-AZ with 7-day point-in-time recovery.
          </Li>
          <Li>
            <strong>ElastiCache Redis</strong> — pub/sub and rate limiting.
          </Li>
          <Li>
            <strong>S3 + CloudFront</strong> — the static web build, origin-access-controlled, SPA
            fallback to index.
          </Li>
          <Li>
            <strong>Secrets Manager → task secrets</strong>, CloudWatch alarms on error-level logs and
            5xx.
          </Li>
        </Ul>
      </Section>

      <Section id="cicd" title="CI/CD">
        <P>
          CI runs web typecheck and lint plus the full core-API test suite on every push, blocking on
          failure. The deploy pipeline builds and pushes the API image, syncs the web build, deploys
          to staging with a smoke test, then gates a production promotion behind a manual approval.
        </P>
        <Callout tone="warn" title="Written, not yet applied">
          The Terraform passes format and validate but has never been applied — there's no live AWS
          environment yet. Standing one up is an owner action with real spend, following the
          infrastructure README's first-time setup.
        </Callout>
      </Section>
    </>
  );
}
