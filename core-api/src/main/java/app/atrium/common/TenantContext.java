package app.atrium.common;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-request tenant identity, populated by {@link TenantContextFilter} from the
 * dev auth headers (Phase 0–2: {@code X-Company-Id} / {@code X-User-Id}; Phase 3
 * swaps the filter for JWT claims without touching callers).
 *
 * <p>Every repository call must be scoped by {@link #requireCompanyId()} —
 * cross-tenant access must be impossible.
 */
public final class TenantContext {

    public record Tenant(UUID companyId, UUID userId) {}

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    static void set(Tenant tenant) {
        CURRENT.set(tenant);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Company id of the current request; throws if the request carried no tenant. */
    public static UUID requireCompanyId() {
        Tenant tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant bound to this request — missing X-Company-Id");
        }
        return tenant.companyId();
    }

    /** Acting user, when the request carried X-User-Id. */
    public static Optional<UUID> userId() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? Optional.empty() : Optional.ofNullable(tenant.userId());
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }
}
