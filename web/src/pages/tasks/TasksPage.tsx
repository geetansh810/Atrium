import { useApp } from "../../shared/store";
import { PagePlaceholder } from "../PagePlaceholder";

export function TasksPage() {
  const { dispatch } = useApp();
  return (
    <PagePlaceholder
      title="Tasks"
      subtitle="Kanban board arrives in MF-3"
      description="Until then, the classic Workspace and Review Queue panels cover task management."
      actions={[
        { label: "Open My Workspace", onClick: () => dispatch({ type: "openPanel", panel: "workspace" }) },
        { label: "Open Review Queue", onClick: () => dispatch({ type: "openPanel", panel: "approvals" }) },
      ]}
    />
  );
}
