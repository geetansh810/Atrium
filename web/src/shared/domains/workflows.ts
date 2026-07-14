// Net-new mock-only domain — a library of named, reusable workflow templates.
// Distinct from the *live* pipeline view on WorkflowPage, which is derived
// straight from real parent/child tasks (see shared/selectors.ts) — this
// domain has no backend equivalent at all (MF-5).
import workflowTemplatesJson from "../mocks/workflowTemplates.json";
import type { WorkflowTemplate } from "../types";

export function useWorkflowTemplates(): WorkflowTemplate[] {
  return workflowTemplatesJson.templates as WorkflowTemplate[];
}
