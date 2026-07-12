package app.atrium.routing.domain;

import app.atrium.common.ConflictException;
import java.util.Map;
import java.util.Set;

/**
 * The task state machine (03 §tasks CHECK + 04 §Tasks semantics + 05 §routing):
 * the ONLY door to {@code tasks.status}. Illegal moves → {@link ConflictException}
 * → 409 problem+json (04 rule 4: wrong-state calls return 409).
 *
 * <pre>
 * queued         → claimed (M0.4) | cancelled
 * claimed        → in_progress | flagged | queued (lease requeue) | cancelled
 * in_progress    → pending_review (complete) | flagged | queued (lease requeue) | cancelled
 * flagged        → in_progress | queued | cancelled
 * pending_review → approved | rejected | in_progress (reject-with-feedback, 04) | cancelled
 * rejected       → in_progress | queued | cancelled       (rework path, M0.6)
 * approved, cancelled — terminal
 * </pre>
 */
public final class TaskStateGuard {

    private static final Map<String, Set<String>> LEGAL = Map.of(
            "queued", Set.of("claimed", "cancelled"),
            "claimed", Set.of("in_progress", "flagged", "queued", "cancelled"),
            "in_progress", Set.of("pending_review", "flagged", "queued", "cancelled"),
            "flagged", Set.of("in_progress", "queued", "cancelled"),
            "pending_review", Set.of("approved", "rejected", "in_progress", "cancelled"),
            "rejected", Set.of("in_progress", "queued", "cancelled"),
            "approved", Set.of(),
            "cancelled", Set.of());

    private TaskStateGuard() {}

    /** Applies {@code next} to the task, or throws 409 if the move is illegal. */
    public static void transition(Task task, String next) {
        String current = task.getStatus();
        Set<String> allowed = LEGAL.get(current);
        if (allowed == null) {
            throw new IllegalStateException("Unknown task status in DB: " + current);
        }
        if (!allowed.contains(next)) {
            throw new ConflictException(
                    "Task " + task.getId() + " is '" + current + "' — cannot move to '" + next + "'");
        }
        task.setStatus(next);
    }

    public static boolean isLegal(String current, String next) {
        Set<String> allowed = LEGAL.get(current);
        return allowed != null && allowed.contains(next);
    }
}
