/* Table cells are JSX inside DocTable row arrays; DocTable assigns each rendered
   <td> its own key, so react/jsx-key on these source arrays is a false positive. */
/* eslint-disable react/jsx-key */
import { Link } from "react-router";
import {
  Callout,
  CardGrid,
  Code,
  CodeBlock,
  DocCard,
  DocTable,
  Lead,
  Li,
  P,
  Section,
  Ul,
} from "../docPrimitives";

export function Architecture() {
  return (
    <>
      <Lead>
        Atrium is a modular monolith with an event backbone: nine packages with one-directional
        dependencies and no circular imports, all speaking through a transactional outbox. Event-driven
        is a shape here, not a broker purchase.
      </Lead>

      <Section id="modules" title="The nine modules">
        <P>
          Each module owns a slice of the domain and a boundary. A session's work touches one module;
          the dependency direction is enforced so one package can never reach sideways into another's
          internals.
        </P>
        <DocTable
          head={["Module", "Owns"]}
          rows={[
            [<Code>registry</Code>, "Companies, users, agents, role definitions, model catalog."],
            [<Code>routing</Code>, "Tasks, skill queues, claim & lease, the task graph."],
            [<Code>execution</Code>, "LLM provider SPI, agent runtimes, pure prompt assembly."],
            [<Code>agentmind</Code>, "Skills, memory, knowledge, the learning pipeline."],
            [<Code>accountability</Code>, "Budgets, usage ledger, approvals, analytics rollups."],
            [<Code>communication</Code>, "Channels, announcements, bot notices on completion."],
            [<Code>eventbus</Code>, "Outbox writer, relay, durable consumer cursors."],
            [<Code>realtimebridge</Code>, "Redis publisher feeding the live dashboard."],
            [<Code>common</Code>, "Tenant context, problem+json errors, clock, ids."],
          ]}
        />
        <P>
          See <Link to="/docs/modules">Modules</Link> for each package's responsibilities and the
          boundaries between them.
        </P>
      </Section>

      <Section id="outbox" title="The outbox backbone">
        <P>
          A state change never just mutates a row. It writes the business change, its audit row, and
          an outbox row in <strong>one transaction</strong> — they commit together or not at all.
          That single rule is what makes the ledger complete and delivery reliable.
        </P>
        <Ul>
          <Li>
            <strong>Same transaction</strong> — the business write, the audit event, and the outbox
            row are atomic.
          </Li>
          <Li>
            <strong>Relay</strong> — a 250ms scheduled batch claims unpublished rows with the same
            skip-locked idiom, publishes to Redis, and stamps them. A crash mid-batch leaves rows
            unpublished, so the next tick re-claims them — at-least-once by construction.
          </Li>
          <Li>
            <strong>Projection consumers</strong> — the dashboard is lossy-tolerant and self-heals on
            its next poll.
          </Li>
          <Li>
            <strong>Durable consumers</strong> — learning, stats, and chat notices keep a cursor row.
            A crash replays; idempotency keys absorb the duplicate.
          </Li>
          <Li>
            <strong>Retention</strong> — a nightly job purges only rows that are published and behind
            every consumer's cursor.
          </Li>
        </Ul>
        <Callout tone="note" title="Why no message broker">
          The outbox gives at-least-once delivery today with only Postgres and Redis, and swaps to
          Kafka later without touching a single producer. Event-driven is the shape; the transport is
          an implementation detail.
        </Callout>
      </Section>

      <Section id="tenancy" title="Multi-tenancy">
        <P>
          Every company-owned table carries a <Code>company_id</Code>, every repository method takes
          one, and Postgres row-level security enforces the same scope at the database using a
          session variable bound per request. Read more in{" "}
          <Link to="/docs/security">Security &amp; tenancy</Link>.
        </P>
      </Section>
    </>
  );
}

