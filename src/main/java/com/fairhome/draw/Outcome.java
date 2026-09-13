package com.fairhome.draw;

public enum Outcome {
    ALLOTTED("Flat allotted"),
    WAITLISTED("On the waiting list"),
    NOT_SELECTED("Not selected"),
    EXCLUDED("Excluded from the draw");

    private final String label;

    Outcome(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
