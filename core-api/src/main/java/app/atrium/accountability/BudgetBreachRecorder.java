package app.atrium.accountability;

import app.atrium.eventbus.OutboxWriter;
import app.atrium.eventbus.Topics;
import app.atrium.registry.AgentDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The auto-pause + {@code budget.exceeded} side effects of a refused claim,
 * split out from {@link BudgetService#canSpend} into their own
 * {@code REQUIRES_NEW} transaction. {@code canSpend} runs inside
 * {@code PostgresWorkBroker.claim}'s transaction, which then throws
 * {@code ConflictException} to produce the 409 — if the pause/event write
 * happened in that same transaction, the throw's rollback would undo them
 * too. This bean commits independently of whatever the caller does next.
 */
@Component
public class BudgetBreachRecorder {

    private final AgentDirectory agentDirectory;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public BudgetBreachRecorder(AgentDirectory agentDirectory, OutboxWriter outboxWriter,
                                ObjectMapper objectMapper) {
        this.agentDirectory = agentDirectory;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAgentBreach(UUID companyId, UUID agentId, String period) {
        agentDirectory.pauseForBudget(companyId, agentId);
        publishExceeded(companyId, agentId, period);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompanyBreach(UUID companyId, String period) {
        publishExceeded(companyId, null, period);
    }

    private void publishExceeded(UUID companyId, UUID agentId, String period) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (agentId != null) payload.put("agentId", agentId.toString());
        payload.put("period", period);
        outboxWriter.append(companyId, Topics.budget(companyId, agentId), "budget.exceeded", payload);
    }
}
