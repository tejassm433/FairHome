package com.fairhome.draw;

import com.fairhome.application.Application;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.testkit.ApplicationFixtures;
import com.fairhome.testkit.JsonCaseExecutor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AllocationJsonTest extends JsonCaseExecutor {

    private final AllocationEngine engine = new AllocationEngine();

    @Override
    protected String caseDirectory() {
        return "cases/allocation";
    }

    @Override
    protected Object execute(JsonNode input) {
        RuleSetDocument rules = mapper.treeToValue(input.get("rules"), RuleSetDocument.class);
        List<Application> applications = ApplicationFixtures.list(input.get("applications"));
        LocalDate drawDate = LocalDate.parse(input.get("drawDate").asString());

        AllocationEngine.Result first = engine.run(rules, applications, drawDate);
        AllocationEngine.Result second = engine.run(rules, applications, drawDate);

        Map<String, Object> byApplication = first.decisions().stream().collect(Collectors.toMap(
                AllocationEngine.Decision::applicationNumber,
                this::decisionView,
                (a, b) -> a,
                LinkedHashMap::new));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allotted", first.allotted());
        out.put("waitlisted", first.waitlisted());
        out.put("notSelected", first.notSelected());
        out.put("excluded", first.excluded());
        out.put("vacantSeats", first.vacantSeats());
        out.put("consideredInDraw", first.consideredInDraw());
        out.put("repeatable", first.resultsHash().equals(second.resultsHash()));
        out.put("resultsHash", first.resultsHash());
        out.put("byApplication", byApplication);
        return out;
    }

    private Map<String, Object> decisionView(AllocationEngine.Decision decision) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("outcome", decision.outcome().name());
        view.put("categoryCode", decision.categoryCode());
        view.put("poolCode", decision.poolCode());
        view.put("rankInCategory", decision.rankInCategory());
        view.put("seatNumber", decision.seatNumber());
        view.put("waitlistPosition", decision.waitlistPosition());
        view.put("reasonCode", decision.reasonCode());
        view.put("lotteryToken", decision.lotteryToken());
        return view;
    }
}
