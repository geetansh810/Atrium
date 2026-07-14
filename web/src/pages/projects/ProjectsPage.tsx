import { useState } from "react";
import { useNavigate } from "react-router";
import { useApp } from "../../shared/store";
import { USE_MOCKS } from "../../shared/config";
import { formatTokens } from "../../shared/format";
import { useProjects } from "../../shared/domains/projects";
import { StatusPill } from "../../ui/StatusPill";
import type { PillTone } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import "./ProjectsPage.css";

const STATUS_LABEL: Record<string, string> = {
  planning: "Planning",
  active: "Active",
  on_hold: "On Hold",
  completed: "Completed",
};

function statusTone(status: string): PillTone {
  if (status === "active") return "running";
  if (status === "completed") return "success";
  if (status === "on_hold") return "warning";
  return "neutral";
}

// Net-new domain, no backend equivalent yet (MF-5) — real tasks are
// client-side-assignable to these mock projects via shared/domains/projects.ts.
export function ProjectsPage() {
  const { state } = useApp();
  const navigate = useNavigate();
  const { projects, taskLinks, createProject } = useProjects();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const submit = () => {
    if (!name.trim()) return;
    const id = createProject({
      name: name.trim(),
      description: description.trim(),
      status: "planning",
      budgetCapTokens: null,
    });
    setName("");
    setDescription("");
    setCreating(false);
    navigate(`/projects/${id}`);
  };

  return (
    <div className="projects-page">
      <header className="projects-page-head">
        <div>
          <h1>Projects</h1>
          <p>Group related tasks together for a shared view.</p>
        </div>
        <div className="projects-page-head-actions">
          {!USE_MOCKS && <StatusPill label="Sample data" tone="warning" />}
          <button className="btn accent" onClick={() => setCreating((v) => !v)}>
            {creating ? "Cancel" : "+ New Project"}
          </button>
        </div>
      </header>

      {creating && (
        <div className="projects-new-form">
          <div className="field">
            <label>Name</label>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Project name" autoFocus />
          </div>
          <div className="field">
            <label>Description</label>
            <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <button className="btn primary sm" disabled={!name.trim()} onClick={submit}>
            Create
          </button>
        </div>
      )}

      {projects.length === 0 ? (
        <EmptyState title="No projects yet." description="Create one to start grouping tasks." />
      ) : (
        <div className="projects-grid">
          {projects.map((project) => {
            const linkedIds = taskLinks[project.id] ?? [];
            const linkedTasks = linkedIds
              .map((id) => state.tasks.find((t) => t.id === id))
              .filter((t): t is NonNullable<typeof t> => Boolean(t));
            return (
              <button key={project.id} className="project-card" onClick={() => navigate(`/projects/${project.id}`)}>
                <div className="project-card-top">
                  <span className="project-card-name">{project.name}</span>
                  <StatusPill label={STATUS_LABEL[project.status] ?? project.status} tone={statusTone(project.status)} />
                </div>
                <p className="project-card-desc">{project.description}</p>
                <div className="project-card-foot">
                  <span>{linkedTasks.length} task{linkedTasks.length === 1 ? "" : "s"}</span>
                  {project.budgetCapTokens != null && <span>{formatTokens(project.budgetCapTokens)} cap</span>}
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
