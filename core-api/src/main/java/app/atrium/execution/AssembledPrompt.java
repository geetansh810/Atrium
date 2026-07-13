package app.atrium.execution;

import app.atrium.execution.spi.LlmMessage;
import java.util.List;

/** What {@link PromptAssembler} produces — the caller adds provider/model/tools/maxTokens. */
public record AssembledPrompt(String systemPrompt, List<LlmMessage> messages) {}
