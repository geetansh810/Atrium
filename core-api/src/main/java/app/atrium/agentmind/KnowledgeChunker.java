package app.atrium.agentmind;

import java.util.ArrayList;
import java.util.List;

/**
 * 14 §3 chunking (~800 tokens / 100 overlap) — reuses {@link
 * SkillContextAssembler}'s documented chars/4 token-estimation heuristic
 * rather than introducing a real tokenizer dependency just for this.
 */
final class KnowledgeChunker {

    private static final int CHARS_PER_TOKEN = 4;
    static final int CHUNK_CHARS = 800 * CHARS_PER_TOKEN;
    static final int OVERLAP_CHARS = 100 * CHARS_PER_TOKEN;
    private static final int STEP_CHARS = CHUNK_CHARS - OVERLAP_CHARS;

    private KnowledgeChunker() {}

    static List<String> chunk(String content) {
        String trimmed = content.strip();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < trimmed.length()) {
            int end = Math.min(start + CHUNK_CHARS, trimmed.length());
            String piece = trimmed.substring(start, end).strip();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= trimmed.length()) {
                break;
            }
            start += STEP_CHARS;
        }
        return chunks;
    }
}
