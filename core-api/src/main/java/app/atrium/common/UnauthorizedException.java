package app.atrium.common;

/** Maps to 401 problem+json — bad login credentials or an invalid/expired/missing token. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
