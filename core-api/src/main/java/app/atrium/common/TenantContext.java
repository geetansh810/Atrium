package app.atrium.common;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.MDC;

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
    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();
    private static final String MDC_COMPANY_ID = "companyId";
    private static final String MDC_USER_ID = "userId";

    private TenantContext() {}

    /**
     * The one place tenant binding happens (filter-bound HTTP request or a
     * background {@link #runAsSystem}/{@link #callAsSystem} call) — also the
     * one place to bind {@code companyId}/{@code userId} into MDC (M3.5, 10
     * §6) so every structured JSON log line emitted while a tenant is bound
     * carries them, with zero changes needed at any of TenantContext's callers.
     */
    static void set(Tenant tenant) {
        CURRENT.set(tenant);
        if (tenant.companyId() != null) {
            MDC.put(MDC_COMPANY_ID, tenant.companyId().toString());
        }
        if (tenant.userId() != null) {
            MDC.put(MDC_USER_ID, tenant.userId().toString());
        }
    }

    static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_COMPANY_ID);
        MDC.remove(MDC_USER_ID);
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

    /**
     * Binds a system-acting tenant (no human user) for the duration of
     * {@code action} — used by non-HTTP callers that already know exactly
     * which company they're operating on: the Worker API gateway (resolves
     * the agent's company, never a human token) and the per-agent runtime
     * poll loops ({@code LlmLoopRuntime}/{@code EchoRuntime}). This is what
     * lets M3.2's Postgres RLS (08 §Security rule 6) see a real
     * {@code app.company_id} on these paths instead of failing closed.
     */
    public static void runAsSystem(UUID companyId, Runnable action) {
        set(new Tenant(companyId, null, null));
        try {
            action.run();
        } finally {
            clear();
        }
    }

    /** {@link #runAsSystem(UUID, Runnable)}, for callers that need a return value. */
    public static <T> T callAsSystem(UUID companyId, Supplier<T> action) {
        set(new Tenant(companyId, null, null));
        try {
            return action.get();
        } finally {
            clear();
        }
    }

    /**
     * Marks the current thread as RLS-bypassed for the duration of
     * {@code action} — reserved for the small, already-documented set of
     * components that are genuinely cross-tenant by design: the outbox/lease/
     * retention/archive/reconciliation background jobs (each sweeps every
     * company's rows in one query) and the pre-auth signup/login path (no
     * tenant is known yet, or the lookup is cross-company by email). Every
     * other caller must go through {@link #runAsSystem} or the JWT-bound
     * request path instead.
     */
    public static void runWithBypass(Runnable action) {
        BYPASS.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            BYPASS.remove();
        }
    }

    /** {@link #runWithBypass(Runnable)}, for callers that need a return value. */
    public static <T> T callWithBypass(Supplier<T> action) {
        BYPASS.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            BYPASS.remove();
        }
    }

    /** Read by {@code TenantAwareJpaTransactionManager} to set {@code app.bypass_rls}. */
    public static boolean isBypass() {
        return Boolean.TRUE.equals(BYPASS.get());
    }
}
