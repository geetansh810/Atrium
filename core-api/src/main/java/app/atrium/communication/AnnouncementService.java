package app.atrium.communication;

import app.atrium.communication.domain.Announcement;
import app.atrium.communication.domain.AnnouncementRepository;
import app.atrium.eventbus.OutboxWriter;
import app.atrium.eventbus.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company-wide announcements (04 §Communication). Each create emits an
 * {@code announcement.created} system event (12 §4) in the same transaction.
 */
@Service
public class AnnouncementService {

    private static final Set<String> CATEGORIES = Set.of("company", "update", "maintenance");

    private final AnnouncementRepository announcements;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public AnnouncementService(AnnouncementRepository announcements, OutboxWriter outbox,
                               ObjectMapper objectMapper) {
        this.announcements = announcements;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public List<Announcement> list(UUID companyId) {
        return announcements.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional
    public Announcement create(UUID companyId, String title, String body, String category) {
        String resolved = (category == null || category.isBlank()) ? "company" : category;
        if (!CATEGORIES.contains(resolved)) {
            throw new app.atrium.common.FieldValidationException(
                    java.util.Map.of("category", "must be one of " + CATEGORIES));
        }
        Announcement saved = announcements.save(new Announcement(companyId, title, body, resolved));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", saved.getId().toString());
        payload.put("title", saved.getTitle());
        payload.put("category", saved.getCategory());
        outbox.append(companyId, Topics.system(companyId), "announcement.created", payload);
        return saved;
    }
}
