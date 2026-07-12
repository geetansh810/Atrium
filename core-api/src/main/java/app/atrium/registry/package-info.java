/**
 * Companies, users, role_definitions, agents, model_catalog, and the
 * AgentDirectory capability registry (our "AgentCards").
 *
 * <p>Boundary (12 §2, 05 §registry): provides {@code AgentDirectory.findBySkill}
 * and {@code RoleDefinitions.resolve} to other modules via service interfaces.
 * Must not know anything about tasks, queues, or LLM calls.
 */
package app.atrium.registry;
