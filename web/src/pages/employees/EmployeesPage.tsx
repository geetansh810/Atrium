import { Avatar } from "../../shared/Avatar";
import { StatusDot, STATUS_LABEL } from "../../shared/StatusDot";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import "./EmployeesPage.css";

// Real (not a placeholder): the Sidebar's old "Who's Here" roster moves here
// so agent lookup doesn't regress to "walk into the office and click them".
// The full profile-page build (skills/memory/performance/settings tabs) is
// MF-4 — for now a card click opens the existing AgentProfilePanel overlay.
export function EmployeesPage() {
  const { state } = useApp();
  const nav = useAppNav();

  return (
    <div className="employees-page">
      <header className="employees-page-head">
        <h1>Employees</h1>
        <p>Full profile pages, workload, and skills arrive in MF-4.</p>
      </header>
      <div className="employees-grid">
        {state.agents.map((agent) => (
          <button key={agent.id} className="employee-card" onClick={() => nav.openAgent(agent.id)}>
            <Avatar name={agent.name} seed={agent.id} size={36} />
            <div className="employee-card-info">
              <div className="employee-card-name">{agent.name}</div>
              <div className="employee-card-role">{agent.roleTitle}</div>
            </div>
            <span className="employee-card-status">
              <StatusDot status={agent.status} />
              {STATUS_LABEL[agent.status]}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
