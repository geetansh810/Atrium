package app.atrium;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for integration tests. Containers are singletons started once
 * per JVM and reused by every subclass (deliberately not @Container-managed,
 * which would stop them after the first test class).
 *
 * <p>Postgres MUST be the pgvector image — V2__agent_platform.sql runs
 * {@code CREATE EXTENSION vector} and fails on stock postgres.
 *
 * <p>{@code withReuse(true)} + {@code testcontainers.reuse.enable=true} in
 * {@code ~/.testcontainers.properties} keeps both containers alive BETWEEN
 * mvn runs — each run skips container startup and re-applies only new Flyway
 * migrations. Consequences: (a) data persists across runs, so tests must use
 * random slugs/ids, never fixed fixtures; (b) if a reused DB ever gets into a
 * bad state (e.g. an edited migration — forbidden anyway, or a stale
 * {@code atrium_app} role from before M3.2), reset with
 * {@code docker ps | grep testcontainers} + {@code docker rm -f <ids>}.
 * Without the opt-in property the flag is silently ignored (CI behavior).
 *
 * <p>M3.2: Postgres RLS (08 §Security rule 6) never applies to a Testcontainers
 * image's bootstrap role (its {@code SUPERUSER} attribute can't even be
 * stripped by hand — Postgres refuses "the bootstrap user must have the
 * SUPERUSER attribute" — confirmed against this exact image), so
 * {@code @ServiceConnection} is deliberately NOT used for Postgres anymore:
 * it would bind the app's own runtime datasource to that same bootstrap role,
 * making every RLS policy a silent no-op in every test. Flyway (which needs
 * real DDL privileges, including creating the {@code atrium_app} role V10's
 * migration grants everything to) keeps the bootstrap credentials; the app's
 * own {@code spring.datasource} connects as {@code atrium_app} instead — the
 * same non-superuser role V10 creates for {@code docker-compose}'s runtime
 * connection (08 §Security rule 6, {@code application.yml}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // fast lease sweeps so reclaim tests observe the REAL scheduled path in seconds
        properties = "atrium.lease.reclaim-ms=1000")
// activates application-test.yml (merges over application.yml) — see its
// header comment: keeps every context's OutboxRelay near-dormant by default
// so it can't race a DIFFERENT context's mocked-relay assertions.
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withReuse(true);

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379)
            .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Flyway migrates as the container's bootstrap (superuser) role; the app's
     * own datasource connects as {@code atrium_app} — the non-superuser role
     * {@code V10__tenant_rls.sql} creates and grants everything to. Both point
     * at the same database, so this is purely a privilege split, not a second
     * database.
     */
    @DynamicPropertySource
    static void tenantAwareDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "atrium_app");
        registry.add("spring.datasource.password", () -> "atrium_app");
    }

    /**
     * A raw admin-role connection for test fixture setup/verification — NOT a
     * Spring bean, deliberately outside the app's own {@code atrium_app}-backed
     * datasource, since test scaffolding legitimately needs cross-tenant SQL
     * (seeding companies/users/tasks by hand, asserting on rows the test itself
     * inserted) the way a real ops/admin connection would, unlike the app's own
     * request-handling code (M3.2, 08 §Security rule 6). Tests that previously
     * did {@code @Autowired JdbcTemplate jdbc} for this purpose now call this
     * instead; anything that exercises the app's actual RLS enforcement goes
     * through the real HTTP API ({@code TestRestTemplate}), never this.
     */
    protected static JdbcTemplate adminJdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }
}
