import { useState } from "react";
import { useApp } from "../shared/store";
import { NavSidebar } from "./NavSidebar";
import { GlobalHeader } from "./GlobalHeader";
import { AppRoutes } from "./routes";
import { Toast } from "../ui/Toast";
import { CommandPalette } from "../ui/CommandPalette";
import { ChatPanel } from "../dashboard/panels/ChatPanel";
import { InviteAgentModal } from "../dashboard/panels/InviteAgentModal";
import { NewTaskModal } from "../dashboard/panels/NewTaskModal";
import "./AppShell.css";

export function AppShell() {
  const { state } = useApp();
  const { inviteOpen, newTaskOpen, chatOpen } = state.ui;
  // Off-canvas nav below 900px (NavSidebar.css) — local chrome state, not
  // store state, matching CommandPalette's own open-state precedent for
  // shell-only UI that neither mode needs to persist or diverge on.
  const [navOpen, setNavOpen] = useState(false);

  return (
    <div className="app-shell">
      <NavSidebar open={navOpen} onClose={() => setNavOpen(false)} />

      <div className="app-shell-main-col">
        <GlobalHeader onMenuClick={() => setNavOpen((v) => !v)} />
        <div className="app-shell-content">
          <AppRoutes />
        </div>
      </div>

      <Toast />
      <CommandPalette />

      {chatOpen && <ChatPanel />}
      {inviteOpen && <InviteAgentModal />}
      {newTaskOpen && <NewTaskModal />}
    </div>
  );
}
