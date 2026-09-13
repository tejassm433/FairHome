package com.fairhome.application;

public enum ApplicationStatus {

    /** Accepted, no duplicate suspicion, will take part in the draw. */
    SUBMITTED("In the draw", "Your application is accepted and will take part in the draw.", true),

    /**
     * A possible duplicate was detected at submission. The application is parked, not thrown away:
     * paper entries contain typos and we will not silently drop a real applicant.
     */
    PENDING_DUPLICATE_REVIEW("Held for duplicate review",
            "We found another application that may belong to the same person. "
                    + "An officer is reviewing both before the draw.", false),

    /** Review concluded this is the same person applying again; the earlier claim survives. */
    REJECTED_DUPLICATE("Rejected as duplicate",
            "This application was found to duplicate an earlier application by the same person. "
                    + "The earlier application remains in the draw.", false),

    /** Fails a published eligibility rule (age band, income outside every band). */
    INELIGIBLE("Not eligible", "This application does not meet the published eligibility rules.", false),

    /** Applicant asked to be taken out. */
    WITHDRAWN("Withdrawn", "This application was withdrawn.", false);

    private final String label;
    private final String applicantExplanation;
    private final boolean eligibleForDraw;

    ApplicationStatus(String label, String applicantExplanation, boolean eligibleForDraw) {
        this.label = label;
        this.applicantExplanation = applicantExplanation;
        this.eligibleForDraw = eligibleForDraw;
    }

    public String getLabel() {
        return label;
    }

    public String getApplicantExplanation() {
        return applicantExplanation;
    }

    public boolean isEligibleForDraw() {
        return eligibleForDraw;
    }
}
