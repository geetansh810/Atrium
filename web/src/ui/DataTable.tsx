import type { ReactNode } from "react";
import { EmptyState } from "./EmptyState";
import "./DataTable.css";

export interface DataTableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  width?: string;
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
  emptyLabel?: string;
}

// Plain generic table — no sorting/pagination yet, just consistent chrome so
// Reports/Projects (MF-5) and any future list surface don't hand-roll <table>.
export function DataTable<T>({ columns, rows, rowKey, onRowClick, emptyLabel = "Nothing here yet." }: DataTableProps<T>) {
  if (rows.length === 0) return <EmptyState title={emptyLabel} />;
  return (
    <table className="data-table">
      <thead>
        <tr>
          {columns.map((col) => (
            <th key={col.key} style={col.width ? { width: col.width } : undefined}>
              {col.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr
            key={rowKey(row)}
            className={onRowClick ? "data-table-row-clickable" : undefined}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
          >
            {columns.map((col) => (
              <td key={col.key}>{col.render(row)}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
