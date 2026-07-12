package app.atrium.common;

/** Maps to 404 problem+json. Always thrown company-scoped — existence of other tenants' rows must be indistinguishable from absence. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " " + id + " not found");
    }
}
