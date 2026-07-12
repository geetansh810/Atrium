package app.atrium.common;

/** Maps to 409 problem+json — illegal state transitions, lost claim races, duplicate keys. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
