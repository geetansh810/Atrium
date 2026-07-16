package app.atrium.communication;

import app.atrium.communication.domain.Channel;
import app.atrium.eventbus.EventConsumerCursorRepository;
import app.atrium.eventbus.EventCursorWorker;
import app.atrium.eventbus.OutboxEvent;
import app.atrium.eventbus.OutboxEventRepository;
import app.atrium.registry.AgentDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The durable consumer (12 §3) behind M2.5's "agent completing work produces a
 * chat message" Done-when. Consumes {@code task.completed} straight from the
 * outbox payload — {@code agentId} and {@code title} (16 §4) — never reaching
 * back into routing (communication has no dependency on routing; the payload is
 * the contract). For each completion it posts a bot message to the company's
 * {@code #general} channel ("🤖 {agent} finished '{title}' — ready for review.").
 *
 * <p>Idempotency: at-least-once delivery means a redelivered batch could post a
 * duplicate notice. That is cosmetic (a chat log, not billed state) and the base
 * class only redelivers on a mid-batch crash, so no dedup key is worth the
 * complexity here — the same "lossy-OK projection" posture realtimebridge takes
 * (12 §2). If exact-once chat ever matters, key a notice on {@code taskId:attempt}
 * like {@code UsageLedger} does.
 */
@Component
public class ChatNoticePipeline extends EventCursorWorker {

    static final String CONSUMER_NAME = "chat_notice";
    static final String GENERAL_CHANNEL = "general";

    private static final Logger log = LoggerFactory.getLogger(ChatNoticePipeline.class);

    private final ChannelService channels;
    private final AgentDirectory agents;
    private final int batchSize;
    private final ChatNoticePipeline self;

    public ChatNoticePipeline(OutboxEventRepository outbox, EventConsumerCursorRepository cursors,
                              ChannelService channels, AgentDirectory agents,
                              @Value("${atrium.chat-notice.batch-size:50}") int batchSize,
                              @Lazy ChatNoticePipeline self) {
        super(CONSUMER_NAME, outbox, cursors);
        this.channels = channels;
        this.agents = agents;
        this.batchSize = batchSize;
        // Same @Lazy-self idiom as StatsRollupWorker/LearningPipeline: pollOnce()
        // is inherited + @Transactional, so an unqualified this.pollOnce(...) from
        // the @Scheduled method would bypass Spring's proxy entirely.
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${atrium.chat-notice.poll-interval-ms:5000}")
    public void poll() {
        self.pollOnce(batchSize);
    }

    @Override
    protected void handle(OutboxEvent event) {
        if (!"task.completed".equals(event.getEventType())) {
            return;
        }
        JsonNode payload = event.getPayload();
        if (payload == null || !payload.hasNonNull("agentId") || !payload.hasNonNull("title")) {
            log.warn("chat_notice: skipping task.completed event {} — missing agentId/title in payload",
                    event.getId());
            return;
        }
        UUID companyId = event.getCompanyId();
        UUID agentId = UUID.fromString(payload.get("agentId").asText());
        String title = payload.get("title").asText();

        String agentName = agents.findById(companyId, agentId)
                .map(a -> a.getName())
                .orElse("An agent");

        Channel general = channels.ensureChannel(companyId, GENERAL_CHANNEL);
        channels.postMessage(companyId, general.getId(), "bot",
                "🤖 " + agentName + " finished '" + title + "' — ready for review.");
    }
}
