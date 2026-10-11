import { StatusDot, STATUS_LABEL } from "../../shared/StatusDot";
import { Avatar } from "../../shared/Avatar";
import { formatTokens, PRIORITY_LABEL } from "../../shared/format";
import {
  companyBudgetBurn,
  companyPulse,
  computeKpis,
  describeStalledSummary,
  findStalledTasks,
  liveMissionTask,
  summarizeStalledTasks,
  synthesizeFeed,
} from "../../shared/selectors";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { Card } from "../../ui/Card";
import { StatCard } from "../../ui/StatCard";
import { StatusPill, taskStatusTone } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import { Feed } from "../../ui/Feed";
import { Banner } from "../../ui/Banner";
import { ProgressBar } from "../../shared/ProgressBar";
import { Graph } from "../../ui/Graph";
import { PageShell } from "../../ui/PageShell";
import { TaskLink } from "../../ui/EntityLink";
import { Markdown } from "../../ui/Markdown";
import { useNavigate } from "react-router";
import type { GraphEdge, GraphNode, GraphNodeStatus } from "../../ui/Graph";
import "./MissionControlPage.css";

function nodeStatus(status: string): GraphNodeStatus {
  if (status === "approved") return "completed";
  if (status === "flagged" || status === "rejected") return "failed";
  if (status === "claimed" || status === "in_progress") return "running";
  return "pending";
}

