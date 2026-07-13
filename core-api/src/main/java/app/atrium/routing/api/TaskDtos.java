package app.atrium.routing.api;

import app.atrium.routing.domain.Subtask;
import app.atrium.routing.domain.Task;
import app.atrium.routing.domain.TaskEvent;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {}

    /** Body per 04 §Tasks: POST /companies/{id}/tasks. */
    public record CreateTaskRequest(
            @NotBlank String title,
            String description,
            @NotBlank String requiredSkill,
            @Min(1) @Max(5) Integer priority,
            @Positive Integer etaMinutes,
            UUID parentTaskId,
            List<@Valid SubtaskCreate> subtasks) {}

    public record SubtaskCreate(@NotBlank String label) {}

    /** Query params of GET /companies/{id}/tasks (04 §Tasks + rule 3). */
    public record TaskListQuery(
            String status,
            String skill,
            UUID agentId,
            String view,
            Integer limit,
            String cursor) {}

    public record TaskResponse(
            UUID id,
            UUID companyId,
            UUID parentTaskId,
            String requiredSkill,
            String title,
            String description,
            int priority,
            String status,
            int progress,
            Integer etaMinutes,
            UUID assignedAgentId,
            UUID createdByUserId,
            Instant claimedAt,
            Instant leaseExpiresAt,
            Instant completedAt,
            Instant createdAt,
            int attempt,
            UUID billingTaskId,
            int requestDepth) {

        public static TaskResponse from(Task task) {
            return new TaskResponse(task.getId(), task.getCompanyId(), task.getParentTaskId(),
                    task.getRequiredSkill(), task.getTitle(), task.getDescription(),
                    task.getPriority(), task.getStatus(), task.getProgress(), task.getEtaMinutes(),
                    task.getAssignedAgentId(), task.getCreatedByUserId(), task.getClaimedAt(),
                    task.getLeaseExpiresAt(), task.getCompletedAt(), task.getCreatedAt(),
                    task.getAttempt(), task.getBillingTaskId(), task.getRequestDepth());
        }
    }

    public record SubtaskResponse(UUID id, String label, int position, String state) {

        public static SubtaskResponse from(Subtask subtask) {
            return new SubtaskResponse(subtask.getId(), subtask.getLabel(),
                    subtask.getPosition(), subtask.getState());
        }
    }

    /** GET /tasks/{id} → Task + subtasks + latest artifact (04 §Tasks). */
    public record TaskDetailResponse(
            TaskResponse task,
            List<SubtaskResponse> subtasks,
            ArtifactResponse latestArtifact) {}

    public record ArtifactResponse(UUID id, String kind, String content, Instant createdAt) {

        public static ArtifactResponse from(app.atrium.routing.domain.Artifact artifact) {
            return new ArtifactResponse(artifact.getId(), artifact.getKind(),
                    artifact.getContent(), artifact.getCreatedAt());
        }
    }

    public record TaskEventResponse(
            UUID id,
            UUID taskId,
            String eventType,
            String actor,
            JsonNode payload,
            Instant createdAt) {

        public static TaskEventResponse from(TaskEvent event) {
            return new TaskEventResponse(event.getId(), event.getTaskId(), event.getEventType(),
                    event.getActor(), event.getPayload(), event.getCreatedAt());
        }
    }
}
