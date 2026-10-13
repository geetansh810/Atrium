import { useEffect } from "react";
import { NavLink } from "react-router";
import { escalationCount, useApp } from "../shared/store";
import { Avatar } from "../shared/Avatar";
import { BrandMark } from "../shared/BrandMark";
import { clearAuthSession, useAuthSession } from "../shared/auth";
import { currentUser } from "../shared/mockData";
import { USE_MOCKS } from "../shared/config";
import {
  EmployeesIcon,
  KnowledgeIcon,
  MissionControlIcon,
  OrganizationIcon,
  SettingsIcon,
  TasksIcon,
  TeamIcon,
} from "../shared/icons";
import "./NavSidebar.css";

const HOME_NAV = [
  { to: "/", label: "Mission Control", icon: MissionControlIcon, end: true },
  { to: "/tasks/review", label: "Review", icon: TasksIcon, badge: true },
];

const WORK_NAV = [{ to: "/tasks", label: "Tasks", icon: TasksIcon, end: true }];

const COMPANY_NAV = [
  { to: "/organization", label: "Organization", icon: OrganizationIcon },
  { to: "/knowledge", label: "Knowledge", icon: KnowledgeIcon },
];

interface NavSidebarProps {
  // Below the 900px breakpoint (see NavSidebar.css) the sidebar becomes an
  // off-canvas drawer driven by these — above it, both are effectively inert
  // since the desktop media query never applies the transform they control.
  open: boolean;
  onClose: () => void;
}

export function NavSidebar({ open, onClose }: NavSidebarProps) {
  const { state, dispatch } = useApp();
  const session = useAuthSession();
  const personName = session?.displayName ?? currentUser.displayName;
  const personRole = session?.role ?? "Demo workspace";
  const escalations = escalationCount(state.tasks);
  const liveIds = new Set(
    state.tasks
      .filter((t) => (t.status === "claimed" || t.status === "in_progress") && t.assignedAgentId)
      .map((t) => t.assignedAgentId as string),
  );
  const crew = [...state.agents].sort((a, b) => {
    const rank = (id: string) => (liveIds.has(id) ? 0 : 1);
    return rank(a.id) - rank(b.id) || a.name.localeCompare(b.name);
  });

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
          <BrandMark compact />
        </div>

        <button
          className="btn sm nav-sidebar-new"
          type="button"
          onClick={() => {
            dispatch({ type: "setNewTaskOpen", open: true });
            onClose();
          }}
        >
          New task
        </button>

        <nav className="nav-sidebar-nav" aria-label="Home">
          <div className="nav-sidebar-label">Home</div>
          {HOME_NAV.map(({ to, label, icon: ItemIcon, end, badge }) => (
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

          <div className="nav-sidebar-label">Work</div>
          {WORK_NAV.map(({ to, label, icon: ItemIcon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) => `nav-sidebar-item${isActive ? " active" : ""}`}
            >
              <ItemIcon />
              <span>{label}</span>
            </NavLink>
          ))}

          <div className="nav-sidebar-label">Crew</div>
          {crew.map((agent) => (
            <NavLink
              key={agent.id}
              to={`/employees/${agent.id}`}
              onClick={onClose}
              className={({ isActive }) => `nav-sidebar-item nav-sidebar-agent${isActive ? " active" : ""}`}
            >
              <span className="nav-sidebar-avatar">
                <Avatar name={agent.name} seed={agent.id} size={25} />
                <i className={`nav-sidebar-dot${liveIds.has(agent.id) ? " live" : ""}`} aria-hidden="true" />
              </span>
              <span className="nav-sidebar-agent-copy">
                <b>{agent.name}</b>
                <small>{liveIds.has(agent.id) ? "Working now" : agent.roleTitle}</small>
              </span>
            </NavLink>
          ))}
          <NavLink to="/team" onClick={onClose} className={({ isActive }) => `nav-sidebar-item${isActive ? " active" : ""}`}>
            <TeamIcon />
            <span>Watch</span>
          </NavLink>
          <NavLink
            to="/employees"
            end
            onClick={onClose}
            className={({ isActive }) => `nav-sidebar-item${isActive ? " active" : ""}`}
          >
            <EmployeesIcon />
            <span>Roster</span>
          </NavLink>

          <div className="nav-sidebar-label">Company</div>
          {COMPANY_NAV.map(({ to, label, icon: ItemIcon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={onClose}
              className={({ isActive }) => `nav-sidebar-item${isActive ? " active" : ""}`}
            >
              <ItemIcon />
              <span>{label}</span>
            </NavLink>
          ))}
          <a className="nav-sidebar-item" href="/docs" onClick={onClose}>
            <KnowledgeIcon />
            <span>Docs</span>
          </a>
        </nav>

        <div className="nav-sidebar-account">
          <NavLink to="/settings" onClick={onClose} className="nav-sidebar-profile">
            <Avatar name={personName} seed={session?.userId ?? personName} size={30} />
            <span>
              <b>{personName}</b>
              <small>{personRole}</small>
            </span>
            <SettingsIcon />
          </NavLink>
          {!USE_MOCKS && (
            <button type="button" onClick={clearAuthSession}>
              Log out
            </button>
          )}
        </div>
      </aside>
    </>
  );
}
