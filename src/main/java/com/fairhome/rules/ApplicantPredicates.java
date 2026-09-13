package com.fairhome.rules;

import com.fairhome.application.Application;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The closed set of eligibility tests a reserved quota may refer to by name.
 *
 * <p>Deliberately not a general expression language. A reviewer, an auditor or a judge can read
 * every possible test in one screen, and an operator editing the rule file can only pick from this
 * list, so no rule edit can silently change what "differently abled" means.
 */
public final class ApplicantPredicates {

    /** (application, ruleSet) -> does this application qualify for the quota. */
    private static final Map<String, BiPredicate<Application, RuleSetDocument>> REGISTRY =
            new LinkedHashMap<>();

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        register("LOCAL_RESIDENT",
                "Has lived in the scheme area for at least localResident.minYearsInArea years",
                (app, rules) -> {
                    int required = rules.localResident() == null ? 0 : rules.localResident().minYearsInArea();
                    return app.getYearsInArea() != null && app.getYearsInArea() >= required;
                });

        register("DIFFERENTLY_ABLED",
                "Declared a certified disability",
                (app, rules) -> Boolean.TRUE.equals(app.getDifferentlyAbled()));

        register("WOMAN",
                "Applicant gender recorded as FEMALE",
                (app, rules) -> app.getGender() == Gender.FEMALE);

        register("EX_SERVICEMAN",
                "Declared ex-serviceman status",
                (app, rules) -> Boolean.TRUE.equals(app.getExServiceman()));

        register("SENIOR_CITIZEN",
                "Aged 60 or above on the draw date",
                (app, rules) -> app.getDateOfBirth() != null
                        && app.getDateOfBirth().plusYears(60).isBefore(LocalDate.now().plusDays(1)));

        register("FIRST_TIME_HOME_BUYER",
                "Declared that the household owns no other home",
                (app, rules) -> Boolean.TRUE.equals(app.getFirstTimeHomeBuyer()));
    }

    private ApplicantPredicates() {
    }

    private static void register(String key, String description,
                                BiPredicate<Application, RuleSetDocument> predicate) {
        REGISTRY.put(key, predicate);
        DESCRIPTIONS.put(key, description);
    }

    public static boolean isKnown(String key) {
        return REGISTRY.containsKey(key);
    }

    public static Set<String> keys() {
        return REGISTRY.keySet();
    }

    public static Map<String, String> descriptions() {
        return Map.copyOf(DESCRIPTIONS);
    }

    public static boolean test(String key, Application application, RuleSetDocument rules) {
        BiPredicate<Application, RuleSetDocument> predicate = REGISTRY.get(key);
        if (predicate == null) {
            throw new IllegalArgumentException("Unknown applicant predicate: " + key);
        }
        return predicate.test(application, rules);
    }
}
