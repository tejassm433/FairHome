package com.fairhome.support;

import com.fairhome.testkit.JsonCaseExecutor;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public class NameMatchingJsonTest extends JsonCaseExecutor {

    @Override
    protected String caseDirectory() {
        return "cases/name-matching";
    }

    @Override
    protected Object execute(JsonNode input) {
        String a = input.get("a").asString();
        String b = input.get("b").asString();
        String normalisedA = NameMatching.normalise(a);
        String normalisedB = NameMatching.normalise(b);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("normalisedA", normalisedA);
        out.put("normalisedB", normalisedB);
        out.put("sameAfterNormalising", normalisedA.equals(normalisedB));
        out.put("similarity", NameMatching.similarity(normalisedA, normalisedB));
        if (input.has("phone")) {
            out.put("normalisedPhone", NameMatching.normalisePhone(input.get("phone").asString()));
        }
        if (input.has("email")) {
            out.put("normalisedEmail", NameMatching.normaliseEmail(input.get("email").asString()));
        }
        return out;
    }
}
