/**
 * Communication — channels, messages, announcements, Atrium Bot notices, and
 * the {@code ChatNoticePipeline} that turns agent work into chat activity (M2.5).
 *
 * <p>Boundary (12 §2): every query is company-scoped (messages have no
 * company_id at query time only when reached via a channel already scoped to
 * the tenant — the {@code company_id} column is still stored). Depends on
 * {@code registry} (AgentDirectory, to resolve agent display names for bot
 * messages) and on {@code eventbus} — it is a durable outbox consumer
 * ({@code EventCursorWorker}) reading {@code task.completed} and a producer
 * writing {@code chat.message}/{@code announcement.created} via
 * {@code OutboxWriter}. It never imports routing/execution internals; the task
 * title it needs for a chat notice rides on the {@code task.completed} payload
 * (16 §4), never a read back into routing.
 */
package app.atrium.communication;
