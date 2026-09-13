package com.fairhome.web;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationForm;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.IntakeService;
import com.fairhome.audit.AuditService;
import com.fairhome.config.CurrentOfficer;
import com.fairhome.config.FairHomeProperties;
import com.fairhome.dedup.DedupService;
import com.fairhome.dedup.DuplicateFlag;
import com.fairhome.dedup.DuplicateResolution;
import com.fairhome.draw.Allocation;
import com.fairhome.draw.DrawMode;
import com.fairhome.draw.DrawRun;
import com.fairhome.draw.DrawService;
import com.fairhome.rules.ApplicantPredicates;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.rules.RuleSetService;
import com.fairhome.rules.RuleSetVersion;
import com.fairhome.support.Hashes;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The same operations the HTML screens use, exposed as JSON.
 *
 * <p>The UI is a client of this backend rather than a parallel implementation: both call the same
 * services, so a rule or a duplicate check cannot behave one way through the browser and another way
 * through the API.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final IntakeService intakeService;
    private final ApplicationRepository applications;
    private final DedupService dedupService;
    private final DrawService drawService;
    private final RuleSetService ruleSetService;
    private final AuditService auditService;
    private final FairHomeProperties properties;
    private final ObjectMapper objectMapper;

    public ApiController(IntakeService intakeService, ApplicationRepository applications,
                         DedupService dedupService, DrawService drawService,
                         RuleSetService ruleSetService, AuditService auditService,
                         FairHomeProperties properties, ObjectMapper objectMapper) {
        this.intakeService = intakeService;
        this.applications = applications;
        this.dedupService = dedupService;
        this.drawService = drawService;
        this.ruleSetService = ruleSetService;
        this.auditService = auditService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/applications")
    public ResponseEntity<IntakeService.Receipt> register(@Valid @RequestBody ApplicationForm form) {
        log.debug("FairHome : ApiController : in method register : START");
        IntakeService.Receipt receipt = intakeService.submitOnline(form);
        log.info("FairHome : ApiController : in method register : application submitted : {}",
                receipt.applicationNumber());
        ResponseEntity<IntakeService.Receipt> response = ResponseEntity.status(HttpStatus.CREATED).body(receipt);
        log.debug("FairHome : ApiController : in method register : END");
        return response;
    }

    @PostMapping("/admin/applications/offline")
    public ResponseEntity<IntakeService.Receipt> recordOffline(@Valid @RequestBody ApplicationForm form) {
        log.debug("FairHome : ApiController : in method recordOffline : START");
        IntakeService.Receipt receipt = intakeService.recordOffline(form,
                form.getRecordedBy() == null ? CurrentOfficer.name(properties)
                        : form.getRecordedBy());
        log.info("FairHome : ApiController : in method recordOffline : application submitted : {}",
                receipt.applicationNumber());
        ResponseEntity<IntakeService.Receipt> response = ResponseEntity.status(HttpStatus.CREATED).body(receipt);
        log.debug("FairHome : ApiController : in method recordOffline : END");
        return response;
    }

    /** The applicant-facing "where do I stand and why" endpoint. */
    @GetMapping("/applications/{applicationNumber}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String applicationNumber,
                                                      @RequestParam String referenceCode) {
        log.debug("FairHome : ApiController : in method status : START");
        Application application = applications.findByApplicationNumber(applicationNumber).orElse(null);
        if (application == null || !application.getStatusLookupKey().equalsIgnoreCase(referenceCode)) {
            log.warn("FairHome : ApiController : in method status : not found : {}", applicationNumber);
            ResponseEntity<Map<String, Object>> response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "No application matches that number and reference code."));
            log.debug("FairHome : ApiController : in method status : END");
            return response;
        }

        RuleSetDocument rules = ruleSetService.activeRules();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationNumber", application.getApplicationNumber());
        body.put("applicantName", application.getFullName());
        body.put("nationalId", application.getMaskedNationalId());
        body.put("channel", application.getChannel());
        body.put("submittedAt", application.getSubmittedAt());
        body.put("status", application.getStatus());
        body.put("statusExplanation", application.getStatus().getApplicantExplanation());
        body.put("statusNote", application.getStatusNote());

        RuleSetDocument.Category category = rules.categoryFor(application.getAnnualIncome());
        body.put("incomeCategory", category == null ? null : category.code());
        body.put("qualifiesFor", ApplicantPredicates.keys().stream()
                .filter(key -> ApplicantPredicates.test(key, application, rules))
                .toList());

        DrawRun published = drawService.runVisibleToApplicants().orElse(null);
        if (published == null) {
            body.put("draw", Map.of("published", false,
                    "message", "The draw has not been published yet."));
            ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(body);
            log.debug("FairHome : ApiController : in method status : END");
            return response;
        }

        Allocation allocation = drawService.allocationFor(published.getId(), application.getId())
                .orElse(null);
        Map<String, Object> draw = new LinkedHashMap<>();
        draw.put("published", true);
        draw.put("drawRunId", published.getId());
        draw.put("publishedAt", published.getPublishedAt());
        draw.put("ruleSetVersion", published.getRuleSetVersion());
        draw.put("ruleSetHash", published.getRuleSetHash());
        draw.put("seed", published.getSeed());
        draw.put("resultsHash", published.getResultsHash());
        if (allocation != null) {
            draw.put("outcome", allocation.getOutcome());
            draw.put("category", allocation.getCategoryCode());
            draw.put("pool", allocation.getPoolCode());
            draw.put("rankInCategory", allocation.getRankInCategory());
            draw.put("applicantsInCategory", allocation.getCategoryApplicantCount());
            draw.put("seatNumber", allocation.getSeatNumber());
            draw.put("waitlistPosition", allocation.getWaitlistPosition());
            draw.put("lotteryToken", allocation.getLotteryToken());
            draw.put("reasonCode", allocation.getReasonCode());
            draw.put("reason", allocation.getReasonText());
            draw.put("howThisWasDecided", List.of(allocation.getExplanationLines()));
        }
        body.put("draw", draw);
        ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(body);
        log.debug("FairHome : ApiController : in method status : END");
        return response;
    }

    @GetMapping("/rules")
    public Map<String, Object> activeRules() {
        log.debug("FairHome : ApiController : in method activeRules : START");
        RuleSetVersion version = ruleSetService.activeVersion();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version.getVersion());
        body.put("versionLabel", version.getVersionLabel());
        body.put("contentHash", version.getContentHash());
        body.put("publishedAt", version.getPublishedAt());
        body.put("availablePredicates", ApplicantPredicates.descriptions());
        body.put("rules", ruleSetService.parse(version.getJson()));
        log.debug("FairHome : ApiController : in method activeRules : END");
        return body;
    }

    @GetMapping("/rules/history")
    public List<Map<String, Object>> ruleHistory() {
        log.debug("FairHome : ApiController : in method ruleHistory : START");
        List<Map<String, Object>> history = ruleSetService.history().stream().map(version -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("version", version.getVersion());
            item.put("versionLabel", version.getVersionLabel());
            item.put("contentHash", version.getContentHash());
            item.put("publishedAt", version.getPublishedAt());
            item.put("publishedBy", version.getPublishedBy());
            item.put("changeNote", version.getChangeNote());
            item.put("active", version.isActive());
            return item;
        }).toList();
        log.debug("FairHome : ApiController : in method ruleHistory : END");
        return history;
    }

    @PutMapping("/admin/rules")
    public Map<String, Object> publishRules(@RequestBody Map<String, Object> request) {
        log.debug("FairHome : ApiController : in method publishRules : START");
        Object rules = request.get("rules");
        if (rules == null) {
            throw new IllegalArgumentException("Send the rule book under a \"rules\" property.");
        }
        String note = request.get("note") == null ? null : String.valueOf(request.get("note"));
        String json = ruleSetService.canonicalise(
                ruleSetService.parse(toJson(rules)));
        RuleSetVersion published = ruleSetService.publish(json,
                CurrentOfficer.name(properties), note);
        log.info("FairHome : ApiController : in method publishRules : published rules : version {}",
                published.getVersion());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", published.getVersion());
        body.put("contentHash", published.getContentHash());
        body.put("publishedAt", published.getPublishedAt());
        log.debug("FairHome : ApiController : in method publishRules : END");
        return body;
    }

    private String toJson(Object value) {
        log.debug("FairHome : ApiController : in method toJson : START");
        try {
            String json = objectMapper.writeValueAsString(value);
            log.debug("FairHome : ApiController : in method toJson : END");
            return json;
        } catch (JacksonException e) {
            log.error("FairHome : ApiController : in method toJson : error : {}", e.getMessage(), e);
            throw new IllegalArgumentException("The rules property is not valid JSON", e);
        }
    }

    @GetMapping("/admin/duplicates")
    public List<Map<String, Object>> duplicateQueue() {
        log.debug("FairHome : ApiController : in method duplicateQueue : START");
        List<Map<String, Object>> queue = dedupService.openQueue().stream().map(flag -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("flagId", flag.getId());
            item.put("matchType", flag.getMatchType());
            item.put("matchDescription", flag.getMatchType().getLabel());
            item.put("score", flag.getScore());
            item.put("evidence", flag.getEvidence());
            item.put("newApplication", flag.getNewApplicationNumber());
            item.put("existingApplication", flag.getExistingApplicationNumber());
            item.put("detectedAt", flag.getDetectedAt());
            return item;
        }).toList();
        log.debug("FairHome : ApiController : in method duplicateQueue : END");
        return queue;
    }

    @PostMapping("/admin/duplicates/{flagId}/resolve")
    public Map<String, Object> resolveDuplicate(@PathVariable Long flagId,
                                                @RequestBody Map<String, String> request) {
        log.debug("FairHome : ApiController : in method resolveDuplicate : START");
        DuplicateResolution decision = DuplicateResolution.valueOf(
                request.getOrDefault("decision", "OPEN"));
        DuplicateFlag resolved = dedupService.resolve(flagId, decision, request.get("keep"),
                request.getOrDefault("officer", CurrentOfficer.name(properties)),
                request.get("note"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flagId", resolved.getId());
        body.put("resolution", resolved.getResolution());
        body.put("outcome", resolved.getOutcomeDescription());
        body.put("keptApplication", resolved.getKeptApplicationNumber());
        body.put("resolvedAt", resolved.getResolvedAt());
        log.debug("FairHome : ApiController : in method resolveDuplicate : END");
        return body;
    }

    @PostMapping("/admin/draws")
    public ResponseEntity<Map<String, Object>> runDraw(
            @RequestParam(defaultValue = "DRY_RUN") DrawMode mode,
            @RequestParam(required = false) String note) {
        log.debug("FairHome : ApiController : in method runDraw : START");
        DrawRun run = drawService.run(mode, CurrentOfficer.name(properties), note);
        log.info("FairHome : ApiController : in method runDraw : draw completed : run {} mode {}",
                run.getId(), mode);
        ResponseEntity<Map<String, Object>> response = ResponseEntity.status(HttpStatus.CREATED)
                .body(describeRun(run));
        log.debug("FairHome : ApiController : in method runDraw : END");
        return response;
    }

    @GetMapping("/draws")
    public List<Map<String, Object>> draws() {
        log.debug("FairHome : ApiController : in method draws : START");
        List<Map<String, Object>> runs = drawService.allRuns().stream().map(this::describeRun).toList();
        log.debug("FairHome : ApiController : in method draws : END");
        return runs;
    }

    @GetMapping("/draws/{runId}")
    public Map<String, Object> draw(@PathVariable Long runId) {
        log.debug("FairHome : ApiController : in method draw : START");
        DrawRun run = drawService.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        Map<String, Object> body = describeRun(run);
        body.put("categorySummary", drawService.categorySummary(runId));
        body.put("workings", List.of(run.getQuotaWorkings().split("\n")));
        log.debug("FairHome : ApiController : in method draw : END");
        return body;
    }

    @PostMapping("/admin/draws/{runId}/publish")
    public Map<String, Object> publish(@PathVariable Long runId) {
        log.debug("FairHome : ApiController : in method publish : START");
        DrawRun run = drawService.publish(runId, CurrentOfficer.name(properties));
        log.info("FairHome : ApiController : in method publish : published draw run : {}", runId);
        Map<String, Object> body = describeRun(run);
        log.debug("FairHome : ApiController : in method publish : END");
        return body;
    }

    /** The public result list, available only once a final draw has been published. */
    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> results() {
        log.debug("FairHome : ApiController : in method results : START");
        DrawRun published = drawService.runVisibleToApplicants().orElse(null);
        if (published == null) {
            ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(Map.of("published", false,
                    "message", "No draw result has been published yet."));
            log.debug("FairHome : ApiController : in method results : END");
            return response;
        }
        Map<String, Object> body = describeRun(published);
        body.put("categorySummary", drawService.categorySummary(published.getId()));
        body.put("allotments", drawService.resultsFor(published.getId()).stream()
                .filter(a -> a.getSeatNumber() != null)
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("seatNumber", a.getSeatNumber());
                    item.put("applicationNumber", a.getApplicationNumber());
                    item.put("applicantName", a.getApplicantName());
                    item.put("category", a.getCategoryCode());
                    item.put("pool", a.getPoolCode());
                    item.put("lotteryToken", a.getLotteryToken());
                    return item;
                }).toList());
        ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(body);
        log.debug("FairHome : ApiController : in method results : END");
        return response;
    }

    /** Recomputes one lottery position from the published seed, for independent verification. */
    @GetMapping("/verify")
    public Map<String, Object> verify(@RequestParam String applicationNumber) {
        log.debug("FairHome : ApiController : in method verify : START");
        RuleSetDocument rules = ruleSetService.activeRules();
        String input = rules.draw().seed() + ":" + applicationNumber;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("algorithm", "SHA-256 hex of seed + \":\" + applicationNumber, sorted ascending");
        body.put("seed", rules.draw().seed());
        body.put("input", input);
        body.put("lotteryToken", Hashes.sha256Hex(input));
        body.put("shellEquivalent", "printf '%s' \"" + input + "\" | sha256sum");
        log.debug("FairHome : ApiController : in method verify : END");
        return body;
    }

    @GetMapping("/admin/audit/verify")
    public AuditService.ChainCheck verifyAuditChain() {
        log.debug("FairHome : ApiController : in method verifyAuditChain : START");
        AuditService.ChainCheck check = auditService.verifyChain();
        log.debug("FairHome : ApiController : in method verifyAuditChain : END");
        return check;
    }

    private Map<String, Object> describeRun(DrawRun run) {
        log.debug("FairHome : ApiController : in method describeRun : START");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", run.getId());
        body.put("mode", run.getMode());
        body.put("executedAt", run.getExecutedAt());
        body.put("executedBy", run.getExecutedBy());
        body.put("ruleSetVersion", run.getRuleSetVersion());
        body.put("ruleSetHash", run.getRuleSetHash());
        body.put("seed", run.getSeed());
        body.put("totalFlats", run.getTotalFlats());
        body.put("applicationsConsidered", run.getApplicationsConsidered());
        body.put("allotted", run.getAllotted());
        body.put("waitlisted", run.getWaitlisted());
        body.put("notSelected", run.getNotSelected());
        body.put("excluded", run.getExcluded());
        body.put("vacantSeats", run.getVacantSeats());
        body.put("resultsHash", run.getResultsHash());
        body.put("published", run.isPublished());
        body.put("publishedAt", run.getPublishedAt());
        log.debug("FairHome : ApiController : in method describeRun : END");
        return body;
    }
}
