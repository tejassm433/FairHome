package com.fairhome.dedup;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.ApplicationStatus;
import com.fairhome.audit.AuditService;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.support.NameMatching;
import com.fairhome.support.NationalId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds applications that probably belong to someone who has already applied.
 *
 * <p>Detection happens at submission time, on both the online form and the offline entry screen, so
 * the second attempt is caught while the person or the clerk is still there. Nothing is auto-rejected:
 * a match parks the new application in a review queue. Paper forms contain typos and a wrongly
 * discarded application is a far worse failure than a queue an officer has to work through.
 */
@Service
public class DedupService {

    private static final Logger log = LoggerFactory.getLogger(DedupService.class);

    private final ApplicationRepository applications;
    private final DuplicateFlagRepository flags;
    private final AuditService auditService;

    public DedupService(ApplicationRepository applications, DuplicateFlagRepository flags,
                        AuditService auditService) {
        this.applications = applications;
        this.flags = flags;
        this.auditService = auditService;
    }

    /** A suspicion about one earlier application, before any human has looked at it. */
    public record Candidate(Application existing, MatchType matchType, double score, String evidence) {
    }

    /**
     * Compares a not-yet-saved application against everything already recorded.
     *
     * <p>The repository lookups find possible earlier claims. The matching rules themselves live in
     * {@link #findCandidatesAgainst}, which is also what the JSON-driven unit tests call, so a change
     * to the matching policy cannot pass tests while the live path does something else.
     */
    public List<Candidate> findCandidates(Application candidate, RuleSetDocument.DuplicateDetection cfg) {
        log.debug("FairHome : DedupService : in method findCandidates : START");
        Map<Long, Application> pool = new LinkedHashMap<>();
        addAll(pool, applications.findByNationalIdOrderByIdAsc(candidate.getNationalId()));
        addAll(pool, applications.findByDateOfBirth(candidate.getDateOfBirth()));
        if (cfg.matchOnPhone() && candidate.getNormalisedPhone() != null
                && !candidate.getNormalisedPhone().isBlank()) {
            addAll(pool, applications.findByNormalisedPhone(candidate.getNormalisedPhone()));
        }
        if (cfg.matchOnEmail() && candidate.getNormalisedEmail() != null
                && !candidate.getNormalisedEmail().isBlank()) {
            addAll(pool, applications.findByNormalisedEmail(candidate.getNormalisedEmail()));
        }
        List<Candidate> result = findCandidatesAgainst(candidate, new ArrayList<>(pool.values()), cfg);
        log.debug("FairHome : DedupService : in method findCandidates : END");
        return result;
    }

