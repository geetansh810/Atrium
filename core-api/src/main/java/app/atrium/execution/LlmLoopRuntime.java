package app.atrium.execution;

import app.atrium.agentmind.ContextAssembler;
import app.atrium.agentmind.ContextBundle;
import app.atrium.common.LogContext;
import app.atrium.common.TenantContext;
import app.atrium.execution.spi.LlmClient;
import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmProvider;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import app.atrium.execution.spi.LlmToolCall;
import app.atrium.execution.spi.LlmToolDef;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * <p>M2.2 added the first tool: a role definition whose {@code allowed_tools}
 * lists {@link ChildTaskTool#NAME} may respond with that tool call instead of
 * text, fanning its own completion out into child tasks (via {@link
 * app.atrium.routing.TaskService#completeWithDecomposition}). This is a
 * single-shot tool use, not a multi-turn loop — a real back-and-forth would
 * need the assistant's tool_use content block replayed verbatim into the next
 * request's history (Anthropic's protocol requires it precede the matching
 * tool_result), which {@link app.atrium.execution.spi.LlmMessage}'s
 * plain-string content can't carry; deferred until a real multi-turn use case
 * needs it. Every other role still gets an empty tools list, unchanged.
 *
 * <p>Deliberately deferred to future tooling milestones: no periodic lease
 * renewal for long calls (single blocking completion per attempt). Context
 * assembly landed at M-CTX1: {@link app.atrium.agentmind.ContextAssembler}.
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
    private final ContextAssembler contextAssembler;
    private final LlmClient llmClient;
    private final LlmCostCalculator costCalculator;
    private final UsageRecorder usageRecorder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final Map<String, LlmProvider> providersById;
    private final Map<UUID, RunningAgent> running = new ConcurrentHashMap<>();

    public LlmLoopRuntime(WorkBroker workBroker, AgentDirectory agentDirectory,
                          RoleDefinitionLookup roleDefinitions, TaskService taskService,
                          ContextAssembler contextAssembler, LlmClient llmClient,
                          LlmCostCalculator costCalculator, UsageRecorder usageRecorder,
                          ObjectMapper objectMapper, List<LlmProvider> providers,
                          PlatformTransactionManager transactionManager) {
        this.workBroker = workBroker;
        this.agentDirectory = agentDirectory;
        this.roleDefinitions = roleDefinitions;
        this.taskService = taskService;
        this.contextAssembler = contextAssembler;
        this.llmClient = llmClient;
        this.costCalculator = costCalculator;
        this.usageRecorder = usageRecorder;
        this.objectMapper = objectMapper;
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
     * drive it deterministically instead of racing the poll timer. Binds
     * {@link TenantContext#runAsSystem} for the whole iteration (M3.2, 08
     * §Security rule 6) — this is a background virtual-thread poll loop, not
     * an HTTP request, so nothing else sets {@code app.company_id} for
     * Postgres RLS to see. Also binds {@link LogContext#putAgent}/{@code
     * putTask} for the whole iteration (M3.5, 10 §6) — {@code taskId} is
     * added once {@code runOnceInternal} actually claims something.
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

        // Step 0: pre-dispatch gate (12 §9) — never claim if we can't dispatch anyway.
        LlmProvider provider = providersById.get(agent.getModelProvider());
        if (provider == null || (provider instanceof ProviderReadiness pr && !pr.isReady())) {
            log.warn("Agent {} parked — provider '{}' not configured", agentId, agent.getModelProvider());
            return;
        }

        // Step 1: claim next task for any of the agent's skills. The context bundle
        // (step 2) is assembled INSIDE the claim's enricher hook so its provenance
        // ids land in this same claimed event's payload, same-tx (03 invariant 1) —
        // captured into bundleHolder so step 2/3 below reuse it instead of
        // re-assembling (assembly is a pure, local-DB-only read either way).
        ContextBundle[] bundleHolder = new ContextBundle[1];
        Optional<Task> claimed = workBroker.claimNext(companyId, agentId, agent.getSkillTags(), claimedTask -> {
            ContextBundle bundle = contextAssembler.assemble(agent, claimedTask);
            bundleHolder[0] = bundle;
            ObjectNode extra = objectMapper.createObjectNode();
            ArrayNode provenance = extra.putArray("contextProvenance");
            bundle.provenanceIds().forEach(id -> provenance.add(id.toString()));
            return extra;
        });
        if (claimed.isEmpty()) {
            return;
        }
        Task task = claimed.get();
        LogContext.putTask(task.getId());
        ContextBundle bundle = bundleHolder[0];

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
            // Steps 2–4: bundle (assembled above, alongside the claim), prompt, LLM
            // call. Feedback (M0.6): a rejected task is requeued and re-claimed like
            // any other work, so the most recent rejection's feedback (if any) rides
            // along into this attempt's prompt.
            String feedback = taskService.latestRejectionFeedback(companyId, task.getId()).orElse(null);
            AssembledPrompt prompt = PromptAssembler.build(roleDef, task, feedback, bundle);
            List<LlmToolDef> tools = allowsTool(roleDef.getAllowedTools(), ChildTaskTool.NAME)
                    ? List.of(ChildTaskTool.DEF) : List.of();
            LlmRequest request = new LlmRequest(agent.getModelProvider(), agent.getModelName(),
                    prompt.systemPrompt(), prompt.messages(), tools, DEFAULT_MAX_OUTPUT_TOKENS,
                    null, null);
            LlmResult result = llmClient.complete(request);

            Optional<LlmToolCall> decomposeCall = result.toolCalls().stream()
                    .filter(call -> ChildTaskTool.NAME.equals(call.name()))
                    .findFirst();

            // Steps 5–6/7: usage + artifact (+ decomposition fan-out, if called) + pending_review,
            // ONE transaction — a crash between usage and completion never records spend without
            // the corresponding result, same invariant as the non-tool path.
            long costMicroUsd = costCalculator.costMicroUsd(agent.getModelProvider(),
                    agent.getModelName(), result.tokensIn(), result.tokensOut());
            // M4.2 compliance audit (07 Phase 4, 16 §4): model + exact prompt version + the
            // task inputs actually fed into this attempt, so task_events alone can
            // reconstruct what happened — see TaskService.CompletionAudit's javadoc.
            TaskService.CompletionAudit audit = new TaskService.CompletionAudit(
                    agent.getModelProvider(), agent.getModelName(), roleDef.getId(),
                    roleDef.getVersion(), feedback);
            txTemplate.executeWithoutResult(status -> {
                usageRecorder.record(companyId, agentId, task.getId(), task.getAttempt(),
                        agent.getModelProvider(), agent.getModelName(),
                        result.tokensIn(), result.tokensOut(), costMicroUsd);
                if (decomposeCall.isPresent()) {
                    JsonNode arguments = decomposeCall.get().arguments();
                    String artifactContent = result.content() != null && !result.content().isBlank()
                            ? result.content()
                            : arguments.path("summary").asText("Decomposed into child tasks.");
                    taskService.completeWithDecomposition(companyId, task.getId(), agentId,
                            artifactContent, parseChildSpecs(arguments), audit);
                } else {
                    taskService.complete(companyId, task.getId(), agentId, "text", result.content(), audit);
                }
            });
        } catch (LlmException e) {
            // Failure path: flag, never crash the loop (05 §execution, 12 §9).
            taskService.flag(companyId, task.getId(), agentId, FlagReasons.forKind(e.kind()));
        }
    }

    /** {@code allowedTools} is a JSON array of tool-name strings (roles are data — no role-key branching). */
    private static boolean allowsTool(JsonNode allowedTools, String toolName) {
        if (allowedTools == null || !allowedTools.isArray()) {
            return false;
        }
        for (JsonNode entry : allowedTools) {
            if (toolName.equals(entry.asText())) {
                return true;
            }
        }
        return false;
    }

    /** {@link ChildTaskTool}'s {@code children} argument → routing's plain spec records. */
    private static List<app.atrium.routing.TaskService.ChildTaskSpec> parseChildSpecs(JsonNode arguments) {
        List<app.atrium.routing.TaskService.ChildTaskSpec> specs = new java.util.ArrayList<>();
        for (JsonNode child : arguments.path("children")) {
            specs.add(new app.atrium.routing.TaskService.ChildTaskSpec(
                    child.path("title").asText(),
                    child.hasNonNull("description") ? child.get("description").asText() : null,
                    child.path("requiredSkill").asText(),
                    child.hasNonNull("priority") ? child.get("priority").asInt() : null));
        }
        return specs;
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
