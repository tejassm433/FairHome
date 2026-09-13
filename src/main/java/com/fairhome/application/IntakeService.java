package com.fairhome.application;

import com.fairhome.audit.AuditService;
import com.fairhome.dedup.DedupService;
import com.fairhome.dedup.DuplicateFlag;
import com.fairhome.dedup.MatchType;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.rules.RuleSetService;
import com.fairhome.support.Hashes;
import com.fairhome.support.NameMatching;
import com.fairhome.support.NationalId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The single door every application comes through, online or on paper.
 *
 * <p>Both channels run the identical validation, duplicate check and audit path. That is the only
 * way "no duplicate applications in either channel" can hold: if the offline screen had its own
 * shortcut for entering data, that shortcut would become the hole.
 */
@Service
public class IntakeService {

    /**
     * Two people submitting the same national ID at the same instant could both pass a check-then-write
     * sequence. Intake is cheap and low volume, so it is serialised outright. On a single node this is
     * airtight; behind more than one instance it would have to become a database-level lock, which the
     * README calls out.
     */
    private final ReentrantLock intakeLock = new ReentrantLock();

    private final SecureRandom random = new SecureRandom();

    private final ApplicationRepository applications;
    private final DedupService dedupService;
    private final RuleSetService ruleSetService;
    private final AuditService auditService;
    private final ZoneId zone = ZoneId.systemDefault();

    public IntakeService(ApplicationRepository applications, DedupService dedupService,
                         RuleSetService ruleSetService, AuditService auditService) {
        this.applications = applications;
        this.dedupService = dedupService;
        this.ruleSetService = ruleSetService;
        this.auditService = auditService;
    }

    /** What the applicant or the clerk is told immediately after a successful submission. */
    public record Receipt(
            String applicationNumber,
            String statusLookupKey,
            ApplicationStatus status,
            String provisionalCategory,
            boolean heldForReview,
            List<String> duplicateNotices
    ) {
    }

    @Transactional
    public Receipt submitOnline(ApplicationForm form) {
        return intake(form, Channel.ONLINE, "public-form");
    }

    @Transactional
    public Receipt recordOffline(ApplicationForm form, String officer) {
        if (form.getRecordedBy() == null || form.getRecordedBy().isBlank()) {
            throw new IntakeException("recordedBy", "Please record which officer is entering this form");
        }
        if (form.getPaperReference() == null || form.getPaperReference().isBlank()) {
            throw new IntakeException("paperReference",
                    "Please record the paper form or counter receipt number so the entry can be traced back");
        }
        return intake(form, Channel.OFFLINE, officer);
    }

    private Receipt intake(ApplicationForm form, Channel channel, String actor) {
        RuleSetDocument rules = ruleSetService.activeRules();

        String canonicalId = NationalId.canonicalise(form.getNationalId());
        Map<String, String> errors = new HashMap<>();

        if (canonicalId == null || canonicalId.length() != 12) {
            errors.put("nationalId", "The national ID must be exactly 12 digits");
        } else if (!NationalId.isStructurallyValid(canonicalId)) {
            errors.put("nationalId",
                    "That national ID number fails its checksum, so at least one digit is wrong. "
                            + "Please check it against the card.");
        }

        LocalDate applicationDate = channel == Channel.OFFLINE && form.getPaperSubmittedOn() != null
                ? form.getPaperSubmittedOn()
                : LocalDate.now(zone);

        if (channel == Channel.OFFLINE && form.getPaperSubmittedOn() != null
                && form.getPaperSubmittedOn().isAfter(LocalDate.now(zone))) {
            errors.put("paperSubmittedOn", "The paper form cannot be dated in the future");
        }

        if (form.getDateOfBirth() != null) {
            if (form.getDateOfBirth().isAfter(applicationDate)) {
                errors.put("dateOfBirth", "The date of birth cannot be in the future");
            } else {
                int age = java.time.Period.between(form.getDateOfBirth(), applicationDate).getYears();
                int minAge = rules.eligibility().minAgeYears();
                int maxAge = rules.eligibility().maxAgeYears();
                if (age < minAge) {
                    errors.put("dateOfBirth", "The scheme is open to applicants aged " + minAge + " to "
                            + maxAge + ". This applicant is " + age + ".");
                } else if (age > maxAge) {
                    errors.put("dateOfBirth", "The scheme is open to applicants aged " + minAge + " to "
                            + maxAge + ". This applicant is " + age + ".");
                }
            }
        }

        RuleSetDocument.Category category = form.getAnnualIncome() == null
                ? null : rules.categoryFor(form.getAnnualIncome());
        if (form.getAnnualIncome() != null && category == null && rules.eligibility().requireIncomeWithinBands()) {
            errors.put("annualIncome", "The declared income does not fall in any published income category");
        }

        if (!form.isDeclarationAccepted()) {
            errors.put("declarationAccepted", channel == Channel.ONLINE
                    ? "Please confirm the declaration before submitting"
                    : "Please confirm the applicant signed the declaration on the paper form");
        }

        if (!errors.isEmpty()) {
            throw new IntakeException(errors);
        }

        Application application = toEntity(form, channel, canonicalId, applicationDate);

        intakeLock.lock();
        try {
            List<DedupService.Candidate> candidates =
                    dedupService.findCandidates(application, rules.duplicateDetection());

            boolean exactIdMatch = candidates.stream()
                    .anyMatch(c -> c.matchType() == MatchType.EXACT_NATIONAL_ID);

            if (exactIdMatch && !rules.duplicateDetection().holdExactNationalIdForReview()) {
                DedupService.Candidate first = candidates.stream()
                        .filter(c -> c.matchType() == MatchType.EXACT_NATIONAL_ID)
                        .findFirst().orElseThrow();
                auditService.record("INTAKE_BLOCKED_DUPLICATE", first.existing().getApplicationNumber(), actor,
                        "Blocked a repeat submission for national ID "
                                + NationalId.masked(canonicalId) + " on the " + channel + " channel.");
                throw new IntakeException("nationalId",
                        "An application already exists for this national ID (" + first.existing()
                                .getApplicationNumber() + "). Only one application per person is allowed.");
            }

            List<DedupService.Candidate> holding = candidates.stream()
                    .filter(c -> c.score() >= rules.duplicateDetection().holdScoreThreshold())
                    .toList();

            if (!holding.isEmpty()) {
                application.setStatus(ApplicationStatus.PENDING_DUPLICATE_REVIEW);
                application.setStatusNote("Held at submission: " + holding.size()
                        + " possible earlier application(s) found. Awaiting officer review.");
            }

            Application saved = persist(application);
            List<DuplicateFlag> raised = dedupService.raiseFlags(saved, candidates);

            auditService.record(channel == Channel.ONLINE ? "APPLICATION_SUBMITTED_ONLINE"
                            : "APPLICATION_RECORDED_OFFLINE",
                    saved.getApplicationNumber(), actor,
                    "Name " + saved.getFullName() + ", national ID " + saved.getMaskedNationalId()
                            + ", income " + saved.getAnnualIncome().toPlainString()
                            + ", years in area " + saved.getYearsInArea()
                            + ", provisional category " + (category == null ? "none" : category.code())
                            + ", status " + saved.getStatus()
                            + (raised.isEmpty() ? "" : ", duplicate flags raised: " + raised.size())
                            + (saved.getPaperReference() == null ? ""
                            : ", paper reference " + saved.getPaperReference()));

            List<String> notices = new ArrayList<>();
            for (DedupService.Candidate candidate : holding) {
                notices.add(candidate.matchType().getLabel() + " - " + candidate.evidence());
            }

            return new Receipt(saved.getApplicationNumber(), saved.getStatusLookupKey(), saved.getStatus(),
                    category == null ? "unclassified" : category.code(), !holding.isEmpty(), notices);
        } finally {
            intakeLock.unlock();
        }
    }

