package app.atrium;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // fast lease sweeps so reclaim tests observe the REAL scheduled path in seconds
        properties = "atrium.lease.reclaim-ms=1000")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }
}
