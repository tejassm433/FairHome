package com.fairhome.draw;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.ApplicationStatus;
import com.fairhome.audit.AuditService;
import com.fairhome.dedup.DedupService;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.rules.RuleSetService;
import com.fairhome.rules.RuleSetVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs draws and keeps their results.
 *
 * <p>Dry runs can be repeated as often as officers like. Exactly one run may be published as the
 * official result, and once it is published nothing about it can be changed, only superseded by an
 * explicit, audited act.
 */
@Service
public class DrawService {

    private static final Logger log = LoggerFactory.getLogger(DrawService.class);

    private final ApplicationRepository applications;
    private final DrawRunRepository drawRuns;
    private final AllocationRepository allocations;
    private final AllocationEngine engine;
    private final RuleSetService ruleSetService;
    private final DedupService dedupService;
    private final AuditService auditService;

    public DrawService(ApplicationRepository applications, DrawRunRepository drawRuns,
                       AllocationRepository allocations, AllocationEngine engine,
                       RuleSetService ruleSetService, DedupService dedupService,
                       AuditService auditService) {
        this.applications = applications;
        this.drawRuns = drawRuns;
        this.allocations = allocations;
        this.engine = engine;
        this.ruleSetService = ruleSetService;
        this.dedupService = dedupService;
        this.auditService = auditService;
    }

    public static class DrawRefusedException extends RuntimeException {
        public DrawRefusedException(String message) {
            super(message);
        }
    }

    @Transactional
    public DrawRun run(DrawMode mode, String executedBy, String note) {
        log.debug("FairHome : DrawService : in method run : START");
        RuleSetVersion ruleVersion = ruleSetService.activeVersion();
        RuleSetDocument rules = ruleSetService.parse(ruleVersion.getJson());
        ruleSetService.validate(rules);

        long openDuplicates = dedupService.openCount();
        if (mode == DrawMode.FINAL && openDuplicates > 0
                && !rules.eligibility().allowFinalDrawWithOpenDuplicateReviews()) {
            log.warn("FairHome : DrawService : in method run : refused final draw : {} open duplicate(s) awaiting review",
                    openDuplicates);
            throw new DrawRefusedException(openDuplicates + " possible duplicate(s) are still awaiting "
                    + "review. The published rules do not allow a final draw while any remain open, "
                    + "because a duplicate resolved after the draw would change who gets a flat. "
                    + "Clear the review queue, or run a dry run instead.");
        }
        if (mode == DrawMode.FINAL) {
            drawRuns.findByPublishedTrue().ifPresent(published -> {
                log.warn("FairHome : DrawService : in method run : refused final draw : run {} already published",
                        published.getId());
                throw new DrawRefusedException("Draw run " + published.getId()
                        + " has already been published as the official result. Withdraw it first if it "
                        + "genuinely has to be re-run; that act is recorded in the audit trail.");
            });
        }

        List<Application> all = applications.findAll(
                org.springframework.data.domain.Sort.by("id").ascending());
        if (all.isEmpty()) {
            log.warn("FairHome : DrawService : in method run : refused draw : no applications on file");
            throw new DrawRefusedException("There are no applications on file, so there is nothing to draw.");
        }

        LocalDate drawDate = LocalDate.now();
        AllocationEngine.Result result = engine.run(rules, all, drawDate);

        DrawRun run = new DrawRun();
        run.setMode(mode);
        run.setExecutedAt(Instant.now());
        run.setExecutedBy(executedBy);
        run.setRuleSetVersion(ruleVersion.getVersion());
        run.setRuleSetHash(ruleVersion.getContentHash());
        run.setSeed(rules.draw().seed());
        run.setTotalFlats(rules.totalFlats());
        run.setApplicationsConsidered(result.consideredInDraw());
        run.setAllotted(result.allotted());
        run.setWaitlisted(result.waitlisted());
        run.setNotSelected(result.notSelected());
        run.setExcluded(result.excluded());
        run.setVacantSeats(result.vacantSeats());
        run.setResultsHash(result.resultsHash());
        run.setQuotaWorkings(result.quotaWorkings());
        run.setPublished(false);
        run.setNote(note);
        DrawRun savedRun = drawRuns.save(run);

        List<Allocation> rows = new ArrayList<>(result.decisions().size());
        for (AllocationEngine.Decision decision : result.decisions()) {
            Allocation allocation = new Allocation();
            allocation.setDrawRunId(savedRun.getId());
            allocation.setApplicationId(decision.applicationId());
            allocation.setApplicationNumber(decision.applicationNumber());
            allocation.setApplicantName(decision.applicantName());
            allocation.setOutcome(decision.outcome());
            allocation.setCategoryCode(decision.categoryCode());
            allocation.setPoolCode(decision.poolCode());
            allocation.setRankInPool(decision.rankInPool());
            allocation.setRankInCategory(decision.rankInCategory());
            allocation.setCategoryApplicantCount(decision.categoryApplicantCount());
            allocation.setSeatNumber(decision.seatNumber());
            allocation.setWaitlistPosition(decision.waitlistPosition());
            allocation.setLotteryToken(decision.lotteryToken());
            allocation.setReasonCode(decision.reasonCode());
            allocation.setReasonText(decision.reasonText());
            allocation.setExplanation(decision.explanation());
            rows.add(allocation);
        }
        allocations.saveAll(rows);

        auditService.record(mode == DrawMode.FINAL ? "DRAW_RUN_FINAL" : "DRAW_RUN_DRY",
                "drawRun:" + savedRun.getId(), executedBy,
                "Rule version " + ruleVersion.getVersion() + " (hash " + ruleVersion.getContentHash()
                        + "), seed \"" + rules.draw().seed() + "\", " + rules.totalFlats() + " flats, "
                        + result.consideredInDraw() + " applications in the draw. Allotted "
                        + result.allotted() + ", waitlisted " + result.waitlisted() + ", not selected "
                        + result.notSelected() + ", excluded " + result.excluded()
                        + ". Results hash " + result.resultsHash());
        log.info("FairHome : DrawService : in method run : draw run completed : id {} mode {} allotted {} waitlisted {}",
                savedRun.getId(), mode, result.allotted(), result.waitlisted());
        log.debug("FairHome : DrawService : in method run : END");
        return savedRun;
    }

