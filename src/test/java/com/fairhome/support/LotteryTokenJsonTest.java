package com.fairhome.support;

import com.fairhome.testkit.JsonCaseExecutor;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public class LotteryTokenJsonTest extends JsonCaseExecutor {

    @Override
    protected String caseDirectory() {
        return "cases/lottery";
    }

    @Override
    protected Object execute(JsonNode input) {
        String seed = input.get("seed").asString();
        String applicationNumber = input.get("applicationNumber").asString();
        String hashed = seed + ":" + applicationNumber;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("input", hashed);
        out.put("lotteryToken", Hashes.sha256Hex(hashed));
        return out;
    }
}
