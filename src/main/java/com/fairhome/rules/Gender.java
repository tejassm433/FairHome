package com.fairhome.rules;

public enum Gender {
    FEMALE("Female"),
    MALE("Male"),
    OTHER("Other / prefer not to say");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
