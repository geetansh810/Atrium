package app.atrium.execution;

import app.atrium.common.LogContext;
import app.atrium.common.TenantContext;
import app.atrium.registry.AgentDirectory;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.runtime.AgentHandle;
import app.atrium.registry.runtime.AgentRuntime;
import app.atrium.registry.runtime.ConfigException;
import app.atrium.registry.runtime.RuntimeHealth;
import app.atrium.routing.TaskService;
import app.atrium.routing.WorkBroker;
import app.atrium.routing.domain.Task;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * M-AR1's runtime-extensibility proof (13 §3.3): a second {@link AgentRuntime}
 * bean, added with zero changes to routing/, registry/, or any migration —
 * the "any agent type without breaking base schema" claim, demonstrated
 * rather than just asserted. Same one-virtual-thread-per-agent poll shape as
 * {@link LlmLoopRuntime}, but claims and completes with a canned artifact:
 * no LLM call, no context assembly, no usage recording (there's no real
 * spend to meter). A dev/test utility runtime, not a production one.
 */
@Component
public class EchoRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(EchoRuntime.class);
    private static final Duration DEFAULT_POLL = Duration.ofSeconds(15);
    private static final String CANNED_ARTIFACT =
            "Echo runtime: task claimed and completed with no real work performed.";

    private final WorkBroker workBroker;
    private final AgentDirectory agentDirectory;
    private final TaskService taskService;
    private final Map<UUID, RunningAgent> running = new ConcurrentHashMap<>();

    public EchoRuntime(WorkBroker workBroker, AgentDirectory agentDirectory, TaskService taskService) {
        this.workBroker = workBroker;
        this.agentDirectory = agentDirectory;
        this.taskService = taskService;
    }

    @Override
    public String type() {
        return "echo";
    }

    @Override
    public void validateConfig(JsonNode runtimeConfig) {
        if (runtimeConfig == null || runtimeConfig.isNull()) {
            return; // defaults apply
        }
        if (!runtimeConfig.isObject()) {
            throw new ConfigException(Map.of("runtimeConfig", "must be a JSON object"));
        }
        for (Iterator<String> it = runtimeConfig.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!"pollSeconds".equals(field)) {
                throw new ConfigException(Map.of("runtimeConfig." + field,
                        "unknown key; allowed: [pollSeconds]"));
            }
            JsonNode value = runtimeConfig.get(field);
            if (!value.isIntegralNumber() || value.asLong() <= 0) {
                throw new ConfigException(Map.of("runtimeConfig." + field, "must be a positive integer"));
            }
        }
    }

    @Override
    public void start(AgentHandle handle) {
        running.computeIfAbsent(handle.agentId(), id -> {
            RunningAgent state = new RunningAgent();
            state.thread = Thread.ofVirtual()
                    .name("echo-loop-" + handle.agentId())
                    .start(() -> poll(handle, state));
            return state;
        });
    }

    @Override
    public void stop(AgentHandle handle) {
        RunningAgent state = running.remove(handle.agentId());
        if (state != null) {
            state.stopRequested = true;
            state.thread.interrupt();
        }
    }

    @Override
    public RuntimeHealth health(AgentHandle handle) {
        RunningAgent state = running.get(handle.agentId());
        if (state == null) {
            return RuntimeHealth.stopped();
        }
        return state.lastError != null ? RuntimeHealth.error(state.lastError) : RuntimeHealth.running();
    }

    /** Stop on shutdown — only this bean knows which agents it has running (same idiom as LlmLoopRuntime). */
    @jakarta.annotation.PreDestroy
    void stopAll() {
        running.keySet().forEach(agentId -> {
            RunningAgent state = running.remove(agentId);
            if (state != null) {
                state.stopRequested = true;
                state.thread.interrupt();
            }
        });
    }

    private void poll(AgentHandle handle, RunningAgent state) {
        while (!state.stopRequested) {
            try {
                runOnce(handle.companyId(), handle.agentId());
                state.lastError = null;
            } catch (RuntimeException e) {
                log.error("echo tick failed for agent {}", handle.agentId(), e);
                state.lastError = e.getMessage();
            }
            try {
                Thread.sleep(DEFAULT_POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Claim next + complete with a canned artifact — public so tests can drive
     * it deterministically instead of racing the poll timer, same convention
     * as {@link LlmLoopRuntime#runOnce}. Binds {@link TenantContext#runAsSystem}
     * for the whole iteration (M3.2, 08 §Security rule 6) — same reasoning as
     * {@code LlmLoopRuntime}: a background poll loop, not an HTTP request.
     */
    public void runOnce(UUID companyId, UUID agentId) {
        LogContext.putAgent(agentId);
        try {
            TenantContext.runAsSystem(companyId, () -> runOnceInternal(companyId, agentId));
        } finally {
            LogContext.clear();
        }
    }

    private void runOnceInternal(UUID companyId, UUID agentId) {
        Agent agent = agentDirectory.findById(companyId, agentId)
                .orElseThrow(() -> new IllegalStateException("Agent vanished mid-loop: " + agentId));

        Optional<Task> claimed = workBroker.claimNext(companyId, agentId, agent.getSkillTags(), null);
        if (claimed.isEmpty()) {
            return;
        }
        Task task = claimed.get();
        LogContext.putTask(task.getId());

        // claimed -> in_progress: TaskStateGuard has no direct claimed -> pending_review edge.
        taskService.progress(companyId, task.getId(), agentId, null, null, null);
        taskService.complete(companyId, task.getId(), agentId, "text", CANNED_ARTIFACT);
    }

    private static final class RunningAgent {
        volatile Thread thread;
        volatile boolean stopRequested;
        volatile String lastError;
    }
}
