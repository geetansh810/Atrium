import { NavLink } from "react-router";
import { PRODUCT_NAME } from "../shared/theme";
import { escalationCount, useApp } from "../shared/store";
import {
  EmployeesIcon,
  FlowIcon,
  KnowledgeIcon,
  MissionControlIcon,
  OrganizationIcon,
  ProjectsIcon,
  ReportsIcon,
  TasksIcon,
  TeamIcon,
} from "../shared/icons";
import "./NavSidebar.css";

const PRIMARY_NAV = [
  { to: "/", label: "Mission Control", icon: MissionControlIcon, end: true },
  { to: "/tasks", label: "Tasks", icon: TasksIcon, badge: true },
  { to: "/workflow", label: "Workflow", icon: FlowIcon },
  { to: "/employees", label: "Employees", icon: EmployeesIcon },
  { to: "/organization", label: "Organization", icon: OrganizationIcon },
  { to: "/projects", label: "Projects", icon: ProjectsIcon },
  { to: "/knowledge", label: "Knowledge", icon: KnowledgeIcon },
  { to: "/reports", label: "Reports", icon: ReportsIcon },
  { to: "/team", label: "Team", icon: TeamIcon },
];

export function NavSidebar() {
  const { state } = useApp();
  const escalations = escalationCount(state.tasks);

  return (
    <aside className="nav-sidebar">
      <div className="nav-sidebar-brand">
        <div className="nav-sidebar-brand-title">{PRODUCT_NAME.toUpperCase()}</div>
        <div className="nav-sidebar-brand-sub">AI Operating System</div>
      </div>

      <nav className="nav-sidebar-nav">
        {PRIMARY_NAV.map(({ to, label, icon: ItemIcon, end, badge }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) => `nav-sidebar-item${isActive ? " active" : ""}`}
          >
            <ItemIcon />
            <span>{label}</span>
            {badge && escalations > 0 && <span className="nav-sidebar-count">{escalations}</span>}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
