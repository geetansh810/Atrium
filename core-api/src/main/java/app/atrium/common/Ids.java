package app.atrium.common;

import java.util.UUID;

/** UUID helpers — parse untrusted path/query ids into 400s instead of 500s. */
public final class Ids {

    private Ids() {}

    public static UUID parse(String raw, String what) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Malformed " + what + " id: " + raw);
        }
    }
}