    /**
     * The matching policy itself, independent of how the earlier applications were loaded.
     *
     * <p>Only the strongest signal per earlier application is kept, so an obvious repeat submission
     * shows up as one item in the queue rather than four.
     */
    public List<Candidate> findCandidatesAgainst(Application candidate, List<Application> alreadyOnFile,
                                                 RuleSetDocument.DuplicateDetection cfg) {
        log.debug("FairHome : DedupService : in method findCandidatesAgainst : START");
        Map<Long, Candidate> strongestPerApplication = new LinkedHashMap<>();
        double nameThreshold = cfg.nameSimilarityThreshold();

        for (Application existing : alreadyOnFile) {
            if (!considerable(existing, candidate)) {
                continue;
            }

            if (existing.getNationalId() != null && existing.getNationalId().equals(candidate.getNationalId())) {
                keepStrongest(strongestPerApplication, new Candidate(existing, MatchType.EXACT_NATIONAL_ID, 1.0,
                        "Identical national ID " + NationalId.masked(existing.getNationalId())
                                + " already recorded on " + existing.getApplicationNumber() + "."));
            }

            if (existing.getDateOfBirth() != null && existing.getDateOfBirth().equals(candidate.getDateOfBirth())) {
                double similarity = NameMatching.similarity(candidate.getNormalisedName(),
                        existing.getNormalisedName());
                if (similarity >= nameThreshold) {
                    keepStrongest(strongestPerApplication, new Candidate(existing, MatchType.NAME_AND_DOB,
                            round(similarity),
                            existing.getApplicationNumber() + " holds the name \"" + existing.getFullName()
                                    + "\", which is " + percent(similarity) + " similar, with the identical "
                                    + "date of birth " + existing.getDateOfBirth() + ". The national ID "
                                    + "differs though: " + NationalId.masked(existing.getNationalId())
                                    + " there against " + NationalId.masked(candidate.getNationalId())
                                    + " here, which is what a single mistyped digit looks like."));
                }
            }

            if (cfg.matchOnPhone() && hasText(candidate.getNormalisedPhone())
                    && candidate.getNormalisedPhone().equals(existing.getNormalisedPhone())) {
                double similarity = NameMatching.similarity(candidate.getNormalisedName(),
                        existing.getNormalisedName());
                if (similarity >= nameThreshold) {
                    keepStrongest(strongestPerApplication, new Candidate(existing, MatchType.NAME_AND_PHONE,
                            round(similarity * 0.95),
                            existing.getApplicationNumber() + " shares the phone number "
                                    + existing.getPhone() + " and its name is " + percent(similarity)
                                    + " similar."));
                }
            }

            if (cfg.matchOnEmail() && hasText(candidate.getNormalisedEmail())
                    && candidate.getNormalisedEmail().equals(existing.getNormalisedEmail())) {
                keepStrongest(strongestPerApplication, new Candidate(existing, MatchType.EMAIL, 0.85,
                        "Same email address " + existing.getEmail() + " as "
                                + existing.getApplicationNumber() + "."));
            }
        }

        List<Candidate> result = new ArrayList<>(strongestPerApplication.values());
        result.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparing(c -> keyOf(c.existing())));
        log.debug("FairHome : DedupService : in method findCandidatesAgainst : END");
        return result;
    }

    /**
     * An application already thrown out as a duplicate, or withdrawn, is not evidence that someone
     * has a live claim, so matching against it would park innocent applications forever.
     */
    private boolean considerable(Application existing, Application candidate) {
        log.debug("FairHome : DedupService : in method considerable : START");
        if (existing.getId() != null && existing.getId().equals(candidate.getId())) {
            log.debug("FairHome : DedupService : in method considerable : END");
            return false;
        }
        boolean result = existing.getStatus() != ApplicationStatus.REJECTED_DUPLICATE
                && existing.getStatus() != ApplicationStatus.WITHDRAWN;
        log.debug("FairHome : DedupService : in method considerable : END");
        return result;
    }

    private void keepStrongest(Map<Long, Candidate> best, Candidate candidate) {
        log.debug("FairHome : DedupService : in method keepStrongest : START");
        long key = keyOf(candidate.existing());
        Candidate existing = best.get(key);
        if (existing == null || candidate.score() > existing.score()) {
            best.put(key, candidate);
        }
        log.debug("FairHome : DedupService : in method keepStrongest : END");
    }

    private void addAll(Map<Long, Application> pool, List<Application> found) {
        log.debug("FairHome : DedupService : in method addAll : START");
        for (Application application : found) {
            pool.putIfAbsent(keyOf(application), application);
        }
        log.debug("FairHome : DedupService : in method addAll : END");
    }

    private static long keyOf(Application application) {
        if (application.getId() != null) {
            return application.getId();
        }
        String number = application.getApplicationNumber();
        return number == null ? System.identityHashCode(application) : number.hashCode();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Turns detected suspicions into stored flags once the new application has an id. */
    @Transactional
    public List<DuplicateFlag> raiseFlags(Application saved, List<Candidate> candidates) {
        log.debug("FairHome : DedupService : in method raiseFlags : START");
        List<DuplicateFlag> created = new ArrayList<>();
        for (Candidate candidate : candidates) {
            DuplicateFlag flag = new DuplicateFlag();
            flag.setNewApplicationId(saved.getId());
            flag.setNewApplicationNumber(saved.getApplicationNumber());
            flag.setExistingApplicationId(candidate.existing().getId());
            flag.setExistingApplicationNumber(candidate.existing().getApplicationNumber());
            flag.setMatchType(candidate.matchType());
            flag.setScore(candidate.score());
            flag.setEvidence(candidate.evidence());
            flag.setResolution(DuplicateResolution.OPEN);
            flag.setDetectedAt(Instant.now());
            created.add(flags.save(flag));
        }
        if (!created.isEmpty()) {
            log.info("FairHome : DedupService : in method raiseFlags : flag raised : {} flag(s) for application {}",
                    created.size(), saved.getApplicationNumber());
            auditService.record("DUPLICATE_FLAGGED", saved.getApplicationNumber(),
                    saved.getChannel().name().toLowerCase() + "-intake",
                    created.size() + " possible duplicate(s) detected: " + created.stream()
                            .map(f -> f.getExistingApplicationNumber() + " via " + f.getMatchType()
                                    + " at " + f.getScorePercent() + "%")
                            .reduce((a, b) -> a + "; " + b).orElse(""));
        }
        log.debug("FairHome : DedupService : in method raiseFlags : END");
        return created;
    }

    public List<DuplicateFlag> openQueue() {
        log.debug("FairHome : DedupService : in method openQueue : START");
        List<DuplicateFlag> result = flags.findByResolutionOrderByScoreDescIdAsc(DuplicateResolution.OPEN);
        log.debug("FairHome : DedupService : in method openQueue : END");
        return result;
    }

    public Page<DuplicateFlag> openQueue(Pageable pageable) {
        log.debug("FairHome : DedupService : in method openQueue : START");
        Page<DuplicateFlag> result = flags.findByResolutionOrderByScoreDescIdAsc(DuplicateResolution.OPEN, pageable);
        log.debug("FairHome : DedupService : in method openQueue : END");
        return result;
    }

    public long openCount() {
        log.debug("FairHome : DedupService : in method openCount : START");
        long count = flags.countByResolution(DuplicateResolution.OPEN);
        log.debug("FairHome : DedupService : in method openCount : END");
        return count;
    }

    public List<DuplicateFlag> flagsFor(Long applicationId) {
        log.debug("FairHome : DedupService : in method flagsFor : START");
        List<DuplicateFlag> result = flags.findByNewApplicationIdOrExistingApplicationId(applicationId, applicationId);
        log.debug("FairHome : DedupService : in method flagsFor : END");
        return result;
    }

    /**
     * Records an officer's decision on one flag.
     *
     * <p>When confirmed, the application the officer chose to drop is rejected and the other stays in
     * the draw; by default we keep the earlier claim, since applying first should not be punished for
     * a system's uncertainty. When rejected, the held application goes straight back into the draw,
     * unless another unresolved flag still touches it.
     */
    @Transactional
    public DuplicateFlag resolve(Long flagId, DuplicateResolution decision, String keepApplicationNumber,
                                 String officer, String note) {
        log.debug("FairHome : DedupService : in method resolve : START");
        DuplicateFlag flag = flags.findById(flagId)
                .orElseThrow(() -> new IllegalArgumentException("No duplicate flag " + flagId));
        if (flag.getResolution() != DuplicateResolution.OPEN) {
            log.warn("FairHome : DedupService : in method resolve : flag {} already resolved as {}",
                    flagId, flag.getResolution());
            throw new IllegalStateException("Flag " + flagId + " was already resolved as "
                    + flag.getResolution());
        }

        Application newApp = applications.findById(flag.getNewApplicationId()).orElseThrow();
        Application existingApp = applications.findById(flag.getExistingApplicationId()).orElseThrow();

        if (decision == null || decision == DuplicateResolution.OPEN) {
            log.warn("FairHome : DedupService : in method resolve : invalid decision for flag {}", flagId);
            throw new IllegalArgumentException(
                    "Choose whether these are the same person or two different people.");
        }

        if (decision == DuplicateResolution.CONFIRMED_DUPLICATE) {
            Application keep = existingApp;
            Application drop = newApp;
            if (existingApp.getApplicationNumber().equals(keepApplicationNumber)) {
                keep = existingApp;
                drop = newApp;
            } else if (newApp.getApplicationNumber().equals(keepApplicationNumber)) {
                keep = newApp;
                drop = existingApp;
            }

            drop.setStatus(ApplicationStatus.REJECTED_DUPLICATE);
            drop.setStatusNote("Confirmed on review as a duplicate of " + keep.getApplicationNumber()
                    + ". " + (note == null || note.isBlank() ? "" : note));
            applications.save(drop);

            if (keep.getStatus() == ApplicationStatus.PENDING_DUPLICATE_REVIEW
                    && noOtherOpenFlags(keep.getId(), flagId)) {
                keep.setStatus(ApplicationStatus.SUBMITTED);
                keep.setStatusNote("Kept as the valid application of this person after duplicate review.");
                applications.save(keep);
            }

            flag.setKeptApplicationNumber(keep.getApplicationNumber());
        } else {
            for (Application app : List.of(newApp, existingApp)) {
                if (app.getStatus() == ApplicationStatus.PENDING_DUPLICATE_REVIEW
                        && noOtherOpenFlags(app.getId(), flagId)) {
                    app.setStatus(ApplicationStatus.SUBMITTED);
                    app.setStatusNote("Reviewed and confirmed as a distinct person; back in the draw.");
                    applications.save(app);
                }
            }
        }

        flag.setResolution(decision);
        flag.setResolvedAt(Instant.now());
        flag.setResolvedBy(officer);
        flag.setResolutionNote(note);
        DuplicateFlag savedFlag = flags.save(flag);

        auditService.record("DUPLICATE_RESOLVED",
                flag.getNewApplicationNumber() + " vs " + flag.getExistingApplicationNumber(), officer,
                "Decision: " + decision + ". Outcome: " + savedFlag.getOutcomeDescription()
                        + ". Reason given: " + (note == null || note.isBlank() ? "none" : note));
        log.info("FairHome : DedupService : in method resolve : duplicate flag {} resolved as {}",
                flagId, decision);
        log.debug("FairHome : DedupService : in method resolve : END");
        return savedFlag;
    }

    private boolean noOtherOpenFlags(Long applicationId, Long ignoringFlagId) {
        log.debug("FairHome : DedupService : in method noOtherOpenFlags : START");
        boolean result = flags.findByNewApplicationIdOrExistingApplicationId(applicationId, applicationId).stream()
                .filter(f -> !f.getId().equals(ignoringFlagId))
                .noneMatch(f -> f.getResolution() == DuplicateResolution.OPEN);
        log.debug("FairHome : DedupService : in method noOtherOpenFlags : END");
        return result;
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private static String percent(double value) {
        return Math.round(value * 100) + "%";
    }
}
