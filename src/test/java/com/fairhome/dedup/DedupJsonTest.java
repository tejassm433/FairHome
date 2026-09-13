package com.fairhome.dedup;

import com.fairhome.application.Application;
import com.fairhome.rules.RuleSetDocument;
import com.fairhome.testkit.ApplicationFixtures;
import com.fairhome.testkit.JsonCaseExecutor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DedupJsonTest extends JsonCaseExecutor {

    private final DedupService service = new DedupService(null, null, null);

    @Override
    protected String caseDirectory() {
        return "cases/dedup";
    }

    @Override
    protected Object execute(JsonNode input) {
        Application candidate = ApplicationFixtures.one(input.get("candidate"), 99L);
        List<Application> existing = ApplicationFixtures.list(input.get("existing"));
        RuleSetDocument.DuplicateDetection cfg = mapper.treeToValue(
                input.get("config"), RuleSetDocument.DuplicateDetection.class);

        List<DedupService.Candidate> matches = service.findCandidatesAgainst(candidate, existing, cfg);
        List<Map<String, Object>> view = new ArrayList<>();
        for (DedupService.Candidate match : matches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("existingApplicationNumber", match.existing().getApplicationNumber());
            row.put("matchType", match.matchType().name());
            row.put("score", match.score());
            view.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matchCount", view.size());
        out.put("matches", view);
        return out;
    }
}
