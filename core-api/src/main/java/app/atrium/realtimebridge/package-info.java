/**
 * Publishes state-change events to Redis pub/sub channels
 * ({@code atrium:events:{companyId}}) for the office/dashboard projections.
 *
 * <p>Boundary (12 §2, 05 §realtimebridge): reads from eventbus only; never
 * writes business state. The office is a projection — consumers self-heal
 * from {@code GET /office-state}.
 */
package app.atrium.realtimebridge;
