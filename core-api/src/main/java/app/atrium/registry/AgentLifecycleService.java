package app.atrium.registry;

import app.atrium.registry.domain.Agent;
import app.atrium.registry.runtime.AgentHandle;
import app.atrium.registry.runtime.AgentRuntime;
import app.atrium.registry.runtime.RuntimeRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts/stops an agent's runtime loop on the lifecycle triggers named in
 * 13 §3.2/17 §M0.5b: hire, unpause, pause (M0.7 adds budget auto-pause via
 * {@code AgentDirectory.pauseForBudget}, which calls {@link #stop} the same
 * way; process-shutdown cleanup is each runtime's own {@code @PreDestroy},
 * since only it knows which agents it has running.)
 *
 * <p>Deferred to after the caller's transaction commits — hire()/patch() are
 * still mid-write when this is called, and a poll tick that can't yet see the
 * agent row would be a spurious NotFoundException race.
 */
@Service
public class AgentLifecycleService {

    private final RuntimeRegistry runtimeRegistry;

    public AgentLifecycleService(RuntimeRegistry runtimeRegistry) {
        this.runtimeRegistry = runtimeRegistry;
    }

    public void start(Agent agent) {
        afterCommit(agent, AgentRuntime::start);
    }

    public void stop(Agent agent) {
        afterCommit(agent, AgentRuntime::stop);
    }

    private void afterCommit(Agent agent, java.util.function.BiConsumer<AgentRuntime, AgentHandle> action) {
        AgentHandle handle = new AgentHandle(agent.getId(), agent.getCompanyId());
        AgentRuntime runtime = runtimeRegistry.require(agent.getRuntimeType());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.accept(runtime, handle);
                }
            });
        } else {
            action.accept(runtime, handle);
        }
    }
}
