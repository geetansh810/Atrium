import {
  Callout,
  Code,
  DocTable,
  Lead,
  Li,
  P,
  Section,
  Ul,
} from "../docPrimitives";

export function BuildLog() {
  return (
    <>
      <Lead>
        Atrium was built one milestone per session, and every session started by amending a contract
        document rather than a class. Each milestone carried a literal done-when check that had to be
        demonstrated — usually in a browser against a real database, not asserted in a summary.
      </Lead>

      <Section id="phases" title="Five phases">
        <DocTable
          head={["Phase", "What shipped", "Status"]}
          rows={[
            [
              "0 · Foundation",
              "Scaffold, registry, skill routing, the claim loop, the first real agent, approval gate, budget enforcement, outbox relay.",
              "Complete",
            ],
            [
              "Depth (interleaved)",
              "Skills registry, context assembly, memory store, governed learning, knowledge ingestion, a second runtime.",
              "Complete",
            ],
            [
              "1 · Validate",
              "A real business role added through the registry alone, then a live pilot run reviewed end to end by a human who wasn't the builder.",
              "Complete",
            ],
            [
              "2 · Expand",
              "Product and design roles, task decomposition with a flow graph, the budget ledger and analytics, escalations and chat.",
              "Complete",
            ],
            [
              "3 · Harden",
              "Real signup and JWT auth, Postgres row-level security, onboarding packs, rate limiting, infrastructure as code.",
              "Deploy pending",
            ],
            [
              "4 · Compliance",
              "A human sign-off gate no background job can bypass, a full completion audit payload, and the compliance documents.",
              "Complete",
            ],
          ]}
        />
      </Section>

      <Section id="numbers" title="By the numbers">
        <DocTable
          head={["Metric", "Value"]}
          rows={[
            ["Build sessions", "33+"],
            ["Tests passing", "177"],
            ["Migrations applied", "12"],
            ["Backend modules", "9"],
          ]}
        />
      </Section>

      <Section id="method" title="How it was built">
        <P>
          The method was deliberate: contracts first, code second. A single knowledge graph of
          wiki-linked notes let each session load only the context its task touched. Every change was
          demonstrated against a running system, and provider or infrastructure gaps were documented
          honestly rather than glossed over.
        </P>
        <Callout tone="note" title="One open item">
          The only outstanding roadmap item is the live AWS deploy — an owner action requiring real
          credentials and spend. Everything else in phases 0 through 4 is done.
        </Callout>
      </Section>
    </>
  );
}

export function DocumentSet() {
  return (
    <>
      <Lead>
        The implementation answers to a set of contract documents — schema, service interfaces, module
        boundaries, and the compliance posture. This site is written from them; the full source lives
        alongside the code in <Code>atrium-docs/</Code>.
      </Lead>

      <Section id="foundation" title="Foundation">
        <DocTable
          head={["#", "Document", "Covers"]}
          rows={[
            ["00", "Master Plan", "Document map, session priming block, phase overview."],
            ["01", "Product Specification", "Vision, personas, the full feature inventory."],
            ["02", "Architecture", "Services, monorepo layout, the life of a task, tenancy."],
            ["03", "Data Model", "Every table and invariant, the canonical claim query."],
            ["04", "API Contract", "REST endpoints and event payloads."],
            ["05", "Module Specs", "Per-module responsibilities and boundaries."],
            ["06", "Open Source Reuse", "What was forked or mined, and the licence ledger."],
            ["07", "Milestones", "Every milestone with dependencies and a done-when check."],
            ["08", "Conventions", "Code style, migration discipline, the security rules."],
            ["09", "LLM Workflow", "Prompt templates and the AI-assisted review checklist."],
            ["10", "Deployment", "Environments, AWS architecture, CI/CD, observability."],
            ["11", "Session Prompts", "A ready-to-paste context block per milestone."],
          ]}
        />
      </Section>

      <Section id="platform" title="Agent platform">
        <DocTable
          head={["#", "Document", "Covers"]}
          rows={[
            ["12", "Backend Architecture", "Event-driven revision: modules, outbox, topic taxonomy."],
            ["13", "LLM & Runtime SPI", "Normative interfaces for providers and runtimes."],
            ["14", "Skills, Memory & Learning", "The differentiating subsystem in full."],
            ["15", "Data Model Delta", "Agent-platform tables and the migration ledger."],
            ["16", "API Contract Delta", "Skills, memories, review queue, worker gateway."],
            ["17", "Backend Execution Plan", "The build script: one card per session."],
            ["18", "Technical Architecture", "The long-form technical reference."],
          ]}
        />
      </Section>

      <Section id="compliance" title="Compliance">
        <P>Written against what is actually built, and explicit about what isn't:</P>
        <Ul>
          <Li>
            <strong>Retention Policy</strong> — real config keys and windows, and an honest list of
            erasure paths that don't exist yet.
          </Li>
          <Li>
            <strong>Review Process</strong> — how the human sign-off gate works and what the audit
            captures.
          </Li>
          <Li>
            <strong>EU AI Act Posture</strong> — which obligations the technical measures satisfy and
            which remain unbuilt. Not legal advice.
          </Li>
        </Ul>
      </Section>
    </>
  );
}
