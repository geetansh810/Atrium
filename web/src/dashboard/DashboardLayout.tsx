import { useApp } from "../shared/store";
import { OfficeCanvas } from "../office/OfficeCanvas";
import { BotBar } from "./BotBar";
import { RightRail } from "./RightRail";
import { Sidebar } from "./Sidebar";
import { TopBar } from "./TopBar";
import { AgentProfilePanel } from "./panels/AgentProfilePanel";
import { AnalyticsPanel } from "./panels/AnalyticsPanel";
import { AnnouncementsPanel } from "./panels/AnnouncementsPanel";
import { ApprovalsPanel } from "./panels/ApprovalsPanel";
import { BudgetPanel } from "./panels/BudgetPanel";
import { ChatPanel } from "./panels/ChatPanel";
import { FocusPodPanel } from "./panels/FocusPodPanel";
import { InviteAgentModal } from "./panels/InviteAgentModal";
import { NewTaskModal } from "./panels/NewTaskModal";
import { TaskFlowPanel } from "./panels/TaskFlowPanel";
import { WorkspacePanel } from "./panels/WorkspacePanel";
import "./DashboardLayout.css";

const PANELS = {
  profile: AgentProfilePanel,
  workspace: WorkspacePanel,
  analytics: AnalyticsPanel,
  chat: ChatPanel,
  announcements: AnnouncementsPanel,
  approvals: ApprovalsPanel,
  budget: BudgetPanel,
  flow: TaskFlowPanel,
  focus: FocusPodPanel,
} as const;

export function DashboardLayout() {
  const { state } = useApp();
  const { activePanel, railVisible, inviteOpen, newTaskOpen } = state.ui;
  const ActivePanel = activePanel ? PANELS[activePanel] : null;

  return (
    <div className="dashboard">
      <Sidebar />

      <div className="dashboard-center">
        <TopBar />
        <div className="dashboard-office">
          <OfficeCanvas />
          {ActivePanel && <ActivePanel />}
        </div>
        <BotBar />
      </div>

      {railVisible && <RightRail />}

      {inviteOpen && <InviteAgentModal />}
      {newTaskOpen && <NewTaskModal />}
    </div>
  );
}
