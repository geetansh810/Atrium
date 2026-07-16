package app.atrium.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One chat message (03 §V2). {@code sender} is a string tag:
 * {@code 'user:<id>'}, {@code 'agent:<id>'}, or {@code 'bot'} — messages are
 * append-only, never edited.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Message() {}

    public Message(UUID companyId, UUID channelId, String sender, String text) {
        this.companyId = companyId;
        this.channelId = channelId;
        this.sender = sender;
        this.text = text;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getChannelId() { return channelId; }
    public String getSender() { return sender; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
