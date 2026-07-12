package app.atrium.registry.runtime;

import java.util.UUID;

/** Runtimes re-read the roster row; never cache role/model (13 §3.1). */
public record AgentHandle(UUID agentId, UUID companyId) {}
