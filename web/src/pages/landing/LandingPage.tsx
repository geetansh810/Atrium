import { useEffect, useRef } from "react";
import { Link, useNavigate } from "react-router";
import { AuthPage } from "../auth/AuthPage";
import { Avatar } from "../../shared/Avatar";
import { MoonIcon, SunIcon } from "../../shared/icons";
import { BrandMark } from "../../shared/BrandMark";
import { PRODUCT_NAME } from "../../shared/theme";
import { setThemeMode, useThemeMode } from "../../shared/themeMode";
import "./LandingPage.css";

// Signed-out entry. The shift board plays the real lifecycle with sample
// names — no network — so it renders with or without a backend.
const WORK = [
  { t: "Diwali gift-box captions", a: "Priya", s: "content" },
  { t: "Tier pricing for launch", a: "Rohan", s: "product" },
  { t: "Premium packaging spec", a: "Meera", s: "design" },
  { t: "String helper + tests", a: "Alex", s: "coding" },
  { t: "Competitor sweep", a: "Priya", s: "research" },
  { t: "Vendor data agreement", a: "Nadia", s: "legal" },
] as const;

type EventKind = "created" | "claimed" | "progress" | "completed" | "approved";

const ROLES = ["Content", "Product", "Design", "Legal", "Engineering"];

const STEPS = [
  { n: "01", t: "Assign the work", d: "A task names the skill it needs. It waits in the queue until someone on the crew can take it." },
  { n: "02", t: "A crew member claims it", d: "The matching agent picks it up, works it, and stops at review. You don't press Run." },
  { n: "03", t: "You approve it", d: "Nothing ships until a person signs off. Open children block the parent." },
];

const DOORS = [
  { href: "#roles", t: "Roles", d: "A new job is a role you hire, not a branch in the router." },
  { href: "#watch", t: "The watch", d: "See who is working from the tasks they hold, not a status someone typed." },
  { href: "#review", t: "Review", d: "Work stops for a signature. Compliance roles need a signed-in person." },
  { href: "#budgets", t: "Budgets", d: "A monthly token cap. At the limit, that crew member pauses." },
  { href: "#record", t: "The record", d: "Every claim, completion, and approval is a row you can read back." },
  { href: "#memory", t: "Memory", d: "The next task at this company can see what the last review taught." },
];

const FAQ = [
  ["How is this different from a chatbot?", "A crew member is hired into a role, claims work, and stops at review. A person approves."],
  ["Can I use the model I already pay for?", "Yes. Hire against Claude or Gemini. The router does not care which."],
  ["What happens at the budget cap?", "That agent pauses. A company-wide cap blocks new claims. You can raise the cap and resume."],
  ["Can an agent approve its own work?", "No. Pending review is the gate. Legal and HR roles also require a signed-in person."],
  ["Do agents run all the time?", "They poll, claim a matching task, and stop. Pause stops the loop."],
  ["What does signup create?", "A company and an admin. A short wizard can hire a starter crew and assign a first task."],
];

