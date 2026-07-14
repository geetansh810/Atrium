package app.atrium.agentmind;

import java.util.Set;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * A scoped semantic + recency recall request (14 §2 MemoryStore SPI). {@code kinds}
 * empty/null means "any kind". Scope-set for {@code agentId}/{@code roleKey} is
 * that agent's own {@code agent} rows &cup; its role's {@code role} rows &cup;
 * {@code company} rows — {@code task}-scope memories are never cross-task recalled.
 */
public record RecallQuery(UUID companyId, UUID agentId, @Nullable String roleKey,
                           String queryText, int k, @Nullable Set<String> kinds) {}
