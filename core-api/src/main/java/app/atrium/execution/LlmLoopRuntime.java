package app.atrium.execution;

import app.atrium.execution.spi.LlmClient;
import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmProvider;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import app.atrium.registry.AgentDirectory;
import app.atrium.registry.RoleDefinitionLookup;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.RoleDefinition;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The built-in {@code llm_loop} runtime (13 §3.2) — the AgentRunner from 05
 * §execution, formalized: one virtual thread per active agent, polling for
 * skill-matched work. Replaces the M0.2 {@code LlmLoopRuntimeDescriptor}
 * placeholder now that there's a real loop to run.
 *
 * <p>Deliberately deferred to M-CTX1/tooling milestones: no tool-calling loop
 * (empty tools list — nothing here can hallucinate a tool_use), no periodic
 * lease renewal for long calls (single blocking completion per attempt),
 * no ContextAssembler (bundle is always null).
 */
@Component
public class LlmLoopRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(LlmLoopRuntime.class);

    private static final Set<String> ALLOWED_CONFIG_KEYS =
            Set.of("pollSeconds", "maxToolTurns", "maxAttemptsPerTask", "contextBudgetTokens");
    private static final Duration DEFAULT_POLL = Duration.ofSeconds(15);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;

    private final WorkBroker workBroker;
    private final AgentDirectory agentDirectory;
    private final RoleDefinitionLookup roleDefinitions;
    private final TaskService taskService;
    private final LlmClient llmClient;
    private final LlmCostCalculator costCalculator;
    private final UsageRecorder usageRecorder;
    private final TransactionTemplate txTemplate;
    private final Map<String, LlmProvider> providersById;
    private final Map<UUID, RunningAgent> running = new ConcurrentHashMap<>();

    public LlmLoopRuntime(WorkBroker workBroker, AgentDirectory agentDirectory,
                          RoleDefinitionLookup roleDefinitions, TaskService taskService,
                          LlmClient llmClient, LlmCostCalculator costCalculator,
                          UsageRecorder usageRecorder, List<LlmProvider> providers,
                          PlatformTransactionManager transactionManager) {
        this.workBroker = workBroker;
        this.agentDirectory = agentDirectory;
        this.roleDefinitions = roleDefinitions;
        this.taskService = taskService;
        this.llmClient = llmClient;
        this.costCalculator = costCalculator;
        this.usageRecorder = usageRecorder;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.providersById = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::id, Function.identity()));
    }

    @Override
    public String type() {
        return "llm_loop";
    }

    @Override
    public void validateConfig(JsonNode runtimeConfig) {
        if (runtimeConfig == null || runtimeConfig.isNull()) {
            return; // defaults apply
        }
        if (!runtimeConfig.isObject()) {
            throw new ConfigException(Map.of("runtimeConfig", "must be a JSON object"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (Iterator<String> it = runtimeConfig.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!ALLOWED_CONFIG_KEYS.contains(field)) {
                errors.put("runtimeConfig." + field, "unknown key; allowed: " + ALLOWED_CONFIG_KEYS);
                continue;
            }
            JsonNode value = runtimeConfig.get(field);
            if (!value.isIntegralNumber() || value.asLong() <= 0) {
                errors.put("runtimeConfig." + field, "must be a positive integer");
            }
        }
        if (!errors.isEmpty()) {
            throw new ConfigException(errors);
        }
    }

    @Override
    public void start(AgentHandle handle) {
        running.computeIfAbsent(handle.agentId(), id -> {
            RunningAgent state = new RunningAgent();
            state.thread = Thread.ofVirtual()
                    .name("llm-loop-" + handle.agentId())
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

    /** Stop on shutdown (13 §3.2) — only this bean knows which agents it has running. */
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
                log.error("llm_loop tick failed for agent {}", handle.agentId(), e);
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
     * One full iteration for one agent — 13 §3.2 steps 0–7. Public so tests can
     * drive it deterministically instead of racing the poll timer.
     */
    public void runOnce(UUID companyId, UUID agentId) {
        Agent agent = agentDirectory.findById(companyId, agentId)
                .orElseThrow(() -> new IllegalStateException("Agent vanished mid-loop: " + agentId));

        // Step 0: pre-dispatch gate (12 §9) — never claim if we can't dispatch anyway.
        LlmProvider provider = providersById.get(agent.getModelProvider());
        if (provider == null || (provider instanceof ProviderReadiness pr && !pr.isReady())) {
            log.warn("Agent {} parked — provider '{}' not configured", agentId, agent.getModelProvider());
            return;
        }

        // Step 1: claim next task for any of the agent's skills.
        Optional<Task> claimed = workBroker.claimNext(companyId, agentId, agent.getSkillTags());
        if (claimed.isEmpty()) {
            return;
        }
        Task task = claimed.get();

        RuntimeConfig config = RuntimeConfig.parse(agent.getRuntimeConfig());
        if (task.getAttempt() > config.maxAttemptsPerTask()) {
            taskService.flag(companyId, task.getId(), agentId, "max_attempts");
            return;
        }

        RoleDefinition roleDef = roleDefinitions.findById(agent.getRoleDefinitionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Agent " + agentId + " references a missing role definition"));

        // claimed -> in_progress: the agent is actively working from here on
        // (TaskStateGuard has no direct claimed -> pending_review edge).
        taskService.progress(companyId, task.getId(), agentId, null, null, null);

        try {
            // Steps 2–4: bundle (null — no ContextAssembler yet), prompt, LLM call.
            AssembledPrompt prompt = PromptAssembler.build(roleDef, task, null, null);
            LlmRequest request = new LlmRequest(agent.getModelProvider(), agent.getModelName(),
                    prompt.systemPrompt(), prompt.messages(), List.of(), DEFAULT_MAX_OUTPUT_TOKENS,
                    null, null);
            LlmResult result = llmClient.complete(request);

            // Steps 5–6/7: usage + artifact + pending_review, ONE transaction.
            long costMicroUsd = costCalculator.costMicroUsd(agent.getModelProvider(),
                    agent.getModelName(), result.tokensIn(), result.tokensOut());
            txTemplate.executeWithoutResult(status -> {
                usageRecorder.record(companyId, agentId, task.getId(), task.getAttempt(),
                        agent.getModelProvider(), agent.getModelName(),
                        result.tokensIn(), result.tokensOut(), costMicroUsd);
                taskService.complete(companyId, task.getId(), agentId, "text", result.content());
            });
        } catch (LlmException e) {
            // Failure path: flag, never crash the loop (05 §execution, 12 §9).
            taskService.flag(companyId, task.getId(), agentId, FlagReasons.forKind(e.kind()));
        }
    }

    /** {pollSeconds?, maxToolTurns?, maxAttemptsPerTask?, contextBudgetTokens?} — 13 §3.2. */
    private record RuntimeConfig(int maxAttemptsPerTask) {

        static RuntimeConfig parse(JsonNode runtimeConfig) {
            int maxAttempts = DEFAULT_MAX_ATTEMPTS;
            if (runtimeConfig != null && runtimeConfig.has("maxAttemptsPerTask")) {
                maxAttempts = runtimeConfig.get("maxAttemptsPerTask").asInt(DEFAULT_MAX_ATTEMPTS);
            }
            return new RuntimeConfig(maxAttempts);
        }
    }

    private static final class RunningAgent {
        volatile Thread thread;
        volatile boolean stopRequested;
        volatile String lastError;
    }
}