    @Transactional
    public DrawRun publish(Long runId, String publishedBy) {
        log.debug("FairHome : DrawService : in method publish : START");
        DrawRun run = drawRuns.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        if (run.getMode() != DrawMode.FINAL) {
            log.warn("FairHome : DrawService : in method publish : refused : run {} was a dry run", runId);
            throw new DrawRefusedException("Only a final draw can be published. Run " + runId
                    + " was a dry run.");
        }
        drawRuns.findByPublishedTrue().ifPresent(existing -> {
            log.warn("FairHome : DrawService : in method publish : refused : run {} already published",
                    existing.getId());
            throw new DrawRefusedException("Draw run " + existing.getId() + " is already published.");
        });
        run.setPublished(true);
        run.setPublishedAt(Instant.now());
        DrawRun saved = drawRuns.save(run);
        auditService.record("DRAW_PUBLISHED", "drawRun:" + runId, publishedBy,
                "Published as the official result. Results hash " + run.getResultsHash()
                        + ", rule version " + run.getRuleSetVersion() + ".");
        log.info("FairHome : DrawService : in method publish : draw run published : {}", runId);
        log.debug("FairHome : DrawService : in method publish : END");
        return saved;
    }

    @Transactional
    public void withdrawPublication(Long runId, String actor, String reason) {
        log.debug("FairHome : DrawService : in method withdrawPublication : START");
        DrawRun run = drawRuns.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        if (reason == null || reason.isBlank()) {
            log.warn("FairHome : DrawService : in method withdrawPublication : refused : reason required for run {}",
                    runId);
            throw new DrawRefusedException("Withdrawing a published result requires a written reason.");
        }
        run.setPublished(false);
        run.setPublishedAt(null);
        drawRuns.save(run);
        auditService.record("DRAW_PUBLICATION_WITHDRAWN", "drawRun:" + runId, actor, "Reason: " + reason);
        log.info("FairHome : DrawService : in method withdrawPublication : publication withdrawn : {}", runId);
        log.debug("FairHome : DrawService : in method withdrawPublication : END");
    }

