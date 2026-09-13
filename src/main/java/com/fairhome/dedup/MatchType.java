package com.fairhome.dedup;

public enum MatchType {
    EXACT_NATIONAL_ID("Same national ID number"),
    NAME_AND_DOB("Very similar name with the same date of birth"),
    NAME_AND_PHONE("Very similar name with the same phone number"),
    EMAIL("Same email address");

    private final String label;

    MatchType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
