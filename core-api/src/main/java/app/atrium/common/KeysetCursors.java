package app.atrium.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Opaque keyset cursors for the pagination envelope (04 rule 3). Position =
 * {@code (created_at, id)} of the last row served — stable under inserts,
 * unlike offsets. Malformed cursors are a client error, not a 500.
 */
public final class KeysetCursors {

    public record Position(Instant createdAt, UUID id) {}

    private KeysetCursors() {}

    public static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.indexOf('|');
            return new Position(Instant.parse(raw.substring(0, split)),
                    UUID.fromString(raw.substring(split + 1)));
        } catch (RuntimeException e) {
            throw new FieldValidationException(Map.of("cursor", "malformed pagination cursor"));
        }
    }
}
