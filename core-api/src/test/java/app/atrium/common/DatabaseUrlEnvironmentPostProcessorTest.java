package app.atrium.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plain unit tests — no Spring context needed, this runs before one exists. */
class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void convertsRenderStyleConnectionStringToJdbc() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.derive(
                "postgresql://atrium_user:s3cret@dpg-abc123-a.oregon-postgres.render.com:5432/atrium_db", null);

        assertThat(props.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-abc123-a.oregon-postgres.render.com:5432/atrium_db");
        assertThat(props.get("spring.flyway.url")).isEqualTo(props.get("spring.datasource.url"));
        // credentials in the URL are the admin ones — Flyway only, never the app datasource
        assertThat(props.get("spring.flyway.user")).isEqualTo("atrium_user");
        assertThat(props.get("spring.flyway.password")).isEqualTo("s3cret");
        assertThat(props).doesNotContainKeys("spring.datasource.username", "spring.datasource.password");
    }

    @Test
    void defaultsThePortWhenTheInternalUrlOmitsIt() {
        // Render's INTERNAL connection string has a bare host and no port.
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.derive(
                "postgresql://atrium_user:s3cret@dpg-abc123-a/atrium_db", null);

        assertThat(props.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-abc123-a:5432/atrium_db");
    }

    @Test
    void preservesQueryParametersSuchAsSslmode() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.derive(
                "postgresql://u:p@host.example.com:5432/db?sslmode=require", null);

        assertThat(props.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://host.example.com:5432/db?sslmode=require");
    }

    @Test
    void anExplicitDatabaseUserWinsOverCredentialsEmbeddedInTheUrl() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.derive(
                "postgresql://urluser:urlpass@host/db", "explicit_admin");

        assertThat(props).containsKey("spring.datasource.url");
        assertThat(props).doesNotContainKeys("spring.flyway.user", "spring.flyway.password");
    }

    @Test
    void percentEncodedCredentialsAreDecoded() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.derive(
                "postgresql://user%40corp:p%40ss%2Fword@host/db", null);

        assertThat(props.get("spring.flyway.user")).isEqualTo("user@corp");
        assertThat(props.get("spring.flyway.password")).isEqualTo("p@ss/word");
    }

    @Test
    void leavesAnAlreadyJdbcUrlCompletelyAlone() {
        // local docker-compose / the test suite pass a real jdbc: URL — must no-op
        assertThat(DatabaseUrlEnvironmentPostProcessor.derive(
                "jdbc:postgresql://localhost:5432/atrium", null)).isEmpty();
    }

    @Test
    void noOpsOnAbsentOrUnparseableValues() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.derive(null, null)).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.derive("", null)).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.derive("   ", null)).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.derive("postgresql:///nohost", null)).isEmpty();
    }
}
