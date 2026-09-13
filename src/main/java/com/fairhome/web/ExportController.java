package com.fairhome.web;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.audit.AuditEvent;
import com.fairhome.audit.AuditEventRepository;
import com.fairhome.draw.Allocation;
import com.fairhome.draw.DrawRun;
import com.fairhome.draw.DrawService;
import com.fairhome.rules.RuleSetService;
import com.fairhome.rules.RuleSetVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Plain-file exports for the people who will not be using the UI: a reporter checking the totals, a
 * lawyer attaching a list to a filing, an auditor diffing two runs.
 *
 * <p>CSV and text on purpose. Anything a spreadsheet or a diff tool can open is more useful as
 * evidence than a format that needs this application to read it.
 */
@Controller
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final DrawService drawService;
    private final ApplicationRepository applications;
    private final AuditEventRepository auditEvents;
    private final RuleSetService ruleSetService;

    public ExportController(DrawService drawService, ApplicationRepository applications,
                            AuditEventRepository auditEvents, RuleSetService ruleSetService) {
        this.drawService = drawService;
        this.applications = applications;
        this.auditEvents = auditEvents;
        this.ruleSetService = ruleSetService;
    }

    @GetMapping("/exports/draw/{runId}/results.csv")
    public ResponseEntity<String> results(@PathVariable Long runId) {
        log.debug("FairHome : ExportController : in method results : START");
        DrawRun run = drawService.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        StringBuilder csv = new StringBuilder();
        csv.append("# FairHome draw run ").append(run.getId()).append(' ').append(run.getMode())
                .append(", executed ").append(run.getExecutedAt())
                .append(", rule version ").append(run.getRuleSetVersion())
                .append(", rule hash ").append(run.getRuleSetHash())
                .append(", seed ").append(run.getSeed())
                .append(", results hash ").append(run.getResultsHash()).append('\n');
        csv.append("seat_number,application_number,applicant_name,outcome,category,pool,"
                + "rank_in_pool,rank_in_category,applicants_in_category,waitlist_position,"
                + "lottery_token,reason_code,reason\n");
        for (Allocation allocation : drawService.resultsFor(runId)) {
            csv.append(value(allocation.getSeatNumber())).append(',')
                    .append(value(allocation.getApplicationNumber())).append(',')
                    .append(value(allocation.getApplicantName())).append(',')
                    .append(value(allocation.getOutcome())).append(',')
                    .append(value(allocation.getCategoryCode())).append(',')
                    .append(value(allocation.getPoolCode())).append(',')
                    .append(value(allocation.getRankInPool())).append(',')
                    .append(value(allocation.getRankInCategory())).append(',')
                    .append(value(allocation.getCategoryApplicantCount())).append(',')
                    .append(value(allocation.getWaitlistPosition())).append(',')
                    .append(value(allocation.getLotteryToken())).append(',')
                    .append(value(allocation.getReasonCode())).append(',')
                    .append(value(allocation.getReasonText())).append('\n');
        }
        ResponseEntity<String> response = file("fairhome-draw-" + runId + "-results.csv", "text/csv",
                csv.toString());
        log.debug("FairHome : ExportController : in method results : END");
        return response;
    }

    /** The step-by-step arithmetic of the draw, in the order it happened. */
    @GetMapping("/exports/draw/{runId}/workings.txt")
    public ResponseEntity<String> workings(@PathVariable Long runId) {
        log.debug("FairHome : ExportController : in method workings : START");
        DrawRun run = drawService.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        String body = """
                FairHome allocation workings
                ============================
                Draw run        : %d (%s)
                Executed        : %s by %s
                Rule version    : %d
                Rule hash       : %s
                Lottery seed    : %s
                Results hash    : %s
                Published       : %s

                %s
                """.formatted(run.getId(), run.getMode(), run.getExecutedAt(), run.getExecutedBy(),
                run.getRuleSetVersion(), run.getRuleSetHash(), run.getSeed(), run.getResultsHash(),
                run.isPublished() ? "yes, at " + run.getPublishedAt() : "no",
                run.getQuotaWorkings());
        ResponseEntity<String> response = file("fairhome-draw-" + runId + "-workings.txt", "text/plain", body);
        log.debug("FairHome : ExportController : in method workings : END");
        return response;
    }

    /** The exact rule book a run used, byte for byte, so the hash can be recomputed. */
    @GetMapping("/exports/rules/{version}.json")
    public ResponseEntity<String> ruleVersion(@PathVariable Integer version) {
        log.debug("FairHome : ExportController : in method ruleVersion : START");
        RuleSetVersion ruleSet = ruleSetService.history().stream()
                .filter(v -> v.getVersion().equals(version))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No rule version " + version));
        ResponseEntity<String> response = file("fairhome-rules-v" + version + ".json",
                MediaType.APPLICATION_JSON_VALUE, ruleSet.getJson());
        log.debug("FairHome : ExportController : in method ruleVersion : END");
        return response;
    }

    /**
     * The intake register. National IDs are masked to their last four digits: a published list has no
     * business carrying a complete identity number.
     */
    @GetMapping("/exports/applications.csv")
    public ResponseEntity<String> applicationRegister() {
        log.debug("FairHome : ExportController : in method applicationRegister : START");
        StringBuilder csv = new StringBuilder(
                "application_number,channel,status,submitted_at,recorded_at,recorded_by,paper_reference,"
                        + "applicant_name,date_of_birth,gender,national_id_masked,ward,annual_income,"
                        + "years_in_area,differently_abled,ex_serviceman\n");
        for (Application a : applications.findAll(Sort.by("id").ascending())) {
            csv.append(value(a.getApplicationNumber())).append(',')
                    .append(value(a.getChannel())).append(',')
                    .append(value(a.getStatus())).append(',')
                    .append(value(a.getSubmittedAt())).append(',')
                    .append(value(a.getRecordedAt())).append(',')
                    .append(value(a.getRecordedBy())).append(',')
                    .append(value(a.getPaperReference())).append(',')
                    .append(value(a.getFullName())).append(',')
                    .append(value(a.getDateOfBirth())).append(',')
                    .append(value(a.getGender())).append(',')
                    .append(value(a.getMaskedNationalId())).append(',')
                    .append(value(a.getCityOrWard())).append(',')
                    .append(value(a.getAnnualIncome())).append(',')
                    .append(value(a.getYearsInArea())).append(',')
                    .append(value(a.getDifferentlyAbled())).append(',')
                    .append(value(a.getExServiceman())).append('\n');
        }
        ResponseEntity<String> response = file("fairhome-applications.csv", "text/csv", csv.toString());
        log.debug("FairHome : ExportController : in method applicationRegister : END");
        return response;
    }

    @GetMapping("/exports/audit.csv")
    public ResponseEntity<String> auditTrail() {
        log.debug("FairHome : ExportController : in method auditTrail : START");
        StringBuilder csv = new StringBuilder(
                "sequence,occurred_at,action,subject,actor,detail,previous_hash,entry_hash\n");
        List<AuditEvent> events = auditEvents.findAllByOrderBySequenceAsc();
        for (AuditEvent event : events) {
            csv.append(value(event.getSequence())).append(',')
                    .append(value(event.getOccurredAt())).append(',')
                    .append(value(event.getAction())).append(',')
                    .append(value(event.getSubject())).append(',')
                    .append(value(event.getActor())).append(',')
                    .append(value(event.getDetail())).append(',')
                    .append(value(event.getPreviousHash())).append(',')
                    .append(value(event.getEntryHash())).append('\n');
        }
        ResponseEntity<String> response = file("fairhome-audit.csv", "text/csv", csv.toString());
        log.debug("FairHome : ExportController : in method auditTrail : END");
        return response;
    }

    private ResponseEntity<String> file(String name, String contentType, String body) {
        log.debug("FairHome : ExportController : in method file : START");
        ResponseEntity<String> response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType + "; charset=UTF-8")
                .body(body);
        log.debug("FairHome : ExportController : in method file : END");
        return response;
    }

    private String value(Object raw) {
        log.debug("FairHome : ExportController : in method value : START");
        if (raw == null) {
            log.debug("FairHome : ExportController : in method value : END");
            return "";
        }
        String text = raw.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            String escaped = '"' + text.replace("\"", "\"\"").replace("\n", " ") + '"';
            log.debug("FairHome : ExportController : in method value : END");
            return escaped;
        }
        log.debug("FairHome : ExportController : in method value : END");
        return text;
    }
}
