package com.fairhome.rules;

import java.util.List;

public class RuleValidationException extends RuntimeException {

    private final List<String> problems;

    public RuleValidationException(List<String> problems) {
        super("Rule set is not valid: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
