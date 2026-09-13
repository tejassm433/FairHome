package com.fairhome.testkit;

import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares an expected JSON tree against the actual output of a case.
 *
 * <p>Default (subtree) mode: every field named in {@code expected} must exist in {@code actual} and
 * match. Fields the test did not mention are ignored, so a case can pin the two or three facts it
 * cares about without freezing the entire response.
 *
 * <p>A few operators are allowed inside expected values, so a case can say "this text must appear"
 * without copying a whole sentence:
 * <ul>
 *   <li>{@code {"$contains": "add up to exactly 100"}} against a string or an array of strings</li>
 *   <li>{@code {"$gte": 0.88}} / {@code {"$lte": 1.0}} against a number</li>
 * </ul>
 */
public final class JsonTreeAssert {

    private JsonTreeAssert() {
    }

    public static void assertMatches(JsonCase testCase, JsonNode actual) {
        List<String> problems = new ArrayList<>();
        compare("$", testCase.expected(), actual, testCase.compareMode(), problems);
        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Case \"").append(testCase.name()).append("\" from ")
                    .append(testCase.source()).append(" did not match.\n");
            for (String problem : problems) {
                message.append("  - ").append(problem).append('\n');
            }
            message.append("Actual output:\n").append(actual.toPrettyString());
            Assertions.fail(message.toString());
        }
    }

    private static void compare(String path, JsonNode expected, JsonNode actual,
                                JsonCase.CompareMode mode, List<String> problems) {
        if (expected == null || expected.isNull()) {
            if (actual != null && !actual.isNull()) {
                problems.add(path + ": expected null, got " + summarise(actual));
            }
            return;
        }
        if (isOperator(expected)) {
            applyOperator(path, expected, actual, problems);
            return;
        }
        if (actual == null || actual.isNull()) {
            problems.add(path + ": expected " + summarise(expected) + ", got null");
            return;
        }
        if (expected.isObject()) {
            if (!actual.isObject()) {
                problems.add(path + ": expected an object, got " + summarise(actual));
                return;
            }
            expected.propertyNames().forEach(field ->
                    compare(path + "." + field, expected.get(field), actual.get(field), mode, problems));
            if (mode == JsonCase.CompareMode.STRICT) {
                actual.propertyNames().forEach(field -> {
                    if (expected.get(field) == null) {
                        problems.add(path + "." + field + ": unexpected field " + summarise(actual.get(field)));
                    }
                });
            }
            return;
        }
        if (expected.isArray()) {
            if (!actual.isArray()) {
                problems.add(path + ": expected an array, got " + summarise(actual));
                return;
            }
            if (expected.size() != actual.size()) {
                problems.add(path + ": expected array length " + expected.size() + ", got " + actual.size());
            }
            int n = Math.min(expected.size(), actual.size());
            for (int i = 0; i < n; i++) {
                compare(path + "[" + i + "]", expected.get(i), actual.get(i), mode, problems);
            }
            return;
        }
        if (expected.isNumber() && actual.isNumber()) {
            if (expected.isFloatingPointNumber() || actual.isFloatingPointNumber()) {
                double e = expected.asDouble();
                double a = actual.asDouble();
                if (Math.abs(e - a) > 1e-6) {
                    problems.add(path + ": expected " + e + ", got " + a);
                }
            } else if (expected.asLong() != actual.asLong()) {
                problems.add(path + ": expected " + expected.asLong() + ", got " + actual.asLong());
            }
            return;
        }
        if (expected.isBoolean() && actual.isBoolean()) {
            if (expected.asBoolean() != actual.asBoolean()) {
                problems.add(path + ": expected " + expected.asBoolean() + ", got " + actual.asBoolean());
            }
            return;
        }
        if (!expected.asString().equals(actual.asString())) {
            problems.add(path + ": expected " + quote(expected.asString()) + ", got " + quote(actual.asString()));
        }
    }

    private static boolean isOperator(JsonNode expected) {
        if (!expected.isObject() || expected.size() != 1) {
            return false;
        }
        String name = expected.propertyNames().iterator().next();
        return name.startsWith("$");
    }

    private static void applyOperator(String path, JsonNode expected, JsonNode actual, List<String> problems) {
        String operator = expected.propertyNames().iterator().next();
        JsonNode argument = expected.get(operator);
        if (actual == null || actual.isNull()) {
            problems.add(path + ": expected " + operator + " " + summarise(argument) + ", got null");
            return;
        }
        switch (operator) {
            case "$contains" -> {
                String needle = argument.asString().toLowerCase(Locale.ROOT);
                if (actual.isArray()) {
                    boolean found = false;
                    for (JsonNode item : actual) {
                        if (item.asString().toLowerCase(Locale.ROOT).contains(needle)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        problems.add(path + ": no element contained " + quote(argument.asString())
                                + "; array was " + summarise(actual));
                    }
                } else if (!actual.asString().toLowerCase(Locale.ROOT).contains(needle)) {
                    problems.add(path + ": expected to contain " + quote(argument.asString())
                            + ", got " + quote(actual.asString()));
                }
            }
            case "$gte" -> {
                if (!actual.isNumber() || actual.asDouble() < argument.asDouble()) {
                    problems.add(path + ": expected >= " + argument.asDouble() + ", got " + summarise(actual));
                }
            }
            case "$lte" -> {
                if (!actual.isNumber() || actual.asDouble() > argument.asDouble()) {
                    problems.add(path + ": expected <= " + argument.asDouble() + ", got " + summarise(actual));
                }
            }
            default -> problems.add(path + ": unknown comparison operator " + operator);
        }
    }

    private static String summarise(JsonNode node) {
        if (node == null) {
            return "null";
        }
        String text = node.toString();
        return text.length() > 180 ? text.substring(0, 177) + "..." : text;
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