export function TaskLifecycle() {
  return (
    <>
      <Lead>
        There is exactly one path work can take, from a request to signed-off output. Each step names
        the mechanism that keeps it safe under concurrency, crashes, and redelivery.
      </Lead>

      <Section id="states" title="The state machine">
        <P>
          A task moves through a fixed set of states. Illegal transitions are rejected by a guard, not
          left to convention.
        </P>
        <CodeBlock label="task states">
          queued → claimed → in_progress → pending_review → approved{"\n"}
          {"                                    "}↘ rejected → queued (retry)
        </CodeBlock>
      </Section>

      <Section id="steps" title="Seven steps">
        <CardGrid>
          <DocCard tag="01 · queued" title="Create">
            A task names a required skill, not an agent. Routing checks the roster actually covers
            that skill and rejects with a field error if it doesn't.
          </DocCard>
          <DocCard tag="02 · claimed" title="Claim">
            Runners poll their skill queues. Postgres <Code>FOR UPDATE SKIP LOCKED</Code> picks the
            winner — three workers racing twenty tasks produce twenty claims and zero double-work. The
            claim takes a 10-minute lease and bumps the attempt counter.
          </DocCard>
          <DocCard tag="03" title="Assemble context">
            One doorway builds the prompt under a token budget: role, skills, memories, knowledge. The
            ids of everything recalled are written into the claim event, so the input is auditable.
          </DocCard>
          <DocCard tag="04 · in_progress" title="Work">
            The runtime calls the model through a provider-neutral interface. Spend is metered against
            an idempotency key of <Code>task id + attempt</Code> — a redelivery can never bill twice.
          </DocCard>
          <DocCard tag="05 · pending_review" title="Hand off">
            Output is stored as an artifact and the task stops. A bot notice posts to the company
            channel; the escalation queue is two clicks from login.
          </DocCard>
          <DocCard tag="06" title="Review">
            Rejection stores the reviewer's feedback and requeues the task, so the retry gets a fresh
            attempt number and a clean budget key — and the feedback is prepended to the next prompt.
          </DocCard>
          <DocCard tag="07" title="Learn">
            A durable consumer reads the outcome and extracts lessons. Agent-scoped lessons activate
            on their own; anything company-wide waits in a human review queue.
          </DocCard>
        </CardGrid>
      </Section>

      <Section id="claim-query" title="The claim, exactly once">
        <P>
          Claiming is the heart of routing. The canonical query locks the next matching task, skips
          rows another worker already holds, and returns the claim in one round trip:
        </P>
        <CodeBlock label="claim (simplified)">{`UPDATE tasks SET
  status = 'claimed',
  assigned_agent_id = :agentId,
  attempt = attempt + 1,
  lease_expires_at = now() + interval '10 minutes'
WHERE id = (
  SELECT id FROM tasks
  WHERE company_id = :companyId
    AND status = 'queued'
    AND required_skill = ANY(:skillTags)
  ORDER BY priority, created_at
  FOR UPDATE SKIP LOCKED
  LIMIT 1
)
RETURNING *;`}</CodeBlock>
        <Callout tone="note" title="Leases, not locks held forever">
          A claim holds a 10-minute lease. If a runner dies mid-work, a scheduled reclaim job returns
          the task to <Code>queued</Code> and records a <Code>requeued</Code> event — the next attempt
          gets a fresh number, so accounting stays honest.
        </Callout>
      </Section>

      <Section id="decomposition" title="Decomposition & the flow graph">
        <P>
          A task can be broken into child tasks — a product manager agent splitting a launch into
          design and copy, for example. The parent can't be approved while any child is still open,
          and the whole tree is queryable as a flow graph from any node in the chain.
        </P>
      </Section>
    </>
  );
}

export function RolesSkills() {
  return (
    <>
      <Lead>
        A capability in Atrium is data you insert, never code you branch on. Roles are the versioned
        identity an agent is hired into; skills are reusable procedures attached to roles and agents.
      </Lead>

      <Section id="roles" title="Role definitions">
        <P>A role is a versioned row. Creating a new version of a role never mutates the old one, so an agent hired against version 1 keeps its exact identity forever. A role carries:</P>
        <Ul>
          <Li>
            <strong>System prompt</strong> — who the agent is and how it should behave.
          </Li>
          <Li>
            <strong>Output contract</strong> — the shape and constraints of what it produces.
          </Li>
          <Li>
            <strong>Allowed tools</strong> — which tools it may call, as a data list.
          </Li>
          <Li>
            <strong>Review required</strong> — whether its work needs a human compliance sign-off.
          </Li>
        </Ul>
        <P>
          A role can be a global template (shared across all tenants — coder, tester, research) or a
          company-owned custom role you author yourself.
        </P>
      </Section>

      <Section id="data-not-branches" title="Roles are data, proven">
        <P>
          The strongest evidence that roles are data: a whole business role — a content writer for a
          real pilot — was added to the platform through the registry API alone, with an{" "}
          <em>empty diff</em> on the routing module. No router edit, no schema change. That's the bar:
          if adding a capability touches routing, something is wrong.
        </P>
        <Callout tone="ok" title="The rule">
          A <Code>if (skill == "...")</Code> in routing is a bug by definition. New capability = a
          role row plus attached skills.
        </Callout>
      </Section>

      <Section id="skills" title="Skills">
        <P>
          A skill is a versioned procedure written as markdown — a code-review checklist, an output
          format, a ranking method. Skills attach to a role (so every agent hired into it inherits
          them) or directly to an agent. They carry a trust level:
        </P>
        <DocTable
          head={["Trust level", "Meaning"]}
          rows={[
            ["platform", "Shipped with the platform, always trusted."],
            ["company", "Authored and promoted by the company."],
            ["agent_proposed", "Drafted from a learned lesson — inert until a human promotes it."],
          ]}
        />
        <P>
          An agent-proposed skill never reaches a prompt until someone promotes it. Learning can
          suggest; only a human generalizes.
        </P>
      </Section>
    </>
  );
}

