import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { USE_MOCKS } from "../../shared/config";
import { tasks7d, topSkills } from "../../shared/mockData";
import { useApp } from "../../shared/store";
import { Card } from "../../ui/Card";
import { StatCard } from "../../ui/StatCard";
import { StatusPill } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import "./ReportsPage.css";

const STATUS_LABEL: Record<string, string> = {
  queued: "Queued",
  claimed: "Claimed",
  in_progress: "In Progress",
  flagged: "Flagged",
  pending_review: "Review",
  approved: "Approved",
  rejected: "Rejected",
  cancelled: "Cancelled",
};

// Port of the classic Analytics panel (MF-5) + real-derived charts —
// completion counts, per-agent budget burn, and status distribution all read
// straight from state; the 7-day trend and top-skills share stay mock-backed
// in both modes (no backend rollup exists yet, same posture as before MF-5),
// flagged with a "Sample data" pill in API mode.
export function ReportsPage() {
  const { state } = useApp();
  const maxDayCount = Math.max(1, ...tasks7d.map((d) => d.count));

  const completedCount = state.tasks.filter((t) => t.status === "approved").length;

  const statusCounts = new Map<string, number>();
  for (const task of state.tasks) {
    statusCounts.set(task.status, (statusCounts.get(task.status) ?? 0) + 1);
  }
  const maxStatusCount = Math.max(1, ...Array.from(statusCounts.values()));

  const agentSpend = state.budgets
    .filter((b) => b.agentId !== null)
    .map((budget) => ({ agent: state.agents.find((a) => a.id === budget.agentId), budget }))
    .filter((row): row is { agent: NonNullable<(typeof row)["agent"]>; budget: (typeof row)["budget"] } =>
      Boolean(row.agent),
    );

  return (
    <div className="reports-page">
      <header className="reports-page-head">
        <h1>Reports</h1>
        <p>Agent performance and task throughput.</p>
      </header>

      <div className="reports-kpi-row">
        <StatCard label="Total Agents" value={state.agents.length} />
        <StatCard label="Tasks Approved" value={completedCount} />
        <StatCard label="Tasks Open" value={state.tasks.length - completedCount} />
      </div>

      <Card title="Task Status Distribution">
        <div className="reports-status-bars">
          {Array.from(statusCounts.entries()).map(([status, count]) => (
            <div className="reports-status-row" key={status}>
              <span className="reports-status-label">{STATUS_LABEL[status] ?? status}</span>
              <span className="reports-status-bar-track">
                <span className="reports-status-bar-fill" style={{ width: `${(count / maxStatusCount) * 100}%` }} />
              </span>
              <span className="reports-status-count">{count}</span>
            </div>
          ))}
        </div>
      </Card>

      <Card title="Per-Agent Budget Burn">
        {agentSpend.length === 0 ? (
          <EmptyState title="No per-agent budget caps set." />
        ) : (
          agentSpend.map(({ agent, budget }) => {
            const pct = budget.capTokens > 0 ? Math.round((budget.spentTokens / budget.capTokens) * 100) : 0;
            return (
              <div className="perf-row" key={budget.id}>
                <span className="perf-name">
                  <Avatar name={agent.name} seed={agent.id} size={22} />
                  {agent.name}
                </span>
                <span className="perf-bar">
                  <ProgressBar value={pct} color={pct >= 80 ? "var(--status-flagged)" : undefined} />
                </span>
                <span className="perf-pct">{pct}%</span>
              </div>
            );
          })
        )}
      </Card>

      <Card
        title="Tasks Completed (7 Days)"
        actions={!USE_MOCKS ? <StatusPill label="Sample data" tone="warning" /> : undefined}
      >
        <div className="bar-chart">
          {tasks7d.map(({ day, count }) => (
            <div className="bar-col" key={day} title={`${day}: ${count} tasks`}>
              <span className="bar-value">{count}</span>
              <span className="bar-fill" style={{ height: `${(count / maxDayCount) * 100}%` }} />
              <span className="bar-label">{day}</span>
            </div>
          ))}
        </div>
      </Card>

      <Card title="Top Skills Used" actions={!USE_MOCKS ? <StatusPill label="Sample data" tone="warning" /> : undefined}>
        <div className="chip-row">
          {topSkills.map(({ skill, sharePct }) => (
            <span className="chip" key={skill}>
              {skill} <strong>{sharePct}%</strong>
            </span>
          ))}
        </div>
      </Card>
    </div>
  );
}
