import { useEffect, useRef } from "react";
import { settleConfirm, usePendingConfirm } from "../shared/confirm";

export function ConfirmHost() {
  const pending = usePendingConfirm();
  const confirmRef = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!pending) return;
    returnFocus.current = document.activeElement as HTMLElement | null;
    confirmRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") settleConfirm(false);
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      returnFocus.current?.focus();
    };
  }, [pending]);

  if (!pending) return null;
  const danger = pending.tone === "danger";

  return (
    <div className="modal-overlay" onClick={() => settleConfirm(false)}>
      <div
        className="modal confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby={pending.body ? "confirm-body" : undefined}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="panel-body">
          <h2 id="confirm-title" className="confirm-title">
            {pending.title}
          </h2>
          {pending.body && (
            <p id="confirm-body" className="confirm-body">
              {pending.body}
            </p>
          )}
        </div>
        <div className="modal-actions">
          <button className="btn" onClick={() => settleConfirm(false)}>
            Cancel
          </button>
          <button
            ref={confirmRef}
            className={`btn ${danger ? "danger" : "primary"}`}
            onClick={() => settleConfirm(true)}
          >
            {pending.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
