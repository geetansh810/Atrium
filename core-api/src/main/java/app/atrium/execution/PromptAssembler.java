package app.atrium.execution;

import app.atrium.agentmind.ContextBundle;
import app.atrium.agentmind.SkillExcerpt;
import app.atrium.execution.spi.LlmMessage;
import app.atrium.registry.domain.RoleDefinition;
import app.atrium.routing.domain.Task;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Pure function of its inputs (05 §execution rule) — sees only the role
 * definition, the task, optional rejection feedback, and the optional context
 * bundle. Secrets are structurally impossible: nothing else is in scope.
 * Layout per 14 §6; the "## What you have learned here" / "## Reference
 * material" sections are omitted (not just empty) since {@code bundle}'s
 * memories/knowledge lists are always empty until M-MEM1/M-KN1 give them
 * something to render.
 */
public final class PromptAssembler {

    private PromptAssembler() {}

    public static AssembledPrompt build(RoleDefinition roleDef, Task task,
                                        @Nullable String feedback, @Nullable ContextBundle bundle) {
        StringBuilder system = new StringBuilder(roleDef.getSystemPrompt());
        if (bundle != null && !bundle.skills().isEmpty()) {
            system.append("\n\n## Your skills\n").append(renderSkills(bundle.skills()));
        }
        if (roleDef.getOutputContract() != null && !roleDef.getOutputContract().isBlank()) {
            system.append("\n\n## Output contract\n").append(roleDef.getOutputContract());
        }

        StringBuilder user = new StringBuilder("## Your task\n").append(task.getTitle());
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            user.append('\n').append(task.getDescription());
        }
        if (feedback != null && !feedback.isBlank()) {
            user.append("\n\n## Reviewer feedback (attempt ").append(task.getAttempt()).append(")\n")
                    .append(feedback);
        }

        return new AssembledPrompt(system.toString(), List.of(new LlmMessage("user", user.toString())));
    }

    private static String renderSkills(List<SkillExcerpt> skills) {
        StringBuilder sb = new StringBuilder();
        for (SkillExcerpt skill : skills) {
            if (skill.indexOnly()) {
                sb.append("- ").append(skill.name()).append(": ").append(skill.description()).append('\n');
            } else {
                sb.append("### ").append(skill.name()).append('\n').append(skill.bodyMd()).append("\n\n");
            }
        }
        return sb.toString().stripTrailing();
    }
}
