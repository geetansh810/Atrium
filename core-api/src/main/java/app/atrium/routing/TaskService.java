package app.atrium.routing;

import app.atrium.common.ConflictException;
import app.atrium.common.FieldValidationException;
import app.atrium.common.KeysetCursors;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import app.atrium.registry.AgentDirectory;
import app.atrium.routing.api.TaskDtos.CreateTaskRequest;
import app.atrium.routing.api.TaskDtos.SubtaskCreate;
import app.atrium.routing.api.TaskDtos.TaskListQuery;
import app.atrium.routing.domain.Artifact;
import app.atrium.routing.domain.ArtifactRepository;
import app.atrium.routing.domain.Subtask;
import app.atrium.routing.domain.SubtaskRepository;
import app.atrium.routing.domain.Task;
import app.atrium.routing.domain.TaskEvent;
import app.atrium.routing.domain.TaskEventRepository;
import app.atrium.routing.domain.TaskRepository;
import app.atrium.routing.domain.TaskStateGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task lifecycle owner (05 §routing). Queue reads are always company-scoped;
 * every state change goes through TaskEventRecorder in the same transaction.
 * No LLM calls, no provider names, no role-specific branching — ever.
 */
@Service
public class TaskService {

    private static final Set<String> STATUSES = Set.of("queued", "claimed", "in_progress",
            "flagged", "pending_review", "approved", "rejected", "cancelled");
    private static final Set<String> VIEWS = Set.of("my", "assigned", "completed");
    private static final Set<String> TERMINAL_STATUSES = Set.of("approved", "cancelled");

    private final TaskRepository tasks;
    private final SubtaskRepository subtasks;
    private final TaskEventRepository taskEvents;
    private final ArtifactRepository artifacts;
    private final AgentDirectory agentDirectory;
    private final TaskEventRecorder recorder;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public TaskService(TaskRepository tasks, SubtaskRepository subtasks,
                       TaskEventRepository taskEvents, ArtifactRepository artifacts,
                       AgentDirectory agentDirectory, TaskEventRecorder recorder,
                       ObjectMapper objectMapper, EntityManager entityManager, Clock clock) {
        this.tasks = tasks;
        this.subtasks = subtasks;
        this.taskEvents = taskEvents;
        this.artifacts = artifacts;
        this.agentDirectory = agentDirectory;
        this.recorder = recorder;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** One page of tasks plus the subtasks of the page's members. */
    public record TaskPage(List<Task> tasks, String nextCursor) {}

    /** latestArtifact is null until complete() writes one (M0.5b). */
    public record TaskDetail(Task task, List<Subtask> subtasks, Artifact latestArtifact) {}

    public record EventPage(List<TaskEvent> events, String nextCursor) {}

    @Transactional
    public TaskDetail create(UUID companyId, CreateTaskRequest request) {
        // Roles are data: the skill must exist on the roster, not in a switch (05 §routing).
        if (agentDirectory.findBySkill(companyId, request.requiredSkill()).isEmpty()) {
            throw new FieldValidationException(Map.of("requiredSkill",
                    "no agent in this company has skill '" + request.requiredSkill() + "'"));
        }

        UUID billingTaskId = null;
        int requestDepth = 0;
        if (request.parentTaskId() != null) {
            Task parent = tasks.findByIdAndCompanyId(request.parentTaskId(), companyId)
                    .orElseThrow(() -> NotFoundException.of("Parent task", request.parentTaskId()));
            billingTaskId = parent.getBillingTaskId() != null
                    ? parent.getBillingTaskId() : parent.getId();
            requestDepth = parent.getRequestDepth() + 1;
        }

        Task task = new Task(companyId, request.parentTaskId(), request.requiredSkill(),
                request.title(), request.description(),
                request.priority() != null ? request.priority() : 3,
                request.etaMinutes(), TenantContext.userId().orElse(null),
                billingTaskId, requestDepth);
        tasks.save(task);
        if (task.getBillingTaskId() == null) {
            task.billToSelf();      // root of a request chain bills to itself (15 §1)
        }

        List<SubtaskCreate> requestedSubtasks =
                request.subtasks() != null ? request.subtasks() : List.of();
        List<Subtask> created = new ArrayList<>(requestedSubtasks.size());
        for (int i = 0; i < requestedSubtasks.size(); i++) {
            created.add(subtasks.save(new Subtask(task.getId(), requestedSubtasks.get(i).label(), i)));
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", task.getTitle());
        payload.put("requiredSkill", task.getRequiredSkill());
        payload.put("priority", task.getPriority());
        if (task.getParentTaskId() != null) {
            payload.put("parentTaskId", task.getParentTaskId().toString());
        }
        recorder.record(task, "created", actor(), payload);

        return new TaskDetail(task, created, null);
    }

    @Transactional(readOnly = true)
    public TaskPage list(UUID companyId, TaskListQuery query) {
        int limit = clampLimit(query.limit());

        StringBuilder sql = new StringBuilder("SELECT * FROM tasks WHERE company_id = :companyId");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId);

        if (query.status() != null) {
            if (!STATUSES.contains(query.status())) {
                throw new FieldValidationException(Map.of("status", "must be one of " + STATUSES));
            }
            sql.append(" AND status = :status");
            params.put("status", query.status());
        }
        if (query.skill() != null) {
            sql.append(" AND required_skill = :skill");
            params.put("skill", query.skill());
        }
        if (query.agentId() != null) {
            sql.append(" AND assigned_agent_id = :agentId");
            params.put("agentId", query.agentId());
        }
        if (query.view() != null) {
            appendViewClause(sql, params, query.view());
        }
        if (query.cursor() != null) {
            KeysetCursors.Position position = KeysetCursors.decode(query.cursor());
            sql.append(" AND (created_at, id) < (:cursorCreatedAt, :cursorId)");
            params.put("cursorCreatedAt", position.createdAt());
            params.put("cursorId", position.id());
        }
        sql.append(" ORDER BY created_at DESC, id DESC");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), Task.class);
        params.forEach(nativeQuery::setParameter);
        nativeQuery.setMaxResults(limit + 1);

