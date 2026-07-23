import { useEffect } from "react";
import { NavLink } from "react-router";
import { PRODUCT_NAME } from "../shared/theme";
import { escalationCount, useApp } from "../shared/store";
import {
  EmployeesIcon,
  FlowIcon,
  KnowledgeIcon,
  LogsIcon,
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
  { to: "/logs", label: "LLM Logs", icon: LogsIcon },
  { to: "/team", label: "Team", icon: TeamIcon },
];

interface NavSidebarProps {
  // Below the 900px breakpoint (see NavSidebar.css) the sidebar becomes an
  // off-canvas drawer driven by these — above it, both are effectively inert
  // since the desktop media query never applies the transform they control.
  open: boolean;
  onClose: () => void;
}

export function NavSidebar({ open, onClose }: NavSidebarProps) {
  const { state } = useApp();
  const escalations = escalationCount(state.tasks);

  // Escape closes the mobile drawer, and the background must not scroll
  // behind it — both harmless no-ops at desktop widths where it's never open.
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose]);

  return (
    <>
      {open && <div className="nav-sidebar-overlay" onClick={onClose} aria-hidden="true" />}
      <aside className={`nav-sidebar${open ? " open" : ""}`}>
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
              onClick={onClose}
              className={({ isActive }) => `nav-sidebar-item${isActive ? " active" : ""}`}
            >
              <ItemIcon />
              <span>{label}</span>
              {badge && escalations > 0 && <span className="nav-sidebar-count">{escalations}</span>}
            </NavLink>
          ))}
        </nav>
      </aside>
    </>
  );
}
