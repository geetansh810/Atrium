// M3.4 card: "instrument the funnel with simple event logs" — no analytics
// backend exists (or is planned) for this, so this is deliberately just a
// structured console log, not a new service.
export function logFunnelEvent(step: string, meta?: Record<string, unknown>) {
  console.info(`[funnel] onboarding.${step}`, meta ?? {});
}