export function MemoryKnowledge() {
  return (
    <>
      <Lead>
        This is the differentiator: an agent gets sharper at your company over time. What it knows is
        four layers of governed data, assembled at work time under a token budget — and those four
        tables are the only stored content that can ever reach a model.
      </Lead>

      <Section id="layers" title="The four layers">
        <CardGrid>
          <DocCard tag="Identity" title="Role">
            Who the agent is and the output it owes. Versioned, always present in full.
          </DocCard>
          <DocCard tag="Curated" title="Skills">
            Reusable procedures attached to a role or agent. Agent-proposed skills stay inert until
            promoted.
          </DocCard>
          <DocCard tag="Governed" title="Memory">
            What's been learned at this company — recalled by vector similarity blended with recency
            and use.
          </DocCard>
          <DocCard tag="On ingest" title="Knowledge">
            Reference material, chunked and embedded, retrieved only for the roles it was attached to.
          </DocCard>
        </CardGrid>
      </Section>

      <Section id="memory" title="Memory">
        <P>
          A memory is a fact, preference, lesson, or summary scoped to an agent, a role, or the whole
          company — for example, "the CEO rejects passive voice." Memories are stored with a vector
          embedding and recalled at work time by a blended score:
        </P>
        <CodeBlock label="recall score">
          0.75 · similarity + 0.15 · recency_decay + 0.10 · normalized_use_count
        </CodeBlock>
        <P>
          Preferences and lessons are ranked ahead of facts and summaries. Only active memories are
          ever recalled — pending or archived ones are excluded by the query itself.
        </P>
      </Section>

      <Section id="knowledge" title="Knowledge">
        <P>
          Knowledge is reference material you ingest — a brand guide, a product catalog. On ingest it
          is chunked with overlap, embedded, and stored. At work time the top chunks are retrieved by
          cosine similarity, but only for the roles the document was attached to. A doc attached to
          "content writer" never leaks into an unrelated agent's prompt.
        </P>
      </Section>

      <Section id="learning" title="Governed learning">
        <P>
          When a task is approved or rejected, a durable consumer extracts lessons from the outcome
          using the agent's own model. Governance decides what happens next, and it never trusts the
          extraction model's own opinion of its blast radius:
        </P>
        <Ul>
          <Li>Agent-scoped lessons and summaries activate on their own.</Li>
          <Li>
            Anything role- or company-scoped, and every extracted fact, waits in a human review queue
            before it can influence another agent.
          </Li>
        </Ul>
        <Callout tone="warn" title="Secrets live nowhere near prompts">
          Only role, skills, memory, and knowledge can reach a model. Prompt assembly sees the role
          definition, the task, and reviewer feedback — never credentials, keys, or another tenant's
          data.
        </Callout>
      </Section>
    </>
  );
}

export function Governance() {
  return (
    <>
      <Lead>
        Governance in Atrium isn't a feature you enable — it's the architecture. The review gate, the
        audit trail, and the compliance controls are structural, so there's no configuration that
        turns accountability off.
      </Lead>

      <Section id="gate" title="The review gate">
        <P>
          Agent work stops at <Code>pending_review</Code>. It cannot advance to <Code>approved</Code>{" "}
          on its own. Approval is additionally blocked while any child task or checklist item is still
          open, so a parent can never be signed off with unfinished work beneath it.
        </P>
      </Section>

      <Section id="compliance" title="Compliance roles">
        <P>
          A role can be flagged as requiring review. When it is, approving its work is rejected unless
          a real authenticated human is on the call — a background job or system actor is refused
          identically to any other unauthenticated caller. This is structural, not a UI convention:
          there is no code path that lets an automated actor approve a gated role's task.
        </P>
        <Callout tone="ok" title="Proven by test">
          A legal-role task's approval throws for a system actor and succeeds only with a real human
          token — while an ungated role's approval is not blocked, proving the gate is data-driven per
          role, not a blanket restriction.
        </Callout>
      </Section>

      <Section id="audit" title="The completion audit">
        <P>
          A completion records a full audit payload: the model and provider used, the role and prompt
          version, and the context that was recalled. Combined with the recalled-context ids captured
          at claim time, a completed task is fully reconstructable from the event ledger alone — no
          cross-referencing another table, no database access, just an authenticated read of the task
          events.
        </P>
      </Section>

      <Section id="escalations" title="Escalations">
        <P>
          Anything flagged or awaiting review surfaces in a single escalation queue, two clicks from
          login. Reviewers approve, or reject with feedback that becomes the first thing the agent's
          next attempt reads.
        </P>
      </Section>
    </>
  );
}

