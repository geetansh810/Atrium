import { useEffect, useState } from "react";
import { PeopleIcon, SettingsIcon } from "../shared/icons";
import { useApp } from "../shared/store";
import "./TopBar.css";

function useClock() {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000 * 30);
    return () => clearInterval(id);
  }, []);
  return now.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

export function TopBar() {
  const { state, dispatch } = useApp();
  const time = useClock();
  const [observeMode, setObserveMode] = useState(false);

  // Live presence: every non-offline agent + the current user
  const onlineCount = state.agents.filter((a) => a.status !== "offline").length + 1;

  return (
    <header className="topbar">
      <button
        className={`topbar-observe${observeMode ? " active" : ""}`}
        onClick={() => setObserveMode((v) => !v)}
      >
        {observeMode ? "OBSERVE MODE: ON" : "OBSERVE MODE"}
      </button>

      <div className="topbar-right">
        <span className="topbar-clock">{time}</span>
        <span className="topbar-online">
          <span className="topbar-online-dot" />
          {onlineCount} online
        </span>
        <button
          className="topbar-icon-btn"
          aria-label="Toggle agent rail"
          onClick={() => dispatch({ type: "toggleRail" })}
        >
          <PeopleIcon />
        </button>
        <button
          className="topbar-icon-btn"
          aria-label="Settings"
          onClick={() => dispatch({ type: "setNotice", text: "Settings arrive with the backend milestones." })}
        >
          <SettingsIcon />
        </button>
      </div>
    </header>
  );
}
