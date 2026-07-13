import type { ReactNode } from "react";
import "./Kanban.css";

export interface KanbanColumnData<T> {
  key: string;
  title: string;
  items: T[];
}

interface KanbanBoardProps<T> {
  columns: KanbanColumnData<T>[];
  getId: (item: T) => string;
  renderCard: (item: T) => ReactNode;
  // Fires on every drop, legal or not — the caller decides what a given
  // (fromColumnKey → toColumnKey) transition means (MF-3: only
  // pending_review → approved is honored; every other drop is a no-op the
  // caller toast-explains, since agents — not drag-and-drop — drive status).
  onDrop: (itemId: string, fromColumnKey: string, toColumnKey: string) => void;
}

// Generic drag-and-drop board — first consumer is the Tasks kanban (MF-3),
// built reusable enough for a future Workflow board (MF-5) without knowing
// anything about tasks itself.
export function KanbanBoard<T>({ columns, getId, renderCard, onDrop }: KanbanBoardProps<T>) {
  return (
    <div className="kanban-board">
      {columns.map((col) => (
        <KanbanColumn
          key={col.key}
          title={col.title}
          count={col.items.length}
          onDrop={(itemId, fromColumnKey) => onDrop(itemId, fromColumnKey, col.key)}
        >
          {col.items.map((item) => (
            <KanbanCard key={getId(item)} itemId={getId(item)} columnKey={col.key}>
              {renderCard(item)}
            </KanbanCard>
          ))}
        </KanbanColumn>
      ))}
    </div>
  );
}

interface KanbanColumnProps {
  title: string;
  count: number;
  onDrop: (itemId: string, fromColumnKey: string) => void;
  children: ReactNode;
}

export function KanbanColumn({ title, count, onDrop, children }: KanbanColumnProps) {
  return (
    <div
      className="kanban-column"
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault();
        const itemId = e.dataTransfer.getData("text/kanban-item-id");
        const fromColumnKey = e.dataTransfer.getData("text/kanban-from-column");
        if (itemId) onDrop(itemId, fromColumnKey);
      }}
    >
      <div className="kanban-column-head">
        <span className="kanban-column-title">{title}</span>
        <span className="kanban-column-count">{count}</span>
      </div>
      <div className="kanban-column-body">
        {count === 0 ? <div className="kanban-column-empty">No tasks</div> : children}
      </div>
    </div>
  );
}

interface KanbanCardProps {
  itemId: string;
  columnKey: string;
  children: ReactNode;
}

export function KanbanCard({ itemId, columnKey, children }: KanbanCardProps) {
  return (
    <div
      className="kanban-card"
      draggable
      onDragStart={(e) => {
        e.dataTransfer.setData("text/kanban-item-id", itemId);
        e.dataTransfer.setData("text/kanban-from-column", columnKey);
        e.dataTransfer.effectAllowed = "move";
      }}
    >
      {children}
    </div>
  );
}
