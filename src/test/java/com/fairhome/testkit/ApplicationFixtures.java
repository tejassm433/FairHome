package com.fairhome.testkit;

import com.fairhome.application.Application;
import com.fairhome.application.ApplicationStatus;
import com.fairhome.application.Channel;
import com.fairhome.rules.Gender;
import com.fairhome.support.NameMatching;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Turns the compact application objects used in JSON cases into {@link Application} entities. */
public final class ApplicationFixtures {

    private ApplicationFixtures() {
    }

    public static List<Application> list(JsonNode array) {
        List<Application> applications = new ArrayList<>();
        long generatedId = 1;
        for (JsonNode node : array) {
            applications.add(one(node, generatedId++));
        }
        return applications;
    }

    public static Application one(JsonNode node) {
        return one(node, node.has("id") ? node.get("id").asLong() : 1L);
    }

    public static Application one(JsonNode node, long fallbackId) {
        Application application = new Application();
        application.setId(node.has("id") ? node.get("id").asLong() : fallbackId);
        application.setApplicationNumber(text(node, "applicationNumber", "FH-TEST-" + application.getId()));
        application.setChannel(enumOr(node, "channel", Channel.class, Channel.ONLINE));
        application.setStatus(enumOr(node, "status", ApplicationStatus.class, ApplicationStatus.SUBMITTED));
        application.setSubmittedAt(node.has("submittedAt")
                ? Instant.parse(node.get("submittedAt").asString())
                : Instant.parse("2026-02-01T00:00:00Z"));
        application.setRecordedAt(Instant.parse("2026-02-01T00:00:00Z"));
        String name = text(node, "fullName", "Test Applicant");
        application.setFullName(name);
        application.setNormalisedName(node.has("normalisedName")
                ? node.get("normalisedName").asString()
                : NameMatching.normalise(name));
        application.setDateOfBirth(LocalDate.parse(text(node, "dateOfBirth", "1990-01-15")));
        application.setGender(enumOr(node, "gender", Gender.class, Gender.OTHER));
        application.setNationalId(text(node, "nationalId", "234567890124"));
        application.setPhone(text(node, "phone", null));
        application.setNormalisedPhone(NameMatching.normalisePhone(application.getPhone()));
        application.setEmail(text(node, "email", null));
        application.setNormalisedEmail(NameMatching.normaliseEmail(application.getEmail()));
        application.setAddressLine(text(node, "addressLine", "1 Test Street"));
        application.setCityOrWard(text(node, "cityOrWard", "Ward 14, North Zone"));
        application.setAnnualIncome(new BigDecimal(text(node, "annualIncome", "250000")));
        application.setYearsInArea(node.has("yearsInArea") ? node.get("yearsInArea").asInt() : 0);
        application.setDifferentlyAbled(bool(node, "differentlyAbled", false));
        application.setExServiceman(bool(node, "exServiceman", false));
        application.setFirstTimeHomeBuyer(bool(node, "firstTimeHomeBuyer", true));
        application.setStatusLookupKey("TESTKEY1");
        application.setStatusNote(text(node, "statusNote", null));
        application.setPaperReference(text(node, "paperReference", null));
        application.setRecordedBy(text(node, "recordedBy", null));
        return application;
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asString() : fallback;
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        return node.has(field) ? node.get(field).asBoolean() : fallback;
    }

    private static <E extends Enum<E>> E enumOr(JsonNode node, String field, Class<E> type, E fallback) {
        if (!node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        return Enum.valueOf(type, node.get(field).asString());
    }
}
