import { useState } from "react";
import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { formatTokens } from "../../shared/format";
import { costPerTask } from "../../shared/mockData";
import { useApp } from "../../shared/store";
import { PanelShell } from "./PanelShell";

function spendColor(pct: number): string {
  if (pct >= 90) return "var(--status-flagged)";
  if (pct >= 75) return "var(--status-away)";
  return "var(--accent)";
}

export function BudgetPanel() {
  const { state, dispatch } = useApp();
  const [editing, setEditing] = useState<string | null>(null);
  const [capDraft, setCapDraft] = useState("");

  const company = state.budgets.find((b) => b.agentId === null);
  const perAgent = state.budgets.filter((b) => b.agentId !== null);
  const agentById = new Map(state.agents.map((a) => [a.id, a]));
  const taskById = new Map(state.tasks.map((t) => [t.id, t]));

  const saveCap = (budgetId: string) => {
    const parsed = Number(capDraft.replace(/[^0-9]/g, ""));
    if (parsed > 0) dispatch({ type: "setBudgetCap", budgetId, capTokens: parsed });
    setEditing(null);
  };

  const renderCap = (budgetId: string, capTokens: number) =>
    editing === budgetId ? (
      <input
        className="budget-cap-input"
        value={capDraft}
        autoFocus
        onChange={(e) => setCapDraft(e.target.value)}
        onBlur={() => saveCap(budgetId)}
        onKeyDown={(e) => e.key === "Enter" && saveCap(budgetId)}
      />
    ) : (
      <button
        className="budget-cap-btn"
        title="Click to edit cap"
        onClick={() => {
          setEditing(budgetId);
          setCapDraft(String(capTokens));
        }}
      >
        / {formatTokens(capTokens)}
      </button>
    );

  return (
    <PanelShell title="Payroll — Token Budgets" subtitle={`Period ${company?.period ?? "2026-07"}`} width={560}>
      {company && (
        <>
          <div className="section-title">Company cap</div>
          <div className="budget-row">
            <span className="budget-name">All agents</span>
            <span className="budget-bar">
              <ProgressBar
                value={(company.spentTokens / company.capTokens) * 100}
                color={spendColor((company.spentTokens / company.capTokens) * 100)}
                height={8}
              />
            </span>
            <span className="budget-nums">
              {formatTokens(company.spentTokens)} {renderCap(company.id, company.capTokens)}
            </span>
          </div>
        </>
      )}

      <div className="section-title">Per-agent caps</div>
      {perAgent.map((budget) => {
        const agent = budget.agentId ? agentById.get(budget.agentId) : undefined;
        if (!agent) return null;
        const pct = (budget.spentTokens / budget.capTokens) * 100;
        return (
          <div className="budget-row" key={budget.id}>
            <span className="budget-name">
              <Avatar name={agent.name} seed={agent.id} size={22} />
              {agent.name}
            </span>
            <span className="budget-bar">
              <ProgressBar value={pct} color={spendColor(pct)} height={8} />
            </span>
            <span className="budget-nums">
              {formatTokens(budget.spentTokens)} {renderCap(budget.id, budget.capTokens)}
            </span>
          </div>
        );
      })}
      <p className="about-text" style={{ marginTop: 10, fontSize: 11.5 }}>
        Over-cap agents are blocked at claim time and flagged — never silently. Click a cap to adjust it.
      </p>

      <div className="section-title">Cost per task (top spenders)</div>
      {costPerTask.map(({ taskId, tokens, costUsd }) => (
        <div className="cost-row" key={taskId}>
          <span>{taskById.get(taskId)?.title ?? taskId}</span>
          <span className="mono">
            {formatTokens(tokens)} tok · ${costUsd.toFixed(2)}
          </span>
        </div>
      ))}
    </PanelShell>
  );
}
