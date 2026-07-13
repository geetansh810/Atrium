import { useApp } from "../../shared/store";
import { PagePlaceholder } from "../PagePlaceholder";

export function WorkflowPage() {
  const { dispatch } = useApp();
  return (
    <PagePlaceholder
      title="Workflow"
      subtitle="Live pipeline view arrives in MF-5"
      description="Until then, the classic Task Flow panel shows the same parent → child task graph."
      actions={[{ label: "Open Task Flow", onClick: () => dispatch({ type: "openPanel", panel: "flow" }) }]}
    />
  );
}
