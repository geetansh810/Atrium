package app.atrium.common;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-request tenant identity, populated by {@link TenantContextFilter} from
 * the {@code Authorization: Bearer} JWT (M3.1 — replaced the Phase 0–2
 * {@code X-Company-Id}/{@code X-User-Id} dev headers entirely).
 *
 * <p>Every repository call must be scoped by {@link #requireCompanyId()} —
 * cross-tenant access must be impossible.
 */
public final class TenantContext {

    public record Tenant(UUID companyId, UUID userId, String role) {}

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
            throw new IllegalStateException("No tenant bound to this request — missing/invalid Authorization");
        }
        return tenant.companyId();
    }

    /** Acting user — every authenticated request carries one since M3.1 (the JWT's own subject). */
    public static Optional<UUID> userId() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? Optional.empty() : Optional.ofNullable(tenant.userId());
    }

    /** {@code admin}/{@code member} — carried on the token; nothing gates on it yet. */
    public static Optional<String> role() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? Optional.empty() : Optional.ofNullable(tenant.role());
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }
}
