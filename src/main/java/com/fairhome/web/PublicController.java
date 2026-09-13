package com.fairhome.web;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationForm;
import com.fairhome.application.ApplicationRepository;
import com.fairhome.application.ApplicationStatus;
import com.fairhome.application.Channel;
import com.fairhome.application.IntakeException;
import com.fairhome.application.IntakeService;
import com.fairhome.draw.Allocation;
import com.fairhome.draw.DrawRun;
import com.fairhome.draw.DrawService;
import com.fairhome.rules.ApplicantPredicates;
import com.fairhome.rules.Gender;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.rules.RuleSetService;
import com.fairhome.rules.RuleSetVersion;
import com.fairhome.support.Hashes;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** Everything a member of the public can reach: apply, look up a placement, read the rules. */
@Controller
public class PublicController {

    private final IntakeService intakeService;
    private final ApplicationRepository applications;
    private final RuleSetService ruleSetService;
    private final DrawService drawService;

    public PublicController(IntakeService intakeService, ApplicationRepository applications,
                            RuleSetService ruleSetService, DrawService drawService) {
        this.intakeService = intakeService;
        this.applications = applications;
        this.ruleSetService = ruleSetService;
        this.drawService = drawService;
    }

    @GetMapping("/")
    public String home(Model model) {
        RuleSetDocument rules = ruleSetService.activeRules();
        model.addAttribute("rules", rules);
        model.addAttribute("totalApplications", applications.count());
        model.addAttribute("onlineCount", applications.countByChannel(Channel.ONLINE));
        model.addAttribute("offlineCount", applications.countByChannel(Channel.OFFLINE));
        model.addAttribute("publishedRun", drawService.publishedRun().orElse(null));
        model.addAttribute("navPage", "home");
        return "public/home";
    }

    @GetMapping("/hld")
    public String hld(Model model) {
        model.addAttribute("navPage", "hld");
        return "public/hld";
    }

    @GetMapping("/apply")
    public String applyForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ApplicationForm());
        }
        addFormReferenceData(model);
        model.addAttribute("navPage", "apply");
        return "public/apply";
    }

    @PostMapping("/apply")
    public String submit(@Valid @ModelAttribute("form") ApplicationForm form, BindingResult binding,
                         Model model) {
        if (binding.hasErrors()) {
            addFormReferenceData(model);
            model.addAttribute("navPage", "apply");
            return "public/apply";
        }
        try {
            IntakeService.Receipt receipt = intakeService.submitOnline(form);
            model.addAttribute("receipt", receipt);
            model.addAttribute("navPage", "apply");
            return "public/receipt";
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
            model.addAttribute("navPage", "apply");
            return "public/apply";
        }
    }

    @GetMapping("/status")
    public String statusForm(Model model) {
        model.addAttribute("navPage", "status");
        return "public/status";
    }

    /**
     * Answers "where do I stand and why".
     *
     * <p>Gated on the application number plus the reference code printed on the receipt, so an
     * applicant reads their own placement and not their neighbour's. Once a result is published the
     * page shows the full reasoning trace the engine recorded, not a bare outcome.
     */
    @PostMapping("/status")
    public String status(@RequestParam String applicationNumber, @RequestParam String referenceCode,
                         Model model) {
        model.addAttribute("navPage", "status");
        model.addAttribute("applicationNumber", applicationNumber);

        Optional<Application> found = applications
                .findByApplicationNumber(applicationNumber == null ? "" : applicationNumber.trim());

        if (found.isEmpty() || referenceCode == null
                || !found.get().getStatusLookupKey().equalsIgnoreCase(referenceCode.trim())) {
            model.addAttribute("error", "We could not match that application number and reference code. "
                    + "Please check both against your receipt.");
            return "public/status";
        }

        Application application = found.get();
        RuleSetDocument rules = ruleSetService.activeRules();
        RuleSetDocument.Category category = rules.categoryFor(application.getAnnualIncome());

        model.addAttribute("application", application);
        model.addAttribute("rules", rules);
        model.addAttribute("category", category);
        model.addAttribute("localResidentQualified",
                ApplicantPredicates.test("LOCAL_RESIDENT", application, rules));

        DrawRun published = drawService.runVisibleToApplicants().orElse(null);
        model.addAttribute("run", published);
        if (published != null) {
            Allocation allocation = drawService
                    .allocationFor(published.getId(), application.getId()).orElse(null);
            model.addAttribute("allocation", allocation);
        }
        return "public/status-result";
    }

    @GetMapping("/rules")
    public String rules(Model model) {
        RuleSetVersion version = ruleSetService.activeVersion();
        model.addAttribute("version", version);
        model.addAttribute("rules", ruleSetService.parse(version.getJson()));
        model.addAttribute("predicates", ApplicantPredicates.descriptions());
        model.addAttribute("history", ruleSetService.history());
        model.addAttribute("navPage", "rules");
        return "public/rules";
    }

    /**
     * Lets anyone reproduce a single lottery position by hand. Given the published seed and an
     * application number, the same SHA-256 that decided the draw is computed in front of them.
     */
    @GetMapping("/verify")
    public String verify(@RequestParam(required = false) String applicationNumber, Model model) {
        RuleSetDocument rules = ruleSetService.activeRules();
        model.addAttribute("rules", rules);
        model.addAttribute("navPage", "rules");
        model.addAttribute("applicationNumber", applicationNumber);
        if (applicationNumber != null && !applicationNumber.isBlank()) {
            String input = rules.draw().seed() + ":" + applicationNumber.trim();
            model.addAttribute("hashInput", input);
            model.addAttribute("token", Hashes.sha256Hex(input));
            model.addAttribute("shellEquivalent", "printf '%s' \"" + input + "\" | sha256sum");
        }
        drawService.publishedRun().ifPresent(run -> model.addAttribute("run", run));
        return "public/verify";
    }

    @GetMapping("/results")
    public String results(Model model) {
        DrawRun published = drawService.runVisibleToApplicants().orElse(null);
        model.addAttribute("run", published);
        model.addAttribute("navPage", "results");
        if (published != null) {
            model.addAttribute("summary", drawService.categorySummary(published.getId()));
            model.addAttribute("rules", ruleSetService.rulesForVersion(published.getRuleSetVersion()));
        }
        return "public/results";
    }

    private void addFormReferenceData(Model model) {
        RuleSetDocument rules = ruleSetService.activeRules();
        model.addAttribute("rules", rules);
        model.addAttribute("genders", Gender.values());
        model.addAttribute("statuses", ApplicationStatus.values());
    }
}
