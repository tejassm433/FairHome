package com.fairhome.draw;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.ApplicationStatus;
import com.fairhome.audit.AuditService;
import com.fairhome.dedup.DedupService;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.rules.RuleSetService;
import com.fairhome.rules.RuleSetVersion;
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
        RuleSetVersion ruleVersion = ruleSetService.activeVersion();
        RuleSetDocument rules = ruleSetService.parse(ruleVersion.getJson());
        ruleSetService.validate(rules);

        long openDuplicates = dedupService.openCount();
        if (mode == DrawMode.FINAL && openDuplicates > 0
                && !rules.eligibility().allowFinalDrawWithOpenDuplicateReviews()) {
            throw new DrawRefusedException(openDuplicates + " possible duplicate(s) are still awaiting "
                    + "review. The published rules do not allow a final draw while any remain open, "
                    + "because a duplicate resolved after the draw would change who gets a flat. "
                    + "Clear the review queue, or run a dry run instead.");
        }
        if (mode == DrawMode.FINAL) {
            drawRuns.findByPublishedTrue().ifPresent(published -> {
                throw new DrawRefusedException("Draw run " + published.getId()
                        + " has already been published as the official result. Withdraw it first if it "
                        + "genuinely has to be re-run; that act is recorded in the audit trail.");
            });
        }

        List<Application> all = applications.findAll(
                org.springframework.data.domain.Sort.by("id").ascending());
        if (all.isEmpty()) {
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
        return savedRun;
    }

    @Transactional
    public DrawRun publish(Long runId, String publishedBy) {
        DrawRun run = drawRuns.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        if (run.getMode() != DrawMode.FINAL) {
            throw new DrawRefusedException("Only a final draw can be published. Run " + runId
                    + " was a dry run.");
        }
        drawRuns.findByPublishedTrue().ifPresent(existing -> {
            throw new DrawRefusedException("Draw run " + existing.getId() + " is already published.");
        });
        run.setPublished(true);
        run.setPublishedAt(Instant.now());
        DrawRun saved = drawRuns.save(run);
        auditService.record("DRAW_PUBLISHED", "drawRun:" + runId, publishedBy,
                "Published as the official result. Results hash " + run.getResultsHash()
                        + ", rule version " + run.getRuleSetVersion() + ".");
        return saved;
    }

    @Transactional
    public void withdrawPublication(Long runId, String actor, String reason) {
        DrawRun run = drawRuns.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        if (reason == null || reason.isBlank()) {
            throw new DrawRefusedException("Withdrawing a published result requires a written reason.");
        }
        run.setPublished(false);
        run.setPublishedAt(null);
        drawRuns.save(run);
        auditService.record("DRAW_PUBLICATION_WITHDRAWN", "drawRun:" + runId, actor, "Reason: " + reason);
    }

    @Transactional
    public void deleteRun(Long runId, String actor) {
        DrawRun run = drawRuns.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        if (run.isPublished()) {
            throw new DrawRefusedException("A published result cannot be deleted. Withdraw it first, "
                    + "which leaves a record of who did so and why.");
        }
        allocations.deleteByDrawRunId(runId);
        drawRuns.delete(run);
        auditService.record("DRAW_RUN_DELETED", "drawRun:" + runId, actor,
                "Deleted a " + run.getMode() + " run executed at " + run.getExecutedAt() + ".");
    }

    public List<DrawRun> allRuns() {
        return drawRuns.findAllByOrderByIdDesc();
    }

    public Optional<DrawRun> publishedRun() {
        return drawRuns.findByPublishedTrue();
    }

    public Optional<DrawRun> findRun(Long id) {
        return drawRuns.findById(id);
    }

    /**
     * The run an applicant is shown: the published result if there is one, otherwise nothing. Dry runs
     * are never visible to the public, or a rehearsal could be mistaken for the outcome.
     */
    public Optional<DrawRun> runVisibleToApplicants() {
        return drawRuns.findByPublishedTrue();
    }

    public Optional<Allocation> allocationFor(Long runId, Long applicationId) {
        return allocations.findByDrawRunIdAndApplicationId(runId, applicationId);
    }

    public List<Allocation> resultsFor(Long runId) {
        return allocations.findByDrawRunIdOrderBySeatNumberAsc(runId);
    }

    /** Per-category totals for the run summary screen and the press export. */
    public List<CategorySummary> categorySummary(Long runId) {
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
        return summaries;
    }

    public Map<String, Long> intakeSnapshot() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", applications.count());
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status.name(), applications.countByStatus(status));
        }
        return counts;
    }

    public record CategorySummary(String category, long allotted, long waitlisted, long notSelected,
                                  long excluded) {
    }
}