    @Transactional
    public void deleteRun(Long runId, String actor) {
        log.debug("FairHome : DrawService : in method deleteRun : START");
        DrawRun run = drawRuns.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        if (run.isPublished()) {
            log.warn("FairHome : DrawService : in method deleteRun : refused : run {} is published", runId);
            throw new DrawRefusedException("A published result cannot be deleted. Withdraw it first, "
                    + "which leaves a record of who did so and why.");
        }
        allocations.deleteByDrawRunId(runId);
        drawRuns.delete(run);
        auditService.record("DRAW_RUN_DELETED", "drawRun:" + runId, actor,
                "Deleted a " + run.getMode() + " run executed at " + run.getExecutedAt() + ".");
        log.info("FairHome : DrawService : in method deleteRun : draw run deleted : {}", runId);
        log.debug("FairHome : DrawService : in method deleteRun : END");
    }

    public List<DrawRun> allRuns() {
        log.debug("FairHome : DrawService : in method allRuns : START");
        List<DrawRun> result = drawRuns.findAllByOrderByIdDesc();
        log.debug("FairHome : DrawService : in method allRuns : END");
        return result;
    }

    public Optional<DrawRun> publishedRun() {
        log.debug("FairHome : DrawService : in method publishedRun : START");
        Optional<DrawRun> result = drawRuns.findByPublishedTrue();
        log.debug("FairHome : DrawService : in method publishedRun : END");
        return result;
    }

    public Optional<DrawRun> findRun(Long id) {
        log.debug("FairHome : DrawService : in method findRun : START");
        Optional<DrawRun> result = drawRuns.findById(id);
        log.debug("FairHome : DrawService : in method findRun : END");
        return result;
    }

    /**
     * The run an applicant is shown: the published result if there is one, otherwise nothing. Dry runs
     * are never visible to the public, or a rehearsal could be mistaken for the outcome.
     */
    public Optional<DrawRun> runVisibleToApplicants() {
        log.debug("FairHome : DrawService : in method runVisibleToApplicants : START");
        Optional<DrawRun> result = drawRuns.findByPublishedTrue();
        log.debug("FairHome : DrawService : in method runVisibleToApplicants : END");
        return result;
    }

    public Optional<Allocation> allocationFor(Long runId, Long applicationId) {
        log.debug("FairHome : DrawService : in method allocationFor : START");
        Optional<Allocation> result = allocations.findByDrawRunIdAndApplicationId(runId, applicationId);
        log.debug("FairHome : DrawService : in method allocationFor : END");
        return result;
    }

    public List<Allocation> resultsFor(Long runId) {
        log.debug("FairHome : DrawService : in method resultsFor : START");
        List<Allocation> result = allocations.findByDrawRunIdOrderBySeatNumberAsc(runId);
        log.debug("FairHome : DrawService : in method resultsFor : END");
        return result;
    }

    /** Per-category totals for the run summary screen and the press export. */
    public List<CategorySummary> categorySummary(Long runId) {
        log.debug("FairHome : DrawService : in method categorySummary : START");
        Map<String, Map<Outcome, Long>> byCategory = new LinkedHashMap<>();
        for (Object[] row : allocations.summariseByCategory(runId)) {
            String category = row[0] == null ? "(excluded)" : (String) row[0];
            Outcome outcome = (Outcome) row[1];
            long count = (Long) row[2];
            byCategory.computeIfAbsent(category, k -> new LinkedHashMap<>()).put(outcome, count);
        }
        List<CategorySummary> summaries = new ArrayList<>();
        byCategory.forEach((category, counts) -> summaries.add(new CategorySummary(category,
                counts.getOrDefault(Outcome.ALLOTTED, 0L),
                counts.getOrDefault(Outcome.WAITLISTED, 0L),
                counts.getOrDefault(Outcome.NOT_SELECTED, 0L),
                counts.getOrDefault(Outcome.EXCLUDED, 0L))));
        summaries.sort(Comparator.comparing(CategorySummary::category));
        log.debug("FairHome : DrawService : in method categorySummary : END");
        return summaries;
    }

    public Map<String, Long> intakeSnapshot() {
        log.debug("FairHome : DrawService : in method intakeSnapshot : START");
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", applications.count());
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status.name(), applications.countByStatus(status));
        }
        log.debug("FairHome : DrawService : in method intakeSnapshot : END");
        return counts;
    }

    public record CategorySummary(String category, long allotted, long waitlisted, long notSelected,
                                  long excluded) {
    }
}
