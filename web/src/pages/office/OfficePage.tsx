import { useEffect } from "react";
import { OfficeCanvas } from "../../office/OfficeCanvas";
import { officeBridge } from "../../office/bridge";
import { ROOM_CAMERA } from "../../office/officeLayout";
import { Avatar } from "../../shared/Avatar";
import { StatusDot, STATUS_LABEL } from "../../shared/StatusDot";
import { currentUser } from "../../shared/mockData";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import "./OfficePage.css";

const ROOMS = Object.keys(ROOM_CAMERA);

// The one page that still mounts the SkyOffice canvas. Everything here is a
// projection of the same store every other page reads — room nav is
// page-local (it only ever meant "pan this page's camera"), and clicking an
// avatar goes through the same nav.openAgent() every other entry point uses.
export function OfficePage() {
  const { state, dispatch } = useApp();
  const nav = useAppNav();

  useEffect(() => {
    return officeBridge.onAgentClicked((agentId) => nav.openAgent(agentId));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="office-page">
      <div className="office-page-main">
        <nav className="office-room-nav">
          {ROOMS.map((label) => (
            <button
              key={label}
              className={`office-room-btn${state.ui.activeRoom === label ? " active" : ""}`}
              onClick={() => {
                dispatch({ type: "setRoom", room: label });
                if (label === "Focus Pods") dispatch({ type: "openPanel", panel: "focus" });
              }}
            >
              {label}
            </button>
          ))}
        </nav>
        <div className="office-canvas-wrap">
          <OfficeCanvas />
        </div>
      </div>

      <aside className="office-roster">
        <div className="office-roster-title">Who's Here ({state.agents.length + 1})</div>
        <ul className="office-roster-list">
          <li className="office-roster-row">
            <Avatar name={currentUser.displayName} seed="you" size={28} />
            <span className="office-roster-info">
              <span className="office-roster-name">{currentUser.displayName} (You)</span>
              <span className="office-roster-status">Online</span>
            </span>
            <StatusDot status="online" />
          </li>
          {state.agents.map((agent) => (
            <li
              className="office-roster-row clickable"
              key={agent.id}
              onClick={() => nav.openAgent(agent.id)}
            >
              <Avatar name={agent.name} seed={agent.id} size={28} />
              <span className="office-roster-info">
                <span className="office-roster-name">{agent.name}</span>
                <span className="office-roster-status">{STATUS_LABEL[agent.status]}</span>
              </span>
              <StatusDot status={agent.status} />
            </li>
          ))}
        </ul>
      </aside>
    </div>
  );
}
