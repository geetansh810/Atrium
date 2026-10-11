import { PersonAvatar } from "../../shared/PersonAvatar";
import { agentWorkload } from "../../shared/selectors";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { PageShell } from "../../ui/PageShell";
import { EmptyState } from "../../ui/EmptyState";
import "./EmployeesPage.css";

// Real (not a placeholder): card grid with role/skills/model/live workload/
// status, upgraded from the MF-1 teaser. Full profile page (EmployeeProfile,
// tabs) is a click away.
export function EmployeesPage() {
  const { state, dispatch } = useApp();
  const nav = useAppNav();

  const workloadByAgent = agentWorkload(state.tasks);

  return (
    <PageShell
      title="Employees"
      subtitle={`${state.agents.length} ${state.agents.length === 1 ? "agent" : "agents"} on the roster.`}
      actions={
        <button className="btn primary" onClick={() => dispatch({ type: "setInviteOpen", open: true })}>
          Hire agent
        </button>
      }
    >
      {state.agents.length === 0 && (
        <EmptyState
          title="No agents yet"
          description="Hire an agent from a role template. It starts claiming matching tasks as soon as it is online."
          action={
            <button className="btn primary sm" onClick={() => dispatch({ type: "setInviteOpen", open: true })}>
              Hire your first agent
            </button>
          }
        />
      )}
      <div className="employees-grid">
        {state.agents.map((agent) => {
          const workload = workloadByAgent.get(agent.id) ?? 0;
          return (
            <button key={agent.id} className="employee-card" onClick={() => nav.openAgent(agent.id)}>
              <div className="employee-card-top">
                <PersonAvatar name={agent.name} seed={agent.id} status={agent.status} size={36} />
                <div className="employee-card-info">
                  <div className="employee-card-name">{agent.name}</div>
                  <div className="employee-card-role">{agent.roleTitle}</div>
                </div>
                {agent.paused && <span className="employee-card-paused">Paused</span>}
              </div>

              <div className="employee-card-skills">
                {agent.skillTags.slice(0, 3).map((skill) => (
                  <span key={skill} className="chip">
                    {skill}
                  </span>
                ))}
                {agent.skillTags.length > 3 && (
                  <span className="employee-card-skill-more">+{agent.skillTags.length - 3}</span>
                )}
              </div>

              <div className="employee-card-foot">
                <span className="employee-card-model">
                  {agent.modelProvider} · {agent.modelName}
                </span>
                <span className="employee-card-workload">
                  {workload} active
                </span>
              </div>
            </button>
          );
        })}
      </div>
    </PageShell>
  );
}
