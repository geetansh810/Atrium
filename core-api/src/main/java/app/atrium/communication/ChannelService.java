package app.atrium.communication;

import app.atrium.common.ConflictException;
import app.atrium.common.KeysetCursors;
import app.atrium.common.NotFoundException;
import app.atrium.communication.domain.Channel;
import app.atrium.communication.domain.ChannelRepository;
import app.atrium.communication.domain.Message;
import app.atrium.communication.domain.MessageRepository;
import app.atrium.eventbus.OutboxWriter;
import app.atrium.eventbus.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Channels + messages (04 §Communication). Every write to {@code messages}
 * emits a {@code chat.message} outbox event in the SAME transaction (12 §4),
 * so the realtimebridge relay and any future consumer see it exactly like any
 * other domain event. Messages are append-only.
 */
@Service
public class ChannelService {

    /** Newest-first "before" cursor sentinel for the first page (symmetric to Instant.EPOCH for ASC). */
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");
    private static final Set<String> CHANNEL_KINDS = Set.of("channel", "dm");

    private final ChannelRepository channels;
    private final MessageRepository messages;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public ChannelService(ChannelRepository channels, MessageRepository messages,
                          OutboxWriter outbox, ObjectMapper objectMapper) {
        this.channels = channels;
        this.messages = messages;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public List<Channel> listChannels(UUID companyId) {
        return channels.findByCompanyIdOrderByCreatedAtAsc(companyId);
    }

    @Transactional
    public Channel createChannel(UUID companyId, String name, String kind) {
        String resolvedKind = (kind == null || kind.isBlank()) ? "channel" : kind;
        if (!CHANNEL_KINDS.contains(resolvedKind)) {
            throw new app.atrium.common.FieldValidationException(
                    java.util.Map.of("kind", "must be one of " + CHANNEL_KINDS));
        }
        channels.findByCompanyIdAndName(companyId, name).ifPresent(existing -> {
            throw new ConflictException("Channel '" + name + "' already exists");
        });
        return channels.save(new Channel(companyId, name, resolvedKind));
    }

    /**
     * Get-or-create a channel by name (used by {@link ChatNoticePipeline} to
     * post bot notices to {@code #general} without assuming a company already
     * has that channel). Idempotent by the {@code (company_id, name)} unique key.
     */
    @Transactional
    public Channel ensureChannel(UUID companyId, String name) {
        return channels.findByCompanyIdAndName(companyId, name)
                .orElseGet(() -> channels.save(new Channel(companyId, name, "channel")));
    }

    /** History, newest-first, keyset-paginated by an opaque {@code before} cursor. */
    public MessagePage listMessages(UUID companyId, UUID channelId, String before, Integer limit) {
        channels.findByIdAndCompanyId(channelId, companyId)
                .orElseThrow(() -> NotFoundException.of("Channel", channelId));

        int pageSize = clampLimit(limit);
        KeysetCursors.Position pos = before != null
                ? KeysetCursors.decode(before)
                : new KeysetCursors.Position(FAR_FUTURE, new UUID(-1L, -1L));
        List<Message> page = messages.findPageBefore(channelId, pos.createdAt(), pos.id(),
                PageRequest.of(0, pageSize + 1));

        String nextCursor = null;
        if (page.size() > pageSize) {
            page = page.subList(0, pageSize);
            Message last = page.get(pageSize - 1);
            nextCursor = KeysetCursors.encode(last.getCreatedAt(), last.getId());
        }
        return new MessagePage(page, nextCursor);
    }

    @Transactional
    public Message postMessage(UUID companyId, UUID channelId, String sender, String text) {
        Channel channel = channels.findByIdAndCompanyId(channelId, companyId)
                .orElseThrow(() -> NotFoundException.of("Channel", channelId));
        Message saved = messages.save(new Message(companyId, channel.getId(), sender, text));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("channelId", channel.getId().toString());
        payload.put("sender", sender);
        payload.put("text", text);
        outbox.append(companyId, Topics.chat(companyId, channel.getId()), "chat.message", payload);
        return saved;
    }

    private static int clampLimit(Integer limit) {
        if (limit == null) return 50;
        return Math.max(1, Math.min(200, limit));
    }

    public record MessagePage(List<Message> messages, String nextCursor) {}
}
