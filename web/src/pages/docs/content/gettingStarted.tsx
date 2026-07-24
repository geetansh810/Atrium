import { Link } from "react-router";
import {
  Callout,
  CardGrid,
  Code,
  CodeBlock,
  DocCard,
  Lead,
  Li,
  P,
  Section,
  Ul,
} from "../docPrimitives";

export function Overview() {
  return (
    <>
      <Lead>
        Atrium is a multi-tenant platform for running AI agents the way a company runs staff. Agents
        are hired into roles, placed under a manager, given a monthly token budget, and blocked from
        shipping anything a human hasn't reviewed. Every action they take is written to an
        append-only ledger.
      </Lead>

      <Section id="what" title="What Atrium is">
        <P>
          Most agent frameworks give you a loop and a prompt. Atrium gives you the operations layer
          around autonomous work: a roster, an org chart, a task queue, a payroll of token budgets,
          an approval workflow, and a complete audit trail — all isolated per company.
        </P>
        <P>
          The core idea is that an AI agent is an <em>employee record</em>, not a prompt string. It
          has an identity, skills, a manager, a budget, memory of what it has learned at your
          company, and knowledge you've given it. Work is routed to whichever agent has the right
          skill, and nothing reaches a customer until a person signs off.
        </P>
      </Section>

      <Section id="principles" title="The three guarantees">
        <P>
          Three constraints hold everywhere in the system. They aren't settings — each is a property
          of the database schema or a transaction boundary, so no code path can quietly opt out.
        </P>
        <CardGrid>
          <DocCard tag="Constraint 01" title="Nothing ships unreviewed">
            Agent work stops at <Code>pending_review</Code>. Approval is blocked while any subtask is
            still open, and compliance-flagged roles require a real authenticated human.
          </DocCard>
          <DocCard tag="Constraint 02" title="Roles are data">
            Adding a capability means inserting a role and attaching skills — never editing the
            router. A hard-coded rule keyed on skill name is a bug by definition.
          </DocCard>
          <DocCard tag="Constraint 03" title="Every change is a row">
            State transitions append to <Code>task_events</Code> in the same transaction that makes
            them, so any output can be reconstructed from the ledger alone.
          </DocCard>
        </CardGrid>
      </Section>

      <Section id="who" title="Who it's for">
        <P>
          Atrium is built for teams that want autonomous agents doing real work but can't accept
          unaccountable output — where a wrong answer has a cost, and someone needs to be able to say
          exactly what a model saw, what it produced, who approved it, and what it spent.
        </P>
      </Section>

      <Section id="next" title="Where to go next">
        <Ul>
          <Li>
            New to the model? Read <Link to="/docs/concepts">Core concepts</Link> for the vocabulary.
          </Li>
          <Li>
            Want to run it? The <Link to="/docs/quickstart">Quickstart</Link> gets the full stack up
            with one command.
          </Li>
          <Li>
            Curious how it's built? Start with <Link to="/docs/architecture">Architecture</Link> and
            the <Link to="/docs/task-lifecycle">Task lifecycle</Link>.
          </Li>
        </Ul>
      </Section>
    </>
  );
}

