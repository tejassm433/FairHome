package com.fairhome.rules;

import com.fairhome.testkit.JsonCaseExecutor;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RuleSetJsonTest extends JsonCaseExecutor {

    private final RuleSetService service = new RuleSetService(null, null, mapper);

    @Override
    protected String caseDirectory() {
        return "cases/rules";
    }

    @Override
    protected Object execute(JsonNode input) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            RuleSetDocument document = mapper.treeToValue(input, RuleSetDocument.class);
            service.validate(document);
            out.put("valid", true);
            out.put("problems", List.of());
            RuleSetDocument.Category category = input.has("classifyIncome")
                    ? document.categoryFor(new java.math.BigDecimal(input.get("classifyIncome").asString()))
                    : null;
            out.put("categoryForIncome", category == null ? null : category.code());
        } catch (RuleValidationException e) {
            out.put("valid", false);
            out.put("problems", e.getProblems());
            out.put("categoryForIncome", null);
        }
        return out;
    }
}
