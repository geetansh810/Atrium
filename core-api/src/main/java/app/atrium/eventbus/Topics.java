package app.atrium.eventbus;

import java.util.UUID;

/**
 * Canonical topic addresses (12 §4) — SAM-style, future-broker-ready. Redis
 * transport keeps using channel {@code atrium:events:{companyId}} (04 §WS);
 * the topic is stored for durable consumers and the eventual broker swap.
 */
public final class Topics {

    private static final String PREFIX = "atrium/v1/";

    private Topics() {}

    /** {@code atrium/v1/{companyId}/task/{taskId}} */
    public static String task(UUID companyId, UUID taskId) {
        return PREFIX + companyId + "/task/" + taskId;
    }

    /** {@code atrium/v1/{companyId}/agent/{agentId}} */
    public static String agent(UUID companyId, UUID agentId) {
        return PREFIX + companyId + "/agent/" + agentId;
    }

    /** {@code atrium/v1/{companyId}/system} */
    public static String system(UUID companyId) {
        return PREFIX + companyId + "/system";
    }

    /** {@code atrium/v1/{companyId}/budget/{agentId|company}} (12 §4). */
    public static String budget(UUID companyId, UUID agentId) {
        return PREFIX + companyId + "/budget/" + (agentId != null ? agentId : "company");
    }

    /** {@code atrium/v1/{companyId}/memory/{agentId|company}} (12 §4, M-LN1) — same fallback idiom as {@link #budget}. */
    public static String memory(UUID companyId, UUID agentId) {
        return PREFIX + companyId + "/memory/" + (agentId != null ? agentId : "company");
    }

    /** {@code atrium/v1/{companyId}/chat/{channelId}} (12 §4, M2.5) — carries {@code chat.message}. */
    public static String chat(UUID companyId, UUID channelId) {
        return PREFIX + companyId + "/chat/" + channelId;
    }
}
