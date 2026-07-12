/**
 * DomainEvent records, OutboxWriter (same-transaction append), OutboxRelay
 * (scheduled publisher to Redis), consumer cursor helpers.
 *
 * <p>Boundary (12 §2): written to by every module ({@code eventbus ← everyone},
 * write-only); read by realtimebridge and durable consumers. No business
 * logic lives here. Transport only — task_events remains the permanent audit.
 * Full spec: atrium-docs/12 §3.
 */
package app.atrium.eventbus;
