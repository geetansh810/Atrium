import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { USE_MOCKS, DEV_COMPANY_ID } from "../../shared/config";
import {
  analyticsSummary as mockSummary,
  agentPerformance as mockAgentPerformance,
  tasks7d as mockTasks7d,
  topSkills as mockTopSkills,
} from "../../shared/mockData";
import { useAgentPerformance, useAnalyticsSummary, useTasks7d, useTopSkills } from "../../shared/queries";
import { useApp } from "../../shared/store";
import type { AgentPerformance, DayCount, SkillShare } from "../../shared/types";
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

interface AnalyticsData {
  avgSuccessRate: number;
  focusMinutesToday: number | null; // null = not tracked (no presence source yet)
  tasks7d: DayCount[];
  agentPerformance: AgentPerformance[];
  topSkills: SkillShare[];
  isSample: boolean;
}

// Mock/API split follows SettingsPage's precedent (MF-6): the whole widget
// block is picked once via USE_MOCKS at module scope, so each variant's hook
// calls stay unconditional and consistent — never branched inside one component.
function useMockAnalytics(): AnalyticsData {
  return {
    avgSuccessRate: mockSummary.avgSuccessRate,
    focusMinutesToday: mockSummary.focusMinutesToday,
    tasks7d: mockTasks7d,
    agentPerformance: mockAgentPerformance,
    topSkills: mockTopSkills,
    isSample: true,
  };
}

function useRealAnalytics(): AnalyticsData {
  const summaryQuery = useAnalyticsSummary(DEV_COMPANY_ID);
  const tasks7dQuery = useTasks7d(DEV_COMPANY_ID);
  const performanceQuery = useAgentPerformance(DEV_COMPANY_ID);
  const topSkillsQuery = useTopSkills(DEV_COMPANY_ID);
  return {
    avgSuccessRate: summaryQuery.data?.successRateAllTime ?? 0,
    focusMinutesToday: null,
    tasks7d: tasks7dQuery.data ?? [],
    agentPerformance: performanceQuery.data ?? [],
    topSkills: topSkillsQuery.data ?? [],
    isSample: false,
  };
}

const useAnalyticsData = USE_MOCKS ? useMockAnalytics : useRealAnalytics;

// Port of the classic Analytics panel (MF-5) + real-derived charts —
// completion counts, per-agent budget burn, and status distribution all read
// straight from state; agent performance/7-day trend/top-skills are now real
// (M2.3) in API mode, still mock-backed in mock mode ("Sample data" pill).
export function ReportsPage() {
  const { state } = useApp();
  const analytics = useAnalyticsData();

  const maxDayCount = Math.max(1, ...analytics.tasks7d.map((d) => d.count));
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

  const agentById = new Map(state.agents.map((a) => [a.id, a]));

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
        <StatCard label="Avg Success Rate" value={`${analytics.avgSuccessRate}%`} />
        <StatCard
          label="Focus Time (Today)"
          value={analytics.focusMinutesToday !== null ? `${Math.round(analytics.focusMinutesToday / 60)}h` : "—"}
        />
      </div>
      {analytics.focusMinutesToday === null && (
        <p className="about-text" style={{ margin: "-8px 0 0", fontSize: 11.5 }}>
          Focus Time isn't tracked yet — no presence source exists.
        </p>
      )}

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
        actions={analytics.isSample ? <StatusPill label="Sample data" tone="warning" /> : undefined}
      >
        {analytics.tasks7d.length === 0 ? (
          <EmptyState title="No completions in the last 7 days yet." />
        ) : (
          <div className="bar-chart">
            {analytics.tasks7d.map(({ day, count }) => (
              <div className="bar-col" key={day} title={`${day}: ${count} tasks`}>
                <span className="bar-value">{count}</span>
                <span className="bar-fill" style={{ height: `${(count / maxDayCount) * 100}%` }} />
                <span className="bar-label">{day}</span>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card
        title="Agent Performance"
        actions={analytics.isSample ? <StatusPill label="Sample data" tone="warning" /> : undefined}
      >
        {analytics.agentPerformance.length === 0 ? (
          <EmptyState title="No completed tasks in the last 7 days yet." />
        ) : (
          analytics.agentPerformance.map((perf) => {
            const agent = agentById.get(perf.agentId);
            return (
              <div className="perf-row" key={perf.agentId}>
                <span className="perf-name">
                  <Avatar name={agent?.name ?? perf.agentId} seed={perf.agentId} size={22} />
                  {agent?.name ?? perf.agentId}
                </span>
                <span className="perf-bar">
                  <ProgressBar value={perf.successRate} />
                </span>
                <span className="perf-pct">{perf.successRate}%</span>
              </div>
            );
          })
        )}
      </Card>

      <Card title="Top Skills Used" actions={analytics.isSample ? <StatusPill label="Sample data" tone="warning" /> : undefined}>
        {analytics.topSkills.length === 0 ? (
          <EmptyState title="No completed tasks yet." />
        ) : (
          <div className="chip-row">
            {analytics.topSkills.map((s) => (
              <span className="chip" key={s.skill}>
                {s.skill} <strong>{s.sharePct}%</strong>
              </span>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
