// Promise-based confirmation for destructive or spend-affecting actions (UX3).
// `confirmAction` can be called from anywhere (event handlers, the command
// palette); <ConfirmHost/> in AppShell renders the dialog. Resolves false on
// Cancel / Escape / backdrop click, so callers just `if (!(await ...)) return`.
import { createDomainStore } from "./domains/createDomainStore";

export interface ConfirmOptions {
  title: string;
  body?: string;
  confirmLabel: string;
  tone?: "danger" | "primary";
}

interface PendingConfirm extends ConfirmOptions {
  resolve: (ok: boolean) => void;
}

const store = createDomainStore<PendingConfirm | null>(null);

export function confirmAction(options: ConfirmOptions): Promise<boolean> {
  // A second request while one is open cancels the first rather than stacking.
  store.get()?.resolve(false);
  return new Promise((resolve) => {
    store.set({ ...options, resolve });
  });
}

export function usePendingConfirm() {
  return store.useDomainState();
}

export function settleConfirm(ok: boolean) {
  const pending = store.get();
  store.set(null);
  pending?.resolve(ok);
}
