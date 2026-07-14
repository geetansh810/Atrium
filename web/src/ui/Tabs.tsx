import type { KeyboardEvent, ReactNode } from "react";
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
  const activeIndex = tabs.findIndex((t) => t.key === active);

  const select = (key: string) => {
    const next = new URLSearchParams(params);
    next.set(paramName, key);
    setParams(next, { replace: true });
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (tabs.length === 0) return;
    let nextIndex: number | null = null;
    if (e.key === "ArrowRight") nextIndex = (activeIndex + 1) % tabs.length;
    else if (e.key === "ArrowLeft") nextIndex = (activeIndex - 1 + tabs.length) % tabs.length;
    else if (e.key === "Home") nextIndex = 0;
    else if (e.key === "End") nextIndex = tabs.length - 1;
    if (nextIndex === null) return;
    e.preventDefault();
    const nextTab = tabs[nextIndex];
    select(nextTab.key);
    document.getElementById(`tab-${nextTab.key}`)?.focus();
  };

  return (
    <div className="tabs">
      <div className="tabs-bar" role="tablist" onKeyDown={onKeyDown}>
        {tabs.map((tab) => (
          <button
            key={tab.key}
            id={`tab-${tab.key}`}
            role="tab"
            aria-selected={tab.key === active}
            aria-controls={`tabpanel-${tab.key}`}
            tabIndex={tab.key === active ? 0 : -1}
            className={`tabs-tab${tab.key === active ? " active" : ""}`}
            onClick={() => select(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="tabs-panel" role="tabpanel" id={`tabpanel-${active}`} aria-labelledby={`tab-${active}`}>
        {tabs.find((t) => t.key === active)?.content}
      </div>
    </div>
  );
}
