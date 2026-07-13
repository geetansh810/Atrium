package app.atrium.routing.api;

import app.atrium.common.NotFoundException;
import app.atrium.common.PageEnvelope;
import app.atrium.common.TenantContext;
import app.atrium.routing.TaskService;
import app.atrium.routing.api.TaskDtos.CreateTaskRequest;
import app.atrium.routing.api.TaskDtos.SubtaskResponse;
import app.atrium.routing.api.TaskDtos.TaskDetailResponse;
import app.atrium.routing.api.TaskDtos.TaskEventResponse;
import app.atrium.routing.api.TaskDtos.TaskListQuery;
import app.atrium.routing.api.TaskDtos.TaskResponse;
import jakarta.validation.Valid;
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
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/companies/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDetailResponse create(@PathVariable UUID id,
                                     @Valid @RequestBody CreateTaskRequest request) {
        requireTenantMatch(id);
        TaskService.TaskDetail detail = taskService.create(id, request);
        return toDetailResponse(detail);
    }

    @GetMapping("/companies/{id}/tasks")
    public PageEnvelope<TaskResponse> list(@PathVariable UUID id,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String skill,
                                           @RequestParam(required = false) UUID agentId,
                                           @RequestParam(required = false) String view,
                                           @RequestParam(required = false) Integer limit,
                                           @RequestParam(required = false) String cursor) {
        requireTenantMatch(id);
        TaskService.TaskPage page = taskService.list(id,
                new TaskListQuery(status, skill, agentId, view, limit, cursor));
        return new PageEnvelope<>(page.tasks().stream().map(TaskResponse::from).toList(),
                page.nextCursor());
    }

    @GetMapping("/tasks/{id}")
    public TaskDetailResponse get(@PathVariable UUID id) {
        return toDetailResponse(taskService.get(TenantContext.requireCompanyId(), id));
    }

    @GetMapping("/tasks/{id}/events")
    public PageEnvelope<TaskEventResponse> events(@PathVariable UUID id,
                                                  @RequestParam(required = false) Integer limit,
                                                  @RequestParam(required = false) String cursor) {
        TaskService.EventPage page =
                taskService.events(TenantContext.requireCompanyId(), id, limit, cursor);
        return new PageEnvelope<>(page.events().stream().map(TaskEventResponse::from).toList(),
                page.nextCursor());
    }

    private TaskDetailResponse toDetailResponse(TaskService.TaskDetail detail) {
        return new TaskDetailResponse(TaskResponse.from(detail.task()),
                detail.subtasks().stream().map(SubtaskResponse::from).toList(),
                detail.latestArtifact() != null
                        ? app.atrium.routing.api.TaskDtos.ArtifactResponse.from(detail.latestArtifact())
                        : null);
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
