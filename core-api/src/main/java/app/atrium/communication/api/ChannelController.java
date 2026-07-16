package app.atrium.communication.api;

import app.atrium.common.NotFoundException;
import app.atrium.common.PageEnvelope;
import app.atrium.common.TenantContext;
import app.atrium.communication.ChannelService;
import app.atrium.communication.api.CommunicationDtos.ChannelResponse;
import app.atrium.communication.api.CommunicationDtos.CreateChannelRequest;
import app.atrium.communication.api.CommunicationDtos.MessageResponse;
import app.atrium.communication.api.CommunicationDtos.SendMessageRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping("/companies/{id}/channels")
    public List<ChannelResponse> list(@PathVariable UUID id) {
        requireTenantMatch(id);
        return channelService.listChannels(id).stream().map(ChannelResponse::from).toList();
    }

    @PostMapping("/companies/{id}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponse create(@PathVariable UUID id, @Valid @RequestBody CreateChannelRequest request) {
        requireTenantMatch(id);
        return ChannelResponse.from(channelService.createChannel(id, request.name(), request.kind()));
    }

    @GetMapping("/channels/{id}/messages")
    public PageEnvelope<MessageResponse> messages(@PathVariable UUID id,
                                                  @RequestParam(required = false) String before,
                                                  @RequestParam(required = false) Integer limit) {
        ChannelService.MessagePage page =
                channelService.listMessages(TenantContext.requireCompanyId(), id, before, limit);
        return new PageEnvelope<>(page.messages().stream().map(MessageResponse::from).toList(),
                page.nextCursor());
    }

    @PostMapping("/channels/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        UUID companyId = TenantContext.requireCompanyId();
        String sender = TenantContext.userId().map(u -> "user:" + u).orElse("user");
        return MessageResponse.from(channelService.postMessage(companyId, id, sender, request.text()));
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
