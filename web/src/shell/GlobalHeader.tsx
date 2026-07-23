import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router";
import { BellIcon, ChatIcon, MenuIcon, PlusIcon, SearchIcon, SettingsIcon } from "../shared/icons";
import { useApp } from "../shared/store";
import { useAppNav } from "../shared/nav";
import { useNotifications } from "../shared/notifications";
import { openCommandPalette } from "../ui/CommandPalette";
import "./GlobalHeader.css";

const PAGE_TITLES: Record<string, string> = {
  "/": "Mission Control",
  "/tasks": "Tasks",
  "/tasks/review": "Review Inbox",
  "/workflow": "Workflow",
  "/employees": "Employees",
  "/organization": "Organization",
  "/projects": "Projects",
  "/knowledge": "Knowledge",
  "/reports": "Reports",
  "/logs": "LLM Logs",
  "/team": "Team",
  "/notifications": "Notifications",
  "/settings": "Settings",
};

function useClock() {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000 * 30);
    return () => clearInterval(id);
  }, []);
  return now.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

interface GlobalHeaderProps {
  // Hamburger toggle for the off-canvas nav below 900px (NavSidebar.css) —
  // the button itself only renders visibly under that same breakpoint.
  onMenuClick: () => void;
}

export function GlobalHeader({ onMenuClick }: GlobalHeaderProps) {
  const { state, dispatch } = useApp();
  const location = useLocation();
  const nav = useAppNav();
  const time = useClock();
  const { unreadCount } = useNotifications();
  const onlineCount = state.agents.filter((a) => a.status !== "offline").length + 1;
  const title =
    PAGE_TITLES[location.pathname] ??
    (location.pathname.startsWith("/tasks/")
      ? "Tasks"
      : location.pathname.startsWith("/employees/")
        ? "Employees"
        : "Atrium");

  return (
    <header className="global-header">
      <div className="global-header-left">
        <button className="global-header-icon-btn global-header-menu-btn" aria-label="Open menu" onClick={onMenuClick}>
          <MenuIcon />
        </button>
        <h1 className="global-header-title">{title}</h1>
      </div>

      <div className="global-header-right">
        <button
          className="global-header-cmdk-hint"
          onClick={openCommandPalette}
          aria-label="Open command palette"
          title="Jump to… (⌘K)"
        >
          <SearchIcon width={13} height={13} />
          <span className="global-header-cmdk-hint-label">Jump to…</span>
          <kbd>⌘K</kbd>
        </button>

        <span className="global-header-clock">{time}</span>
        <span className="global-header-online" title={`${onlineCount} online`}>
          <span className="global-header-online-dot" />
          <span className="global-header-online-label">{onlineCount} online</span>
        </span>

        <button className="global-header-icon-btn" aria-label="Chat" onClick={() => nav.openChat()}>
          <ChatIcon />
        </button>
        <Link className="global-header-icon-btn" to="/notifications" aria-label="Notifications">
          <BellIcon />
          {unreadCount > 0 && <span className="global-header-badge">{unreadCount}</span>}
        </Link>
        <Link className="global-header-icon-btn" to="/settings" aria-label="Settings">
          <SettingsIcon />
        </Link>

        <button
          className="btn"
          title="New Task"
          onClick={() => dispatch({ type: "setNewTaskOpen", open: true })}
        >
          <PlusIcon width={13} height={13} /> <span className="btn-label">New Task</span>
        </button>
        <button
          className="btn primary"
          title="Hire Agent"
          onClick={() => dispatch({ type: "setInviteOpen", open: true })}
        >
          <PlusIcon width={13} height={13} /> <span className="btn-label">Hire Agent</span>
        </button>
      </div>
    </header>
  );
}
