import { useEffect, useRef } from "react";
import { Link } from "react-router";
import { PRODUCT_NAME, PRODUCT_TAGLINE } from "../../shared/theme";
import "./LandingPage.css";

// Marketing entry point (M-LP1) — the default page for a signed-out visitor
// in API mode. Mock mode never routes here (see App.tsx); it has no concept
// of a signed-out guest. The shift board below plays out the real task
// lifecycle (create → claim → work → review → approve) with fabricated data —
// no network calls, so it renders identically with or without a live backend.
const WORK = [
  { t: "Diwali gift-box captions", a: "Priya", s: "content" },
  { t: "Tier pricing for launch", a: "Rohan", s: "product" },
  { t: "Premium packaging spec", a: "Meera", s: "design" },
  { t: "String helper + tests", a: "Alex", s: "coding" },
  { t: "Competitor sweep", a: "Priya", s: "research" },
  { t: "Vendor data agreement", a: "Nadia", s: "legal" },
] as const;

type EventKind = "created" | "claimed" | "progress" | "completed" | "approved";

export function LandingPage() {
  const queuedRef = useRef<HTMLDivElement>(null);
  const workingRef = useRef<HTMLDivElement>(null);
  const reviewRef = useRef<HTMLDivElement>(null);
  const approvedRef = useRef<HTMLDivElement>(null);
  const ledgerRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add("in");
            io.unobserve(e.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -60px 0px" },
    );
    document.querySelectorAll(".lp-rv").forEach((el) => io.observe(el));

    const zones = {
      queued: queuedRef.current,
      working: workingRef.current,
      review: reviewRef.current,
      approved: approvedRef.current,
    };
    const ledger = ledgerRef.current;
    if (!ledger || !zones.queued || !zones.working || !zones.review || !zones.approved) {
      return () => io.disconnect();
    }

    let clock = 9 * 60 + 14;
    let seq = 1;
    const timers: number[] = [];

    const stamp = () => {
      clock += 1 + Math.floor(Math.random() * 3);
      const h = Math.floor(clock / 60) % 24;
      const m = clock % 60;
      return `${h < 10 ? "0" : ""}${h}:${m < 10 ? "0" : ""}${m}`;
    };

    const log = (kind: EventKind, task: (typeof WORK)[number]) => {
      const li = document.createElement("li");
      li.innerHTML = `<span>${stamp()}</span><em class="lp-ev-${kind}">${kind}</em><span>${task.t}</span>`;
      ledger.insertBefore(li, ledger.firstChild);
      while (ledger.children.length > 4) ledger.removeChild(ledger.lastChild as Node);
    };

    const card = (task: (typeof WORK)[number], pct: number | null) => {
      const d = document.createElement("div");
      d.className = "lp-card";
      const bar = pct === null ? "" : `<div class="lp-bar-track"><div class="lp-bar-fill" style="width:${pct}%"></div></div>`;
      d.innerHTML = `<b>${task.t}</b><i>${task.a} · ${task.s}</i>${bar}`;
      return d;
    };

    const trim = (zone: HTMLElement, max: number) => {
      while (zone.children.length > max) zone.removeChild(zone.lastChild as Node);
    };

    const run = (task: (typeof WORK)[number]) => {
      const el = card(task, null);
      zones.queued!.insertBefore(el, zones.queued!.firstChild);
      trim(zones.queued!, 2);
      log("created", task);

      timers.push(
        window.setTimeout(() => {
          el.parentNode?.removeChild(el);
          const w = card(task, 12);
          zones.working!.insertBefore(w, zones.working!.firstChild);
          trim(zones.working!, 2);
          log("claimed", task);

          let pct = 12;
          const tick = window.setInterval(() => {
            pct = Math.min(100, pct + 14 + Math.floor(Math.random() * 16));
            const f = w.querySelector<HTMLElement>(".lp-bar-fill");
            if (f) f.style.width = pct + "%";
            if (pct >= 100) {
              window.clearInterval(tick);
              log("progress", task);
              timers.push(
                window.setTimeout(() => {
                  w.parentNode?.removeChild(w);
                  const r = card(task, null);
                  zones.review!.insertBefore(r, zones.review!.firstChild);
                  trim(zones.review!, 2);
                  log("completed", task);

                  timers.push(
                    window.setTimeout(
                      () => {
                        r.parentNode?.removeChild(r);
                        const a = card(task, null);
                        zones.approved!.insertBefore(a, zones.approved!.firstChild);
                        trim(zones.approved!, 2);
                        log("approved", task);
                      },
                      2600 + Math.random() * 1400,
                    ),
                  );
                }, 900),
              );
            }
          }, 620);
        }, 2200 + Math.random() * 900),
      );
    };

    run(WORK[0]);
    timers.push(window.setTimeout(() => run(WORK[1]), 1500));

    let loop: number | undefined;
    if (!reduce) {
      loop = window.setInterval(() => {
        seq = (seq + 1) % WORK.length;
        run(WORK[seq]);
      }, 4200);
    }

    return () => {
      io.disconnect();
      timers.forEach((id) => window.clearTimeout(id));
      if (loop) window.clearInterval(loop);
    };
  }, []);

  return (
    <div className="landing-page">
      <header className="lp-bar">
        <div className="lp-bar-in">
          <a className="lp-brand" href="#top">
            <b>{PRODUCT_NAME}</b>
            <span>{PRODUCT_TAGLINE}</span>
          </a>
          <nav className="lp-bar-nav">
            <a href="#how">How it works</a>
            <a href="#architecture">Architecture</a>
            <a href="#depth">Agent depth</a>
            <a href="#docs">Documentation</a>
            <a href="#journey">Build log</a>
          </nav>
          <div className="lp-bar-auth">
            <Link className="lp-loginlink" to="/login">
              Log in
            </Link>
            <Link className="lp-btn lp-btn-p lp-btn-sm" to="/signup">
              Sign up
            </Link>
          </div>
        </div>
      </header>

      <main id="top">
        {/* ══ HERO ══ */}
        <section className="lp-hero">
          <div className="lp-wrap lp-hero-grid">
            <div>
              <div className="lp-eyebrow">Multi-tenant · Java 21 · Postgres · Event-driven</div>
              <h1>
                AI agents you <em>hire</em>, not prompts you paste.
              </h1>
              <p className="lp-hero-sub">
                Atrium runs a workforce of AI agents the way a company runs staff: hired into roles,
                placed under a manager, given a monthly token budget, and blocked from shipping
                anything a human hasn't signed off on. Every state change lands in an append-only
                ledger.
              </p>
              <div className="lp-hero-meta">
                <span className="lp-tag">Roles are data</span>
                <span className="lp-tag">Any LLM provider</span>
                <span className="lp-tag">Any agent runtime</span>
                <span className="lp-tag">Per-tenant memory</span>
              </div>
              <div className="lp-hero-cta">
                <Link className="lp-btn lp-btn-p" to="/signup">
                  Sign up — it's free to try
                </Link>
                <a className="lp-btn lp-btn-s" href="#how">
                  See the task lifecycle
                </a>
              </div>
              <p className="lp-hero-fine">
                Already hiring agents? <Link to="/login">Log in</Link>.
              </p>
            </div>

            {/* signature element: a live shift board running the real lifecycle */}
            <div
              className="lp-board"
              role="img"
              aria-label="A simulated live board showing tasks moving from queued to working to awaiting review to approved, with an audit event log beneath it."
            >
              <div className="lp-board-hd">
                <b>Team view · derived from live task state</b>
                <span className="lp-live">
                  <i></i>LIVE
                </span>
              </div>
              <div className="lp-zones">
                <div className="lp-zone lp-z-q">
                  <div className="lp-zone-hd">Queued</div>
                  <div ref={queuedRef}></div>
                </div>
                <div className="lp-zone lp-z-w">
                  <div className="lp-zone-hd">Working</div>
                  <div ref={workingRef}></div>
                </div>
                <div className="lp-zone lp-z-r">
                  <div className="lp-zone-hd">Awaiting review</div>
                  <div ref={reviewRef}></div>
                </div>
                <div className="lp-zone lp-z-a">
                  <div className="lp-zone-hd">Approved</div>
                  <div ref={approvedRef}></div>
                </div>
              </div>
              <div className="lp-ledger">
                <div className="lp-ledger-hd">task_events · append-only</div>
                <ul ref={ledgerRef}></ul>
              </div>
            </div>
          </div>
        </section>

        {/* ══ INVARIANTS ══ */}
        <section id="why">
          <div className="lp-wrap lp-rv">
            <div className="lp-eyebrow">What the system refuses to do</div>
            <h2 className="lp-sec-title">Three constraints, enforced structurally.</h2>
            <p className="lp-sec-lede">
              Most agent frameworks make these promises in a README. In Atrium each one is a
              property of the schema or the transaction boundary — there is no code path that can
              quietly opt out.
            </p>

            <div className="lp-rules">
              <div className="lp-rule">
                <div className="lp-rule-k">Constraint 01</div>
                <h3>Nothing ships unreviewed.</h3>
                <p>
                  An agent's work stops at <code>pending_review</code>. Approval is blocked while
                  any child task or checklist item is still open, and roles flagged for compliance
                  reject approval outright unless a real authenticated human is on the call — a
                  background job cannot sign off.
                </p>
              </div>
              <div className="lp-rule">
                <div className="lp-rule-k">Constraint 02</div>
                <h3>Roles are data, never branches.</h3>
                <p>
                  Adding a Legal agent means inserting a role definition and attaching skills. A{" "}
                  <code>if (skill == …)</code> in the router is a bug by definition. A whole pilot
                  role shipped with an empty diff on the routing module.
                </p>
              </div>
              <div className="lp-rule">
                <div className="lp-rule-k">Constraint 03</div>
                <h3>Every change is a row.</h3>
                <p>
                  State transitions append to <code>task_events</code> in the same transaction that
                  makes them, alongside an outbox row. A completion carries its model, prompt
                  version and recalled context, so any output can be reconstructed from the ledger
                  alone.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* ══ LIFECYCLE ══ */}
        <section id="how">
          <div className="lp-wrap lp-rv">
            <div className="lp-eyebrow">The life of a task</div>
            <h2 className="lp-sec-title">Seven steps from request to signed-off work.</h2>
            <p className="lp-sec-lede">
              This is the only path work can take. Each step names the mechanism that makes it
              safe under concurrency, crashes and redelivery.
            </p>

            <div className="lp-steps">
              <div className="lp-step">
                <div className="lp-step-n">01</div>
                <div className="lp-step-t">
                  <h3>Create</h3>
                  <code>status = queued</code>
                </div>
                <div className="lp-step-d">
                  A task names a <b>required skill</b>, not an agent. Routing checks the company's
                  roster actually covers that skill and rejects the request with a field error if
                  it doesn't.
                </div>
              </div>
              <div className="lp-step">
                <div className="lp-step-n">02</div>
                <div className="lp-step-t">
                  <h3>Claim</h3>
                  <code>FOR UPDATE SKIP LOCKED</code>
                </div>
                <div className="lp-step-d">
                  Agent runners poll their skill queues. Postgres decides the winner — three
                  workers racing twenty tasks produce twenty distinct claims and zero double-work.
                  The claim takes a <b>10-minute lease</b> and increments the attempt counter.
                </div>
              </div>
              <div className="lp-step">
                <div className="lp-step-n">03</div>
                <div className="lp-step-t">
                  <h3>Assemble context</h3>
                  <code>ContextAssembler</code>
                </div>
                <div className="lp-step-d">
                  One doorway builds the prompt under a token budget: role identity, attached
                  skills, recalled memories, retrieved knowledge. <b>The ids of everything recalled
                  are written into the claim event</b>, so the input is auditable, not inferred.
                </div>
              </div>
              <div className="lp-step">
                <div className="lp-step-n">04</div>
                <div className="lp-step-t">
                  <h3>Work</h3>
                  <code>status = in_progress</code>
                </div>
                <div className="lp-step-d">
                  The runtime calls the model through a provider-neutral SPI. Token spend is
                  metered per call against an idempotency key of <b>task id plus attempt</b> — a
                  redelivered task can never bill twice.
                </div>
              </div>
              <div className="lp-step">
                <div className="lp-step-n">05</div>
                <div className="lp-step-t">
                  <h3>Hand off</h3>
                  <code>status = pending_review</code>
                </div>
                <div className="lp-step-d">
                  Output is stored as an artifact and the task stops. A bot notice posts to the
                  company channel; the escalation queue is two clicks from login.
                </div>
              </div>
              <div className="lp-step">
                <div className="lp-step-n">06</div>
                <div className="lp-step-t">
                  <h3>Review</h3>
                  <code>approve · reject</code>
                </div>
                <div className="lp-step-d">
                  Rejection stores the reviewer's feedback and requeues the task, so the retry gets
                  a <b>fresh attempt number and a clean budget key</b> — and the feedback is
                  prepended to the next prompt.
                </div>
              </div>
              <div className="lp-step">
                <div className="lp-step-n">07</div>
                <div className="lp-step-t">
                  <h3>Learn</h3>
                  <code>governed extraction</code>
                </div>
                <div className="lp-step-d">
                  A durable consumer reads the review outcome and extracts lessons. Agent-scoped
                  lessons activate on their own; <b>anything company-wide waits in a human review
                  queue</b> before it can influence another agent.
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ══ ARCHITECTURE ══ */}
        <section id="architecture">
          <div className="lp-wrap lp-rv">
            <div className="lp-eyebrow">Backend shape</div>
            <h2 className="lp-sec-title">A modular monolith with an event backbone.</h2>
            <p className="lp-sec-lede">
              Nine packages with one-directional dependencies and no circular imports. Event-driven
              is a shape, not a broker purchase — the outbox gives at-least-once delivery today and
              swaps to Kafka later without touching a producer.
            </p>

            <div className="lp-arch">
              <div className="lp-mods">
                <div className="lp-mod">
                  <b>registry</b>
                  <span>Companies, users, agents, role definitions, model catalog.</span>
                </div>
                <div className="lp-mod">
                  <b>routing</b>
                  <span>Tasks, skill queues, claim &amp; lease, the task graph.</span>
                </div>
                <div className="lp-mod">
                  <b>execution</b>
                  <span>LLM provider SPI, agent runtimes, pure prompt assembly.</span>
                </div>
                <div className="lp-mod lp-mod-new">
                  <b>agentmind</b>
                  <span>Skills, memory, knowledge, the learning pipeline.</span>
                </div>
                <div className="lp-mod">
                  <b>accountability</b>
                  <span>Budgets, usage ledger, approvals, analytics rollups.</span>
                </div>
                <div className="lp-mod lp-mod-new">
                  <b>communication</b>
                  <span>Channels, announcements, bot notices on completion.</span>
                </div>
                <div className="lp-mod lp-mod-new">
                  <b>eventbus</b>
                  <span>Outbox writer, relay, durable consumer cursors.</span>
                </div>
                <div className="lp-mod">
                  <b>realtimebridge</b>
                  <span>Redis publisher feeding the live dashboard.</span>
                </div>
                <div className="lp-mod">
                  <b>common</b>
                  <span>Tenant context, problem+json errors, clock, ids.</span>
                </div>
              </div>

              <div className="lp-spine">
                <h3>The outbox backbone</h3>
                <p>How a state change becomes an event that no consumer can miss.</p>
                <ul className="lp-flow">
                  <li>
                    <b>1 · Same transaction</b>The business write, its audit row and its outbox row
                    commit together or not at all.
                  </li>
                  <li>
                    <b>2 · Relay</b>A 250ms scheduled batch claims unpublished rows with the same
                    skip-locked idiom, publishes to Redis, stamps them.
                  </li>
                  <li>
                    <b>3 · Projection consumers</b>Lossy is fine. The dashboard self-heals on its
                    next poll.
                  </li>
                  <li>
                    <b>4 · Durable consumers</b>Learning, stats and chat notices keep a cursor row. A
                    crash replays; idempotency keys absorb it.
                  </li>
                  <li>
                    <b>5 · Retention</b>A nightly job purges only what is published and behind every
                    cursor.
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        {/* ══ DEPTH ══ */}
        <section id="depth">
          <div className="lp-wrap lp-rv">
            <div className="lp-eyebrow">The differentiator</div>
            <h2 className="lp-sec-title">Agents get deeper at one company over time.</h2>
            <p className="lp-sec-lede">
              An agent is not one prompt with a name tag. What it knows is four layers of data,
              drawn on at prompt time under a token budget — and these four tables are the{" "}
              <em>only</em> stored content that can reach a model. Secrets live nowhere near them.
            </p>

            <div className="lp-layers">
              <div className="lp-layer">
                <div className="lp-layer-n">
                  Identity
                  <small>Rarely changes</small>
                </div>
                <div className="lp-layer-t">role_definitions</div>
                <div className="lp-layer-d">
                  Versioned. Who the agent is, what it may use, and the <em>output contract</em> it
                  owes. Always present in full.
                </div>
              </div>
              <div className="lp-layer">
                <div className="lp-layer-n">
                  Skills
                  <small>Curated</small>
                </div>
                <div className="lp-layer-t">skills</div>
                <div className="lp-layer-d">
                  Versioned procedures written as markdown, attached to a role or an agent. An
                  agent-proposed skill stays <em>inert</em> until a human promotes it.
                </div>
              </div>
              <div className="lp-layer">
                <div className="lp-layer-n">
                  Memory
                  <small>Continuous, governed</small>
                </div>
                <div className="lp-layer-t">memories</div>
                <div className="lp-layer-d">
                  What has been learned at <em>this</em> company — "the CEO rejects passive voice."
                  Recalled by vector similarity blended with recency and use.
                </div>
              </div>
              <div className="lp-layer">
                <div className="lp-layer-n">
                  Knowledge
                  <small>On ingest</small>
                </div>
                <div className="lp-layer-t">knowledge_docs</div>
                <div className="lp-layer-d">
                  Reference material — a brand guide, a product catalog — chunked, embedded, and
                  retrieved only for the roles it was attached to.
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ══ DOCS ══ */}
        <section id="docs" className="lp-library">
          <div className="lp-wrap lp-rv">
            <div className="lp-eyebrow">The paper trail</div>
            <h2 className="lp-sec-title">Nineteen contracts, written before the code.</h2>
            <p className="lp-sec-lede">
              Atrium was built one milestone per session, and every session started by amending a
              document rather than a class. These are the contracts the implementation answers to
              — schema, SPIs, module boundaries, and the compliance posture.
            </p>
            <p className="lp-lib-note">atrium-docs/ · open INDEX.html for the visual hub</p>

            <div className="lp-shelf">
              <div className="lp-shelf-hd">Foundation — vision through conventions</div>
              <div className="lp-files">
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>00</span>
                    <em>index</em>
                  </div>
                  <b>Master Plan</b>
                  <span>
                    Document map, the context priming block pasted at the start of every session,
                    and the phase overview.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>01</span>
                    <em>product</em>
                  </div>
                  <b>Product Specification</b>
                  <span>
                    Vision, personas, and the complete feature inventory: roster, tasks, budgets,
                    approvals, analytics.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>02</span>
                    <em>system</em>
                  </div>
                  <b>Architecture</b>
                  <span>
                    Services, monorepo layout, the life of a task, the realtime contract, and the
                    tenancy model.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>03</span>
                    <em>schema</em>
                  </div>
                  <b>Data Model</b>
                  <span>
                    Every table and invariant, including the canonical skip-locked claim query and
                    the pagination rule.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>04</span>
                    <em>api</em>
                  </div>
                  <b>API Contract</b>
                  <span>
                    REST endpoints and websocket event payloads for registry, routing,
                    accountability and communication.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>05</span>
                    <em>modules</em>
                  </div>
                  <b>Module Specs</b>
                  <span>
                    Per-module responsibilities and the boundaries that keep one session's work
                    from touching the world.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>06</span>
                    <em>reuse</em>
                  </div>
                  <b>Open Source Reuse</b>
                  <span>
                    What was forked, what was mined for patterns, and the licence ledger for every
                    borrowed asset.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>07</span>
                    <em>plan</em>
                  </div>
                  <b>Milestones</b>
                  <span>
                    All milestones with dependencies and a literal done-when check, plus deferred
                    and retired tracks.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>08</span>
                    <em>rules</em>
                  </div>
                  <b>Conventions</b>
                  <span>
                    Code style, migration discipline, testing bar, and the eight security rules
                    including tenant isolation.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>09</span>
                    <em>method</em>
                  </div>
                  <b>LLM Workflow</b>
                  <span>
                    Prompt templates and the review checklist applied to every line of AI-assisted
                    code before merge.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>10</span>
                    <em>ops</em>
                  </div>
                  <b>Deployment</b>
                  <span>
                    Environments, the AWS architecture, CI/CD, observability, and backup posture.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>11</span>
                    <em>sessions</em>
                  </div>
                  <b>Session Prompts</b>
                  <span>
                    A ready-to-paste prompt block for every milestone, so a session starts with the
                    right context loaded.
                  </span>
                </div>
              </div>

              <div className="lp-shelf-hd">Agent platform — the pluggability contracts</div>
              <div className="lp-files">
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>12</span>
                    <em>backend</em>
                  </div>
                  <b>Backend Architecture</b>
                  <span>
                    The event-driven revision: module map, transactional outbox, topic taxonomy,
                    gateways, scaling path.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>13</span>
                    <em>spi</em>
                  </div>
                  <b>LLM &amp; Runtime SPI</b>
                  <span>
                    Normative Java signatures. Adding a provider is one adapter class; adding a
                    model is table rows.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>14</span>
                    <em>depth</em>
                  </div>
                  <b>Skills, Memory &amp; Learning</b>
                  <span>
                    The differentiating subsystem: skills registry, memory store, context assembly,
                    governed learning.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>15</span>
                    <em>schema</em>
                  </div>
                  <b>Data Model Delta</b>
                  <span>
                    The agent-platform tables and the migration ledger, renumbered by real
                    application order.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>16</span>
                    <em>api</em>
                  </div>
                  <b>API Contract Delta</b>
                  <span>
                    Skills, memories, the review queue, the model catalog, and the worker gateway
                    endpoints.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>17</span>
                    <em>build</em>
                  </div>
                  <b>Backend Execution Plan</b>
                  <span>
                    The build script: one card per session with its paste list, done-when, test
                    matrix and config keys.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>18</span>
                    <em>reference</em>
                  </div>
                  <b>Technical Architecture</b>
                  <span>
                    The long-form technical reference assembled once the platform was complete —
                    the deepest single document.
                  </span>
                </div>
              </div>

              <div className="lp-shelf-hd">Compliance — written against what is actually built</div>
              <div className="lp-files">
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>C1</span>
                    <em>data</em>
                  </div>
                  <b>Retention Policy</b>
                  <span>
                    Real config keys and retention windows — and an honest list of the erasure
                    paths that do not exist yet.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>C2</span>
                    <em>process</em>
                  </div>
                  <b>Review Process</b>
                  <span>
                    How the human sign-off gate works for roles marked as requiring review, and
                    what the audit trail captures.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>C3</span>
                    <em>posture</em>
                  </div>
                  <b>EU AI Act Posture</b>
                  <span>
                    Which obligations the technical measures satisfy, and which remain entirely
                    unbuilt. Not legal advice.
                  </span>
                </div>
              </div>

              <div className="lp-shelf-hd">Research &amp; navigation</div>
              <div className="lp-files">
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>N1</span>
                    <em>notes</em>
                  </div>
                  <b>Paperclip Findings</b>
                  <span>
                    Patterns mined for the adapter SPI, skills-as-data, the two-layer memory model
                    and budget tiers.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>N2</span>
                    <em>notes</em>
                  </div>
                  <b>Agent Mesh Findings</b>
                  <span>
                    Topic taxonomy, outbox-as-mesh, gateways, and the deliberate divergences from
                    that design.
                  </span>
                </div>
                <div className="lp-file">
                  <div className="lp-file-id">
                    <span>G</span>
                    <em>graph</em>
                  </div>
                  <b>Project Graph</b>
                  <span>
                    Seventeen wiki-linked notes — one per module — so a session loads only the
                    context its task touches.
                  </span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ══ JOURNEY ══ */}
        <section id="journey">
          <div className="lp-wrap lp-rv">
            <div className="lp-eyebrow">Build log</div>
            <h2 className="lp-sec-title">Five phases, one milestone per session.</h2>
            <p className="lp-sec-lede">
              Every milestone carried a literal done-when check that had to be demonstrated —
              usually in a browser against a real database, not asserted in a summary.
            </p>

            <div className="lp-phases">
              <div className="lp-phase">
                <div className="lp-phase-n">
                  Foundation
                  <small>Phase 0</small>
                </div>
                <div className="lp-phase-d">
                  Scaffold, registry, skill routing, the claim loop, the first real agent, approval
                  gate, budget enforcement, outbox relay.
                </div>
                <div className="lp-phase-s">Complete</div>
              </div>
              <div className="lp-phase">
                <div className="lp-phase-n">
                  Depth
                  <small>Interleaved</small>
                </div>
                <div className="lp-phase-d">
                  Skills registry, context assembly, the memory store, the governed learning
                  pipeline, knowledge ingestion, a second runtime.
                </div>
                <div className="lp-phase-s">Complete</div>
              </div>
              <div className="lp-phase">
                <div className="lp-phase-n">
                  Validate
                  <small>Phase 1</small>
                </div>
                <div className="lp-phase-d">
                  A real business role added through the registry alone, then a live pilot run
                  reviewed end to end by a human who wasn't the builder.
                </div>
                <div className="lp-phase-s">Complete</div>
              </div>
              <div className="lp-phase">
                <div className="lp-phase-n">
                  Expand
                  <small>Phase 2</small>
                </div>
                <div className="lp-phase-d">
                  Product and design roles, task decomposition with a flow graph, the budget ledger
                  and analytics, escalations and chat.
                </div>
                <div className="lp-phase-s">Complete</div>
              </div>
              <div className="lp-phase">
                <div className="lp-phase-n">
                  Harden
                  <small>Phase 3</small>
                </div>
                <div className="lp-phase-d">
                  Real signup and JWT auth, Postgres row-level security as a second isolation layer,
                  onboarding packs, rate limiting and infrastructure as code.
                </div>
                <div className="lp-phase-s lp-partial">Deploy pending</div>
              </div>
              <div className="lp-phase">
                <div className="lp-phase-n">
                  Compliance
                  <small>Phase 4</small>
                </div>
                <div className="lp-phase-d">
                  A human sign-off gate no background job can bypass, a full completion audit
                  payload, and the compliance documents above.
                </div>
                <div className="lp-phase-s">Complete</div>
              </div>
            </div>

            <div className="lp-counts">
              <div className="lp-count">
                <b>33</b>
                <span>Build sessions</span>
              </div>
              <div className="lp-count">
                <b>177</b>
                <span>Tests passing</span>
              </div>
              <div className="lp-count">
                <b>12</b>
                <span>Migrations applied</span>
              </div>
              <div className="lp-count">
                <b>9</b>
                <span>Backend modules</span>
              </div>
            </div>
          </div>
        </section>

        {/* ══ STACK ══ */}
        <section id="stack">
          <div className="lp-wrap lp-rv">
            <div className="lp-eyebrow">What it runs on</div>
            <h2 className="lp-sec-title">Boring infrastructure, carefully used.</h2>
            <p className="lp-sec-lede">
              No broker, no orchestration framework, no vector database. Postgres does the
              queueing, the isolation and the similarity search — which is why the whole thing
              starts with one compose file.
            </p>

            <div className="lp-stack">
              <div className="lp-st">
                <div className="lp-st-h">Core API</div>
                <ul>
                  <li>
                    <b>Java 21</b> — virtual threads per agent
                  </li>
                  <li>
                    <b>Spring Boot 3.5</b> — modular monolith
                  </li>
                  <li>
                    <b>Flyway</b> — migrations, never edited
                  </li>
                  <li>
                    <b>Testcontainers</b> — real Postgres in tests
                  </li>
                </ul>
              </div>
              <div className="lp-st">
                <div className="lp-st-h">Data</div>
                <ul>
                  <li>
                    <b>Postgres 16</b> — source of truth
                  </li>
                  <li>
                    <b>pgvector</b> — memory and knowledge recall
                  </li>
                  <li>
                    <b>Row-level security</b> — second isolation layer
                  </li>
                  <li>
                    <b>Redis</b> — pub/sub and rate limiting
                  </li>
                </ul>
              </div>
              <div className="lp-st">
                <div className="lp-st-h">Web</div>
                <ul>
                  <li>
                    <b>React + TypeScript</b> — strict, no any
                  </li>
                  <li>
                    <b>React Query</b> — live polling
                  </li>
                  <li>
                    <b>Vite</b> — dev and build
                  </li>
                  <li>
                    <b>Design tokens</b> — no hardcoded colour
                  </li>
                </ul>
              </div>
              <div className="lp-st">
                <div className="lp-st-h">Operations</div>
                <ul>
                  <li>
                    <b>Terraform</b> — ECS, RDS, CloudFront
                  </li>
                  <li>
                    <b>GitHub Actions</b> — CI and staged deploy
                  </li>
                  <li>
                    <b>Structured logs</b> — JSON with tenant context
                  </li>
                  <li>
                    <b>k6</b> — load tested at p95 38ms
                  </li>
                </ul>
              </div>
            </div>

            <div className="lp-final-cta">
              <h3>Ready to hire your first agent?</h3>
              <Link className="lp-btn lp-btn-p" to="/signup">
                Sign up — it's free to try
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="lp-footer">
        <div className="lp-wrap lp-foot">
          <p>
            {PRODUCT_NAME} · {PRODUCT_TAGLINE} — built by Geetansh Agrawal, one milestone per
            session.
          </p>
          <div className="lp-foot-links">
            <a href="#how">Lifecycle</a>
            <a href="#architecture">Architecture</a>
            <a href="#docs">Documentation</a>
            <a href="#journey">Build log</a>
            <Link to="/signup">Sign up</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
