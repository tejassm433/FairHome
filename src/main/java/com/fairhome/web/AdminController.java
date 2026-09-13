package com.fairhome.web;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationForm;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.ApplicationStatus;
import com.fairhome.application.Channel;
import com.fairhome.application.IntakeException;
import com.fairhome.application.IntakeService;
import com.fairhome.audit.AuditService;
import com.fairhome.config.CurrentOfficer;
import com.fairhome.config.FairHomeProperties;
import com.fairhome.dedup.DedupService;
import com.fairhome.dedup.DuplicateFlag;
import com.fairhome.dedup.DuplicateResolution;
import com.fairhome.draw.Allocation;
import com.fairhome.draw.AllocationRepository;
import com.fairhome.draw.DrawMode;
import com.fairhome.draw.DrawRun;
import com.fairhome.draw.DrawService;
import com.fairhome.draw.Outcome;
import com.fairhome.rules.ApplicantPredicates;
import com.fairhome.rules.Gender;
import com.fairhome.rules.RuleSetService;
import com.fairhome.rules.RuleSetVersion;
import com.fairhome.rules.RuleValidationException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The officer-facing console: offline entry, the duplicate queue, the rule book, draws and the audit
 * trail. Reached only after a username and password check; see {@link com.fairhome.config.SecurityConfig}.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final IntakeService intakeService;
    private final ApplicationRepository applications;
    private final DedupService dedupService;
    private final DrawService drawService;
    private final AllocationRepository allocations;
    private final RuleSetService ruleSetService;
    private final AuditService auditService;
    private final FairHomeProperties properties;

    public AdminController(IntakeService intakeService, ApplicationRepository applications,
                           DedupService dedupService, DrawService drawService,
                           AllocationRepository allocations, RuleSetService ruleSetService,
                           AuditService auditService, FairHomeProperties properties) {
        this.intakeService = intakeService;
        this.applications = applications;
        this.dedupService = dedupService;
        this.drawService = drawService;
        this.allocations = allocations;
        this.ruleSetService = ruleSetService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @ModelAttribute("officer")
    public String officer() {
        return CurrentOfficer.name(properties);
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String loggedOut,
                        Model model) {
        model.addAttribute("navPage", "admin-login");
        model.addAttribute("loginError", error != null);
        model.addAttribute("loggedOut", loggedOut != null);
        return "admin/login";
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("navPage", "admin-dashboard");
        model.addAttribute("total", applications.count());
        model.addAttribute("online", applications.countByChannel(Channel.ONLINE));
        model.addAttribute("offline", applications.countByChannel(Channel.OFFLINE));
        model.addAttribute("inDraw", applications.countByStatus(ApplicationStatus.SUBMITTED));
        model.addAttribute("held", applications.countByStatus(ApplicationStatus.PENDING_DUPLICATE_REVIEW));
        model.addAttribute("rejectedDuplicates",
                applications.countByStatus(ApplicationStatus.REJECTED_DUPLICATE));
        model.addAttribute("withdrawn", applications.countByStatus(ApplicationStatus.WITHDRAWN));
        model.addAttribute("openFlags", dedupService.openCount());
        model.addAttribute("ruleVersion", ruleSetService.activeVersion());
        model.addAttribute("rules", ruleSetService.activeRules());
        model.addAttribute("runs", drawService.allRuns());
        model.addAttribute("publishedRun", drawService.publishedRun().orElse(null));
        model.addAttribute("auditCount", auditService.count());
        return "admin/dashboard";
    }

    @GetMapping("/offline-entry")
    public String offlineEntryForm(Model model) {
        if (!model.containsAttribute("form")) {
            ApplicationForm form = new ApplicationForm();
            form.setRecordedBy(CurrentOfficer.name(properties));
            form.setPaperSubmittedOn(LocalDate.now());
            model.addAttribute("form", form);
        }
        addFormReferenceData(model);
        return "admin/offline-entry";
    }

    @PostMapping("/offline-entry")
    public String recordOffline(@Valid @ModelAttribute("form") ApplicationForm form,
                                BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            addFormReferenceData(model);
            return "admin/offline-entry";
        }
        try {
            IntakeService.Receipt receipt = intakeService.recordOffline(form,
                    form.getRecordedBy() == null ? CurrentOfficer.name(properties)
                            : form.getRecordedBy());
            model.addAttribute("receipt", receipt);
            model.addAttribute("navPage", "admin-offline");
            return "admin/offline-receipt";
        } catch (IntakeException e) {
            e.getFieldErrors().forEach((field, message) -> {
                if (binding.getFieldError(field) == null) {
                    binding.rejectValue(field, "intake", message);
                }
            });
            if (!binding.hasErrors()) {
                binding.reject("intake", e.getMessage());
            }
            addFormReferenceData(model);
            return "admin/offline-entry";
        }
    }

    @GetMapping("/duplicates")
    public String duplicates(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("navPage", "admin-duplicates");
        Page<DuplicateFlag> flags = dedupService.openQueue(PageRequest.of(Math.max(page, 0), 8));
        List<DuplicateQueueItem> queue = new ArrayList<>();
        for (DuplicateFlag flag : flags.getContent()) {
            Application newApp = applications.findById(flag.getNewApplicationId()).orElse(null);
            Application existingApp = applications.findById(flag.getExistingApplicationId()).orElse(null);
            if (newApp != null && existingApp != null) {
                queue.add(new DuplicateQueueItem(flag, newApp, existingApp,
                        buildComparison(newApp, existingApp)));
            }
        }
        model.addAttribute("queue", queue);
        model.addAttribute("results", new PageImpl<>(queue, flags.getPageable(), flags.getTotalElements()));
        model.addAttribute("openCount", flags.getTotalElements());
        model.addAttribute("pageNumber", flags.getNumber());
        return "admin/duplicates";
    }

    @GetMapping("/duplicates/{flagId}/resolve")
    public String resolveDuplicateGet(@PathVariable Long flagId, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error",
                "A decision has to be submitted from the queue, not opened as a link. "
                        + "Use the buttons on the card for flag " + flagId + ".");
        return "redirect:/admin/duplicates";
    }

    @PostMapping("/duplicates/{flagId}/resolve")
    public String resolveDuplicate(@PathVariable Long flagId,
                                   @RequestParam DuplicateResolution decision,
                                   @RequestParam(required = false) String keep,
                                   @RequestParam(required = false) String note,
                                   @RequestParam(defaultValue = "0") int page,
                                   RedirectAttributes redirect) {
        try {
            DuplicateFlag resolved = dedupService.resolve(flagId, decision, keep,
                    CurrentOfficer.name(properties), note);
            redirect.addFlashAttribute("message", decision == DuplicateResolution.CONFIRMED_DUPLICATE
                    ? "Confirmed as a duplicate. " + resolved.getKeptApplicationNumber()
                    + " stays in the draw."
                    : "Recorded as two different people. Both applications are back in the draw.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/duplicates?page=" + Math.max(page, 0);
    }

    @GetMapping("/applications")
    public String applicationList(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) ApplicationStatus status,
                                  @RequestParam(required = false) Channel channel,
                                  @RequestParam(defaultValue = "0") int page,
                                  Model model) {
        Page<Application> results = applications.search(q, status, channel, PageRequest.of(page, 25));
        model.addAttribute("navPage", "admin-applications");
        model.addAttribute("results", results);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("channel", channel);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("channels", Channel.values());
        model.addAttribute("pageNumber", page);
        return "admin/applications";
    }

    @GetMapping("/applications/{applicationNumber}")
    @Transactional(readOnly = true)
    public String applicationDetail(@PathVariable String applicationNumber, Model model) {
        Application application = applications.findByApplicationNumber(applicationNumber)
                .orElseThrow(() -> new IllegalArgumentException("No application " + applicationNumber));
        model.addAttribute("navPage", "admin-applications");
        // Not "application": Thymeleaf/Spring bind that name to the servlet context, which 500s the page.
        model.addAttribute("app", application);
        model.addAttribute("rules", ruleSetService.activeRules());
        model.addAttribute("category",
                ruleSetService.activeRules().categoryFor(application.getAnnualIncome()));
        model.addAttribute("flags", dedupService.flagsFor(application.getId()));

        Map<String, Boolean> qualifies = new LinkedHashMap<>();
        for (String key : ApplicantPredicates.keys()) {
            qualifies.put(key, ApplicantPredicates.test(key, application, ruleSetService.activeRules()));
        }
        model.addAttribute("qualifies", qualifies);

        List<Allocation> history = new ArrayList<>();
        for (DrawRun run : drawService.allRuns()) {
            drawService.allocationFor(run.getId(), application.getId()).ifPresent(alloc -> {
                alloc.getExplanation();
                history.add(alloc);
            });
        }
        model.addAttribute("allocationHistory", history);
        model.addAttribute("runs", drawService.allRuns());
        return "admin/application-detail";
    }

    @PostMapping("/applications/{applicationNumber}/withdraw")
    public String withdraw(@PathVariable String applicationNumber,
                           @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        intakeService.withdraw(applicationNumber, CurrentOfficer.name(properties), reason);
        redirect.addFlashAttribute("message", applicationNumber + " marked as withdrawn.");
        return "redirect:/admin/applications/" + applicationNumber;
    }

    @GetMapping("/rules")
    public String rulesEditor(Model model) {
        RuleSetVersion active = ruleSetService.activeVersion();
        model.addAttribute("navPage", "admin-rules");
        if (!model.containsAttribute("json")) {
            model.addAttribute("json", active.getJson());
        }
        model.addAttribute("active", active);
        model.addAttribute("history", ruleSetService.history());
        model.addAttribute("predicates", ApplicantPredicates.descriptions());
        model.addAttribute("hasRuns", !drawService.allRuns().isEmpty());
        return "admin/rules";
    }

    @PostMapping("/rules")
    public String publishRules(@RequestParam String json, @RequestParam(required = false) String note,
                               RedirectAttributes redirect, Model model) {
        try {
            RuleSetVersion published = ruleSetService.publish(json,
                    CurrentOfficer.name(properties), note);
            redirect.addFlashAttribute("message", "Published rule version " + published.getVersion()
                    + " with content hash " + published.getContentHash() + ".");
            return "redirect:/admin/rules";
        } catch (RuleValidationException e) {
            model.addAttribute("problems", e.getProblems());
            model.addAttribute("json", json);
            model.addAttribute("note", note);
            return rulesEditor(model);
        }
    }

    @PostMapping("/rules/reset")
    public String resetRules(RedirectAttributes redirect) {
        try {
            RuleSetVersion published = ruleSetService.publish(ruleSetService.defaultRuleJson(),
                    CurrentOfficer.name(properties),
                    "Reverted to the rule book shipped with the application");
            redirect.addFlashAttribute("message", "Reverted to the shipped rule book as version "
                    + published.getVersion() + ".");
        } catch (RuleValidationException e) {
            redirect.addFlashAttribute("error", String.join("; ", e.getProblems()));
        }
        return "redirect:/admin/rules";
    }

    @GetMapping("/draw")
    public String drawConsole(Model model) {
        model.addAttribute("navPage", "admin-draw");
        model.addAttribute("runs", drawService.allRuns());
        model.addAttribute("openFlags", dedupService.openCount());
        model.addAttribute("inDraw", applications.countByStatus(ApplicationStatus.SUBMITTED));
        model.addAttribute("rules", ruleSetService.activeRules());
        model.addAttribute("ruleVersion", ruleSetService.activeVersion());
        model.addAttribute("publishedRun", drawService.publishedRun().orElse(null));
        return "admin/draw";
    }

    @PostMapping("/draw/run")
    public String runDraw(@RequestParam DrawMode mode, @RequestParam(required = false) String note,
                          RedirectAttributes redirect) {
        try {
            DrawRun run = drawService.run(mode, CurrentOfficer.name(properties), note);
            redirect.addFlashAttribute("message", mode.getLabel() + " " + run.getId()
                    + " completed: " + run.getAllotted() + " flats allotted, " + run.getWaitlisted()
                    + " waitlisted. Results hash " + run.getShortResultsHash() + ".");
            return "redirect:/admin/draw/" + run.getId();
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/draw";
        }
    }

    @GetMapping("/draw/{runId}")
    public String runDetail(@PathVariable Long runId,
                            @RequestParam(required = false) Outcome outcome,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String q,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        DrawRun run = drawService.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("No draw run " + runId));
        model.addAttribute("navPage", "admin-draw");
        model.addAttribute("run", run);
        model.addAttribute("summary", drawService.categorySummary(runId));
        model.addAttribute("rules", ruleSetService.rulesForVersion(run.getRuleSetVersion()));
        model.addAttribute("results",
                allocations.search(runId, outcome, category, q, PageRequest.of(page, 40)));
        model.addAttribute("outcomes", Outcome.values());
        model.addAttribute("outcome", outcome);
        model.addAttribute("category", category);
        model.addAttribute("q", q);
        model.addAttribute("pageNumber", page);
        return "admin/draw-detail";
    }

    @PostMapping("/draw/{runId}/publish")
    public String publishRun(@PathVariable Long runId, RedirectAttributes redirect) {
        try {
            drawService.publish(runId, CurrentOfficer.name(properties));
            redirect.addFlashAttribute("message", "Draw run " + runId
                    + " is now the published official result and is visible to applicants.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/draw/" + runId;
    }

    @PostMapping("/draw/{runId}/withdraw")
    public String withdrawRun(@PathVariable Long runId, @RequestParam(required = false) String reason,
                              RedirectAttributes redirect) {
        try {
            drawService.withdrawPublication(runId, CurrentOfficer.name(properties), reason);
            redirect.addFlashAttribute("message", "Publication of draw run " + runId
                    + " withdrawn. The reason is on the audit trail.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/draw/" + runId;
    }

    @PostMapping("/draw/{runId}/delete")
    public String deleteRun(@PathVariable Long runId, RedirectAttributes redirect) {
        try {
            drawService.deleteRun(runId, CurrentOfficer.name(properties));
            redirect.addFlashAttribute("message", "Deleted draw run " + runId + ".");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/draw";
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("navPage", "admin-audit");
        model.addAttribute("events", auditService.page(PageRequest.of(page, 50)));
        model.addAttribute("pageNumber", page);
        model.addAttribute("chain", auditService.verifyChain());
        return "admin/audit";
    }

    private void addFormReferenceData(Model model) {
        model.addAttribute("navPage", "admin-offline");
        model.addAttribute("rules", ruleSetService.activeRules());
        model.addAttribute("genders", Gender.values());
    }

    /** Field-by-field comparison shown side by side in the duplicate queue. */
    private List<FieldComparison> buildComparison(Application a, Application b) {
        List<FieldComparison> rows = new ArrayList<>();
        rows.add(compare("Name as written", a.getFullName(), b.getFullName()));
        rows.add(compare("Name normalised", a.getNormalisedName(), b.getNormalisedName()));
        rows.add(compare("Date of birth", String.valueOf(a.getDateOfBirth()),
                String.valueOf(b.getDateOfBirth())));
        rows.add(compare("National ID", a.getMaskedNationalId(), b.getMaskedNationalId()));
        rows.add(compare("Phone", a.getPhone(), b.getPhone()));
        rows.add(compare("Email", a.getEmail(), b.getEmail()));
        rows.add(compare("Address", a.getAddressLine(), b.getAddressLine()));
        rows.add(compare("Ward", a.getCityOrWard(), b.getCityOrWard()));
        rows.add(compare("Annual income", String.valueOf(a.getAnnualIncome()),
                String.valueOf(b.getAnnualIncome())));
        rows.add(compare("Years in area", String.valueOf(a.getYearsInArea()),
                String.valueOf(b.getYearsInArea())));
        rows.add(compare("Channel", a.getChannel().getLabel(), b.getChannel().getLabel()));
        rows.add(compare("Paper reference", a.getPaperReference(), b.getPaperReference()));
        return rows;
    }

    private FieldComparison compare(String label, String left, String right) {
        String l = left == null ? "-" : left;
        String r = right == null ? "-" : right;
        return new FieldComparison(label, l, r, l.equalsIgnoreCase(r));
    }

    public record FieldComparison(String label, String newValue, String existingValue, boolean same) {
    }

    public record DuplicateQueueItem(DuplicateFlag flag, Application newApplication,
                                     Application existingApplication,
                                     List<FieldComparison> comparison) {
    }
}
