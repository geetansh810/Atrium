package app.atrium;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** M0.1 Done-when: migrations apply, health is UP, a company can be inserted and read back. */
class CompanySmokeTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate rest;

    @Test
    void bothMigrationsApplyOnPgvector() {
        Integer coreAndPlatform = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2')",
                Integer.class);
        assertThat(coreAndPlatform).isEqualTo(2);

        Integer vector = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        assertThat(vector).isEqualTo(1);
    }

    @Test
    void insertsAndReadsCompany() {
        UUID id = jdbc.queryForObject(
                "INSERT INTO companies (name, slug) VALUES ('Acme Robotics', 'acme-robotics') RETURNING id",
                UUID.class);
        assertThat(id).isNotNull();

        String name = jdbc.queryForObject(
                "SELECT name FROM companies WHERE id = ?", String.class, id);
        assertThat(name).isEqualTo("Acme Robotics");
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
