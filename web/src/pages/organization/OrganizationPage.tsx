import { useApp } from "../../shared/store";
import { PagePlaceholder } from "../PagePlaceholder";

export function OrganizationPage() {
  const { dispatch } = useApp();
  return (
    <PagePlaceholder
      title="Organization"
      subtitle="Org chart arrives in MF-4"
      description="It'll build directly from each agent's manager, with payroll folded in as a tab. The classic Payroll panel covers budgets for now."
      actions={[{ label: "Open Payroll", onClick: () => dispatch({ type: "openPanel", panel: "budget" }) }]}
    />
  );
}
