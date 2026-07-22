package app.atrium.common;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Normalizes a libpq-style {@code postgresql://user:pass@host:port/db} DATABASE_URL
 * into the {@code jdbc:postgresql://...} form Spring/Flyway require.
 *
 * <p>Managed hosts (Render, Heroku, Fly) hand out connection strings in libpq form —
 * Spring's {@code spring.datasource.url} only accepts {@code jdbc:}, and a Blueprint
 * env var can't string-compose one. Rather than making the deploy depend on
 * hand-entered host/port values, this converts the connection string at startup.
 *
 * <p>No-ops unless DATABASE_URL is actually libpq-style, so local docker-compose
 * (which passes a real {@code jdbc:} URL) and the test suite are unaffected.
 *
 * <p>Only the URL is derived here. The app's own datasource credentials stay
 * {@code ATRIUM_APP_DB_USER}/{@code ATRIUM_APP_DB_PASSWORD} (the least-privilege
 * atrium_app role RLS depends on — see V10). Credentials embedded in the URL are
 * the admin ones, so they're applied to Flyway only, and only when DATABASE_USER
 * wasn't supplied explicitly.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "normalizedDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        Map<String, Object> derived = derive(raw, environment.getProperty("DATABASE_USER"));
        if (!derived.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, derived));
        }
    }

    /**
     * Visible for testing. Returns the properties to contribute, or an empty map when
     * {@code raw} isn't a libpq URL (already jdbc:, blank, or absent).
     */
    static Map<String, Object> derive(String raw, String explicitUser) {
        Map<String, Object> props = new HashMap<>();
        if (raw == null || raw.isBlank()) return props;
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) return props;

        URI uri = URI.create(raw);
        String host = uri.getHost();
        if (host == null || host.isBlank()) return props; // unparseable — leave config alone

        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
        String jdbc = "jdbc:postgresql://" + host + ":" + port + "/" + database + query;

        props.put("spring.datasource.url", jdbc);
        props.put("spring.flyway.url", jdbc);

        // Flyway needs the admin role (DDL, CREATE EXTENSION, CREATE ROLE). Prefer an
        // explicitly configured DATABASE_USER; otherwise fall back to the URL's own
        // credentials so a bare connection string is enough to boot.
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank() && (explicitUser == null || explicitUser.isBlank())) {
            String[] parts = userInfo.split(":", 2);
            props.put("spring.flyway.user", decode(parts[0]));
            if (parts.length > 1) props.put("spring.flyway.password", decode(parts[1]));
        }
        return props;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
