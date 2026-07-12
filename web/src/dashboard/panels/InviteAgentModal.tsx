import { useState } from "react";
import { CloseIcon } from "../../shared/icons";
import { roleTemplates } from "../../shared/mockData";
import { useApp } from "../../shared/store";
import type { ModelProvider } from "../../shared/types";
import "./panels.css";

// Hire flow per 01-product-spec §3.1: pick a role template or define custom
// (role, skills, model, budget, manager).
export function InviteAgentModal() {
  const { state, dispatch } = useApp();
  const [templateKey, setTemplateKey] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [roleTitle, setRoleTitle] = useState("");
  const [skills, setSkills] = useState("");
  const [provider, setProvider] = useState<ModelProvider>("anthropic");
  const [modelName, setModelName] = useState("claude-sonnet-5");
  const [managerId, setManagerId] = useState("");
  const [budget, setBudget] = useState("1000000");
  const [about, setAbout] = useState("");

  const close = () => dispatch({ type: "setInviteOpen", open: false });

  const pickTemplate = (key: string) => {
    const tpl = roleTemplates.find((t) => t.key === key);
    if (!tpl) return;
    setTemplateKey(key);
    setRoleTitle(tpl.title);
    setSkills(tpl.skillTags.join(", "));
    setProvider(tpl.modelProvider);
    setModelName(tpl.modelName);
    setBudget(String(tpl.defaultBudgetTokens));
    setAbout(tpl.description);
  };

  const canHire = name.trim() && roleTitle.trim() && skills.trim() && modelName.trim();

  const hire = () => {
    if (!canHire) return;
    dispatch({
      type: "inviteAgent",
      input: {
        name: name.trim(),
        roleTitle: roleTitle.trim(),
        skillTags: skills.split(",").map((s) => s.trim()).filter(Boolean),
        modelProvider: provider,
        modelName: modelName.trim(),
        managerAgentId: managerId || null,
        about: about.trim() || `${roleTitle.trim()} agent.`,
        budgetTokens: Math.max(1, Number(budget) || 1_000_000),
      },
    });
  };

  return (
    <div className="modal-overlay" onClick={close}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <header className="panel-head">
          <div>
            <h2>Invite Agent</h2>
            <div className="panel-subtitle">Pick a role template or define a custom role</div>
          </div>
          <button className="panel-close" onClick={close} aria-label="Close">
            <CloseIcon />
          </button>
        </header>

        <div className="panel-body">
          <div className="template-grid">
            {roleTemplates.map((tpl) => (
              <button
                key={tpl.key}
                className={`template-card${templateKey === tpl.key ? " selected" : ""}`}
                onClick={() => pickTemplate(tpl.key)}
              >
                <div className="template-card-title">{tpl.title}</div>
                <div className="template-card-desc">{tpl.description}</div>
              </button>
            ))}
          </div>

          <div className="field-row">
            <div className="field">
              <label>Agent name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. LegalAgent" />
            </div>
            <div className="field">
              <label>Role title</label>
              <input value={roleTitle} onChange={(e) => setRoleTitle(e.target.value)} placeholder="e.g. Legal Reviewer" />
            </div>
          </div>

          <div className="field">
            <label>Skills (comma-separated)</label>
            <input value={skills} onChange={(e) => setSkills(e.target.value)} placeholder="Contract Review, Summarization" />
          </div>

          <div className="field-row">
            <div className="field">
              <label>Model provider</label>
              <select value={provider} onChange={(e) => setProvider(e.target.value as ModelProvider)}>
                <option value="anthropic">Anthropic</option>
                <option value="openai">OpenAI</option>
                <option value="google">Google</option>
              </select>
            </div>
            <div className="field">
              <label>Model</label>
              <input value={modelName} onChange={(e) => setModelName(e.target.value)} />
            </div>
          </div>

          <div className="field-row">
            <div className="field">
              <label>Manager</label>
              <select value={managerId} onChange={(e) => setManagerId(e.target.value)}>
                <option value="">None (reports to you)</option>
                {state.agents.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Monthly budget (tokens)</label>
              <input value={budget} onChange={(e) => setBudget(e.target.value.replace(/[^0-9]/g, ""))} />
            </div>
          </div>

          <div className="field">
            <label>About (optional)</label>
            <textarea rows={2} value={about} onChange={(e) => setAbout(e.target.value)} />
          </div>
        </div>

        <div className="modal-actions">
          <button className="btn" onClick={close}>
            Cancel
          </button>
          <button className="btn primary" disabled={!canHire} onClick={hire}>
            Hire agent
          </button>
        </div>
      </div>
    </div>
  );
}
