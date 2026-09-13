package com.fairhome.draw;

import com.fairhome.application.Application;
import com.fairhome.rules.ApplicantPredicates;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.support.Hashes;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The allocation engine: rules plus applications in, decisions plus reasons out.
 *
 * <p>Deliberately a pure function. It touches no repository, no clock beyond the draw date it is
 * handed, and no random number generator, so the same inputs always produce byte-identical output.
 * That is what lets a court, a newspaper or an applicant re-run the draw and get the same list, and
 * it is why the whole thing is unit-testable without a database.
 *
 * <p>The order of operations is fixed and published: derive the lottery order, split seats between
 * income categories, carve reserved quotas out of each category, fill reserved pools then the open
 * pool, redistribute seats nobody claimed, then build waiting lists.
 */
@Component
public class AllocationEngine {

    private static final String OPEN_POOL = "OPEN";
    private static final String SPILL_POOL = "SPILL";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public Result run(RuleSetDocument rules, List<Application> allApplications, LocalDate drawDate) {
        List<String> workings = new ArrayList<>();
        workings.add("Scheme: " + rules.schemeName());
        workings.add("Rule book: " + rules.rulesVersionLabel());
        workings.add("Flats available: " + rules.totalFlats());
        workings.add("Lottery order: " + rules.draw().rankStrategy()
                + " using the published seed \"" + rules.draw().seed() + "\"");
        workings.add("Applications on file: " + allApplications.size());
        workings.add("");

        List<Row> rows = new ArrayList<>();
        List<Row> eligible = new ArrayList<>();

        for (Application application : allApplications) {
            Row row = new Row(application);
            rows.add(row);
            if (!screen(row, rules, drawDate)) {
                continue;
            }
            eligible.add(row);
        }

        workings.add("Step 1 - eligibility screening");
        workings.add("  " + eligible.size() + " applications entered the draw, "
                + (rows.size() - eligible.size()) + " were excluded before it.");
        Map<String, Integer> exclusionTally = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.outcome == Outcome.EXCLUDED) {
                exclusionTally.merge(row.reasonCode, 1, Integer::sum);
            }
        }
        exclusionTally.forEach((code, count) -> workings.add("  " + count + " x " + code));
        workings.add("");

        assignLotteryOrder(rules, eligible);
        workings.add("Step 2 - lottery order");
        workings.add("  Every application's position is the ascending SHA-256 of \""
                + rules.draw().seed() + ":<application number>\".");
        workings.add("  No random number generator is involved, so the order can be recomputed by "
                + "anyone holding the seed and the list of application numbers.");
        workings.add("");

        Map<String, List<Row>> byCategory = groupByCategory(rules, eligible);

        Map<String, Integer> categorySeats = divideSeats(rules, workings);

        List<Row> allotted = new ArrayList<>();
        Map<String, Integer> vacantByCategory = new LinkedHashMap<>();

        workings.add("Step 4 - filling each category");
        for (RuleSetDocument.Category category : rules.categories()) {
            List<Row> pool = byCategory.getOrDefault(category.code(), List.of());
            int seats = categorySeats.getOrDefault(category.code(), 0);
            int filled = fillCategory(rules, category, pool, seats, allotted, workings);
            int vacant = seats - filled;
            if (vacant > 0) {
                vacantByCategory.put(category.code(), vacant);
            }
        }
        workings.add("");

        int vacantAfterSpill = spillLeftoverSeats(rules, eligible, vacantByCategory, allotted, workings);

        assignSeatNumbers(rules, allotted);

        int waitlisted = buildWaitlists(rules, byCategory, categorySeats, workings);

        finishNotSelected(byCategory, workings);

        String resultsHash = hashResults(rows);

        int allottedCount = (int) rows.stream().filter(r -> r.outcome == Outcome.ALLOTTED).count();
        int notSelected = (int) rows.stream().filter(r -> r.outcome == Outcome.NOT_SELECTED).count();
        int excluded = (int) rows.stream().filter(r -> r.outcome == Outcome.EXCLUDED).count();

        workings.add("Step 7 - result");
        workings.add("  " + allottedCount + " flats allotted, " + waitlisted + " applicants waitlisted, "
                + notSelected + " not selected, " + excluded + " excluded before the draw.");
        if (vacantAfterSpill > 0) {
            workings.add("  " + vacantAfterSpill + " flats remain unallotted under the published spill rules.");
        }
        workings.add("  Results hash (SHA-256 over the ordered decision list): " + resultsHash);

        List<Decision> decisions = rows.stream().map(Row::toDecision).toList();
        return new Result(decisions, String.join("\n", workings), resultsHash, allottedCount, waitlisted,
                notSelected, excluded, vacantAfterSpill, eligible.size());
    }

    /**
     * Applies the published eligibility rules. An application that fails here is recorded as excluded
     * with the reason, never dropped silently.
     */
    private boolean screen(Row row, RuleSetDocument rules, LocalDate drawDate) {
        Application application = row.application;

        if (!application.getStatus().isEligibleForDraw()) {
            row.exclude("STATUS_" + application.getStatus().name(),
                    application.getStatus().getApplicantExplanation());
            row.trace.add("Excluded before the draw because the application status is "
                    + application.getStatus().getLabel() + ".");
            if (application.getStatusNote() != null && !application.getStatusNote().isBlank()) {
                row.trace.add("Note on file: " + application.getStatusNote());
            }
            return false;
        }

        int age = application.ageOn(drawDate);
        if (age < rules.eligibility().minAgeYears() || age > rules.eligibility().maxAgeYears()) {
            row.exclude("ELIGIBILITY_AGE", "The scheme is open to applicants aged "
                    + rules.eligibility().minAgeYears() + " to " + rules.eligibility().maxAgeYears()
                    + ". This application was " + age + " on the draw date.");
            row.trace.add("Excluded on age: " + age + " years on " + DATE.format(drawDate)
                    + ", outside the published band of " + rules.eligibility().minAgeYears() + " to "
                    + rules.eligibility().maxAgeYears() + ".");
            return false;
        }

        RuleSetDocument.Category category = rules.categoryFor(application.getAnnualIncome());
        if (category == null) {
            row.exclude("ELIGIBILITY_INCOME", "The declared annual income of "
                    + money(application.getAnnualIncome())
                    + " does not fall inside any published income category.");
            row.trace.add("Excluded on income: " + money(application.getAnnualIncome())
                    + " matches none of the published bands.");
            return false;
        }

        row.category = category;
        row.trace.add("Accepted on " + DATE.format(
                application.getSubmittedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                + " through the " + application.getChannel().getLabel().toLowerCase(Locale.ENGLISH) + ".");
        row.trace.add("Declared annual income " + money(application.getAnnualIncome())
                + " falls in the " + category.code() + " band (" + category.label() + ", "
                + money(category.incomeMin()) + (category.incomeMax() == null ? " and above"
                : " to " + money(category.incomeMax())) + ").");
        return true;
    }

    /**
     * Lottery position is the ascending hex SHA-256 of the seed and the application number.
     *
     * <p>A seeded PRNG would also be reproducible in principle, but only for someone running the same
     * language and library version. A hash of two published strings can be checked by hand with any
     * command line tool, which is the difference between "trust our jar" and "here, verify it".
     */
    private void assignLotteryOrder(RuleSetDocument rules, List<Row> eligible) {
        String seed = rules.draw().seed();
        for (Row row : eligible) {
            row.token = Hashes.sha256Hex(seed + ":" + row.application.getApplicationNumber());
        }
        eligible.sort(Comparator.comparing((Row r) -> r.token)
                .thenComparing(r -> r.application.getApplicationNumber()));
        int rank = 1;
        for (Row row : eligible) {
            row.globalRank = rank++;
        }
    }

    private Map<String, List<Row>> groupByCategory(RuleSetDocument rules, List<Row> eligible) {
        Map<String, List<Row>> byCategory = new LinkedHashMap<>();
        for (RuleSetDocument.Category category : rules.categories()) {
            byCategory.put(category.code(), new ArrayList<>());
        }
        for (Row row : eligible) {
            byCategory.get(row.category.code()).add(row);
        }
        byCategory.forEach((code, rows) -> {
            int rank = 1;
            for (Row row : rows) {
                row.rankInCategory = rank++;
                row.categoryCount = rows.size();
            }
        });
        for (Row row : eligible) {
            row.trace.add("Lottery token SHA-256(\"" + rules.draw().seed() + ":"
                    + row.application.getApplicationNumber() + "\") = " + row.token.substring(0, 24)
                    + "..., which places this application at position " + row.globalRank
                    + " scheme-wide and position " + row.rankInCategory + " of " + row.categoryCount
                    + " inside " + row.category.code() + ".");
        }
        return byCategory;
    }

    /**
     * Splits the flats between income categories by the largest remainder method.
     *
     * <p>Percentages of 600 rarely come out whole. Largest remainder is used because it is the method
     * that can be shown as arithmetic on a page: every category gets its whole part, and the seats
     * left over go to the categories with the biggest fractions, ties broken by the order the
     * categories appear in the published rule book.
     */
    private Map<String, Integer> divideSeats(RuleSetDocument rules, List<String> workings) {
        workings.add("Step 3 - dividing " + rules.totalFlats() + " flats between income categories");

        Map<String, Integer> seats = new LinkedHashMap<>();
        Map<String, BigDecimal> remainders = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.valueOf(rules.totalFlats());
        int assigned = 0;

        for (RuleSetDocument.Category category : rules.categories()) {
            BigDecimal exact = total.multiply(category.quotaPercent())
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            int whole = exact.setScale(0, RoundingMode.FLOOR).intValue();
            seats.put(category.code(), whole);
            remainders.put(category.code(), exact.subtract(BigDecimal.valueOf(whole)));
            assigned += whole;
            workings.add("  " + category.code() + ": " + trim(category.quotaPercent()) + "% of "
                    + rules.totalFlats() + " = " + trim(exact) + ", whole part " + whole);
        }

        int leftover = rules.totalFlats() - assigned;
        if (leftover > 0) {
            List<String> order = new ArrayList<>(remainders.keySet());
            order.sort(Comparator.comparing((String code) -> remainders.get(code)).reversed());
            workings.add("  " + leftover + " flat(s) left over after the whole parts; awarded to the "
                    + "largest fractional remainders in this order: " + order.subList(0, leftover));
            for (int i = 0; i < leftover; i++) {
                String code = order.get(i);
                seats.merge(code, 1, Integer::sum);
            }
        }
        seats.forEach((code, count) -> workings.add("  Final: " + code + " gets " + count + " flats"));
        workings.add("");
        return seats;
    }

    /**
     * Fills one income category: reserved quotas first, in the order the rule book lists them, then
     * the open pool.
     *
     * <p>Reserved quotas are vertical, not horizontal: a reserved claimant gets first refusal on the
     * reserved seats and then competes for the open seats alongside everyone else. Fractions are
     * rounded down for reserved quotas, so rounding can never take a seat away from the open pool.
     */
    private int fillCategory(RuleSetDocument rules, RuleSetDocument.Category category, List<Row> pool,
                             int seats, List<Row> allotted, List<String> workings) {
        workings.add("  " + category.code() + " - " + seats + " flats, " + pool.size()
                + " applications in the draw");

        if (seats == 0) {
            return 0;
        }

        Map<String, Integer> reservedSeats = new LinkedHashMap<>();
        int reservedTotal = 0;
        for (RuleSetDocument.Reservation reservation : safe(rules.reservations())) {
            BigDecimal exact = BigDecimal.valueOf(seats).multiply(reservation.percentOfCategory())
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            int count = exact.setScale(0, RoundingMode.FLOOR).intValue();
            count = Math.min(count, seats - reservedTotal);
            reservedSeats.put(reservation.code(), count);
            reservedTotal += count;
            workings.add("    reserved " + reservation.code() + ": " + trim(reservation.percentOfCategory())
                    + "% of " + seats + " = " + trim(exact) + ", rounded down to " + count + " seats");
        }
        int openSeats = seats - reservedTotal;
        workings.add("    open pool: " + openSeats + " seats");

        int filled = 0;
        int shortfall = 0;

        for (RuleSetDocument.Reservation reservation : safe(rules.reservations())) {
            int quota = reservedSeats.getOrDefault(reservation.code(), 0);
            if (quota == 0) {
                continue;
            }
            List<Row> qualifying = pool.stream()
                    .filter(r -> ApplicantPredicates.test(reservation.predicate(), r.application, rules))
                    .toList();
            int granted = 0;
            for (Row row : qualifying) {
                if (granted == quota) {
                    break;
                }
                if (row.outcome != null) {
                    continue;
                }
                granted++;
                row.allot(category.code(), reservation.code(), granted);
                row.trace.add("Qualified for the " + reservation.code() + " reserved quota ("
                        + reservation.label() + "): position " + granted + " among "
                        + qualifying.size() + " qualifying applications in " + category.code()
                        + ", and the quota holds " + quota + " seats.");
                row.reasonCode = "ALLOTTED_RESERVED_" + reservation.code();
                row.reasonText = "Allotted against the " + quota + " seats reserved in " + category.code()
                        + " for " + reservation.label() + ". You were number " + granted
                        + " in the lottery among the " + qualifying.size()
                        + " applications that qualified for this quota.";
                allotted.add(row);
                filled++;
            }
            workings.add("    " + reservation.code() + ": " + qualifying.size()
                    + " qualifying applications, " + granted + " of " + quota + " seats filled");
            if (granted < quota) {
                shortfall += quota - granted;
            }
        }

        int openCapacity = openSeats;
        if (shortfall > 0) {
            if (rules.spill().unfilledReservedSeats() == RuleSetDocument.UnfilledReservedSeats.SPILL_TO_OPEN) {
                openCapacity += shortfall;
                workings.add("    " + shortfall + " reserved seat(s) had no eligible claimant and were "
                        + "released to the open pool, which now holds " + openCapacity + " seats");
            } else {
                workings.add("    " + shortfall + " reserved seat(s) had no eligible claimant and are "
                        + "held vacant under the published rules");
            }
        }

        int openGranted = 0;
        for (Row row : pool) {
            if (openGranted == openCapacity) {
                break;
            }
            if (row.outcome != null) {
                continue;
            }
            openGranted++;
            row.allot(category.code(), OPEN_POOL, openGranted);
            row.trace.add("Considered for the " + category.code() + " open pool of " + openCapacity
                    + " seats and allotted at open-pool position " + openGranted + ".");
            row.reasonCode = "ALLOTTED_OPEN";
            row.reasonText = "Allotted from the open pool of " + category.code() + ", which held "
                    + openCapacity + " seats. Your lottery position inside " + category.code() + " was "
                    + row.rankInCategory + " of " + row.categoryCount + ".";
            allotted.add(row);
            filled++;
        }
        workings.add("    open pool: " + openGranted + " of " + openCapacity + " seats filled");

        for (Row row : pool) {
            if (row.outcome == null) {
                row.trace.add("Not reached in " + category.code() + ": the category's "
                        + seats + " seats were exhausted before lottery position "
                        + row.rankInCategory + ".");
            }
        }
        return filled;
    }

    /**
     * Hands seats no category could fill to the best-ranked applicants left anywhere in the scheme.
     *
     * <p>The alternative, holding them vacant, is also configurable, because a scheme that would rather
     * explain empty flats than cross-category movement is making a policy choice, not a technical one.
     */
    private int spillLeftoverSeats(RuleSetDocument rules, List<Row> eligible,
                                   Map<String, Integer> vacantByCategory, List<Row> allotted,
                                   List<String> workings) {
        int vacant = vacantByCategory.values().stream().mapToInt(Integer::intValue).sum();
        workings.add("Step 5 - seats no category could fill");
        if (vacant == 0) {
            workings.add("  Every category filled its seats, so nothing needed redistributing.");
            workings.add("");
            return 0;
        }
        workings.add("  " + vacant + " seat(s) unfilled: " + vacantByCategory);

        if (rules.spill().unfilledCategorySeats() != RuleSetDocument.UnfilledCategorySeats.POOL_BY_RANK) {
            workings.add("  The published rule is " + rules.spill().unfilledCategorySeats()
                    + ", so these flats stay unallotted.");
            workings.add("");
            return vacant;
        }

        workings.add("  The published rule is POOL_BY_RANK, so they go to the best-ranked applications "
                + "still without a flat, regardless of category.");
        int granted = 0;
        for (Row row : eligible) {
            if (granted == vacant) {
                break;
            }
            if (row.outcome != null) {
                continue;
            }
            granted++;
            row.allot(row.category.code(), SPILL_POOL, granted);
            row.trace.add("A seat left unfilled elsewhere in the scheme was redistributed under the "
                    + "POOL_BY_RANK rule; this application was number " + granted
                    + " in scheme-wide lottery order among those still waiting.");
            row.reasonCode = "ALLOTTED_SPILL";
            row.reasonText = "Allotted from the " + vacant + " seat(s) that no category could fill. "
                    + "Under the published spill rule these went to the best-ranked applications left, "
                    + "and you were number " + granted + " of those.";
            allotted.add(row);
        }
        workings.add("  " + granted + " of " + vacant + " redistributed seat(s) allotted.");
        workings.add("");
        return vacant - granted;
    }

    /** Allotment serials run category by category, and inside a category quota by quota. */
    private void assignSeatNumbers(RuleSetDocument rules, List<Row> allotted) {
        List<String> poolOrder = new ArrayList<>();
        for (RuleSetDocument.Reservation reservation : safe(rules.reservations())) {
            poolOrder.add(reservation.code());
        }
        poolOrder.add(OPEN_POOL);
        poolOrder.add(SPILL_POOL);

        List<String> categoryOrder = rules.categories().stream().map(RuleSetDocument.Category::code).toList();

        allotted.sort(Comparator
                .comparingInt((Row r) -> categoryOrder.indexOf(r.categoryCode))
                .thenComparingInt(r -> poolOrder.indexOf(r.poolCode))
                .thenComparingInt(r -> r.rankInPool));

        int seat = 1;
        for (Row row : allotted) {
            row.seatNumber = seat++;
            row.trace.add("Allotment serial " + row.seatNumber + " of " + allotted.size() + ".");
        }
    }

    /**
     * Builds a waiting list per category from the applicants who just missed out.
     *
     * <p>Run after spill so that a person who would have received a redistributed flat gets the flat
     * rather than a place in a queue.
     */
    private int buildWaitlists(RuleSetDocument rules, Map<String, List<Row>> byCategory,
                              Map<String, Integer> categorySeats, List<String> workings) {
        workings.add("Step 6 - waiting lists");
        BigDecimal percent = rules.waitlist().percentOfCategorySeats();
        int total = 0;
        for (RuleSetDocument.Category category : rules.categories()) {
            int seats = categorySeats.getOrDefault(category.code(), 0);
            int size = BigDecimal.valueOf(seats).multiply(percent)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).intValue();
            List<Row> pool = byCategory.getOrDefault(category.code(), List.of());
            int position = 0;
            for (Row row : pool) {
                if (position == size) {
                    break;
                }
                if (row.outcome != null) {
                    continue;
                }
                position++;
                row.outcome = Outcome.WAITLISTED;
                row.categoryCode = category.code();
                row.poolCode = OPEN_POOL;
                row.waitlistPosition = position;
                row.reasonCode = "WAITLISTED";
                row.reasonText = "Waiting list position " + position + " of " + size + " for "
                        + category.code() + ". If an allotment in " + category.code()
                        + " is surrendered or cancelled, the flat is offered in this order.";
                row.trace.add("Placed at waiting list position " + position + " of " + size + " for "
                        + category.code() + " (the list is " + trim(percent) + "% of the category's "
                        + seats + " seats, rounded up).");
                total++;
            }
            workings.add("  " + category.code() + ": waiting list of " + size + " (" + trim(percent)
                    + "% of " + seats + " seats, rounded up), " + position + " place(s) filled");
        }
        workings.add("");
        return total;
    }

    /** Everyone still without an outcome is told where they stood and what it would have taken. */
    private void finishNotSelected(Map<String, List<Row>> byCategory, List<String> workings) {
        for (Map.Entry<String, List<Row>> entry : byCategory.entrySet()) {
            List<Row> pool = entry.getValue();
            int lastAllottedRank = pool.stream()
                    .filter(r -> r.outcome == Outcome.ALLOTTED)
                    .mapToInt(r -> r.rankInCategory)
                    .max().orElse(0);
            int lastWaitlistedRank = pool.stream()
                    .filter(r -> r.outcome == Outcome.WAITLISTED)
                    .mapToInt(r -> r.rankInCategory)
                    .max().orElse(0);

            for (Row row : pool) {
                if (row.outcome != null) {
                    continue;
                }
                String cutoff = lastAllottedRank == 0
                        ? "No flat in " + entry.getKey() + " was allotted in this draw"
                        : "The last flat in " + entry.getKey() + " went to lottery position "
                        + lastAllottedRank;
                if (lastWaitlistedRank > 0) {
                    cutoff += " and the waiting list closed at position " + lastWaitlistedRank;
                }

                row.outcome = Outcome.NOT_SELECTED;
                row.categoryCode = entry.getKey();
                row.reasonCode = "NOT_SELECTED";
                row.reasonText = "Your lottery position inside " + entry.getKey() + " was "
                        + row.rankInCategory + " of " + row.categoryCount + ". " + cutoff
                        + ", so this application did not reach a flat.";
                row.trace.add("Final position " + row.rankInCategory + " of " + row.categoryCount
                        + " in " + entry.getKey() + ". " + cutoff + ".");
            }
        }
        workings.add("");
    }

    /**
     * A single hash over every decision in a fixed order.
     *
     * <p>Publishing this one string alongside the result list means anyone who downloads the list can
     * check it has not been edited since, without needing access to the database.
     */
    private String hashResults(List<Row> rows) {
        List<String> lines = rows.stream()
                .map(r -> r.application.getApplicationNumber() + "|" + r.outcome + "|"
                        + nullSafe(r.categoryCode) + "|" + nullSafe(r.poolCode) + "|"
                        + nullSafe(r.seatNumber) + "|" + nullSafe(r.waitlistPosition) + "|"
                        + nullSafe(r.rankInCategory) + "|" + nullSafe(r.token))
                .sorted()
                .toList();
        return Hashes.sha256Hex(String.join("\n", lines));
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String nullSafe(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "-" : String.format(Locale.ENGLISH, "%,.0f", value);
    }

    private static String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Mutable per-application scratch space used while the draw runs. */
    private static final class Row {
        private final Application application;
        private final List<String> trace = new ArrayList<>();
        private String token;
        private int globalRank;
        private RuleSetDocument.Category category;
        private int rankInCategory;
        private int categoryCount;
        private Outcome outcome;
        private String categoryCode;
        private String poolCode;
        private Integer rankInPool;
        private Integer seatNumber;
        private Integer waitlistPosition;
        private String reasonCode;
        private String reasonText;

        private Row(Application application) {
            this.application = application;
        }

        private void exclude(String reasonCode, String reasonText) {
            this.outcome = Outcome.EXCLUDED;
            this.reasonCode = reasonCode;
            this.reasonText = reasonText;
        }

        private void allot(String categoryCode, String poolCode, int rankInPool) {
            this.outcome = Outcome.ALLOTTED;
            this.categoryCode = categoryCode;
            this.poolCode = poolCode;
            this.rankInPool = rankInPool;
        }

        private Decision toDecision() {
            return new Decision(application.getId(), application.getApplicationNumber(),
                    application.getFullName(), outcome, categoryCode, poolCode, rankInPool,
                    rankInCategory == 0 ? null : rankInCategory,
                    categoryCount == 0 ? null : categoryCount,
                    seatNumber, waitlistPosition, token == null ? "not-drawn" : token,
                    reasonCode, reasonText, numberedTrace());
        }

        private String numberedTrace() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < trace.size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(i + 1).append(". ").append(trace.get(i));
            }
            return sb.toString();
        }
    }

    /** One application's outcome plus the reasoning behind it. */
    public record Decision(
            Long applicationId,
            String applicationNumber,
            String applicantName,
            Outcome outcome,
            String categoryCode,
            String poolCode,
            Integer rankInPool,
            Integer rankInCategory,
            Integer categoryApplicantCount,
            Integer seatNumber,
            Integer waitlistPosition,
            String lotteryToken,
            String reasonCode,
            String reasonText,
            String explanation
    ) {
    }

    public record Result(
            List<Decision> decisions,
            String quotaWorkings,
            String resultsHash,
            int allotted,
            int waitlisted,
            int notSelected,
            int excluded,
            int vacantSeats,
            int consideredInDraw
    ) {
    }
}
