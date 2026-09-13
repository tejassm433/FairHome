package com.fairhome.support;

import com.fairhome.testkit.JsonCaseExecutor;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public class NationalIdJsonTest extends JsonCaseExecutor {

    @Override
    protected String caseDirectory() {
        return "cases/national-id";
    }

    @Override
    protected Object execute(JsonNode input) {
        String raw = input.get("raw").asString();
        String canonical = NationalId.canonicalise(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("canonical", canonical);
        out.put("valid", NationalId.isStructurallyValid(canonical));
        out.put("last4", NationalId.last4(canonical));
        out.put("masked", NationalId.masked(canonical));
        return out;
    }
}
