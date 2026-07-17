package app.atrium.common;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * MDC binding for structured logs (M3.5, 10 §6: "companyId/taskId/agentId on
 * every business log line"). {@code companyId}/{@code userId} are bound by
 * {@link TenantContext} itself ({@code set}/{@code clear} — the one place
 * tenant binding happens, HTTP request or background loop alike); this class
 * only covers the two fields TenantContext has no concept of, at the "per
 * unit of work" boundaries that know them: {@code LlmLoopRuntime}/{@code
 * EchoRuntime}'s poll iteration and {@code WorkerTaskController}'s claim/renew.
 */
public final class LogContext {

    public static final String TASK_ID = "taskId";
    public static final String AGENT_ID = "agentId";

    private LogContext() {}

    public static void putAgent(UUID agentId) {
        if (agentId != null) {
            MDC.put(AGENT_ID, agentId.toString());
        }
    }

    public static void putTask(UUID taskId) {
        if (taskId != null) {
            MDC.put(TASK_ID, taskId.toString());
        }
    }

    public static void clear() {
        MDC.remove(TASK_ID);
        MDC.remove(AGENT_ID);
    }
}
