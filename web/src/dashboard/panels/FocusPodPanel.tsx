import { useEffect, useState } from "react";
import { Avatar } from "../../shared/Avatar";
import { formatElapsed } from "../../shared/format";
import { useApp } from "../../shared/store";
import { PanelShell } from "./PanelShell";

export function FocusPodPanel() {
  const { state, dispatch } = useApp();
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const selected = state.agents.find((a) => a.id === state.ui.selectedAgentId);
  const agent =
    selected?.status === "in_focus" ? selected : state.agents.find((a) => a.status === "in_focus");

  if (!agent) {
    return (
      <PanelShell title="Focus Pods" width={430}>
        <div className="empty-note">No one is in a focus pod right now.</div>
      </PanelShell>
    );
  }

  const podNumber = agent.locationKey.startsWith("focus_pod_")
    ? agent.locationKey.replace("focus_pod_", "")
    : "1";

  return (
    <PanelShell title={`Focus Pod ${podNumber}`} subtitle="Deep work in progress" width={430}>
      <div className="pod-art">
        <Avatar name={agent.name} seed={agent.id} size={64} square />
        <span style={{ fontSize: 13, color: "var(--text-muted)" }}>
          {agent.name} · {agent.currentActivity}
        </span>
      </div>

      <div className="pod-footer">
        <span className="pod-dnd">● Do not disturb ●</span>
        <span className="pod-timer">{formatElapsed(agent.statusSince, now)}</span>
        <button className="btn danger" onClick={() => dispatch({ type: "exitFocusPod", agentId: agent.id })}>
          Exit Pod
        </button>
      </div>

      <p className="about-text" style={{ marginTop: 14, fontSize: 12 }}>
        While in a pod, {agent.name} won't respond to chat and holds all meetings. Exiting the pod
        returns them to their desk.
      </p>
    </PanelShell>
  );
}
