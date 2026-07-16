package app.atrium.communication.api;

import app.atrium.communication.domain.Announcement;
import app.atrium.communication.domain.Channel;
import app.atrium.communication.domain.Message;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class CommunicationDtos {

    private CommunicationDtos() {}

    public record CreateChannelRequest(@NotBlank String name, String kind) {}

    public record ChannelResponse(UUID id, UUID companyId, String name, String kind, Instant createdAt) {
        public static ChannelResponse from(Channel c) {
            return new ChannelResponse(c.getId(), c.getCompanyId(), c.getName(), c.getKind(), c.getCreatedAt());
        }
    }

    /** Body of POST /channels/{id}/messages — user send (agent/bot messages come from core-api internally). */
    public record SendMessageRequest(@NotBlank String text) {}

    public record MessageResponse(UUID id, UUID channelId, String sender, String text, Instant createdAt) {
        public static MessageResponse from(Message m) {
            return new MessageResponse(m.getId(), m.getChannelId(), m.getSender(), m.getText(), m.getCreatedAt());
        }
    }

    public record CreateAnnouncementRequest(@NotBlank String title, String body, String category) {}

    public record AnnouncementResponse(UUID id, String title, String body, String category, Instant createdAt) {
        public static AnnouncementResponse from(Announcement a) {
            return new AnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), a.getCategory(), a.getCreatedAt());
        }
    }
}
