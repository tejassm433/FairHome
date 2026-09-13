package com.fairhome.rules;

import com.fairhome.audit.AuditService;
import com.fairhome.support.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads, validates and versions the published rule book.
 *
 * <p>On first start the rule file shipped on the classpath is published as version 1. After that the
 * database is the source of truth, and every edit made through the admin console creates a new
 * version rather than mutating the old one.
 */
@Service
public class RuleSetService {

    private static final Logger log = LoggerFactory.getLogger(RuleSetService.class);
    private static final String DEFAULT_RULES = "rules/default-ruleset.json";

    private final RuleSetRepository repository;
    private final AuditService auditService;
    private final ObjectMapper mapper;

    public RuleSetService(RuleSetRepository repository, AuditService auditService, ObjectMapper mapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    @Transactional
    public RuleSetVersion activeVersion() {
        return repository.findByActiveTrue().orElseGet(this::installDefaultRuleSet);
    }

    public RuleSetDocument activeRules() {
        return parse(activeVersion().getJson());
    }

    public RuleSetDocument rulesForVersion(int version) {
        return repository.findByVersion(version)
                .map(v -> parse(v.getJson()))
                .orElseThrow(() -> new IllegalArgumentException("No rule version " + version));
    }

    public List<RuleSetVersion> history() {
        return repository.findAllByOrderByVersionDesc();
    }

    public RuleSetDocument parse(String json) {
        try {
            return mapper.readValue(json, RuleSetDocument.class);
        } catch (JacksonException e) {
            throw new RuleValidationException(List.of("The rule file is not valid JSON: " + e.getMessage()));
        }
    }

    /**
     * Re-serialises through Jackson before storing or hashing.
     *
     * <p>Whitespace and key order in what an operator pasted must not change the content hash, or two
     * identical rule books would hash differently and the hash would prove nothing.
     */
    public String canonicalise(RuleSetDocument document) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialise rule set", e);
        }
    }

    @Transactional
    public RuleSetVersion publish(String json, String publishedBy, String changeNote) {
        RuleSetDocument document = parse(json);
        validate(document);

        String canonical = canonicalise(document);
        String hash = Hashes.sha256Hex(canonical);

        RuleSetVersion current = repository.findByActiveTrue().orElse(null);
        if (current != null) {
            if (current.getContentHash().equals(hash)) {
                throw new RuleValidationException(
                        List.of("These rules are identical to the active version " + current.getVersion()
                                + ", so there is nothing to publish."));
            }
            current.setActive(false);
            repository.save(current);
        }

        int nextVersion = repository.findAllByOrderByVersionDesc().stream()
                .mapToInt(RuleSetVersion::getVersion).max().orElse(0) + 1;

        RuleSetVersion version = new RuleSetVersion();
        version.setVersion(nextVersion);
        version.setVersionLabel(document.rulesVersionLabel() == null
                ? "v" + nextVersion : document.rulesVersionLabel());
        version.setJson(canonical);
        version.setContentHash(hash);
        version.setPublishedAt(Instant.now());
        version.setPublishedBy(publishedBy);
        version.setChangeNote(changeNote);
        version.setActive(true);
        RuleSetVersion saved = repository.save(version);

        auditService.record("RULES_PUBLISHED", "ruleSetVersion:" + nextVersion, publishedBy,
                "Published rule version " + nextVersion + " (" + saved.getVersionLabel() + "), hash "
                        + hash + ". Note: " + (changeNote == null ? "-" : changeNote));
        return saved;
    }

    /**
     * Rejects rule books that would produce an indefensible draw. Refusing to run is always better
     * than publishing a result nobody can justify.
     */
    public void validate(RuleSetDocument d) {
        List<String> problems = new ArrayList<>();

        if (d.totalFlats() <= 0) {
            problems.add("totalFlats must be greater than zero");
        }
        if (d.draw() == null || d.draw().seed() == null || d.draw().seed().isBlank()) {
            problems.add("draw.seed is required, because the lottery order must be reproducible");
        }
        if (d.draw() != null && d.draw().rankStrategy() == null) {
            problems.add("draw.rankStrategy must be one of " + List.of(RuleSetDocument.RankStrategy.values()));
        }
        if (d.eligibility() == null) {
            problems.add("eligibility block is required");
        } else if (d.eligibility().minAgeYears() < 0
                || d.eligibility().maxAgeYears() <= d.eligibility().minAgeYears()) {
            problems.add("eligibility.maxAgeYears must be greater than eligibility.minAgeYears");
        }

        if (d.categories() == null || d.categories().isEmpty()) {
            problems.add("at least one category is required");
        } else {
            Set<String> codes = new HashSet<>();
            BigDecimal quotaTotal = BigDecimal.ZERO;
            for (RuleSetDocument.Category c : d.categories()) {
                if (c.code() == null || c.code().isBlank()) {
                    problems.add("every category needs a code");
                } else if (!codes.add(c.code())) {
                    problems.add("duplicate category code " + c.code());
                }
                if (c.quotaPercent() == null || c.quotaPercent().signum() < 0) {
                    problems.add("category " + c.code() + " needs a quotaPercent of zero or more");
                } else {
                    quotaTotal = quotaTotal.add(c.quotaPercent());
                }
                if (c.incomeMin() == null) {
                    problems.add("category " + c.code() + " needs an incomeMin");
                } else if (c.incomeMax() != null && c.incomeMax().compareTo(c.incomeMin()) < 0) {
                    problems.add("category " + c.code() + " has incomeMax below incomeMin");
                }
            }
            if (quotaTotal.compareTo(new BigDecimal("100")) != 0) {
                problems.add("category quotaPercent values must add up to exactly 100, they add up to "
                        + quotaTotal.stripTrailingZeros().toPlainString());
            }
            problems.addAll(findIncomeBandGaps(d.categories()));
        }

        if (d.reservations() != null) {
            Set<String> codes = new HashSet<>();
            BigDecimal reservedTotal = BigDecimal.ZERO;
            for (RuleSetDocument.Reservation r : d.reservations()) {
                if (r.code() == null || r.code().isBlank()) {
                    problems.add("every reservation needs a code");
                } else if (!codes.add(r.code())) {
                    problems.add("duplicate reservation code " + r.code());
                }
                if ("OPEN".equals(r.code())) {
                    problems.add("OPEN is reserved for the unreserved pool and cannot be a reservation code");
                }
                if (!ApplicantPredicates.isKnown(r.predicate())) {
                    problems.add("reservation " + r.code() + " uses unknown predicate '" + r.predicate()
                            + "'. Available predicates: " + ApplicantPredicates.keys());
                }
                if (r.percentOfCategory() == null || r.percentOfCategory().signum() < 0) {
                    problems.add("reservation " + r.code() + " needs a percentOfCategory of zero or more");
                } else {
                    reservedTotal = reservedTotal.add(r.percentOfCategory());
                }
            }
            if (reservedTotal.compareTo(new BigDecimal("100")) > 0) {
                problems.add("reservation percentages add up to " + reservedTotal.stripTrailingZeros().toPlainString()
                        + "% of each category, which is more than the category itself");
            }
        }

        if (d.spill() == null || d.spill().unfilledReservedSeats() == null
                || d.spill().unfilledCategorySeats() == null) {
            problems.add("spill.unfilledReservedSeats and spill.unfilledCategorySeats are both required, "
                    + "because what happens to a seat nobody claims must be decided in advance");
        }
        if (d.waitlist() == null || d.waitlist().percentOfCategorySeats() == null
                || d.waitlist().percentOfCategorySeats().signum() < 0) {
            problems.add("waitlist.percentOfCategorySeats must be zero or more");
        }
        if (d.duplicateDetection() == null) {
            problems.add("duplicateDetection block is required");
        } else {
            double nameThreshold = d.duplicateDetection().nameSimilarityThreshold();
            if (nameThreshold <= 0 || nameThreshold > 1) {
                problems.add("duplicateDetection.nameSimilarityThreshold must be between 0 and 1");
            }
            double hold = d.duplicateDetection().holdScoreThreshold();
            if (hold <= 0 || hold > 1) {
                problems.add("duplicateDetection.holdScoreThreshold must be between 0 and 1");
            }
        }

        if (!problems.isEmpty()) {
            throw new RuleValidationException(problems);
        }
    }

    /**
     * An income that falls in no band means an applicant nobody can place, so overlaps and holes in
     * the bands are treated as configuration errors rather than discovered on draw day.
     */
    private List<String> findIncomeBandGaps(List<RuleSetDocument.Category> categories) {
        List<String> problems = new ArrayList<>();
        List<RuleSetDocument.Category> sorted = new ArrayList<>(categories);
        sorted.sort((a, b) -> {
            BigDecimal am = a.incomeMin() == null ? BigDecimal.ZERO : a.incomeMin();
            BigDecimal bm = b.incomeMin() == null ? BigDecimal.ZERO : b.incomeMin();
            return am.compareTo(bm);
        });
        boolean sawOpenEnded = false;
        for (int i = 0; i < sorted.size(); i++) {
            RuleSetDocument.Category c = sorted.get(i);
            if (c.incomeMax() == null) {
                if (i != sorted.size() - 1) {
                    problems.add("category " + c.code()
                            + " has no incomeMax but is not the highest band, so bands overlap");
                }
                sawOpenEnded = true;
                continue;
            }
            if (i + 1 < sorted.size()) {
                RuleSetDocument.Category next = sorted.get(i + 1);
                BigDecimal expected = c.incomeMax().add(BigDecimal.ONE);
                if (next.incomeMin() != null && next.incomeMin().compareTo(expected) != 0) {
                    problems.add("income bands are not contiguous between " + c.code() + " (ends at "
                            + c.incomeMax().toPlainString() + ") and " + next.code() + " (starts at "
                            + next.incomeMin().toPlainString() + ")");
                }
            }
        }
        if (!sawOpenEnded) {
            problems.add("the highest income band needs incomeMax set to null, "
                    + "otherwise a high earner falls into no category at all");
        }
        return problems;
    }

    @Transactional
    public RuleSetVersion installDefaultRuleSet() {
        String json = readClasspathRules();
        RuleSetDocument document = parse(json);
        validate(document);

        String canonical = canonicalise(document);
        RuleSetVersion version = new RuleSetVersion();
        version.setVersion(1);
        version.setVersionLabel(document.rulesVersionLabel() == null ? "v1" : document.rulesVersionLabel());
        version.setJson(canonical);
        version.setContentHash(Hashes.sha256Hex(canonical));
        version.setPublishedAt(Instant.now());
        version.setPublishedBy("system");
        version.setChangeNote("Initial rule book loaded from " + DEFAULT_RULES);
        version.setActive(true);
        RuleSetVersion saved = repository.save(version);
        log.info("Installed default rule set version 1, hash {}", saved.getContentHash());
        auditService.record("RULES_PUBLISHED", "ruleSetVersion:1", "system",
                "Initial rule book loaded from " + DEFAULT_RULES + ", hash " + saved.getContentHash());
        return saved;
    }

    public String defaultRuleJson() {
        return readClasspathRules();
    }

    private String readClasspathRules() {
        try (InputStream in = new ClassPathResource(DEFAULT_RULES).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + DEFAULT_RULES + " from the classpath", e);
        }
    }
}