    /**
     * Persists in one insert. The sequence-backed id is available straight after save, so the public
     * application number can be derived from it before the row is flushed.
     */
    private Application persist(Application application) {
        application.setApplicationNumber("PENDING");
        Application saved = applications.save(application);
        saved.setApplicationNumber(applicationNumberFor(saved.getId(), application.getSubmittedAt()));
        return applications.saveAndFlush(saved);
    }

    private String applicationNumberFor(Long id, Instant submittedAt) {
        int year = submittedAt.atZone(zone).getYear();
        return String.format(Locale.ROOT, "FH-%d-%06d", year, id);
    }

    private Application toEntity(ApplicationForm form, Channel channel, String canonicalId,
                                 LocalDate applicationDate) {
        Application application = new Application();
        application.setChannel(channel);
        application.setSubmittedAt(applicationDate.atStartOfDay(zone).toInstant());
        application.setRecordedAt(Instant.now());
        application.setRecordedBy(channel == Channel.OFFLINE ? form.getRecordedBy() : null);
        application.setPaperReference(channel == Channel.OFFLINE ? form.getPaperReference() : null);
        application.setFullName(form.getFullName().trim());
        application.setNormalisedName(NameMatching.normalise(form.getFullName()));
        application.setDateOfBirth(form.getDateOfBirth());
        application.setGender(form.getGender());
        application.setNationalId(canonicalId);
        application.setPhone(blankToNull(form.getPhone()));
        application.setNormalisedPhone(NameMatching.normalisePhone(form.getPhone()));
        application.setEmail(blankToNull(form.getEmail()));
        application.setNormalisedEmail(NameMatching.normaliseEmail(form.getEmail()));
        application.setAddressLine(form.getAddressLine().trim());
        application.setCityOrWard(form.getCityOrWard().trim());
        application.setPostalCode(blankToNull(form.getPostalCode()));
        application.setAnnualIncome(form.getAnnualIncome().setScale(2, RoundingMode.HALF_UP));
        application.setYearsInArea(form.getYearsInArea() == null ? 0 : form.getYearsInArea());
        application.setDifferentlyAbled(form.isDifferentlyAbled());
        application.setExServiceman(form.isExServiceman());
        application.setFirstTimeHomeBuyer(form.isFirstTimeHomeBuyer());
        application.setStatusLookupKey(generateLookupKey());
        return application;
    }

    /**
     * A short shared secret printed on the receipt. Combined with the application number it gates the
     * public status page, so an applicant can read their own placement and nobody else's.
     */
    private String generateLookupKey() {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        return Hashes.sha256Hex(java.util.HexFormat.of().formatHex(bytes))
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public void withdraw(String applicationNumber, String actor, String reason) {
        Application application = applications.findByApplicationNumber(applicationNumber)
                .orElseThrow(() -> new IllegalArgumentException("No application " + applicationNumber));
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setStatusNote("Withdrawn: " + (reason == null ? "no reason given" : reason));
        applications.save(application);
        auditService.record("APPLICATION_WITHDRAWN", applicationNumber, actor,
                "Reason: " + (reason == null ? "none" : reason));
    }
}
