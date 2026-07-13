import { useApp } from "../../shared/store";
import { PagePlaceholder } from "../PagePlaceholder";

// Real Mission Control (KPIs, Company Pulse, workflow graph, live feed)
// arrives in MF-2. This teaser already reads live state so the counts are
// real in both mock and API mode — it's not decorative.
export function MissionControlPage() {
  const { state } = useApp();
  const activeAgents = state.agents.filter((a) => a.status !== "offline").length;
  const runningTasks = state.tasks.filter(
    (t) => t.status === "claimed" || t.status === "in_progress",
  ).length;
  const awaitingReview = state.tasks.filter(
    (t) => t.status === "pending_review" || t.status === "flagged",
  ).length;

  return (
    <PagePlaceholder
      title="Mission Control"
      subtitle="Arriving in MF-2"
      description={
        <>
          {activeAgents} agent{activeAgents === 1 ? "" : "s"} active · {runningTasks} task
          {runningTasks === 1 ? "" : "s"} running · {awaitingReview} awaiting your review.
          <br />
          The full KPI/Pulse/workflow-graph home page lands next milestone — use the nav to reach
          everything else in the meantime.
        </>
      }
    />
  );
}
