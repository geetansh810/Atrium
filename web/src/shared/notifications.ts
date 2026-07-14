// Notifications = derived (MF-6): no backend table, just task events +
// announcements merged into one feed. "Read" state is purely client-side —
// a localStorage id set, since there's nothing to sync server-side yet.
import { useCallback } from "react";
import { useApp } from "./store";
import { synthesizeFeed, taskEventTone, TASK_EVENT_LABEL } from "./selectors";
import { createDomainStore } from "./domains/createDomainStore";
import type { Agent, Announcement, AnnouncementCategory, Task } from "./types";
import type { PillTone } from "../ui/StatusPill";

const STORAGE_KEY = "atrium:readNotificationIds";

function loadReadIds(): Set<string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? new Set(JSON.parse(raw) as string[]) : new Set();
  } catch {
    return new Set();
  }
}

function persistReadIds(ids: Set<string>) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...ids]));
  } catch {
    // localStorage unavailable (private mode etc.) — unread count just won't persist
  }
}

// GlobalHeader's bell and NotificationsPage both read this — a plain
// useState per component would give each its own copy, so the header's
// badge would never notice the page marking things read. Shared external
// store (MF-5's createDomainStore pattern) keeps every consumer in sync.
const readIdsStore = createDomainStore<Set<string>>(loadReadIds());

function markIdsRead(ids: string[]) {
  readIdsStore.set((prev) => {
    const next = new Set(prev);
    let changed = false;
    for (const id of ids) {
      if (!next.has(id)) {
        next.add(id);
        changed = true;
      }
    }
    if (!changed) return prev;
    persistReadIds(next);
    return next;
  });
}

export type NotificationKind = "task" | "announcement";

export interface NotificationItem {
  id: string;
  kind: NotificationKind;
  title: string;
  detail: string | null;
  category: AnnouncementCategory | null;
  tone: PillTone;
  createdAt: string;
}

export function buildNotifications(
  tasks: Task[],
  agents: Agent[],
  announcements: Announcement[],
  limit = 60,
): NotificationItem[] {
  const taskItems: NotificationItem[] = synthesizeFeed(tasks, agents, limit).map((f) => ({
    id: `task:${f.id}`,
    kind: "task",
    title: `${TASK_EVENT_LABEL[f.eventType] ?? f.eventType} — ${f.taskTitle}`,
    detail: f.agentName,
    category: null,
    tone: taskEventTone(f.eventType),
    createdAt: f.createdAt,
  }));
  const announcementItems: NotificationItem[] = announcements.map((a) => ({
    id: `ann:${a.id}`,
    kind: "announcement",
    title: a.title,
    detail: a.body,
    category: a.category,
    tone: "info",
    createdAt: a.createdAt,
  }));
  return [...taskItems, ...announcementItems]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, limit);
}

export function useNotifications() {
  const { state } = useApp();
  const readIds = readIdsStore.useDomainState();
  const items = buildNotifications(state.tasks, state.agents, state.announcements);

  const markAllRead = useCallback(() => {
    markIdsRead(items.map((item) => item.id));
  }, [items]);

  const unreadCount = items.filter((item) => !readIds.has(item.id)).length;

  return { items, unreadCount, markAllRead, isRead: (id: string) => readIds.has(id) };
}
