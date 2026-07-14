package app.atrium.agentmind.api;

import app.atrium.agentmind.SkillService.AgentSkillView;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentMindDtos {

    private AgentMindDtos() {}

    /** 16 §1 — memoryCounts/knowledgeDocs are zeroed until M-MEM1/M-KN1. */
    public record AgentMindResponse(List<SkillEntry> skills, MemoryCounts memoryCounts,
                                    List<Object> knowledgeDocs) {

        public static AgentMindResponse from(List<AgentSkillView> views) {
            return new AgentMindResponse(views.stream().map(SkillEntry::from).toList(),
                    MemoryCounts.zero(), List.of());
        }
    }

    public record SkillEntry(UUID id, String key, String name, String kind, String source, short proficiency) {

        static SkillEntry from(AgentSkillView view) {
            return new SkillEntry(view.skill().getId(), view.skill().getKey(), view.skill().getName(),
                    view.skill().getKind(), view.attachment().getSource(), view.attachment().getProficiency());
        }
    }

    public record MemoryCounts(Map<String, Integer> byScope, Map<String, Integer> byStatus) {

        static MemoryCounts zero() { return new MemoryCounts(Map.of(), Map.of()); }
    }
}
