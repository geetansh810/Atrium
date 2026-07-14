import { useState } from "react";
import { CloseIcon } from "../../shared/icons";
import { useApp } from "../../shared/store";

export function NewTaskModal() {
  const { state, dispatch } = useApp();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [skill, setSkill] = useState("");
  const [priority, setPriority] = useState("3");
  const [eta, setEta] = useState("");

  const close = () => dispatch({ type: "setNewTaskOpen", open: false });

  const allSkills = Array.from(new Set(state.agents.flatMap((a) => a.skillTags))).sort();
  const effectiveSkill = skill || allSkills[0] || "";
  const canCreate = title.trim() && effectiveSkill;

  const create = () => {
    if (!canCreate) return;
    dispatch({
      type: "createTask",
      input: {
        title: title.trim(),
        description: description.trim(),
        requiredSkill: effectiveSkill,
        priority: Number(priority),
        etaMinutes: eta ? Number(eta) : null,
      },
    });
  };

  return (
    <div className="modal-overlay" onClick={close}>
      <div className="modal" onClick={(e) => e.stopPropagation()} style={{ width: 460 }}>
        <header className="panel-head">
          <div>
            <h2>New Task</h2>
            <div className="panel-subtitle">Routed to the first agent with the required skill</div>
          </div>
          <button className="panel-close" onClick={close} aria-label="Close">
            <CloseIcon />
          </button>
        </header>

        <div className="panel-body">
          <div className="field">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What needs doing?" autoFocus />
          </div>
          <div className="field">
            <label>Description (optional)</label>
            <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <div className="field">
            <label>Required skill</label>
            <select value={effectiveSkill} onChange={(e) => setSkill(e.target.value)}>
              {allSkills.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div className="field-row">
            <div className="field">
              <label>Priority</label>
              <select value={priority} onChange={(e) => setPriority(e.target.value)}>
                <option value="1">1 — Critical</option>
                <option value="2">2 — High</option>
                <option value="3">3 — Normal</option>
                <option value="4">4 — Low</option>
                <option value="5">5 — Backlog</option>
              </select>
            </div>
            <div className="field">
              <label>ETA estimate (minutes)</label>
              <input value={eta} onChange={(e) => setEta(e.target.value.replace(/[^0-9]/g, ""))} placeholder="30" />
            </div>
          </div>
        </div>

        <div className="modal-actions">
          <button className="btn" onClick={close}>
            Cancel
          </button>
          <button className="btn primary" disabled={!canCreate} onClick={create}>
            Create task
          </button>
        </div>
      </div>
    </div>
  );
}
