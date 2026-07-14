import { NavLink } from "react-router";
import { PRODUCT_NAME } from "../shared/theme";
import { escalationCount, useApp } from "../shared/store";
import type { PanelKey } from "../shared/store";
import {
  AnalyticsIcon,
  ChatIcon,
  EmployeesIcon,
  FlowIcon,
  KnowledgeIcon,
  MegaphoneIcon,
  MissionControlIcon,
  OfficeIcon,
  OrganizationIcon,
  ProjectsIcon,
  ReportsIcon,
  TasksIcon,
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
  { to: "/office", label: "Office View", icon: OfficeIcon },
];

// Every classic reference-2 panel, unchanged, so nothing goes unreachable
// while its routed replacement is still a placeholder page. Retired
// milestone-by-milestone as each panel gets a real page (gone by MF-6).
const CLASSIC_PANELS: { label: string; panel: PanelKey; icon: typeof ChatIcon }[] = [
  { label: "Chat", panel: "chat", icon: ChatIcon },
  { label: "Analytics", panel: "analytics", icon: AnalyticsIcon },
  { label: "Announcements", panel: "announcements", icon: MegaphoneIcon },
  { label: "Task Flow", panel: "flow", icon: FlowIcon },
];

export function NavSidebar() {
  const { state, dispatch } = useApp();
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

      <div className="nav-sidebar-section-label">Classic Panels</div>
      <nav className="nav-sidebar-nav">
        {CLASSIC_PANELS.map(({ label, panel, icon: PanelIcon }) => (
          <button
            key={panel}
            className={`nav-sidebar-item${state.ui.activePanel === panel ? " active" : ""}`}
            onClick={() => dispatch({ type: "openPanel", panel })}
          >
            <PanelIcon />
            <span>{label}</span>
          </button>
        ))}
      </nav>
    </aside>
  );
}
