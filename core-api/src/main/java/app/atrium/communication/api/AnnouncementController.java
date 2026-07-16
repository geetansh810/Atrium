package app.atrium.communication.api;

import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import app.atrium.communication.AnnouncementService;
import app.atrium.communication.api.CommunicationDtos.AnnouncementResponse;
import app.atrium.communication.api.CommunicationDtos.CreateAnnouncementRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{id}/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<AnnouncementResponse> list(@PathVariable UUID id) {
        requireTenantMatch(id);
        return announcementService.list(id).stream().map(AnnouncementResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse create(@PathVariable UUID id,
                                       @Valid @RequestBody CreateAnnouncementRequest request) {
        requireTenantMatch(id);
        return AnnouncementResponse.from(
                announcementService.create(id, request.title(), request.body(), request.category()));
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
