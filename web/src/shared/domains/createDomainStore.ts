// Minimal external store for the mock-only domains (projects/knowledge/
// workflows, MF-5). These need state shared across independently-routed
// pages — e.g. a project created on ProjectsPage must still be there when
// ProjectDetail mounts after navigation — which a plain per-component
// useState can't do. Deliberately NOT part of AppState/Action (redesign plan
// §2: domains stay outside the dual-provider contract); this is a separate,
// provider-independent layer that both VITE_USE_MOCKS modes share as-is.
import { useSyncExternalStore } from "react";

export function createDomainStore<T>(initial: T) {
  let state = initial;
  const listeners = new Set<() => void>();

  function get() {
    return state;
  }

  function set(updater: T | ((prev: T) => T)) {
    state = typeof updater === "function" ? (updater as (prev: T) => T)(state) : updater;
    listeners.forEach((listener) => listener());
  }

  function subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  }

  function useDomainState() {
    return useSyncExternalStore(subscribe, get);
  }

  return { get, set, useDomainState };
}
