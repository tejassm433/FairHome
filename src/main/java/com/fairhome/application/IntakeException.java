package com.fairhome.application;

import java.util.List;
import java.util.Map;

/**
 * Thrown when an application cannot be accepted at all. Carries per-field messages so both the HTML
 * form and the JSON API can tell the applicant exactly what to fix.
 */
public class IntakeException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public IntakeException(String field, String message) {
        super(message);
        this.fieldErrors = Map.of(field, message);
    }

    public IntakeException(Map<String, String> fieldErrors) {
        super("Application rejected: " + String.join("; ", fieldErrors.values()));
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public List<String> getMessages() {
        return List.copyOf(fieldErrors.values());
    }
}
