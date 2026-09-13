package com.fairhome.dedup;

public enum DuplicateResolution {
    OPEN("Awaiting review"),
    CONFIRMED_DUPLICATE("Confirmed duplicate"),
    NOT_A_DUPLICATE("Different people");

    private final String label;

    DuplicateResolution(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
