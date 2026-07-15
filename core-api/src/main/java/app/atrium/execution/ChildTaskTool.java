package app.atrium.execution;

import app.atrium.execution.spi.LlmToolDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The one built-in tool this milestone introduces (M2.2, 12 §6): lets a role
 * whose {@code role_definitions.allowed_tools} lists {@link #NAME} split its
 * own task into child tasks for other skills. Offered to the model only when
 * that data says so — routing/execution never branch on a role key (roles
 * are data, CLAUDE.md's hard rule), they branch on this JSON array instead.
 */
public final class ChildTaskTool {

    public static final String NAME = "create_child_tasks";

    public static final LlmToolDef DEF;

    static {
        String schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "summary": {
                      "type": "string",
                      "description": "Short note on the decomposition, used as this task's own completion artifact."
                    },
                    "children": {
                      "type": "array",
                      "description": "One entry per child task to spawn.",
                      "items": {
                        "type": "object",
                        "properties": {
                          "title": {"type": "string"},
                          "description": {"type": "string"},
                          "requiredSkill": {"type": "string"},
                          "priority": {"type": "integer"}
                        },
                        "required": ["title", "requiredSkill"]
                      }
                    }
                  },
                  "required": ["children"]
                }
                """;
        try {
            JsonNode schema = new ObjectMapper().readTree(schemaJson);
            DEF = new LlmToolDef(NAME,
                    "Split this task into child tasks assigned to other skills/roles. "
                            + "Use this to delegate distinct pieces of work instead of doing them yourself.",
                    schema);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ChildTaskTool() {}
}
