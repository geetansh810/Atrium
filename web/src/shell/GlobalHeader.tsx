import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router";
import { BellIcon, ChatIcon, PlusIcon, SettingsIcon } from "../shared/icons";
import { escalationCount, useApp } from "../shared/store";
import { useAppNav } from "../shared/nav";
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
  "/office": "Office View",
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

export function GlobalHeader() {
  const { state, dispatch } = useApp();
  const location = useLocation();
  const nav = useAppNav();
  const time = useClock();
  const escalations = escalationCount(state.tasks);
  const onlineCount = state.agents.filter((a) => a.status !== "offline").length + 1;
  const title =
    PAGE_TITLES[location.pathname] ?? (location.pathname.startsWith("/tasks/") ? "Tasks" : "Atrium");

  return (
    <header className="global-header">
      <h1 className="global-header-title">{title}</h1>

      <div className="global-header-right">
        <span className="global-header-clock">{time}</span>
        <span className="global-header-online">
          <span className="global-header-online-dot" />
          {onlineCount} online
        </span>

        <button className="global-header-icon-btn" aria-label="Chat" onClick={() => nav.openChat()}>
          <ChatIcon />
        </button>
        <Link className="global-header-icon-btn" to="/notifications" aria-label="Notifications">
          <BellIcon />
          {escalations > 0 && <span className="global-header-badge">{escalations}</span>}
        </Link>
        <Link className="global-header-icon-btn" to="/settings" aria-label="Settings">
          <SettingsIcon />
        </Link>

        <button
          className="btn"
          onClick={() => dispatch({ type: "setNewTaskOpen", open: true })}
        >
          <PlusIcon width={13} height={13} /> New Task
        </button>
        <button
          className="btn primary"
          onClick={() => dispatch({ type: "setInviteOpen", open: true })}
        >
          + Hire Agent
        </button>
      </div>
    </header>
  );
}