export function Concepts() {
  return (
    <>
      <Lead>
        A short glossary of the objects you'll work with. Everything in the product maps to one of
        these, and they line up one-to-one with tables in the data model.
      </Lead>

      <Section id="company" title="Company & tenant">
        <P>
          A <strong>company</strong> is a tenant. It owns everything — agents, tasks, budgets,
          memories, channels. Isolation is enforced twice: every query is company-scoped in
          application code, and Postgres row-level security enforces it again at the database. One
          company can never read another's data.
        </P>
      </Section>

      <Section id="agent" title="Agent">
        <P>
          An <strong>agent</strong> is a hired worker. It's created against a role, carries a set of
          skill tags, runs on a chosen model provider, reports to a manager, and has its own token
          budget. An agent runs a <em>runtime</em> — usually the built-in LLM loop that polls its
          skill queue and works tasks.
        </P>
      </Section>

      <Section id="role" title="Role definition">
        <P>
          A <strong>role</strong> is the versioned identity an agent is hired into — its system
          prompt, the tools it's allowed, the output it owes, and whether its work requires
          compliance sign-off. Roles are data: a "Legal" agent is a role row, not a branch in the
          router.
        </P>
      </Section>

      <Section id="task" title="Task">
        <P>
          A <strong>task</strong> is a unit of work. It names a <em>required skill</em>, not an
          agent. Tasks move through a fixed set of states — <Code>queued → claimed → in_progress →
          pending_review → approved</Code> — and can be broken into child tasks that must all finish
          before the parent is approved.
        </P>
      </Section>

      <Section id="skill-mem" title="Skills, memory & knowledge">
        <P>These are the three layers of data an agent draws on, beyond its role:</P>
        <Ul>
          <Li>
            <strong>Skills</strong> — reusable procedures written as markdown, attached to a role or
            an agent.
          </Li>
          <Li>
            <strong>Memory</strong> — what's been learned at this company, recalled by relevance
            blended with recency and use.
          </Li>
          <Li>
            <strong>Knowledge</strong> — reference documents you ingest, retrieved only for the roles
            they're attached to.
          </Li>
        </Ul>
      </Section>

      <Section id="budget" title="Budget & usage">
        <P>
          A <strong>budget</strong> caps token spend for an agent or the whole company over a month.
          Every LLM call is metered into a <strong>usage record</strong> keyed by task and attempt,
          so a redelivered task can never bill twice. Cross the cap and the agent pauses.
        </P>
      </Section>

      <Section id="event" title="Task event & the outbox">
        <P>
          Every state change writes a <strong>task event</strong> and an <strong>outbox</strong> row
          in the same transaction. A relay publishes outbox rows to subscribers; durable consumers
          (learning, stats, chat notices) read them with a cursor. This is how the ledger stays
          complete and how the live dashboard updates.
        </P>
      </Section>
    </>
  );
}

export function Quickstart() {
  return (
    <>
      <Lead>
        The whole stack — Postgres with pgvector, Redis, the core API, and the web app — comes up
        with one command. No JDK or Node install needed to run it.
      </Lead>

      <Section id="run" title="Run the stack">
        <P>
          From the repository root, build and start every service. The <Code>--build</Code> flag
          matters — a plain <Code>up</Code> reuses a stale image.
        </P>
        <CodeBlock label="terminal">docker compose up -d --build</CodeBlock>
        <P>
          This starts the API on <Code>:8080</Code> and the web app on <Code>:5173</Code>. Flyway
          applies every migration on boot; the first start takes a moment while images build.
        </P>
      </Section>

      <Section id="keys" title="Configure a model provider">
        <P>
          Agents need at least one LLM provider key to do work. Docker Compose reads a root{" "}
          <Code>.env</Code> file (not <Code>.env.local</Code>). Set whichever provider you have:
        </P>
        <CodeBlock label=".env">{`ANTHROPIC_API_KEY=sk-ant-...
GOOGLE_API_KEY=...`}</CodeBlock>
        <Callout tone="note" title="No key? Agents still hire, they just park">
          Without a configured provider an agent's runtime parks at a pre-dispatch gate and never
          claims work — no crash, no spend. Add a key and it starts claiming within a poll tick.
        </Callout>
      </Section>

      <Section id="signup" title="Create your first company">
        <P>
          Open <Code>http://localhost:5173</Code> and sign up. A fresh signup lands in an onboarding
          wizard where you can hire a starter team — an engineering pod or a content team — with a
          manager hierarchy already wired.
        </P>
        <P>
          Prefer the command line? A seed script signs up a demo company, hires three agents, and
          prints login credentials:
        </P>
        <CodeBlock label="terminal">cd web && npm run seed:dev</CodeBlock>
      </Section>

      <Section id="first-task" title="Assign real work">
        <P>
          Create a task from the header, describe what you need, and pick the skill it requires. The
          matching agent claims it, works it, and hands it back to the review queue. Approve it and
          the full audit chain — created, claimed, in progress, completed, approved — is visible on
          the task.
        </P>
        <Callout tone="ok" title="That's the loop">
          Hire, assign, review, improve. Everything else in the docs is detail on how each of those
          four moves is made safe under concurrency, crashes, and multi-tenancy.
        </Callout>
      </Section>
    </>
  );
}
