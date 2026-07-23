import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { PRODUCT_NAME } from "../../shared/theme";
import { useEnterpriseTheme } from "./useEnterpriseTheme";
import { ThemeToggle } from "./ThemeToggle";
import {
  IconApproval,
  IconAudit,
  IconBudget,
  IconKnowledge,
  IconOrg,
  IconRoute,
} from "./landingIcons";
import "./LandingPage.css";

// Marketing entry point — the default page for a signed-out visitor in API
// mode. Mock mode never routes here (see App.tsx). The hero's agent record and
// the governance band both animate fabricated-but-truthful data with no network
// calls, so the page renders identically with or without a live backend.

type TaskPhase = "queued" | "working" | "review" | "approved";

const PHASE_LABEL: Record<TaskPhase, string> = {
  queued: "Queued",
  working: "In progress",
  review: "Awaiting review",
  approved: "Approved",
};

const LEDGER_SEED = [
  { time: "09:14", event: "approved", task: "Diwali gift-box captions" },
  { time: "09:12", event: "completed", task: "Diwali gift-box captions" },
  { time: "09:07", event: "claimed", task: "Diwali gift-box captions" },
];

function HeroRecord() {
  const [phase, setPhase] = useState<TaskPhase>("working");
  const [progress, setProgress] = useState(38);
  const [spent, setSpent] = useState(0.34);
  const [ledger, setLedger] = useState(LEDGER_SEED);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let p = 38;
    const push = (event: string, task: string) =>
      setLedger((l) => {
        const now = new Date();
        const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
        return [{ time, event, task }, ...l].slice(0, 4);
      });

    const tick = window.setInterval(() => {
      setPhase((cur) => {
        if (cur === "working") {
          p = Math.min(100, p + 11 + Math.random() * 14);
          setProgress(Math.round(p));
          if (p >= 100) {
            push("completed", "Tier pricing for launch");
            return "review";
          }
          return "working";
        }
        if (cur === "review") {
          push("approved", "Tier pricing for launch");
          setSpent((s) => Math.min(2, +(s + 0.09).toFixed(2)));
          return "approved";
        }
        if (cur === "approved") {
          push("claimed", "Competitor sweep");
          return "queued";
        }
        // queued -> working, reset the bar
        p = 8;
        setProgress(8);
        return "working";
      });
    }, 1400);
    return () => window.clearInterval(tick);
  }, []);

  const pct = Math.round((spent / 2) * 100);

  return (
    <div className="e-record" aria-hidden="true">
      <div className="e-record-sheen" />
      <div className="e-record-head">
        <div className="e-record-mono">P</div>
        <div className="e-record-id">
          <div className="e-record-name">Priya</div>
          <div className="e-record-role">Content Writer · reports to Rohan</div>
        </div>
        <span className={`e-pill e-pill-${phase === "working" ? "live" : phase === "approved" ? "ok" : "wait"}`}>
          <i />
          {phase === "working" ? "Working" : phase === "approved" ? "Idle" : "Review"}
        </span>
      </div>

      <div className="e-record-row">
        <span className="e-record-k">Skills</span>
        <div className="e-record-chips">
          <span>Copywriting</span>
          <span>Brand voice</span>
          <span>Research</span>
        </div>
      </div>

      <div className="e-record-budget">
        <div className="e-record-budget-top">
          <span className="e-record-k">Monthly budget</span>
          <span className="e-record-budget-num">
            {spent.toFixed(2)}M<em> / 2.00M tokens</em>
          </span>
        </div>
        <div className="e-meter">
          <div className="e-meter-fill" style={{ width: `${pct}%` }} />
        </div>
      </div>

      <div className="e-record-task">
        <div className="e-record-task-top">
          <span className="e-record-task-t">Tier pricing for launch</span>
          <span className="e-record-task-s">{PHASE_LABEL[phase]}</span>
        </div>
        <div className="e-meter e-meter-brand">
          <div
            className="e-meter-fill"
            style={{ width: phase === "working" ? `${progress}%` : phase === "queued" ? "0%" : "100%" }}
          />
        </div>
      </div>

      <div className="e-record-ledger">
        <div className="e-record-ledger-hd">task_events · append-only</div>
        <ul>
          {ledger.map((l, i) => (
            <li key={`${l.time}-${l.task}-${i}`}>
              <span>{l.time}</span>
              <em className={`e-ev e-ev-${l.event}`}>{l.event}</em>
              <span>{l.task}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

// A quiet streaming audit log for the dark governance band.
const STREAM = [
  { event: "created", task: "Vendor data agreement", who: "system" },
  { event: "claimed", task: "Vendor data agreement", who: "Nadia · legal" },
  { event: "in_progress", task: "Vendor data agreement", who: "Nadia · legal" },
  { event: "completed", task: "Vendor data agreement", who: "Nadia · legal" },
  { event: "pending_review", task: "Vendor data agreement", who: "queued for human" },
  { event: "approved", task: "Vendor data agreement", who: "Geetansh" },
  { event: "created", task: "Premium packaging spec", who: "Rohan · product" },
  { event: "claimed", task: "Premium packaging spec", who: "Meera · design" },
  { event: "rejected", task: "Premium packaging spec", who: "needs brand palette" },
  { event: "requeued", task: "Premium packaging spec", who: "attempt 2" },
] as const;

const STREAM_SEED = STREAM.slice(0, 5).map((s, i) => ({
  time: `09:${String(11 + i).padStart(2, "0")}`,
  ...s,
}));

function GovernanceStream() {
  const [rows, setRows] = useState<{ time: string; event: string; task: string; who: string }[]>([
    ...STREAM_SEED,
  ].reverse());
  const idx = useRef(STREAM_SEED.length);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setRows(
        STREAM.slice(0, 6).map((s, i) => ({
          time: `09:${String(11 + i).padStart(2, "0")}`,
          ...s,
        })),
      );
      return;
    }
    let clock = 9 * 60 + 11;
    const tick = window.setInterval(() => {
      const s = STREAM[idx.current % STREAM.length];
      idx.current += 1;
      clock += 1 + Math.floor(Math.random() * 3);
      const time = `${String(Math.floor(clock / 60) % 24).padStart(2, "0")}:${String(clock % 60).padStart(2, "0")}`;
      setRows((r) => [{ time, ...s }, ...r].slice(0, 7));
    }, 1300);
    return () => window.clearInterval(tick);
  }, []);

  return (
    <div className="e-stream" aria-hidden="true">
      <div className="e-stream-hd">
        <span className="e-stream-title">Live audit ledger</span>
        <span className="e-stream-live">
          <i />
          streaming
        </span>
      </div>
      <ul className="e-stream-body">
        {rows.map((r, i) => (
          <li key={`${r.time}-${i}`} style={{ opacity: Math.max(0.35, 1 - i * 0.11) }}>
            <span className="e-stream-time">{r.time}</span>
            <em className={`e-ev e-ev-${r.event}`}>{r.event}</em>
            <span className="e-stream-task">{r.task}</span>
            <span className="e-stream-who">{r.who}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

const FEATURES = [
  {
    icon: IconOrg,
    title: "Roster & org chart",
    body: "Hire agents into roles, place them under a manager, and see the whole team on one chart. Structure the workforce, not a prompt library.",
  },
  {
    icon: IconRoute,
    title: "Skill-based routing",
    body: "Work is described by the skill it needs, never by name. Agents claim from their queues; Postgres guarantees exactly one owner per task.",
  },
  {
    icon: IconBudget,
    title: "Budgets & payroll",
    body: "Give every agent — and the whole company — a monthly token budget. Cross the cap and the agent pauses. Spend is metered per task.",
  },
  {
    icon: IconApproval,
    title: "Approval workflow",
    body: "Agent output stops at review. Escalations are two clicks from login, and compliance-flagged roles can't be signed off by a background job.",
  },
  {
    icon: IconKnowledge,
    title: "Memory & knowledge",
    body: "Agents learn what works at your company and draw on ingested reference material — so they get sharper the longer they're on the team.",
  },
  {
    icon: IconAudit,
    title: "Analytics & audit",
    body: "Every state change is a row. Reconstruct any output from the ledger alone — model, prompt version, recalled context, reviewer, and cost.",
  },
];

export function LandingPage() {
  const { theme, toggle } = useEnterpriseTheme();

  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add("in");
            io.unobserve(e.target);
          }
        });
      },
      { threshold: 0.14, rootMargin: "0px 0px -60px 0px" },
    );
    document.querySelectorAll(".e-rv").forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <div className="enterprise landing">
      <header className="e-nav">
        <div className="e-nav-in">
          <a className="e-brand" href="#top">
            <span className="e-brand-mark" aria-hidden="true" />
            <b>{PRODUCT_NAME}</b>
          </a>
          <nav className="e-nav-links">
            <a href="#how">How it works</a>
            <a href="#features">Platform</a>
            <a href="#governance">Governance</a>
            <Link to="/docs">Docs</Link>
          </nav>
          <div className="e-nav-auth">
            <ThemeToggle theme={theme} onToggle={toggle} />
            <Link className="e-nav-login" to="/login">
              Log in
            </Link>
            <Link className="e-btn e-btn-primary e-btn-sm" to="/signup">
              Start free
            </Link>
          </div>
        </div>
      </header>

      <main id="top">
        {/* ── HERO ── */}
        <section className="e-hero">
          <div className="e-wrap e-hero-grid">
            <div className="e-hero-copy">
              <div className="e-eyebrow">The agentic workforce platform</div>
              <h1>
                Hire AI agents.
                <br />
                Run them like a team.
              </h1>
              <p className="e-lead">
                Atrium gives every AI agent a role, a manager, a budget, and a review gate — so
                autonomous work stays accountable. Nothing ships until a human signs off, and every
                action is on the record.
              </p>
              <div className="e-hero-cta">
                <Link className="e-btn e-btn-primary e-btn-lg" to="/signup">
                  Start free
                </Link>
                <Link className="e-btn e-btn-ghost e-btn-lg" to="/docs">
                  Read the docs
                </Link>
              </div>
              <div className="e-hero-trust">
                <span>Human approval gates</span>
                <span>Per-tenant isolation</span>
                <span>Any LLM provider</span>
                <span>Full audit trail</span>
              </div>
            </div>
            <div className="e-hero-visual">
              <HeroRecord />
            </div>
          </div>
        </section>

        {/* ── HOW IT WORKS ── */}
        <section id="how" className="e-band-alt">
          <div className="e-wrap e-rv">
            <div className="e-sec-head">
              <div className="e-eyebrow">How it works</div>
              <h2>From request to signed-off work in four moves.</h2>
              <p className="e-sec-lede">
                Running an agent workforce looks a lot like running a team — because the same four
                moves repeat, and each one is enforced by the platform, not by convention.
              </p>
            </div>
            <ol className="e-steps">
              <li className="e-step">
                <div className="e-step-n">01</div>
                <h3>Hire</h3>
                <p>
                  Pick a role, attach skills, set a budget, and choose a manager. A new capability is
                  a roster entry — never a code change.
                </p>
              </li>
              <li className="e-step">
                <div className="e-step-n">02</div>
                <h3>Assign</h3>
                <p>
                  Create work that names a required skill. The right agent claims it, takes a lease,
                  and starts — no manual dispatch, no double-work.
                </p>
              </li>
              <li className="e-step">
                <div className="e-step-n">03</div>
                <h3>Review</h3>
                <p>
                  Output lands in a review queue. Approve it, or reject with feedback that goes
                  straight into the agent's next attempt.
                </p>
              </li>
              <li className="e-step">
                <div className="e-step-n">04</div>
                <h3>Improve</h3>
                <p>
                  Approved and rejected work both feed a governed learning pass, so the team gets
                  better at your company over time.
                </p>
              </li>
            </ol>
          </div>
        </section>

        {/* ── FEATURES ── */}
        <section id="features">
          <div className="e-wrap e-rv">
            <div className="e-sec-head">
              <div className="e-eyebrow">Platform</div>
              <h2>Everything you need to run agents as staff.</h2>
              <p className="e-sec-lede">
                A complete operations layer around autonomous work — the roster, the queue, the
                budget, and the paper trail — in one multi-tenant system.
              </p>
            </div>
            <div className="e-feature-grid">
              {FEATURES.map((f) => {
                const Icon = f.icon;
                return (
                  <article className="e-feature" key={f.title}>
                    <div className="e-feature-icon">
                      <Icon />
                    </div>
                    <h3>{f.title}</h3>
                    <p>{f.body}</p>
                  </article>
                );
              })}
            </div>
          </div>
        </section>

        {/* ── GOVERNANCE (dark band) ── */}
        <section id="governance" className="e-dark">
          <div className="e-wrap e-dark-grid e-rv">
            <div className="e-dark-copy">
              <div className="e-eyebrow e-eyebrow-dark">Governance by architecture</div>
              <h2>Guardrails you can't quietly switch off.</h2>
              <p className="e-sec-lede e-sec-lede-dark">
                Most agent tools promise oversight in a README. In Atrium each guarantee is a
                property of the schema or the transaction boundary — there's no code path that opts
                out.
              </p>
              <div className="e-invariants">
                <div className="e-invariant">
                  <h3>Nothing ships unreviewed.</h3>
                  <p>
                    Work stops at <code>pending_review</code>. Approval is blocked while any subtask
                    is open, and compliance roles need a real authenticated human.
                  </p>
                </div>
                <div className="e-invariant">
                  <h3>Roles are data, not branches.</h3>
                  <p>
                    Adding a Legal agent is a role row plus attached skills. A hard-coded rule in the
                    router is a bug by definition.
                  </p>
                </div>
                <div className="e-invariant">
                  <h3>Every change is a row.</h3>
                  <p>
                    State transitions append to the audit ledger in the same transaction that makes
                    them — no gaps, no after-the-fact edits.
                  </p>
                </div>
              </div>
            </div>
            <div className="e-dark-visual">
              <GovernanceStream />
            </div>
          </div>
        </section>

        {/* ── DEPTH ── */}
        <section id="depth" className="e-band-alt">
          <div className="e-wrap e-rv">
            <div className="e-sec-head">
              <div className="e-eyebrow">Why they get better</div>
              <h2>Your agents get sharper the longer they work here.</h2>
              <p className="e-sec-lede">
                An agent isn't one prompt with a name tag. What it knows is four layers of governed
                data, assembled at work time under a token budget. Secrets never live anywhere near
                them.
              </p>
            </div>
            <div className="e-layers">
              <div className="e-layer">
                <div className="e-layer-tag">Identity</div>
                <h3>Role</h3>
                <p>Who the agent is, what it may use, and the output it owes. Versioned, always present in full.</p>
              </div>
              <div className="e-layer">
                <div className="e-layer-tag">Curated</div>
                <h3>Skills</h3>
                <p>Reusable procedures attached to a role or agent. An agent-proposed skill stays inert until a human promotes it.</p>
              </div>
              <div className="e-layer">
                <div className="e-layer-tag">Governed</div>
                <h3>Memory</h3>
                <p>What's been learned at your company — recalled by relevance, blended with recency and use.</p>
              </div>
              <div className="e-layer">
                <div className="e-layer-tag">On ingest</div>
                <h3>Knowledge</h3>
                <p>Reference material — a brand guide, a catalog — retrieved only for the roles it was attached to.</p>
              </div>
            </div>
          </div>
        </section>

        {/* ── DOCS CTA ── */}
        <section className="e-docs-cta">
          <div className="e-wrap e-rv">
            <div className="e-docs-card">
              <div>
                <div className="e-eyebrow">Documentation</div>
                <h2>Everything about the platform, documented.</h2>
                <p>
                  Architecture, the task lifecycle, roles and skills, security and tenancy, the tech
                  stack, and the full API surface — written for the people running it.
                </p>
              </div>
              <Link className="e-btn e-btn-primary e-btn-lg" to="/docs">
                Open the docs
              </Link>
            </div>
          </div>
        </section>

        {/* ── FINAL CTA ── */}
        <section className="e-final">
          <div className="e-wrap e-rv">
            <h2>Put your first agent on the org chart.</h2>
            <p>Sign up, hire a starter team, and assign real work in minutes. Free to try.</p>
            <div className="e-hero-cta e-final-cta">
              <Link className="e-btn e-btn-primary e-btn-lg" to="/signup">
                Start free
              </Link>
              <Link className="e-btn e-btn-ghost e-btn-lg" to="/login">
                Log in
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="e-footer">
        <div className="e-wrap e-footer-in">
          <div className="e-footer-brand">
            <a className="e-brand" href="#top">
              <span className="e-brand-mark" aria-hidden="true" />
              <b>{PRODUCT_NAME}</b>
            </a>
            <p>The agentic workforce platform.</p>
          </div>
          <div className="e-footer-cols">
            <div className="e-footer-col">
              <h4>Product</h4>
              <a href="#how">How it works</a>
              <a href="#features">Platform</a>
              <a href="#governance">Governance</a>
            </div>
            <div className="e-footer-col">
              <h4>Documentation</h4>
              <Link to="/docs">Overview</Link>
              <Link to="/docs/architecture">Architecture</Link>
              <Link to="/docs/task-lifecycle">Task lifecycle</Link>
            </div>
            <div className="e-footer-col">
              <h4>Get started</h4>
              <Link to="/signup">Sign up</Link>
              <Link to="/login">Log in</Link>
            </div>
          </div>
        </div>
        <div className="e-wrap e-footer-fine">
          <span>© {new Date().getFullYear()} {PRODUCT_NAME}</span>
          <span>Built by Geetansh Agrawal</span>
        </div>
      </footer>
    </div>
  );
}
