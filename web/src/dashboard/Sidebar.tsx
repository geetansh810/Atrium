import { PRODUCT_NAME } from "../shared/theme";
import { StatusDot, STATUS_LABEL } from "../shared/StatusDot";
import { Avatar } from "../shared/Avatar";
import { currentUser } from "../shared/mockData";
import { escalationCount, useApp } from "../shared/store";
import type { PanelKey } from "../shared/store";
import {
  AnalyticsIcon,
  ApprovalsIcon,
  CafeIcon,
  ChatIcon,
  CoinsIcon,
  FlowIcon,
  FocusPodsIcon,
  LobbyIcon,
  MeetingRoomsIcon,
  MegaphoneIcon,
  RooftopIcon,
  ServerRoomIcon,
  WorkspaceIcon,
  WorkstationsIcon,
} from "../shared/icons";
import "./Sidebar.css";

const ROOMS = [
  { label: "Lobby", icon: LobbyIcon },
  { label: "Workstations", icon: WorkstationsIcon },
  { label: "Meeting Rooms", icon: MeetingRoomsIcon },
  { label: "Cafe", icon: CafeIcon },
  { label: "Focus Pods", icon: FocusPodsIcon },
  { label: "Server Room", icon: ServerRoomIcon },
  { label: "Rooftop", icon: RooftopIcon },
];

const APPS: { label: string; panel: PanelKey; icon: typeof ChatIcon }[] = [
  { label: "My Workspace", panel: "workspace", icon: WorkspaceIcon },
  { label: "Chat", panel: "chat", icon: ChatIcon },
  { label: "Analytics", panel: "analytics", icon: AnalyticsIcon },
  { label: "Announcements", panel: "announcements", icon: MegaphoneIcon },
  { label: "Approvals", panel: "approvals", icon: ApprovalsIcon },
  { label: "Payroll", panel: "budget", icon: CoinsIcon },
  { label: "Task Flow", panel: "flow", icon: FlowIcon },
];

export function Sidebar() {
  const { state, dispatch } = useApp();
  const escalations = escalationCount(state.tasks);

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-brand-title">{PRODUCT_NAME.toUpperCase()}</div>
        <div className="sidebar-brand-sub">Agentic Employee Office</div>
      </div>

      <nav className="sidebar-nav">
        {ROOMS.map(({ label, icon: RoomIcon }) => (
          <button
            key={label}
            className={`sidebar-nav-item${state.ui.activeRoom === label ? " active" : ""}`}
            onClick={() => {
              dispatch({ type: "setRoom", room: label });
              if (label === "Focus Pods") dispatch({ type: "openPanel", panel: "focus" });
            }}
          >
            <RoomIcon />
            <span>{label}</span>
          </button>
        ))}
      </nav>

      <div className="sidebar-section-label">Apps</div>
      <nav className="sidebar-nav">
        {APPS.map(({ label, panel, icon: AppIcon }) => (
          <button
            key={panel}
            className={`sidebar-nav-item${state.ui.activePanel === panel ? " active" : ""}`}
            onClick={() => dispatch({ type: "openPanel", panel })}
          >
            <AppIcon />
            <span>{label}</span>
            {panel === "approvals" && escalations > 0 && (
              <span className="sidebar-count">{escalations}</span>
            )}
          </button>
        ))}
      </nav>

      <div className="sidebar-roster">
        <div className="sidebar-roster-title">Who's Here ({state.agents.length + 1})</div>
        <ul className="sidebar-roster-list">
          <li className="sidebar-roster-row">
            <span className="sidebar-avatar sidebar-avatar-user">{currentUser.displayName[0]}</span>
            <span className="sidebar-roster-info">
              <span className="sidebar-roster-name">{currentUser.displayName} (You)</span>
              <span className="sidebar-roster-status">Online</span>
            </span>
            <StatusDot status="online" />
          </li>
          {state.agents.map((agent) => (
            <li
              className="sidebar-roster-row clickable"
              key={agent.id}
              onClick={() => dispatch({ type: "openPanel", panel: "profile", agentId: agent.id })}
            >
              <Avatar name={agent.name} seed={agent.id} size={28} />
              <span className="sidebar-roster-info">
                <span className="sidebar-roster-name">{agent.name}</span>
                <span className="sidebar-roster-status">{STATUS_LABEL[agent.status]}</span>
              </span>
              <StatusDot status={agent.status} />
            </li>
          ))}
        </ul>
      </div>

      <button className="sidebar-invite" onClick={() => dispatch({ type: "setInviteOpen", open: true })}>
        + Invite Agent
      </button>
    </aside>
  );
}
