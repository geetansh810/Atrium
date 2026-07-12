package app.atrium.routing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Checklist row under a task — tenant-scoped through its task, no company_id column. */
@Entity
@Table(name = "subtasks")
public class Subtask {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int position;

    /** todo | doing | done. */
    @Column(nullable = false)
    private String state = "todo";

    protected Subtask() {}

    public Subtask(UUID taskId, String label, int position) {
        this.taskId = taskId;
        this.label = label;
        this.position = position;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getLabel() { return label; }
    public int getPosition() { return position; }
    public String getState() { return state; }

    public void setState(String state) { this.state = state; }
}
