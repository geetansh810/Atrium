import { useMemo, useState } from "react";
import { PageShell } from "../../ui/PageShell";
import { EmptyState } from "../../ui/EmptyState";
import { DataTable, type DataTableColumn } from "../../ui/DataTable";
import { StatusPill } from "../../ui/StatusPill";
import { Drawer } from "../../ui/Drawer";
import { USE_MOCKS } from "../../shared/config";
import { useAuthSession } from "../../shared/auth";
import { useApp } from "../../shared/store";
import { useLlmLogs, useLlmLog, describeApiError } from "../../shared/queries";
import type { LlmLogRow } from "../../shared/api";
import { formatTokens, formatTimeAgo } from "../../shared/format";
import "./LogsPage.css";

type StatusFilter = "all" | "ok" | "error";

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: "all", label: "All" },
  { key: "ok", label: "OK" },
  { key: "error", label: "Errors" },
];

export function LogsPage() {
  const session = useAuthSession();
  const companyId = session?.companyId ?? "";
  const { state } = useApp();

  const agentName = useMemo(() => {
    const map = new Map(state.agents.map((a) => [a.id, a.name]));
    return (id: string | null) => (id ? (map.get(id) ?? "—") : "—");
  }, [state.agents]);

  const [status, setStatus] = useState<StatusFilter>("all");
  const [limit, setLimit] = useState(50);
  const [openId, setOpenId] = useState<string | null>(null);

  const query = useMemo(
    () => ({ status: status === "all" ? undefined : status, limit }),
    [status, limit],
  );
  const logsQuery = useLlmLogs(companyId, query);

  // Mock mode has no LLM backend, so there are no requests to log.
  if (USE_MOCKS) {
    return (
      <PageShell title="LLM Logs" subtitle="Every request sent to a language model, with its full prompt.">
        <EmptyState
          title="LLM logging is live-API-only"
          description="Requests are captured on the real backend when agents call a model. Run the app against core-api (not mock mode) to see them."
        />
      </PageShell>
    );
  }

  const rows = logsQuery.data?.data ?? [];
  const hasMore = !!logsQuery.data?.nextCursor;

  const columns: DataTableColumn<LlmLogRow>[] = [
    {
      key: "time",
      header: "When",
      width: "120px",
      render: (r) => <span className="logs-muted">{formatTimeAgo(r.createdAt)}</span>,
    },
    { key: "agent", header: "Agent", render: (r) => agentName(r.agentId) },
    {
      key: "model",
      header: "Model",
      render: (r) => (
        <span className="logs-model">
          <span className="logs-provider">{r.provider}</span>
          {r.model}
        </span>
      ),
    },
    {
      key: "tokens",
      header: "Tokens",
      width: "130px",
      render: (r) =>
        r.tokensIn == null ? (
          <span className="logs-muted">—</span>
        ) : (
          <span className="logs-muted">
            {formatTokens(r.tokensIn)} in · {formatTokens(r.tokensOut ?? 0)} out
          </span>
        ),
    },
    {
      key: "latency",
      header: "Latency",
      width: "90px",
      render: (r) => <span className="logs-muted">{r.latencyMs} ms</span>,
    },
    {
      key: "status",
      header: "Status",
      width: "110px",
      render: (r) =>
        r.status === "ok" ? (
          <StatusPill label="OK" tone="success" />
        ) : (
          <StatusPill label={r.errorKind ?? "Error"} tone="danger" />
        ),
    },
  ];

  return (
    <PageShell
      title="LLM Logs"
      subtitle="Every request sent to a language model, with its full prompt and response."
      actions={
        <div className="logs-filters">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.key}
              className={`logs-chip${status === f.key ? " active" : ""}`}
              onClick={() => {
                setStatus(f.key);
                setLimit(50);
              }}
            >
              {f.label}
            </button>
          ))}
        </div>
      }
    >
      {logsQuery.isError ? (
        <EmptyState title="Couldn't load logs" description={describeApiError(logsQuery.error)} />
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={rows}
            rowKey={(r) => r.id}
            onRowClick={(r) => setOpenId(r.id)}
            emptyLabel={logsQuery.isLoading ? "Loading…" : "No LLM requests logged yet."}
          />
          {hasMore && (
            <div className="logs-more">
              <button className="logs-more-btn" onClick={() => setLimit((n) => Math.min(n + 50, 200))}>
                Load more
              </button>
            </div>
          )}
        </>
      )}

      {openId && (
        <LogDetailDrawer companyId={companyId} logId={openId} onClose={() => setOpenId(null)} />
      )}
    </PageShell>
  );
}

function LogDetailDrawer({
  companyId,
  logId,
  onClose,
}: {
  companyId: string;
  logId: string;
  onClose: () => void;
}) {
  const detail = useLlmLog(companyId, logId);
  const d = detail.data;

  return (
    <Drawer
      title="LLM request"
      subtitle={d ? `${d.provider} · ${d.model}` : undefined}
      width={620}
      onClose={onClose}
    >
      {detail.isLoading && <div className="logs-detail-loading">Loading…</div>}
      {detail.isError && <EmptyState title="Couldn't load this request" />}
      {d && (
        <div className="logs-detail">
          <div className="logs-detail-meta">
            {d.status === "ok" ? (
              <StatusPill label="OK" tone="success" />
            ) : (
              <StatusPill label={d.errorKind ?? "Error"} tone="danger" />
            )}
            {d.tokensIn != null && (
              <span className="logs-muted">
                {formatTokens(d.tokensIn)} in · {formatTokens(d.tokensOut ?? 0)} out
              </span>
            )}
            <span className="logs-muted">{d.latencyMs} ms</span>
            {d.stopReason && <span className="logs-muted">stop: {d.stopReason}</span>}
            <span className="logs-muted">{new Date(d.createdAt).toLocaleString()}</span>
          </div>

          {d.status === "error" && d.errorMessage && (
            <Section title="Error">
              <pre className="logs-block logs-block-error">{d.errorMessage}</pre>
            </Section>
          )}

          {d.systemPrompt && (
            <Section title="System prompt">
              <pre className="logs-block">{d.systemPrompt}</pre>
            </Section>
          )}

          <Section title="Messages">
            {d.messages.length === 0 ? (
              <div className="logs-muted">No messages.</div>
            ) : (
              d.messages.map((m, i) => (
                <div key={i} className="logs-msg">
                  <div className="logs-msg-role">{m.role}</div>
                  <pre className="logs-block">{m.content}</pre>
                </div>
              ))
            )}
          </Section>

          {d.tools && d.tools.length > 0 && (
            <Section title="Tools offered">
              <pre className="logs-block">{JSON.stringify(d.tools, null, 2)}</pre>
            </Section>
          )}

          {d.responseText != null && d.responseText !== "" && (
            <Section title="Response">
              <pre className="logs-block">{d.responseText}</pre>
            </Section>
          )}

          {d.toolCalls && d.toolCalls.length > 0 && (
            <Section title="Tool calls returned">
              <pre className="logs-block">{JSON.stringify(d.toolCalls, null, 2)}</pre>
            </Section>
          )}
        </div>
      )}
    </Drawer>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="logs-section">
      <div className="logs-section-title">{title}</div>
      {children}
    </div>
  );
}
