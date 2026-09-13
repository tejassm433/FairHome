package com.fairhome.application;

public enum Channel {
    ONLINE("Online form"),
    OFFLINE("Paper form, keyed in");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
