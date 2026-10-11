import { useState, type ReactNode } from "react";
import { PageShell } from "../../ui/PageShell";
import { Card } from "../../ui/Card";
import { StatCard } from "../../ui/StatCard";
import { StatusPill, type PillTone } from "../../ui/StatusPill";
import { StatusDot } from "../../shared/StatusDot";
import { ProgressBar } from "../../shared/ProgressBar";
import { Banner } from "../../ui/Banner";
import { DataTable } from "../../ui/DataTable";
import { EmptyState } from "../../ui/EmptyState";
import { Tabs } from "../../ui/Tabs";
import { Timeline } from "../../ui/Timeline";
import { Skeleton, ErrorState, ForbiddenState } from "../../ui/states";
import { setThemeMode, useThemeMode } from "../../shared/themeMode";
import "./DesignGalleryPage.css";

// UX1 primitives gallery: every ui/ component in default / loading / error /
// empty / forbidden, in whichever theme is active (toggle at the top). This is
// the visual regression surface for the design system.

interface Row {
  id: string;
  task: string;
  agent: string;
  tone: PillTone;
  status: string;
  tokens: number;
}

const ROWS: Row[] = [
  { id: "1", task: "Diwali gift box caption", agent: "Priya", tone: "running", status: "Working", tokens: 1204 },
  { id: "2", task: "Size gift box line extension", agent: "Rohan", tone: "warning", status: "Needs review", tokens: 841 },
  { id: "3", task: "Packaging concept spec", agent: "Meera", tone: "success", status: "Approved", tokens: 879 },
  { id: "4", task: "Reverse a string", agent: "GeminiCoder", tone: "danger", status: "Flagged", tokens: 379 },
];

type TableState = "default" | "loading" | "error" | "empty" | "forbidden";
const TABLE_STATES: TableState[] = ["default", "loading", "error", "empty", "forbidden"];

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <section className="design-section">
      <div className="design-section-head">
        <h2>{title}</h2>
        {note && <p>{note}</p>}
      </div>
      {children}
    </section>
  );
}

export function DesignGalleryPage() {
  const mode = useThemeMode();
  const [tableState, setTableState] = useState<TableState>("default");

  const table = (() => {
    switch (tableState) {
      case "loading":
        return <Skeleton lines={4} label="Loading tasks…" />;
      case "error":
        return (
          <ErrorState
            title="Could not load tasks"
            description="core-api did not answer within 10 seconds. Check that the backend is running, then retry."
            action={<button className="btn sm" onClick={() => setTableState("default")}>Retry</button>}
          />
        );
      case "forbidden":
        return (
          <ForbiddenState
            title="You can't view payroll"
            description="Budgets are visible to company admins only. Ask an admin to change your role."
          />
        );
      case "empty":
        return (
          <EmptyState
            title="No tasks yet"
            description="Create a task and an agent with the matching skill will claim it."
            action={<button className="btn primary sm">New task</button>}
          />
        );
      default:
        return (
          <DataTable<Row>
            rows={ROWS}
            rowKey={(r) => r.id}
            columns={[
              { key: "task", header: "Task", render: (r) => r.task },
              { key: "agent", header: "Agent", render: (r) => r.agent },
              { key: "status", header: "Status", render: (r) => <StatusPill label={r.status} tone={r.tone} /> },
              { key: "tokens", header: "Tokens", render: (r) => <span className="mono-num">{r.tokens.toLocaleString()}</span> },
            ]}
          />
        );
    }
  })();

  return (
    <PageShell
      title="Design system"
      subtitle="Control room direction. Every primitive, every state, both themes."
      actions={
        <div className="design-toggle" role="group" aria-label="Theme">
          <button className="btn sm" aria-pressed={mode === "dark"} onClick={() => setThemeMode("dark")}>Dark</button>
          <button className="btn sm" aria-pressed={mode === "light"} onClick={() => setThemeMode("light")}>Light</button>
        </div>
      }
    >
      <div className="design-gallery">
        <Section title="Buttons" note="Primary is the only amber fill on a screen. Danger always sits behind a confirmation.">
          <div className="design-row">
            <button className="btn primary">Approve</button>
            <button className="btn">Request changes</button>
            <button className="btn danger">Pause all agents</button>
            <button className="btn sm">Small</button>
            <button className="btn primary" disabled>Disabled</button>
          </div>
        </Section>

        <Section title="Status" note="Amber is live work. Green is done, red is flagged, blue is informational.">
          <div className="design-row">
            <StatusPill label="Queued" tone="neutral" />
            <StatusPill label="Working" tone="running" />
            <StatusPill label="Needs review" tone="warning" />
            <StatusPill label="Approved" tone="success" />
            <StatusPill label="Flagged" tone="danger" />
            <StatusPill label="In focus" tone="info" />
          </div>
          <div className="design-row">
            <span className="design-dot"><StatusDot status="working" /> Working</span>
            <span className="design-dot"><StatusDot status="online" /> Online</span>
            <span className="design-dot"><StatusDot status="away" /> Away</span>
            <span className="design-dot"><StatusDot status="offline" /> Offline</span>
          </div>
          <div className="design-bars">
            <ProgressBar value={64} live />
            <ProgressBar value={38} />
            <ProgressBar value={92} color="var(--status-flagged)" />
          </div>
        </Section>

        <Section title="Summary tiles">
          <div className="design-grid">
            <StatCard label="Agents working" value="4 / 6" />
            <StatCard label="Awaiting review" value="3" tone="warn" hint="Oldest waiting 42 min" />
            <StatCard label="Budget used" value="91%" tone="danger" hint="Cap 10M tokens" />
          </div>
        </Section>

        <Section title="Table" note="Same container, five states.">
          <div className="design-row" role="group" aria-label="Table state">
            {TABLE_STATES.map((s) => (
              <button key={s} className="btn sm" aria-pressed={tableState === s} onClick={() => setTableState(s)}>
                {s[0].toUpperCase() + s.slice(1)}
              </button>
            ))}
          </div>
          {table}
        </Section>

        <Section title="Banners">
          <Banner tone="info" title="Learning is on" description="Rejected tasks produce lessons that agents recall next time." />
          <Banner tone="warning" title="2 tasks queued 5+ min" description="No agent with the skill is claiming them. Check the provider key." />
          <Banner tone="danger" title="Budget cap reached" description="Priya is paused. Raise the cap to resume work." />
        </Section>

        <Section title="Tabs and timeline">
          <Card title="Task a9b91cff">
            <Tabs
              paramName="gallery"
              tabs={[
                {
                  key: "timeline",
                  label: "Timeline",
                  content: (
                    <Timeline
                      items={[
                        { id: "a", label: "Created by user", timestamp: "10:02", tone: "neutral" },
                        { id: "b", label: "Claimed by Priya", timestamp: "10:02", tone: "running" },
                        { id: "c", label: "Completed, 388 tokens", timestamp: "10:03", tone: "info" },
                        { id: "d", label: "Approved by Geetansh", timestamp: "10:09", tone: "success" },
                      ]}
                    />
                  ),
                },
                { key: "output", label: "Output", content: <p className="design-note">Artifact text renders here.</p> },
              ]}
            />
          </Card>
        </Section>
      </div>
    </PageShell>
  );
}
