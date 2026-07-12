import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { formatFocusTime } from "../../shared/format";
import {
  agentPerformance,
  analyticsSummary,
  tasks7d,
  topSkills,
} from "../../shared/mockData";
import { useApp } from "../../shared/store";
import { PanelShell } from "./PanelShell";

export function AnalyticsPanel() {
  const { state } = useApp();
  const agentById = new Map(state.agents.map((a) => [a.id, a]));
  const maxCount = Math.max(...tasks7d.map((d) => d.count));

  return (
    <PanelShell title="Atrium Analytics" width={640}>
      <div className="kpi-grid">
        <div className="stat-card">
          <div className="stat-card-label">Total Agents</div>
          <div className="stat-card-value">{state.agents.length}</div>
          <div className="stat-card-delta">{analyticsSummary.totalAgentsDelta}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">Tasks Completed</div>
          <div className="stat-card-value">{analyticsSummary.tasksCompleted}</div>
          <div className="stat-card-delta">{analyticsSummary.tasksCompletedDelta}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">Avg. Success Rate</div>
          <div className="stat-card-value">{analyticsSummary.avgSuccessRate}%</div>
          <div className="stat-card-delta">{analyticsSummary.avgSuccessRateDelta}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">Focus Time (Today)</div>
          <div className="stat-card-value">{formatFocusTime(analyticsSummary.focusMinutesToday)}</div>
          <div className="stat-card-delta">{analyticsSummary.focusMinutesDelta}</div>
        </div>
      </div>

      <div className="section-title">Tasks Completed (7 Days)</div>
      <div className="bar-chart">
        {tasks7d.map(({ day, count }) => (
          <div className="bar-col" key={day} title={`${day}: ${count} tasks`}>
            <span className="bar-value">{count}</span>
            <span className="bar-fill" style={{ height: `${(count / maxCount) * 100}%` }} />
            <span className="bar-label">{day}</span>
          </div>
        ))}
      </div>

      <div className="section-title">Agent Performance</div>
      {agentPerformance.map(({ agentId, successRate }) => {
        const agent = agentById.get(agentId);
        if (!agent) return null;
        return (
          <div className="perf-row" key={agentId}>
            <span className="perf-name">
              <Avatar name={agent.name} seed={agent.id} size={22} />
              {agent.name}
            </span>
            <span className="perf-bar">
              <ProgressBar value={successRate} />
            </span>
            <span className="perf-pct">{successRate}%</span>
          </div>
        );
      })}

      <div className="section-title">Top Skills Used</div>
      <div className="chip-row">
        {topSkills.map(({ skill, sharePct }) => (
          <span className="chip" key={skill}>
            {skill} <strong>{sharePct}%</strong>
          </span>
        ))}
      </div>
    </PanelShell>
  );
}
