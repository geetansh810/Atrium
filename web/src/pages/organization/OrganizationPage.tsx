import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import { useState } from "react";
import { Avatar } from "../../shared/Avatar";
import { PersonAvatar } from "../../shared/PersonAvatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { ChevronDownIcon } from "../../shared/icons";
import { USE_MOCKS } from "../../shared/config";
import { useAuthSession } from "../../shared/auth";
import { formatCost, formatTokens } from "../../shared/format";
import { costPerTask } from "../../shared/mockData";
import { currentPeriod, useCostPerTask } from "../../shared/queries";
import { orgTree } from "../../shared/selectors";
import type { OrgNode } from "../../shared/selectors";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import type { Agent, Task } from "../../shared/types";
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
  const companyId = useAuthSession()?.companyId ?? "";
  const query = useCostPerTask(companyId, period);
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

function spendColor(pct: number): string {
  if (pct >= 90) return "var(--status-flagged)";
  if (pct >= 75) return "var(--status-away)";
  return "var(--accent)";
}

interface OrgCardProps {
  agent: Agent;
  onOpen: () => void;
}

// The card itself is a plain div, not a button: OrgTreeNode nests a second,
// independently-clickable control (the expand/collapse toggle) right below
// it, and a <button> inside a <button> is invalid HTML — same reasoning
// TeamPage's card/task-row split already documents. Deliberately minimal —
// identity (avatar + presence dot, name, role) only. A tree's job is to
// show reporting lines at a glance; workload/status detail lives one click
// away on the agent's own profile.
function OrgCard({ agent, onOpen }: OrgCardProps) {
  const onKeyDown = (e: ReactKeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onOpen();
    }
  };
  return (
    <div className="org-card" role="button" tabIndex={0} onClick={onOpen} onKeyDown={onKeyDown}>
      <PersonAvatar name={agent.name} seed={agent.id} status={agent.status} size={32} />
      <div className="org-card-info">
        <div className="org-card-name">{agent.name}</div>
        <div className="org-card-role">{agent.roleTitle}</div>
      </div>
    </div>
  );
}

interface OrgTreeNodeProps {
  node: OrgNode;
  collapsed: Set<string>;
  onToggle: (id: string) => void;
  onOpen: (id: string) => void;
}

// A real top-down hierarchy tree: cards connected by thin CSS-drawn lines
// (no canvas/SVG geometry to keep in sync), each level's siblings joined by
// one continuous bar that drops a stem into every card below it. Deliberately
// not the generic left-to-right ui/Graph — that's built for task DAGs with
// arbitrary edges, not a strict single-parent org hierarchy, and a top-down
// tree is the shape everyone already recognizes as "org chart".
function OrgTreeNode({ node, collapsed, onToggle, onOpen }: OrgTreeNodeProps) {
  const { agent, children } = node;
  const hasChildren = children.length > 0;
  const isCollapsed = collapsed.has(agent.id);
  const reportWord = children.length === 1 ? "report" : "reports";

  return (
    <li className="org-tree-node">
      <OrgCard agent={agent} onOpen={() => onOpen(agent.id)} />
      {hasChildren && (
        <button
          className={`org-tree-toggle${isCollapsed ? " collapsed" : ""}`}
          onClick={() => onToggle(agent.id)}
          aria-expanded={!isCollapsed}
          aria-label={`${isCollapsed ? "Expand" : "Collapse"} ${agent.name}'s ${children.length} direct ${reportWord}`}
        >
          <ChevronDownIcon width={10} height={10} />
          <span className="org-tree-toggle-count">{children.length}</span>
        </button>
      )}
      {hasChildren && !isCollapsed && (
        <ul className="org-tree-children">
          {children.map((child) => (
            <OrgTreeNode key={child.agent.id} node={child} collapsed={collapsed} onToggle={onToggle} onOpen={onOpen} />
          ))}
        </ul>
      )}
    </li>
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
          <div className="org-tree-scroll">
            <ul className="org-tree">
              {tree.map((root) => (
                <OrgTreeNode key={root.agent.id} node={root} collapsed={collapsed} onToggle={toggle} onOpen={nav.openAgent} />
              ))}
            </ul>
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