export function Budgets() {
  return (
    <>
      <Lead>
        Every agent — and the whole company — can carry a monthly token budget. Spend is metered per
        task, budgets pause agents that run over, and none of this is billing: it's control and
        accountability.
      </Lead>

      <Section id="caps" title="Caps & scopes">
        <P>
          A budget caps token spend over a calendar month at two scopes: a per-agent cap and a
          company-wide cap. A missing budget row means that scope is uncapped — budgets are opt-in.
        </P>
        <Ul>
          <Li>
            An <strong>agent-scope</strong> breach pauses that agent and records a budget event.
          </Li>
          <Li>
            A <strong>company-wide</strong> breach refuses the next claim without pausing any single
            agent — the cap is shared, so punishing one would be arbitrary.
          </Li>
        </Ul>
      </Section>

      <Section id="metering" title="Metering & idempotency">
        <P>
          Every LLM call is metered into a usage record keyed by <Code>task id + attempt</Code>. The
          insert is idempotent, so a redelivered task never double-counts spend even under concurrent
          runners. When spend crosses an alert threshold for the first time in a period, a single
          threshold event fires — a new period resets it for free.
        </P>
      </Section>

      <Section id="payroll" title="Payroll & attribution">
        <P>
          The budget ledger reads like a payroll: what each agent cost this period, and the cost of
          each individual task. When work is delegated down a chain of child tasks, cost is attributed
          up to the billing task at the root — so you can see the true cost of a request, not just its
          leaves.
        </P>
        <Callout tone="note" title="Metering is not monetization">
          Token budgets, caps, usage records, and the payroll ledger meter tokens for control. No
          payment processing exists in the platform today — pricing is a post-pilot concern.
        </Callout>
      </Section>
    </>
  );
}

export function Security() {
  return (
    <>
      <Lead>
        Tenant isolation is the platform's first rule and it's enforced twice — once in application
        code and again in the database. Auth is JWT-based, and the security posture is documented
        against what's actually built, not what's aspirational.
      </Lead>

      <Section id="isolation" title="Two isolation layers">
        <P>Cross-tenant access is designed to be impossible, defended at two independent layers:</P>
        <Ul>
          <Li>
            <strong>Application scoping</strong> — every repository method takes a{" "}
            <Code>company_id</Code>, and every endpoint is company-scoped. A resource requested with
            another company's token returns 404, not another tenant's data.
          </Li>
          <Li>
            <strong>Row-level security</strong> — Postgres RLS enforces the same scope at the database
            using a session variable bound per request. Even a query that forgot its filter returns
            zero rows rather than another tenant's.
          </Li>
        </Ul>
        <Callout tone="ok" title="Defense in depth, verified">
          Deliberately removing the application-level filter on a read still returned 404 under RLS
          alone — and disabling both layers was needed to leak data, confirming the two layers are
          genuinely independent.
        </Callout>
      </Section>

      <Section id="auth" title="Authentication">
        <P>
          Signup creates a company and its first admin user in one transaction and issues a JWT. Every
          API request carries a bearer token whose claims bind the tenant for the request. The worker
          gateway that agent runtimes use is a separate axis, authenticated by agent identity rather
          than a human token.
        </P>
      </Section>

      <Section id="db-roles" title="Least-privilege database roles">
        <P>
          The application connects as a dedicated non-superuser role that cannot bypass RLS. Migrations
          run as a separate role with DDL rights. Postgres never enforces RLS for a superuser, so this
          split is a correctness requirement, not a nicety.
        </P>
      </Section>

      <Section id="hardening" title="Hardening">
        <Ul>
          <Li>
            <strong>Rate limiting</strong> — a Redis-backed fixed-window limiter scoped by company,
            agent, or client IP, returning <Code>429</Code> with <Code>Retry-After</Code>.
          </Li>
          <Li>
            <strong>Structured logs</strong> — JSON logs carrying tenant, user, task, and agent
            context in production.
          </Li>
          <Li>
            <strong>Secrets discipline</strong> — provider keys are read from the environment, never
            logged, and never placed in a prompt.
          </Li>
        </Ul>
      </Section>
    </>
  );
}