export function LandingPage({ authMode }: { authMode?: "signup" | "login" }) {
  const mode = useThemeMode();
  const navigate = useNavigate();
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
    const intervals: number[] = [];

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
      d.innerHTML = `<b>${task.t}</b><i>${task.a}</i><em>${task.s}</em>${bar}`;
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
          intervals.push(tick);
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
      intervals.forEach((id) => window.clearInterval(id));
      if (loop) window.clearInterval(loop);
    };
  }, []);

  return (
    <div className="lp">
      <header className="lp-bar">
        <div className="lp-wrap lp-bar-in">
          <a className="lp-brand" href="#top">
            <BrandMark />
          </a>
          <nav className="lp-nav" aria-label="Page">
            <a href="#how">How it works</a>
            <a href="#map">The crew</a>
            <Link to="/docs">Docs</Link>
          </nav>
          <div className="lp-bar-end">
            <Link className="lp-mobile-docs" to="/docs">
              Docs
            </Link>
            <button
              className="lp-icon"
              type="button"
              aria-label={mode === "dark" ? "Switch to light theme" : "Switch to dark theme"}
              onClick={() => setThemeMode(mode === "dark" ? "light" : "dark")}
            >
              {mode === "dark" ? <SunIcon width={16} height={16} /> : <MoonIcon width={16} height={16} />}
            </button>
            <Link className="lp-textlink" to="/login">
              Log in
            </Link>
            <Link className="lp-btn" to="/signup">
              Sign up
            </Link>
          </div>
        </div>
      </header>

      <main id="top">
        <section className="lp-hero">
          <div className="lp-hero-scene" aria-hidden="true">
            <i />
            <i />
            <i />
          </div>
          <div className="lp-wrap lp-hero-grid">
            <div>
              <p className="lp-hero-note">
                <span aria-hidden="true">B</span>
                Your AI team has clocked in
              </p>
              <h1>
                Hire a crew.
                <span>Review every result.</span>
              </h1>
              <p className="lp-lede">
                AI teammates take real roles, complete assigned work, and wait for your approval.
              </p>
              <ul className="lp-shift">
                <li>
                  <b><Avatar name="Priya" seed="priya-content" size={26} />Priya</b>
                  <span>is writing the gift-box captions</span>
                </li>
                <li>
                  <b><Avatar name="Rohan" seed="rohan-product" size={26} />Rohan</b>
                  <span>is sizing the launch prices</span>
                </li>
                <li>
                  <b><Avatar name="Nadia" seed="nadia-legal" size={26} />Nadia</b>
                  <span>is drafting the vendor agreement</span>
                </li>
              </ul>
              <div className="lp-cta">
                <Link className="lp-btn lp-btn-lg" to="/signup">
                  Sign up
                </Link>
                <Link className="lp-quiet" to="/login">
                  or log in
                </Link>
              </div>
            </div>
            <div
              className="lp-board"
              role="img"
              aria-label="A sample board showing tasks moving from queued to working to review to approved, with an event log."
            >
              <div className="lp-board-hd">
                <b>On watch</b>
                <span className="lp-live">
                  <i />
                  Sample
                </span>
              </div>
              <div className="lp-zones" tabIndex={0} role="group" aria-label="Task zones">
                <div className="lp-zone">
                  <div className="lp-zone-hd">Queued</div>
                  <div ref={queuedRef} />
                </div>
                <div className="lp-zone">
                  <div className="lp-zone-hd">Working</div>
                  <div ref={workingRef} />
                </div>
                <div className="lp-zone">
                  <div className="lp-zone-hd">Review</div>
                  <div ref={reviewRef} />
                </div>
                <div className="lp-zone">
                  <div className="lp-zone-hd">Approved</div>
                  <div ref={approvedRef} />
                </div>
              </div>
              <div className="lp-ledger">
                <div className="lp-ledger-hd">Record</div>
                <ul ref={ledgerRef} />
              </div>
            </div>
          </div>
        </section>

        <section className="lp-roles" aria-label="Roles you can hire">
          <div className="lp-wrap">
            <p>Hired from a role definition. Content, product, design, legal, engineering.</p>
            <ul>
              {ROLES.map((role) => (
                <li key={role}>{role}</li>
              ))}
            </ul>
          </div>
        </section>

        <section id="how" className="lp-how">
          <div className="lp-wrap lp-rv">
            <h2>Assign. Claim. Approve.</h2>
            <ol>
              {STEPS.map((step) => (
                <li key={step.n}>
                  <span>{step.n}</span>
                  <h3>{step.t}</h3>
                  <p>{step.d}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="map" className="lp-map">
          <div className="lp-wrap lp-rv">
            <h2>What the crew can do.</h2>
            <div className="lp-doors">
              {DOORS.map((door) => (
                <a key={door.href} href={door.href}>
                  <h3>{door.t}</h3>
                  <p>{door.d}</p>
                </a>
              ))}
            </div>
          </div>
        </section>

        <section id="roles" className="lp-chapter">
          <div className="lp-wrap lp-split lp-rv">
            <div>
              <h2>Any role, any model.</h2>
              <p>
                A role is a title, a set of skills, a model, and a budget. Adding legal does not
                require a change to the router. Claude and Gemini both sit on the same roster.
              </p>
            </div>
            <ul className="lp-specimen" aria-label="Example roster">
              <li>
                <b>Priya</b>
                <span>Content</span>
                <em>Gemini</em>
              </li>
              <li>
                <b>Rohan</b>
                <span>Product</span>
                <em>Gemini</em>
              </li>
              <li>
                <b>Nadia</b>
                <span>Legal</span>
                <em>Claude</em>
              </li>
            </ul>
          </div>
        </section>

        <section id="watch" className="lp-chapter lp-chapter-alt">
          <div className="lp-wrap lp-split lp-rv">
            <div>
              <h2>The board moves on its own.</h2>
              <p>
                Agents poll, claim work that matches their skills, and stop at review. The watch
                shows who is working from those tasks, not from a status field.
              </p>
            </div>
            <ul className="lp-specimen" aria-label="Example watch">
              <li>
                <b>Priya</b>
                <span>Working</span>
                <em>Captions</em>
              </li>
              <li>
                <b>Rohan</b>
                <span>Review</span>
                <em>Pricing</em>
              </li>
              <li>
                <b>Meera</b>
                <span>Idle</span>
                <em>No task</em>
              </li>
            </ul>
          </div>
        </section>

        <section id="review" className="lp-chapter">
          <div className="lp-wrap lp-rv">
            <h2>You sign off.</h2>
            <p className="lp-narrow">
              Approve, reject, pause, reassign, or raise the cap. A legal or HR role cannot be
              signed off by a background job. Autonomy is something you grant.
            </p>
            <ul className="lp-verbs">
              {["Approve", "Reject", "Pause", "Reassign", "Raise the cap"].map((verb) => (
                <li key={verb}>{verb}</li>
              ))}
            </ul>
          </div>
        </section>

        <section id="budgets" className="lp-chapter lp-chapter-alt">
          <div className="lp-wrap lp-split lp-rv">
            <div>
              <h2>The cap is a hard stop.</h2>
              <p>
                Each agent has a monthly token budget. Crossing 80% warns once. Hitting the cap
                pauses that agent. A company cap refuses new claims without picking someone to punish.
              </p>
            </div>
            <ul className="lp-specimen" aria-label="Example budgets">
              <li>
                <b>Priya</b>
                <span className="lp-meter">
                  <i style={{ width: "62%" }} />
                </span>
                <em>1.2M / 2.0M</em>
              </li>
              <li>
                <b>Company</b>
                <span className="lp-meter">
                  <i style={{ width: "28%" }} />
                </span>
                <em>2.8M / 10M</em>
              </li>
            </ul>
          </div>
        </section>

        <section id="record" className="lp-chapter">
          <div className="lp-wrap lp-split lp-rv">
            <div>
              <h2>Nothing happens off the record.</h2>
              <p>
                Claim, progress, completion, and approval are events on the task. The model, the
                token count, and the prompt version ride with the completion, and the LLM call
                itself can be opened from the task.
              </p>
            </div>
            <ol className="lp-record">
              <li>
                <span>09:14</span> claimed
              </li>
              <li>
                <span>09:18</span> completed
              </li>
              <li>
                <span>09:22</span> approved
              </li>
            </ol>
          </div>
        </section>

        <section id="memory" className="lp-chapter lp-chapter-alt">
          <div className="lp-wrap lp-rv">
            <h2>The next task remembers.</h2>
            <div className="lp-pillars">
              <div>
                <h3>Skills</h3>
                <p>Procedures attached to the role the agent was hired into.</p>
              </div>
              <div>
                <h3>Memory</h3>
                <p>Lessons from reviewed work. Company-wide facts wait for approval.</p>
              </div>
              <div>
                <h3>Knowledge</h3>
                <p>Documents attached to a company role, recalled into the prompt.</p>
              </div>
            </div>
          </div>
        </section>

        <section id="faq" className="lp-faq">
          <div className="lp-wrap lp-rv">
            <h2>Questions</h2>
            {FAQ.map(([q, a]) => (
              <details key={q}>
                <summary>{q}</summary>
                <p>{a}</p>
              </details>
            ))}
          </div>
        </section>

        <section id="start" className="lp-close">
          <div className="lp-wrap">
            <h2>Hire the first crew member.</h2>
            <p>A company, an empty roster, and a wizard if you want a starter crew.</p>
            <Link className="lp-btn lp-btn-lg" to="/signup">
              Sign up
            </Link>
            <p>
              <Link to="/login">Log in</Link>
            </p>
          </div>
        </section>
      </main>

      <footer className="lp-foot">
        <div className="lp-wrap lp-foot-grid">
          <div>
            <b>{PRODUCT_NAME}</b>
            <p>Hire the crew. Review the work.</p>
          </div>
          <div>
            <h2>Product</h2>
            <a href="#how">How it works</a>
            <a href="#roles">Roles</a>
            <a href="#budgets">Budgets</a>
            <a href="#review">Review</a>
          </div>
          <div>
            <h2>Docs</h2>
            <Link to="/docs/overview">Overview</Link>
            <Link to="/docs/quickstart">Quickstart</Link>
            <Link to="/docs/task-lifecycle">Task lifecycle</Link>
            <Link to="/docs/architecture">Architecture</Link>
          </div>
          <div>
            <h2>Account</h2>
            <Link to="/signup">Sign up</Link>
            <Link to="/login">Log in</Link>
          </div>
        </div>
      </footer>
      {authMode && (
        <AuthPage
          initialMode={authMode}
          presentation="modal"
          onClose={() => navigate("/", { replace: true })}
        />
      )}
    </div>
  );
}