// Real Mission Control (MF-2): Company Pulse, KPI row, Live Mission hero,
// Agent Status grid, a mini workflow-graph preview, live activity feed (from
// the new Task.events contract extension), output preview, and a budget burn
// bar with an 80% warning. Replaces the teaser-only placeholder.
export function MissionControlPage() {
  const { state, dispatch } = useApp();
  const nav = useAppNav();
  const navigate = useNavigate();
  const { agents, tasks, budgets } = state;

  const kpis = computeKpis(agents, tasks);
  const pulse = companyPulse(agents, tasks, budgets);
  const burn = companyBudgetBurn(budgets);
  const hero = liveMissionTask(tasks);
  const heroAgent = hero?.assignedAgentId ? agents.find((a) => a.id === hero.assignedAgentId) : undefined;
  const feedItems = synthesizeFeed(tasks, agents, 12).map((item) => ({
    id: item.id,
    taskId: item.taskId,
    agentName: item.agentName,
    text: `${item.agentName ?? "System"} — ${item.eventType.replace("_", " ")}: ${item.taskTitle}`,
    createdAt: item.createdAt,
  }));

  // Mini workflow graph: the hero task's parent→children chain, or its own
  // subtasks if it's a root. Falls back to nothing if there's no active chain.
  const graphRoot = hero ? (tasks.find((t) => t.id === hero.parentTaskId) ?? hero) : undefined;
  const graphChildren = graphRoot ? tasks.filter((t) => t.parentTaskId === graphRoot.id) : [];
  const rootNode: GraphNode | undefined = graphRoot
    ? { id: graphRoot.id, label: graphRoot.title, sublabel: `${graphRoot.progress}%`, status: nodeStatus(graphRoot.status) }
    : undefined;
  const childNodes: GraphNode[] = graphChildren.map((t) => ({
    id: t.id,
    label: t.title,
    sublabel: `${t.progress}%`,
    status: nodeStatus(t.status),
  }));
  const graphLayers = rootNode ? (childNodes.length ? [[rootNode], childNodes] : [[rootNode]]) : [];
  const graphEdges: GraphEdge[] = rootNode ? graphChildren.map((t) => ({ from: rootNode.id, to: t.id })) : [];

  const stalledSummary = summarizeStalledTasks(findStalledTasks(tasks, agents));

  return (
    <PageShell
      title="Mission Control"
      subtitle={pulse}
      actions={
        <>
          {kpis.awaitingReview > 0 && (
            <button className="btn primary" onClick={() => navigate("/tasks/review")}>
              Review {kpis.awaitingReview} {kpis.awaitingReview === 1 ? "task" : "tasks"}
            </button>
          )}
          <button
            className={kpis.awaitingReview > 0 ? "btn" : "btn primary"}
            onClick={() => dispatch({ type: "setNewTaskOpen", open: true })}
          >
            New task
          </button>
        </>
      }
    >
      {stalledSummary.length > 0 && (
        <Banner
          tone={stalledSummary.some((s) => s.reason === "no-agent-with-skill") ? "danger" : "warning"}
          title="Some tasks aren't moving"
          description={stalledSummary.map(describeStalledSummary).join("\n")}
        />
      )}

      <div className="mission-kpi-row">
        <StatCard label="Active agents" value={`${kpis.activeAgents}/${kpis.totalAgents}`} />
        <StatCard label="Tasks running" value={kpis.tasksInProgress} />
        <StatCard label="Completed today" value={kpis.tasksCompletedToday} />
        <StatCard
          label="Awaiting review"
          value={kpis.awaitingReview}
          tone={kpis.awaitingReview > 0 ? "warn" : "neutral"}
        />
      </div>

      <div className="mission-grid">
        <div className="mission-col-main">
          <Card title="Live mission" className="mission-hero-card">
            {hero ? (
              <button className="mission-hero" onClick={() => nav.openTask(hero.id)}>
                <div className="mission-hero-top">
                  <span className="mission-hero-title">{hero.title}</span>
                  <StatusPill label={PRIORITY_LABEL[hero.priority] ?? `P${hero.priority}`} tone="info" />
                </div>
                <p className="mission-hero-desc">{hero.description}</p>
                <div className="mission-hero-meta">
                  {heroAgent && (
                    <span className="mission-hero-agent">
                      <Avatar name={heroAgent.name} seed={heroAgent.id} size={22} />
                      {heroAgent.name}
                    </span>
                  )}
                  <StatusPill label={hero.status.replace("_", " ")} tone={taskStatusTone(hero.status)} />
                </div>
                <ProgressBar value={hero.progress} live />
              </button>
            ) : (
              <EmptyState
                title="Nothing in progress right now."
                description="Create a task and an agent with the matching skill will claim it."
                action={
                  <button className="btn primary sm" onClick={() => dispatch({ type: "setNewTaskOpen", open: true })}>
                    New task
                  </button>
                }
              />
            )}
          </Card>

          <Card title="Workflow preview">
            {graphLayers.length ? (
              <Graph layers={graphLayers} edges={graphEdges} onNodeClick={nav.openTask} compact />
            ) : (
              <EmptyState title="No active task chains." description="Multi-step tasks will appear here as a graph." />
            )}
          </Card>

          <Card title="Output preview">
            {hero?.artifact ? (
              <>
                <div className="mission-artifact">
                  <Markdown>{hero.artifact.content}</Markdown>
                </div>
                <TaskLink task={hero} />
              </>
            ) : (
              <EmptyState title="No output ready yet." description="The latest artifact from the live mission will preview here." />
            )}
          </Card>
        </div>

        <div className="mission-col-side">
          <Card title="Agent status">
            <ul className="mission-agent-grid">
              {agents.map((agent) => (
                <li key={agent.id}>
                  <button className="mission-agent-row" onClick={() => nav.openAgent(agent.id)}>
                    <Avatar name={agent.name} seed={agent.id} size={24} />
                    <div className="mission-agent-info">
                      <div className="mission-agent-name">{agent.name}</div>
                      <div className="mission-agent-role">{agent.roleTitle}</div>
                    </div>
                    <span className="mission-agent-status">
                      <StatusDot status={agent.status} />
                      {STATUS_LABEL[agent.status]}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="Budget burn">
            {burn ? (
              <>
                <ProgressBar value={burn.pct} color={burn.warn ? "var(--status-flagged)" : undefined} height={8} />
                <div className="mission-budget-nums">
                  {formatTokens(burn.spentTokens)} / {formatTokens(burn.capTokens)} tokens ({burn.pct}%)
                </div>
                {burn.warn && <div className="mission-budget-warn">Company budget is at {burn.pct}%. New work pauses at the cap.</div>}
              </>
            ) : (
              <EmptyState title="No company-wide cap set." />
            )}
          </Card>

          <Card title="Live activity">
            <Feed items={feedItems} onOpenTask={nav.openTask} />
          </Card>
        </div>
      </div>
    </PageShell>
  );
}
