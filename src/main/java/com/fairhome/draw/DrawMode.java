package com.fairhome.draw;

public enum DrawMode {
    DRY_RUN("Dry run"),
    FINAL("Final draw");

    private final String label;

    DrawMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
