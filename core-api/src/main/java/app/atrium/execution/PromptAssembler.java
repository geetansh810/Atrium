package app.atrium.execution;

import app.atrium.execution.spi.LlmMessage;
import app.atrium.registry.domain.RoleDefinition;
import app.atrium.routing.domain.Task;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Pure function of its inputs (05 §execution rule) — sees only the role
 * definition, the task, optional rejection feedback, and the optional context
 * bundle. Secrets are structurally impossible: nothing else is in scope.
 * Layout per 14 §6; the skills/memories sections are simply absent while
 * {@code bundle} is null (M-CTX1 builds the real ContextAssembler).
 */
public final class PromptAssembler {

    private PromptAssembler() {}

    public static AssembledPrompt build(RoleDefinition roleDef, Task task,
                                        @Nullable String feedback, @Nullable ContextBundle bundle) {
        StringBuilder system = new StringBuilder(roleDef.getSystemPrompt());
        if (roleDef.getOutputContract() != null && !roleDef.getOutputContract().isBlank()) {
            system.append("\n\n## Output contract\n").append(roleDef.getOutputContract());
        }

        StringBuilder user = new StringBuilder("## Your task\n").append(task.getTitle());
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            user.append('\n').append(task.getDescription());
        }
        if (feedback != null && !feedback.isBlank()) {
            user.append("\n\n## Feedback from your previous attempt\n").append(feedback);
        }
        // bundle is always null until M-CTX1 — "## Your skills" / "## What you have
        // learned here" sections land here, appended after the task, when it isn't.

        return new AssembledPrompt(system.toString(), List.of(new LlmMessage("user", user.toString())));
    }
}
