// Net-new mock-only domain — Projects have no backend equivalent yet (MF-5,
// redesign plan §MF-5). Identical fixture + external store in both
// VITE_USE_MOCKS modes; useProjects() is the future React-Query swap point
// once a real Projects API lands. Real tasks are assignable to a project via
// a client-side-only id mapping (taskLinks) — nothing here is persisted
// server-side, so it resets on a full page reload.
import projectsJson from "../mocks/projects.json";
import type { Project, ProjectStatus } from "../types";
import { createDomainStore } from "./createDomainStore";

interface ProjectsDomainState {
  projects: Project[];
  taskLinks: Record<string, string[]>; // projectId -> real task ids
}

const store = createDomainStore<ProjectsDomainState>({
  projects: projectsJson.projects as Project[],
  taskLinks: projectsJson.taskLinks as Record<string, string[]>,
});

let idCounter = 0;
function makeProjectId(): string {
  idCounter += 1;
  return `project-local-${Date.now()}-${idCounter}`;
}

export interface NewProjectInput {
  name: string;
  description: string;
  status: ProjectStatus;
  budgetCapTokens: number | null;
}

export function useProjects() {
  const state = store.useDomainState();

  const createProject = (input: NewProjectInput): string => {
    const project: Project = {
      id: makeProjectId(),
      ownerAgentId: null,
      createdAt: new Date().toISOString(),
      ...input,
    };
    store.set((prev) => ({ ...prev, projects: [...prev.projects, project] }));
    return project.id;
  };

  const setProjectStatus = (projectId: string, status: ProjectStatus) => {
    store.set((prev) => ({
      ...prev,
      projects: prev.projects.map((p) => (p.id === projectId ? { ...p, status } : p)),
    }));
  };

  const setProjectBudgetCap = (projectId: string, capTokens: number | null) => {
    store.set((prev) => ({
      ...prev,
      projects: prev.projects.map((p) => (p.id === projectId ? { ...p, budgetCapTokens: capTokens } : p)),
    }));
  };

  const linkTask = (projectId: string, taskId: string) => {
    store.set((prev) => {
      const existing = prev.taskLinks[projectId] ?? [];
      if (existing.includes(taskId)) return prev;
      return { ...prev, taskLinks: { ...prev.taskLinks, [projectId]: [...existing, taskId] } };
    });
  };

  const unlinkTask = (projectId: string, taskId: string) => {
    store.set((prev) => ({
      ...prev,
      taskLinks: {
        ...prev.taskLinks,
        [projectId]: (prev.taskLinks[projectId] ?? []).filter((id) => id !== taskId),
      },
    }));
  };

  return {
    projects: state.projects,
    taskLinks: state.taskLinks,
    createProject,
    setProjectStatus,
    setProjectBudgetCap,
    linkTask,
    unlinkTask,
  };
}
