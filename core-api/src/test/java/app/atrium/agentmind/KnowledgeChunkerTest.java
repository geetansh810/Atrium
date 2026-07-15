package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 14 §3 chunking (~800 tokens / 100 overlap, chars/4 heuristic — 3200/400 chars). */
class KnowledgeChunkerTest {

    @Test
    void shortContentIsOneChunk() {
        List<String> chunks = KnowledgeChunker.chunk("Our brand voice is friendly and concise.");
        assertThat(chunks).containsExactly("Our brand voice is friendly and concise.");
    }

    @Test
    void blankContentProducesNoChunks() {
        assertThat(KnowledgeChunker.chunk("   \n  ")).isEmpty();
    }

    @Test
    void longContentSplitsWithOverlap() {
        // 3200 chars/chunk, 2800-char step -> a 7000-char doc needs 3 chunks
        // (0-3200, 2800-6000, 5600-7000) and consecutive chunks share the 400-char overlap.
        String content = "x".repeat(7000);
        List<String> chunks = KnowledgeChunker.chunk(content);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(3200);
        assertThat(chunks.get(1)).hasSize(3200);
        assertThat(chunks.get(2)).hasSize(1400);
        assertThat(chunks.stream().mapToInt(String::length).sum()).isEqualTo(7000 + 2 * 400);
    }

    @Test
    void contentIsStrippedPerChunk() {
        List<String> chunks = KnowledgeChunker.chunk("  leading and trailing whitespace  ");
        assertThat(chunks).containsExactly("leading and trailing whitespace");
    }
}
