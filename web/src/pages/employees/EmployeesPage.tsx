import { Avatar } from "../../shared/Avatar";
import { StatusDot, STATUS_LABEL } from "../../shared/StatusDot";
import { skillColor } from "../../shared/skillColor";
import { agentWorkload } from "../../shared/selectors";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { StatusPill } from "../../ui/StatusPill";
import "./EmployeesPage.css";

// Real (not a placeholder): card grid with role/skills/model/live workload/
// status, upgraded from the MF-1 teaser. Full profile page (EmployeeProfile,
// tabs) is a click away.
export function EmployeesPage() {
  const { state } = useApp();
  const nav = useAppNav();

  const workloadByAgent = agentWorkload(state.tasks);

  return (
    <div className="employees-page">
      <header className="employees-page-head">
        <h1>Employees</h1>
        <p>{state.agents.length} agents on the roster.</p>
      </header>
      <div className="employees-grid">
        {state.agents.map((agent) => {
          const workload = workloadByAgent.get(agent.id) ?? 0;
          return (
            <button key={agent.id} className="employee-card" onClick={() => nav.openAgent(agent.id)}>
              <div className="employee-card-top">
                <Avatar name={agent.name} seed={agent.id} size={36} />
                <div className="employee-card-info">
                  <div className="employee-card-name">{agent.name}</div>
                  <div className="employee-card-role">{agent.roleTitle}</div>
                </div>
                <span className="employee-card-status">
                  <StatusDot status={agent.status} />
                  {STATUS_LABEL[agent.status]}
                </span>
              </div>

              <div className="employee-card-skills">
                {agent.skillTags.slice(0, 3).map((skill) => (
                  <span key={skill} className="employee-card-skill" style={{ background: skillColor(skill) }}>
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
                {agent.paused && <StatusPill label="Paused" tone="warning" />}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
