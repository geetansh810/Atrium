package app.atrium.common;

/** Maps to 403 problem+json — the actor is authenticated but structurally not
 *  allowed to do this (e.g. a compliance-gated role's approval requires a
 *  named human, never an agent or system actor — M4.1). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
