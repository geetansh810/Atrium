import type { KeyboardEvent as ReactKeyboardEvent, ReactNode, SVGProps } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { useApp } from "../shared/store";
import { useAppNav } from "../shared/nav";
import { USE_MOCKS } from "../shared/config";
import {
  EmployeesIcon,
  FlowIcon,
  KnowledgeIcon,
  MissionControlIcon,
  OrganizationIcon,
  PlusIcon,
  ProjectsIcon,
  ReportsIcon,
  SettingsIcon,
  TasksIcon,
  TeamIcon,
} from "../shared/icons";
import "./CommandPalette.css";

// Fired by any component (GlobalHeader's "Jump to…" hint) to open the
// palette without prop-drilling — a tiny module-level pub/sub, since the
// palette itself is the single AppShell-level instance that owns open state.
type Listener = () => void;
const listeners = new Set<Listener>();
export function openCommandPalette() {
  listeners.forEach((l) => l());
}

interface Command {
  id: string;
  section: "Pages" | "Agents" | "Tasks" | "Actions";
  label: string;
  sublabel?: string;
  icon?: (props: SVGProps<SVGSVGElement>) => ReactNode;
  run: () => void;
}

const PAGES: { to: string; label: string; icon: Command["icon"] }[] = [
  { to: "/", label: "Mission Control", icon: MissionControlIcon },
  { to: "/tasks", label: "Tasks", icon: TasksIcon },
  { to: "/tasks/review", label: "Review Inbox", icon: TasksIcon },
  { to: "/workflow", label: "Workflow", icon: FlowIcon },
  { to: "/employees", label: "Employees", icon: EmployeesIcon },
  { to: "/organization", label: "Organization", icon: OrganizationIcon },
  { to: "/projects", label: "Projects", icon: ProjectsIcon },
  { to: "/knowledge", label: "Knowledge", icon: KnowledgeIcon },
  { to: "/reports", label: "Reports", icon: ReportsIcon },
  { to: "/team", label: "Team", icon: TeamIcon },
  { to: "/settings", label: "Settings", icon: SettingsIcon },
];

export function CommandPalette() {
  const { state, dispatch } = useApp();
  const navigate = useNavigate();
  const nav = useAppNav();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const listener = () => setOpen(true);
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((v) => !v);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    if (open) {
      previouslyFocusedRef.current = document.activeElement as HTMLElement | null;
      setQuery("");
      setActiveIndex(0);
      // Focus after the overlay mounts.
      requestAnimationFrame(() => inputRef.current?.focus());
    } else {
      previouslyFocusedRef.current?.focus();
    }
  }, [open]);

  const close = () => setOpen(false);

  const pauseAll = () => {
    if (USE_MOCKS) {
      for (const agent of state.agents) {
        if (!agent.paused) dispatch({ type: "setAgentPaused", agentId: agent.id, paused: true });
      }
    } else {
      dispatch({
        type: "setNotice",
        text: "Pause All isn't available yet — no bulk-pause endpoint. Pause agents individually from their profile.",
      });
    }
  };

  const commands = useMemo<Command[]>(() => {
    const pages: Command[] = PAGES.map((p) => ({
      id: `page:${p.to}`,
      section: "Pages",
      label: p.label,
      icon: p.icon,
      run: () => navigate(p.to),
    }));

    const agents: Command[] = state.agents.map((a) => ({
      id: `agent:${a.id}`,
      section: "Agents",
      label: a.name,
      sublabel: a.roleTitle,
      icon: EmployeesIcon,
      run: () => nav.openAgent(a.id),
    }));

    const tasks: Command[] = state.tasks.map((t) => ({
      id: `task:${t.id}`,
      section: "Tasks",
      label: t.title,
      sublabel: t.status,
      icon: TasksIcon,
      run: () => nav.openTask(t.id),
    }));

    const actions: Command[] = [
      {
        id: "action:new-task",
        section: "Actions",
        label: "New Task",
        icon: PlusIcon,
        run: () => dispatch({ type: "setNewTaskOpen", open: true }),
      },
      {
        id: "action:hire-agent",
        section: "Actions",
        label: "Hire Agent",
        icon: PlusIcon,
        run: () => dispatch({ type: "setInviteOpen", open: true }),
      },
      {
        id: "action:pause-all",
        section: "Actions",
        label: "Pause All",
        sublabel: USE_MOCKS ? "Pauses every active agent" : "No bulk endpoint yet",
        icon: SettingsIcon,
        run: pauseAll,
      },
    ];

    return [...pages, ...actions, ...agents, ...tasks];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.agents, state.tasks, navigate]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return commands.filter((c) => c.section === "Pages" || c.section === "Actions");
    return commands.filter(
      (c) => c.label.toLowerCase().includes(q) || c.sublabel?.toLowerCase().includes(q),
    ).slice(0, 40);
  }, [commands, query]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  if (!open) return null;

  const select = (cmd: Command) => {
    cmd.run();
    close();
  };

  const onKeyDown = (e: ReactKeyboardEvent) => {
    if (e.key === "Escape") {
      close();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, filtered.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const cmd = filtered[activeIndex];
      if (cmd) select(cmd);
    } else if (e.key === "Tab") {
      // Single-input combobox — keep focus locked on the input rather than
      // letting Tab escape to whatever's behind the overlay.
      e.preventDefault();
    }
  };

  let renderedSection: string | null = null;

  return (
    <div className="cmdk-overlay" onClick={close}>
      <div
        className="cmdk"
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={onKeyDown}
      >
        <input
          ref={inputRef}
          className="cmdk-input"
          placeholder="Jump to a page, agent, task, or action…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          role="combobox"
          aria-expanded="true"
          aria-controls="cmdk-list"
          aria-activedescendant={filtered[activeIndex] ? `cmdk-item-${filtered[activeIndex].id}` : undefined}
        />
        <ul className="cmdk-list" id="cmdk-list" role="listbox">
          {filtered.length === 0 && <li className="cmdk-empty">No matches.</li>}
          {filtered.map((cmd, i) => {
            const showHeader = cmd.section !== renderedSection;
            renderedSection = cmd.section;
            const Icon = cmd.icon;
            return (
              <li key={cmd.id}>
                {showHeader && <div className="cmdk-section-label">{cmd.section}</div>}
                <button
                  id={`cmdk-item-${cmd.id}`}
                  role="option"
                  aria-selected={i === activeIndex}
                  className={`cmdk-item${i === activeIndex ? " active" : ""}`}
                  onMouseEnter={() => setActiveIndex(i)}
                  onClick={() => select(cmd)}
                >
                  {Icon && <Icon width={14} height={14} />}
                  <span className="cmdk-item-label">{cmd.label}</span>
                  {cmd.sublabel && <span className="cmdk-item-sublabel">{cmd.sublabel}</span>}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
