package app.atrium.registry.runtime;

import app.atrium.common.FieldValidationException;
import java.util.Map;

/** Invalid runtime_config — surfaces as 400 problem+json with fieldErrors (13 §3.1). */
public class ConfigException extends FieldValidationException {

    public ConfigException(Map<String, String> fieldErrors) {
        super(fieldErrors);
    }
}
