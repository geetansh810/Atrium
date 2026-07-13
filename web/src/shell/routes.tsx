import { Navigate, Route, Routes } from "react-router";
import { MissionControlPage } from "../pages/mission/MissionControlPage";
import { TasksPage } from "../pages/tasks/TasksPage";
import { WorkflowPage } from "../pages/workflow/WorkflowPage";
import { EmployeesPage } from "../pages/employees/EmployeesPage";
import { OrganizationPage } from "../pages/organization/OrganizationPage";
import { ProjectsPage } from "../pages/projects/ProjectsPage";
import { KnowledgePage } from "../pages/knowledge/KnowledgePage";
import { ReportsPage } from "../pages/reports/ReportsPage";
import { OfficePage } from "../pages/office/OfficePage";
import { NotificationsPage } from "../pages/notifications/NotificationsPage";
import { SettingsPage } from "../pages/settings/SettingsPage";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<MissionControlPage />} />
      <Route path="/tasks" element={<TasksPage />} />
      <Route path="/workflow" element={<WorkflowPage />} />
      <Route path="/employees" element={<EmployeesPage />} />
      <Route path="/organization" element={<OrganizationPage />} />
      <Route path="/projects" element={<ProjectsPage />} />
      <Route path="/knowledge" element={<KnowledgePage />} />
      <Route path="/reports" element={<ReportsPage />} />
      <Route path="/office" element={<OfficePage />} />
      <Route path="/notifications" element={<NotificationsPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