        @SuppressWarnings("unchecked")
        List<Task> page = nativeQuery.getResultList();
        String nextCursor = null;
        if (page.size() > limit) {
            page = page.subList(0, limit);
            Task last = page.get(limit - 1);
            nextCursor = KeysetCursors.encode(last.getCreatedAt(), last.getId());
        }
        return new TaskPage(page, nextCursor);
    }

    @Transactional(readOnly = true)
    public TaskDetail get(UUID companyId, UUID taskId) {
        Task task = tasks.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));
        Artifact latest = artifacts.findFirstByTaskIdAndCompanyIdOrderByCreatedAtDesc(taskId, companyId)
                .orElse(null);
        return new TaskDetail(task, subtasks.findByTaskScoped(taskId, companyId), latest);
    }

    /**
     * Worker progress update (04 §Tasks: POST progress) — claimed→in_progress on
     * first report. Called directly by LlmLoopRuntime (M0.5b); never touch
     * task.status outside TaskStateGuard.
     */
    @Transactional
    public Task progress(UUID companyId, UUID taskId, UUID agentId, Integer progressPct,
                         Integer etaMinutes, String note) {
        Task task = requireAssigned(companyId, taskId, agentId);
        if ("claimed".equals(task.getStatus())) {
            TaskStateGuard.transition(task, "in_progress");
        } else if (!"in_progress".equals(task.getStatus())) {
            throw new ConflictException("Task " + taskId + " is '" + task.getStatus()
                    + "' — cannot report progress");
        }
        if (progressPct != null || etaMinutes != null) {
            task.updateProgress(progressPct != null ? progressPct : task.getProgress(), etaMinutes);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        if (progressPct != null) payload.put("progress", progressPct);
        if (note != null) payload.put("note", note);
        recorder.record(task, "progress", "agent:" + agentId, payload);
        return task;
    }

    /**
     * Loop step 7 success path (13 §3.2): artifact + pending_review. The ONLY
     * place artifacts are written — execution never touches the repository.
     */
    @Transactional
    public Task complete(UUID companyId, UUID taskId, UUID agentId, String artifactKind,
                         String artifactContent) {
        Task task = requireAssigned(companyId, taskId, agentId);
        Artifact artifact = artifacts.save(new Artifact(companyId, taskId, artifactKind, artifactContent));
        TaskStateGuard.transition(task, "pending_review");
        task.updateProgress(100, null);
        task.markCompleted(Instant.now(clock));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("artifactId", artifact.getId().toString());
        payload.put("artifactKind", artifactKind);
        payload.put("agentId", agentId.toString());
        recorder.record(task, "completed", "agent:" + agentId, payload);
        return task;
    }

    /**
     * Loop step 7 failure path: provider/runner errors flag, never crash
     * (05 §execution). {@code reason} is the 13 §1.3 taxonomy's flag reason.
     */
    @Transactional
    public Task flag(UUID companyId, UUID taskId, UUID agentId, String reason) {
        Task task = requireAssigned(companyId, taskId, agentId);
        TaskStateGuard.transition(task, "flagged");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("reason", reason);
        recorder.record(task, "flagged", "agent:" + agentId, payload);
        return task;
    }

    /**
     * Human/supervisor ship gate (04 §Tasks, 03 invariant 5): blocked while any
     * child task or checklist subtask is still open. The only way status
     * reaches 'approved' — pending_review is a hard gate until this runs.
     */
    @Transactional
    public Task approve(UUID companyId, UUID taskId) {
        Task task = tasks.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));
        if (tasks.existsByParentTaskIdAndCompanyIdAndStatusNotIn(taskId, companyId, TERMINAL_STATUSES)) {
            throw new ConflictException("Task " + taskId + " has open child tasks — approve those first");
        }
        if (subtasks.existsOpenScoped(taskId, companyId)) {
            throw new ConflictException("Task " + taskId + " has open subtasks — complete those first");
        }
        TaskStateGuard.transition(task, "approved");
        ObjectNode payload = objectMapper.createObjectNode();
        if (task.getAssignedAgentId() != null) {
            payload.put("agentId", task.getAssignedAgentId().toString());
        }
        recorder.record(task, "approved", actor(), payload);
        return task;
    }

    /**
     * Sends work back for rework (04 §Tasks: reject {@code {feedback}}).
     * Persists the real 'rejected' status with the feedback in the audit
     * trail, then requeues in the same transaction so the next claim
     * increments {@code attempt} — a fresh {@code taskId:attempt} idempotency
     * key for the redo, same as any other re-claim (M0.4). LlmLoopRuntime
     * reads the feedback back via {@link #latestRejectionFeedback} and feeds
     * it to PromptAssembler on the next attempt.
     */
    @Transactional
    public Task reject(UUID companyId, UUID taskId, String feedback) {
        Task task = tasks.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));
        UUID previousAgentId = task.getAssignedAgentId();

        TaskStateGuard.transition(task, "rejected");
        ObjectNode rejectedPayload = objectMapper.createObjectNode();
        rejectedPayload.put("feedback", feedback);
        if (previousAgentId != null) {
            rejectedPayload.put("agentId", previousAgentId.toString());
        }
        recorder.record(task, "rejected", actor(), rejectedPayload);

        TaskStateGuard.transition(task, "queued");
        task.clearAssignment();
        ObjectNode requeuedPayload = objectMapper.createObjectNode();
        if (previousAgentId != null) {
            requeuedPayload.put("previousAgentId", previousAgentId.toString());
        }
        recorder.record(task, "requeued", "system", requeuedPayload);
        return task;
    }

    /** Feeds the loop's next-attempt prompt (M0.6) — empty until a task is ever rejected. */
    @Transactional(readOnly = true)
    public Optional<String> latestRejectionFeedback(UUID companyId, UUID taskId) {
        return taskEvents.findFirstByTaskIdAndCompanyIdAndEventTypeOrderByCreatedAtDesc(
                        taskId, companyId, "rejected")
                .map(TaskEvent::getPayload)
                .filter(payload -> payload != null && payload.hasNonNull("feedback"))
                .map(payload -> payload.get("feedback").asText());
    }

    private Task requireAssigned(UUID companyId, UUID taskId, UUID agentId) {
        Task task = tasks.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));
        if (!agentId.equals(task.getAssignedAgentId())) {
            throw new ConflictException("Task " + taskId + " is not assigned to agent:" + agentId);
        }
        return task;
    }

    @Transactional(readOnly = true)
    public EventPage events(UUID companyId, UUID taskId, Integer limit, String cursor) {
        tasks.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));

        int pageSize = clampLimit(limit);
        KeysetCursors.Position after = cursor != null
                ? KeysetCursors.decode(cursor)
                : new KeysetCursors.Position(Instant.EPOCH, new UUID(0, 0));
        List<TaskEvent> page = taskEvents.findPageAfter(taskId, companyId,
                after.createdAt(), after.id(), PageRequest.of(0, pageSize + 1));

        String nextCursor = null;
        if (page.size() > pageSize) {
            page = page.subList(0, pageSize);
            TaskEvent last = page.get(pageSize - 1);
            nextCursor = KeysetCursors.encode(last.getCreatedAt(), last.getId());
        }
        return new EventPage(page, nextCursor);
    }

    private void appendViewClause(StringBuilder sql, Map<String, Object> params, String view) {
        if (!VIEWS.contains(view)) {
            throw new FieldValidationException(Map.of("view", "must be one of " + VIEWS));
        }
        switch (view) {
            case "my" -> {
                UUID userId = TenantContext.userId().orElseThrow(() -> new FieldValidationException(
                        Map.of("view", "view=my requires the X-User-Id header")));
                sql.append(" AND created_by_user_id = :viewUserId");
                params.put("viewUserId", userId);
            }
            case "assigned" -> sql.append(" AND assigned_agent_id IS NOT NULL");
            // "completed" tab = shipped work; approval is the only ship gate (07)
            case "completed" -> sql.append(" AND status = 'approved'");
            default -> throw new IllegalStateException("unreachable");
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return 50;
        if (limit < 1 || limit > 200) {
            throw new FieldValidationException(Map.of("limit", "must be between 1 and 200"));
        }
        return limit;
    }

    private String actor() {
        return TenantContext.userId().map(id -> "user:" + id).orElse("system");
    }
}
