import { useState } from "react";
import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { StatusDot, STATUS_LABEL } from "../../shared/StatusDot";
import { DEV_COMPANY_ID, USE_MOCKS } from "../../shared/config";
import { formatCost, formatTokens } from "../../shared/format";
import { costPerTask } from "../../shared/mockData";
import { currentPeriod, useCostPerTask } from "../../shared/queries";
import { orgTree } from "../../shared/selectors";
import type { OrgNode } from "../../shared/selectors";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import type { Task } from "../../shared/types";
import { Tabs } from "../../ui/Tabs";
import type { TabItem } from "../../ui/Tabs";
import { EmptyState } from "../../ui/EmptyState";
import "./OrganizationPage.css";

interface CostPerTaskSectionProps {
  period: string;
  taskById: Map<string, Task>;
}

// Mock/API split follows SettingsPage's precedent (MF-6) — the mock fixture
// is a fixed, un-period-scoped sample; the real query (M2.3) genuinely varies
// by the ledger's period selector.
function MockCostPerTaskSection({ taskById }: CostPerTaskSectionProps) {
  return (
    <>
      {costPerTask.map(({ taskId, tokens, costUsd }) => (
        <div className="cost-row" key={taskId}>
          <span>{taskById.get(taskId)?.title ?? taskId}</span>
          <span className="mono">
            {formatTokens(tokens)} tok · {formatCost(costUsd)}
          </span>
        </div>
      ))}
      <p className="about-text" style={{ marginTop: 6, fontSize: 11.5 }}>
        Mock fixtures don't vary by period — this is always the same sample.
      </p>
    </>
  );
}

function ApiCostPerTaskSection({ period, taskById }: CostPerTaskSectionProps) {
  const query = useCostPerTask(DEV_COMPANY_ID, period);
  if (query.isLoading) return <p className="about-text">Loading…</p>;
  const rows = query.data ?? [];
  if (rows.length === 0) return <EmptyState title={`No spend recorded for ${period}.`} />;
  return (
    <>
      {rows.map(({ taskId, tokens, costUsd }) => (
        <div className="cost-row" key={taskId}>
          <span>{taskById.get(taskId)?.title ?? taskId}</span>
          <span className="mono">
            {formatTokens(tokens)} tok · {formatCost(costUsd)}
          </span>
        </div>
      ))}
    </>
  );
}

const CostPerTaskSection = USE_MOCKS ? MockCostPerTaskSection : ApiCostPerTaskSection;

const ACTIVE_STATUSES = new Set(["queued", "claimed", "in_progress", "flagged"]);

function spendColor(pct: number): string {
  if (pct >= 90) return "var(--status-flagged)";
  if (pct >= 75) return "var(--status-away)";
  return "var(--accent)";
}

interface OrgChartRowProps {
  node: OrgNode;
  depth: number;
  collapsed: Set<string>;
  onToggle: (id: string) => void;
  workloadByAgent: Map<string, number>;
  onOpen: (id: string) => void;
}

// Live status dots + workload counts, expand/collapse per node — a plain
// indented tree reads clearer here than forcing this into the generic
// left-to-right ui/Graph (built for task DAGs, not strict org hierarchies).
function OrgChartRow({ node, depth, collapsed, onToggle, workloadByAgent, onOpen }: OrgChartRowProps) {
  const { agent, children } = node;
  const hasChildren = children.length > 0;
  const isCollapsed = collapsed.has(agent.id);
  const workload = workloadByAgent.get(agent.id) ?? 0;

  return (
    <>
      <div className="org-row" style={{ paddingLeft: depth * 24 }}>
        <button
          className={`org-toggle${hasChildren ? "" : " org-toggle-empty"}`}
          onClick={() => hasChildren && onToggle(agent.id)}
          aria-label={hasChildren ? (isCollapsed ? "Expand" : "Collapse") : undefined}
        >
          {hasChildren ? (isCollapsed ? "▸" : "▾") : ""}
        </button>
        <button className="org-agent" onClick={() => onOpen(agent.id)}>
          <Avatar name={agent.name} seed={agent.id} size={26} />
          <span className="org-agent-name">{agent.name}</span>
          <span className="org-agent-role">{agent.roleTitle}</span>
        </button>
        <span className="org-agent-status">
          <StatusDot status={agent.status} />
          {STATUS_LABEL[agent.status]}
        </span>
        <span className="org-agent-workload">{workload} active</span>
        {hasChildren && <span className="org-agent-reports">{children.length} report{children.length === 1 ? "" : "s"}</span>}
      </div>
      {hasChildren && !isCollapsed && (
        <>
          {children.map((child) => (
            <OrgChartRow
              key={child.agent.id}
              node={child}
              depth={depth + 1}
              collapsed={collapsed}
              onToggle={onToggle}
              workloadByAgent={workloadByAgent}
              onOpen={onOpen}
            />
          ))}
        </>
      )}
    </>
  );
}

export function OrganizationPage() {
  const { state, dispatch } = useApp();
  const nav = useAppNav();
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const [editing, setEditing] = useState<string | null>(null);
  const [capDraft, setCapDraft] = useState("");
  const [ledgerPeriod, setLedgerPeriod] = useState(currentPeriod());

  const toggle = (id: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const workloadByAgent = new Map<string, number>();
  for (const task of state.tasks) {
    if (!task.assignedAgentId || !ACTIVE_STATUSES.has(task.status)) continue;
    workloadByAgent.set(task.assignedAgentId, (workloadByAgent.get(task.assignedAgentId) ?? 0) + 1);
  }

  const tree = orgTree(state.agents);

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

  const tabs: TabItem[] = [
    {
      key: "chart",
      label: "Org Chart",
      content:
        tree.length === 0 ? (
          <EmptyState title="No agents on the roster yet." />
        ) : (
          <div className="org-chart">
            {tree.map((root) => (
              <OrgChartRow
                key={root.agent.id}
                node={root}
                depth={0}
                collapsed={collapsed}
                onToggle={toggle}
                workloadByAgent={workloadByAgent}
                onOpen={nav.openAgent}
              />
            ))}
          </div>
        ),
    },
    {
      key: "payroll",
      label: "Payroll",
      content: (
        <div className="org-payroll">
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

          <div className="ledger-cost-head">
            <span className="section-title">Cost per task (top spenders)</span>
            <input
              type="month"
              className="ledger-period-input"
              value={ledgerPeriod}
              onChange={(e) => setLedgerPeriod(e.target.value)}
              aria-label="Ledger period"
            />
          </div>
          <CostPerTaskSection period={ledgerPeriod} taskById={taskById} />
        </div>
      ),
    },
  ];

  return (
    <div className="organization-page">
      <header className="organization-page-head">
        <h1>Organization</h1>
        <p>Reporting lines from each agent's manager, plus token-budget payroll.</p>
      </header>
      <Tabs tabs={tabs} />
    </div>
  );
}
