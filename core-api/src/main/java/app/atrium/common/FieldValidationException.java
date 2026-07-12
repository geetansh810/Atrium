package app.atrium.common;

import java.util.Map;

/** Maps to 400 problem+json with a fieldErrors map — for validation done in services, not bean validation. */
public class FieldValidationException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    public FieldValidationException(Map<String, String> fieldErrors) {
        super("Validation failed: " + fieldErrors);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
