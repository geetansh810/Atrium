import { formatTokens } from "../../shared/format";
import {
  companyBudgetBurn,
  computeKpis,
  describeStalledSummary,
  findStalledTasks,
  summarizeStalledTasks,
  synthesizeFeed,
  TASK_EVENT_LABEL,
} from "../../shared/selectors";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { Avatar } from "../../shared/Avatar";
import { useAuthSession } from "../../shared/auth";
import { currentUser } from "../../shared/mockData";
import { Card } from "../../ui/Card";
import { StatCard } from "../../ui/StatCard";
import { StatusPill, taskStatusTone } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import { Feed, type FeedEntry } from "../../ui/Feed";
import { Banner } from "../../ui/Banner";
import { PageShell } from "../../ui/PageShell";
import { useNavigate } from "react-router";
import "./MissionControlPage.css";

// Home is a status board: four numbers, then the two lists a person actually
// scans. The task page owns the single-task preview.
export function MissionControlPage() {
  const { state, dispatch } = useApp();
  const nav = useAppNav();
  const navigate = useNavigate();
  const session = useAuthSession();
  const firstName = (session?.displayName ?? currentUser.displayName).split(" ")[0];
  const { agents, tasks, budgets } = state;

  const kpis = computeKpis(agents, tasks);
  const burn = companyBudgetBurn(budgets);
  const stalledSummary = summarizeStalledTasks(findStalledTasks(tasks, agents));
  const feedItems: FeedEntry[] = synthesizeFeed(tasks, agents, 12).map((f) => ({
    id: f.id,
    agentName: f.agentName,
    taskId: f.taskId,
    text: `${TASK_EVENT_LABEL[f.eventType] ?? f.eventType} — ${f.taskTitle}`,
    createdAt: f.createdAt,
  }));
  const paused = agents.filter((a) => a.paused).length;
  const workingAgents = new Set(
    tasks
      .filter((t) => (t.status === "claimed" || t.status === "in_progress") && t.assignedAgentId)
      .map((t) => t.assignedAgentId),
  ).size;
  const openTasks = tasks.filter((t) => t.status !== "approved" && t.status !== "cancelled").length;
  const recentTasks = [...tasks]
    .filter((t) => t.status !== "approved" && t.status !== "cancelled")
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 8);

  return (
    <PageShell
      title={`Welcome back, ${firstName}`}
      subtitle="Your crew, their work, and everything waiting for your signature."
      actions={
        <button className="btn primary" type="button" onClick={() => dispatch({ type: "setNewTaskOpen", open: true })}>
          Assign new work
        </button>
      }
    >
      <section className="mission-brief" aria-labelledby="mission-brief-title">
        <div>
          <span className="mission-brief-mark" aria-hidden="true">✦</span>
          <h2 id="mission-brief-title">You set the work. The crew moves it.</h2>
          <p>
            Assign a skill, watch the right employee claim it, then approve what comes back.
            Nothing ships without you.
          </p>
        </div>
        <ol>
          <li><b>1</b><span><strong>Assign</strong>A task enters the queue</span></li>
          <li><b>2</b><span><strong>Work</strong>Your crew claims it</span></li>
          <li><b>3</b><span><strong>Review</strong>You sign it off</span></li>
        </ol>
      </section>

      <section className="mission-crew" aria-labelledby="mission-crew-title">
        <header>
          <div>
            <h2 id="mission-crew-title">Your crew</h2>
            <p>{workingAgents} working now</p>
          </div>
          <button className="btn sm" type="button" onClick={() => navigate("/team")}>Open the watch</button>
        </header>
        <ul>
          {agents.slice(0, 6).map((agent) => {
            const activeTask = tasks.find(
              (task) =>
                task.assignedAgentId === agent.id &&
                (task.status === "claimed" || task.status === "in_progress"),
            );
            return (
              <li key={agent.id}>
                <button type="button" onClick={() => nav.openAgent(agent.id)}>
                  <span className="mission-crew-avatar">
                    <Avatar name={agent.name} seed={agent.id} size={34} />
                    <i className={activeTask ? "live" : ""} aria-hidden="true" />
                  </span>
                  <span>
                    <b>{agent.name}</b>
                    <small>{activeTask?.title ?? agent.roleTitle}</small>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </section>

      {stalledSummary.length > 0 && (
        <Banner
          tone={stalledSummary.some((s) => s.reason === "no-agent-with-skill") ? "danger" : "warning"}
          title="Some tasks aren't moving"
          description={stalledSummary.map(describeStalledSummary).join("\n")}
        />
      )}

      <div className="mission-kpi-row">
        <button className="mission-stat" type="button" onClick={() => navigate("/team")}>
          <StatCard label="Crew working" value={workingAgents} hint={`${paused} paused`} />
        </button>
        <button className="mission-stat" type="button" onClick={() => navigate("/tasks")}>
          <StatCard label="Tasks in progress" value={kpis.tasksInProgress} hint={`${openTasks} open`} />
        </button>
        <button className="mission-stat" type="button" onClick={() => navigate("/organization?tab=payroll")}>
          <StatCard
            label="Spent this month"
            value={burn ? `${burn.pct}%` : "No cap"}
            hint={burn ? `${formatTokens(burn.spentTokens)} / ${formatTokens(burn.capTokens)}` : "Set a company cap"}
            tone={burn?.warn ? "warn" : "neutral"}
          />
        </button>
        <button className="mission-stat" type="button" onClick={() => navigate("/tasks/review")}>
          <StatCard
            label="Awaiting review"
            value={kpis.awaitingReview}
            hint="Needs a signature"
            tone={kpis.awaitingReview > 0 ? "warn" : "neutral"}
          />
        </button>
      </div>

      <div className="mission-grid">
        <Card title="Recent tasks">
          {recentTasks.length === 0 ? (
            <EmptyState title="No open tasks." description="New work shows up here as soon as it is assigned." />
          ) : (
            <ul className="mission-list">
              {recentTasks.map((task) => {
                const agent = agents.find((a) => a.id === task.assignedAgentId);
                return (
                  <li key={task.id}>
                    <button type="button" onClick={() => nav.openTask(task.id)}>
                      <StatusPill label={task.status.replaceAll("_", " ")} tone={taskStatusTone(task.status)} />
                      <span className="mission-list-title">{task.title}</span>
                      <span className="mission-list-who">{agent?.name ?? "Unassigned"}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>

        <Card title="Recent activity">
          {feedItems.length === 0 ? (
            <EmptyState title="No activity yet." description="Claims, completions, and approvals land here." />
          ) : (
            <Feed items={feedItems} onOpenTask={nav.openTask} />
          )}
        </Card>
      </div>
    </PageShell>
  );
}
