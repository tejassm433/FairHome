package com.fairhome.testkit;

import tools.jackson.databind.JsonNode;

/**
 * One unit of work for {@link JsonCaseExecutor}: a name, the input the subject is given, and the
 * output the subject is required to produce.
 */
public record JsonCase(String name, String source, JsonNode input, JsonNode expected, CompareMode compareMode) {

    public enum CompareMode {
        /** Every field in {@code expected} must appear in the actual output and match. Extra actual fields are ignored. */
        SUBTREE,
        /** The actual output must be exactly the expected object, no extra fields. */
        STRICT
    }
}
