package app.atrium.routing;

import app.atrium.eventbus.OutboxWriter;
import app.atrium.eventbus.Topics;
import app.atrium.routing.domain.Task;
import app.atrium.routing.domain.TaskEvent;
import app.atrium.routing.domain.TaskEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE one helper for recording task changes: appends the {@code task_events}
 * audit row AND the {@code outbox_events} row in the caller's transaction —
 * a single doorway so forgetting one half is impossible (17 §M0.3).
 * {@code MANDATORY} propagation: calling this outside a business tx is a bug
 * and fails loudly.
 */
@Component
public class TaskEventRecorder {

    private final TaskEventRepository taskEvents;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public TaskEventRecorder(TaskEventRepository taskEvents, OutboxWriter outboxWriter,
                             ObjectMapper objectMapper) {
        this.taskEvents = taskEvents;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * @param eventType 03 vocabulary: created|claimed|progress|note|flagged|
     *                  completed|approved|rejected|requeued|cancelled
     * @param actor     'user:&lt;id&gt;' | 'agent:&lt;id&gt;' | 'system'
     * @param payload   event-specific detail; may be null for the audit row
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Task task, String eventType, String actor, ObjectNode payload) {
        taskEvents.save(new TaskEvent(task.getCompanyId(), task.getId(), eventType, actor, payload));

        ObjectNode outboxPayload = payload != null
                ? payload.deepCopy() : objectMapper.createObjectNode();
        outboxPayload.put("taskId", task.getId().toString());
        outboxPayload.put("status", task.getStatus());
        outboxWriter.append(task.getCompanyId(),
                Topics.task(task.getCompanyId(), task.getId()),
                "task." + eventType, outboxPayload);
    }
}
