import { useApp } from "../shared/store";
import { AgentProfilePanel } from "../dashboard/panels/AgentProfilePanel";
import { AnalyticsPanel } from "../dashboard/panels/AnalyticsPanel";
import { AnnouncementsPanel } from "../dashboard/panels/AnnouncementsPanel";
import { ApprovalsPanel } from "../dashboard/panels/ApprovalsPanel";
import { BudgetPanel } from "../dashboard/panels/BudgetPanel";
import { ChatPanel } from "../dashboard/panels/ChatPanel";
import { FocusPodPanel } from "../dashboard/panels/FocusPodPanel";
import { TaskFlowPanel } from "../dashboard/panels/TaskFlowPanel";
import { WorkspacePanel } from "../dashboard/panels/WorkspacePanel";

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

// Temporary bridge (deleted in MF-6): every panel not yet ported to a routed
// page still renders as a full-viewport overlay, driven by the same
// ui.activePanel/openPanel/closePanel contract as before the redesign. Each
// milestone that ports a panel to a real page removes its entry here.
export function LegacyPanelHost() {
  const { state } = useApp();
  const ActivePanel = state.ui.activePanel ? PANELS[state.ui.activePanel] : null;
  return ActivePanel ? <ActivePanel /> : null;
}
