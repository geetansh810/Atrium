/**
 * Cross-cutting infrastructure: TenantContext, problem+json error handling,
 * config, UUID/Clock utilities.
 *
 * <p>Boundary (12 §2): may be used by every module; depends on none of them.
 * No business logic, no entities, no repositories.
 */
package app.atrium.common;
