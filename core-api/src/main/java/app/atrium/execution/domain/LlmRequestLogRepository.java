package app.atrium.execution.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Writes go through {@code LlmRequestLogService.record} (REQUIRES_NEW,
 * best-effort); the keyset-paginated read is a native query in the service,
 * matching the {@code TaskService.list} convention.
 */
public interface LlmRequestLogRepository extends JpaRepository<LlmRequestLog, UUID> {
}
