package app.atrium.execution;

import app.atrium.agentmind.ContextBundle;
import app.atrium.agentmind.KnowledgeHit;
import app.atrium.agentmind.MemoryHit;
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
 * Layout per 14 §6, complete as of M-KN1: "## What you have learned here"
 * (bundle.memories(), M-MEM1) and "## Reference material" (bundle.knowledge(),
 * M-KN1) both render now that neither list is structurally always empty.
 */
public final class PromptAssembler {

    private PromptAssembler() {}

    public static AssembledPrompt build(RoleDefinition roleDef, Task task,
                                        @Nullable String feedback, @Nullable ContextBundle bundle) {
        StringBuilder system = new StringBuilder(roleDef.getSystemPrompt());
        if (bundle != null && !bundle.skills().isEmpty()) {
            system.append("\n\n## Your skills\n").append(renderSkills(bundle.skills()));
        }
        if (bundle != null && !bundle.memories().isEmpty()) {
            system.append("\n\n## What you have learned here\n")
                    .append("_Learned context — verify if critical._\n")
                    .append(renderMemories(bundle.memories()));
        }
        if (roleDef.getOutputContract() != null && !roleDef.getOutputContract().isBlank()) {
            system.append("\n\n## Output contract\n").append(roleDef.getOutputContract());
        }

        StringBuilder user = new StringBuilder("## Your task\n").append(task.getTitle());
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            user.append('\n').append(task.getDescription());
        }
        if (bundle != null && !bundle.knowledge().isEmpty()) {
            user.append("\n\n## Reference material\n").append(renderKnowledge(bundle.knowledge()));
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

    private static String renderMemories(List<MemoryHit> memories) {
        StringBuilder sb = new StringBuilder();
        for (MemoryHit hit : memories) {
            sb.append("- (").append(hit.memory().kind()).append(") ")
                    .append(hit.memory().content()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String renderKnowledge(List<KnowledgeHit> knowledge) {
        StringBuilder sb = new StringBuilder();
        for (KnowledgeHit hit : knowledge) {
            sb.append("- (").append(hit.docTitle()).append(") ").append(hit.content()).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
