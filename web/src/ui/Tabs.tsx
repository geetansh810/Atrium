import type { ReactNode } from "react";
import { useSearchParams } from "react-router";
import "./Tabs.css";

export interface TabItem {
  key: string;
  label: string;
  content: ReactNode;
}

interface TabsProps {
  tabs: TabItem[];
  paramName?: string;
}

// Tab bar that syncs the active tab to `?tab=` so a drawer/page deep-links to
// a specific tab and survives refresh (first real consumer: TaskDrawer/
// EmployeeProfile in MF-3/MF-4 — built now per the plan's primitives list).
export function Tabs({ tabs, paramName = "tab" }: TabsProps) {
  const [params, setParams] = useSearchParams();
  const active = params.get(paramName) && tabs.some((t) => t.key === params.get(paramName))
    ? (params.get(paramName) as string)
    : (tabs[0]?.key ?? "");

  const select = (key: string) => {
    const next = new URLSearchParams(params);
    next.set(paramName, key);
    setParams(next, { replace: true });
  };

  return (
    <div className="tabs">
      <div className="tabs-bar" role="tablist">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            role="tab"
            aria-selected={tab.key === active}
            className={`tabs-tab${tab.key === active ? " active" : ""}`}
            onClick={() => select(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="tabs-panel">{tabs.find((t) => t.key === active)?.content}</div>
    </div>
  );
}
