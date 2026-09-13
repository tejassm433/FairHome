package com.fairhome.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The whole published rule book, as a value object parsed from JSON.
 *
 * <p>This file <em>is</em> the scheme's published policy: it is versioned, hashed and reproduced
 * verbatim on the public rules page, so a journalist or a court reads exactly what the engine ran.
 * The engine never hard-codes a quota, an income band or a seed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleSetDocument(
        String schemeName,
        String rulesVersionLabel,
        String effectiveFrom,
        int totalFlats,
        Draw draw,
        Eligibility eligibility,
        List<Category> categories,
        List<Reservation> reservations,
        LocalResident localResident,
        Spill spill,
        Waitlist waitlist,
        DuplicateDetection duplicateDetection
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Draw(
            RankStrategy rankStrategy,
            String seed,
            String seedPublishedOn,
            String notes
    ) {
    }

    public enum RankStrategy {
        /**
         * Rank = ascending SHA-256(seed + ":" + applicationNumber). Deterministic, independently
         * recomputable with any SHA-256 tool, and immune to JVM/RNG implementation drift.
         */
        SEEDED_LOTTERY
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Eligibility(
            int minAgeYears,
            int maxAgeYears,
            boolean requireIncomeWithinBands,
            boolean allowFinalDrawWithOpenDuplicateReviews
    ) {
    }

    /**
     * A vertical income category. Applications are placed into exactly one category, derived from
     * declared annual income at draw time rather than self-selected.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String code,
            String label,
            BigDecimal quotaPercent,
            BigDecimal incomeMin,
            BigDecimal incomeMax
    ) {
        public boolean matchesIncome(BigDecimal income) {
            if (income == null) {
                return false;
            }
            if (incomeMin != null && income.compareTo(incomeMin) < 0) {
                return false;
            }
            return incomeMax == null || income.compareTo(incomeMax) <= 0;
        }

        public String bandDescription() {
            if (incomeMax == null) {
                return "above " + incomeMin;
            }
            return incomeMin + " to " + incomeMax;
        }
    }

    /**
     * A reserved sub-quota carved out of every category's seat count. {@code predicate} names a
     * predicate implemented in {@link ApplicantPredicates}; config composes them, code supplies
     * them, so an operator can re-weight or drop a quota without a redeploy but cannot invent an
     * unauditable eligibility test.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reservation(
            String code,
            String label,
            String predicate,
            BigDecimal percentOfCategory
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalResident(int minYearsInArea, String areaLabel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Spill(
            UnfilledReservedSeats unfilledReservedSeats,
            UnfilledCategorySeats unfilledCategorySeats
    ) {
    }

    public enum UnfilledReservedSeats {
        /** Reserved seats with no eligible claimant are released to the category's open pool. */
        SPILL_TO_OPEN,
        /** Reserved seats are left vacant and reported as such. */
        HOLD_VACANT
    }

    public enum UnfilledCategorySeats {
        /** Seats a category cannot fill go to the best-ranked unallocated applicants scheme-wide. */
        POOL_BY_RANK,
        /** Seats a category cannot fill are left vacant and reported as such. */
        HOLD_VACANT
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Waitlist(BigDecimal percentOfCategorySeats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DuplicateDetection(
            boolean holdExactNationalIdForReview,
            double nameSimilarityThreshold,
            double holdScoreThreshold,
            boolean matchOnPhone,
            boolean matchOnEmail
    ) {
    }

    public Category categoryFor(BigDecimal income) {
        if (categories == null) {
            return null;
        }
        for (Category c : categories) {
            if (c.matchesIncome(income)) {
                return c;
            }
        }
        return null;
    }

    public Category categoryByCode(String code) {
        if (categories == null || code == null) {
            return null;
        }
        return categories.stream().filter(c -> code.equals(c.code())).findFirst().orElse(null);
    }

    public Reservation reservationByCode(String code) {
        if (reservations == null || code == null) {
            return null;
        }
        return reservations.stream().filter(r -> code.equals(r.code())).findFirst().orElse(null);
    }
}
