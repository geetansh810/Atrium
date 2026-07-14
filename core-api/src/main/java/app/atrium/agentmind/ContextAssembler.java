package app.atrium.agentmind;

import app.atrium.registry.domain.Agent;
import app.atrium.routing.domain.Task;

/**
 * The single doorway into prompts (14 §6). Deterministic and pure over its
 * inputs plus the current DB state of the agent's skills/memories/knowledge —
 * same inputs at the same moment must yield a byte-identical {@link
 * ContextBundle}, since {@code provenanceIds} is what makes a prompt
 * auditable ("why did the agent think that").
 */
public interface ContextAssembler {

    ContextBundle assemble(Agent agent, Task task);
}
