package app.atrium.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.atrium.common.ConflictException;
import app.atrium.routing.domain.TaskStateGuard;
import org.junit.jupiter.api.Test;

/** The transition matrix is the contract (03 §tasks + 04 §Tasks) — pin it. */
class TaskStateGuardTest {

    @Test
    void legalTransitionsAreAccepted() {
        assertThat(TaskStateGuard.isLegal("queued", "claimed")).isTrue();
        assertThat(TaskStateGuard.isLegal("claimed", "in_progress")).isTrue();
        assertThat(TaskStateGuard.isLegal("claimed", "queued")).isTrue();          // lease requeue
        assertThat(TaskStateGuard.isLegal("in_progress", "pending_review")).isTrue();
        assertThat(TaskStateGuard.isLegal("in_progress", "flagged")).isTrue();
        assertThat(TaskStateGuard.isLegal("in_progress", "queued")).isTrue();      // lease requeue
        assertThat(TaskStateGuard.isLegal("flagged", "in_progress")).isTrue();
        assertThat(TaskStateGuard.isLegal("pending_review", "approved")).isTrue();
        assertThat(TaskStateGuard.isLegal("pending_review", "rejected")).isTrue();
        assertThat(TaskStateGuard.isLegal("pending_review", "in_progress")).isTrue(); // reject+feedback
        assertThat(TaskStateGuard.isLegal("rejected", "in_progress")).isTrue();       // rework
        assertThat(TaskStateGuard.isLegal("queued", "cancelled")).isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThat(TaskStateGuard.isLegal("queued", "in_progress")).isFalse();  // must claim first
        assertThat(TaskStateGuard.isLegal("queued", "approved")).isFalse();     // nothing ships unreviewed
        assertThat(TaskStateGuard.isLegal("queued", "pending_review")).isFalse();
        assertThat(TaskStateGuard.isLegal("in_progress", "approved")).isFalse();
        assertThat(TaskStateGuard.isLegal("claimed", "pending_review")).isFalse();
    }

    @Test
    void terminalStatesAllowNothing() {
        for (String next : new String[]{"queued", "claimed", "in_progress", "flagged",
                "pending_review", "approved", "rejected", "cancelled"}) {
            assertThat(TaskStateGuard.isLegal("approved", next)).as("approved → " + next).isFalse();
            assertThat(TaskStateGuard.isLegal("cancelled", next)).as("cancelled → " + next).isFalse();
        }
    }

    @Test
    void transitionThrowsConflictOnIllegalMove_theHttp409Path() {
        // Fresh entity defaults to 'queued'; approving it skips the review gate.
        var task = new app.atrium.routing.domain.Task(java.util.UUID.randomUUID(), null,
                "coding", "t", null, 3, null, null, null, 0);
        assertThatThrownBy(() -> TaskStateGuard.transition(task, "approved"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("queued");
        assertThat(task.getStatus()).isEqualTo("queued");

        TaskStateGuard.transition(task, "claimed");
        assertThat(task.getStatus()).isEqualTo("claimed");
    }
}
