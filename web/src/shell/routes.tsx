import { Navigate, Route, Routes } from "react-router";
import { MissionControlPage } from "../pages/mission/MissionControlPage";
import { TasksPage } from "../pages/tasks/TasksPage";
import { ReviewInbox } from "../pages/tasks/ReviewInbox";
import { WorkflowPage } from "../pages/workflow/WorkflowPage";
import { EmployeesPage } from "../pages/employees/EmployeesPage";
import { EmployeeProfile } from "../pages/employees/EmployeeProfile";
import { OrganizationPage } from "../pages/organization/OrganizationPage";
import { ProjectsPage } from "../pages/projects/ProjectsPage";
import { ProjectDetail } from "../pages/projects/ProjectDetail";
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
      <Route path="/tasks/review" element={<ReviewInbox />} />
      <Route path="/tasks/:id" element={<TasksPage />} />
      <Route path="/workflow" element={<WorkflowPage />} />
      <Route path="/employees" element={<EmployeesPage />} />
      <Route path="/employees/:id" element={<EmployeeProfile />} />
      <Route path="/organization" element={<OrganizationPage />} />
      <Route path="/projects" element={<ProjectsPage />} />
      <Route path="/projects/:id" element={<ProjectDetail />} />
      <Route path="/knowledge" element={<KnowledgePage />} />
      <Route path="/reports" element={<ReportsPage />} />
      <Route path="/office" element={<OfficePage />} />
      <Route path="/notifications" element={<NotificationsPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
