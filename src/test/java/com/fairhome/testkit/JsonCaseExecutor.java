package com.fairhome.testkit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Stream;

/**
 * Parent for every JSON-driven unit test in this project.
 *
 * <p>A suite extends this class, points at a classpath folder of {@code .json} files, and implements
 * {@link #execute(JsonNode)}. The parent loads each case, runs that method on {@code input}, and
 * compares the result to {@code expected}. Adding a test is then a matter of dropping another JSON
 * file in the folder, not writing another Java method.
 *
 * <p>The JSON shape every file must follow:
 * <pre>
 * {
 *   "name": "short description shown in the test report",
 *   "compare": "SUBTREE",
 *   "input": { ... anything this suite understands ... },
 *   "expected": { ... the fields that must come back ... }
 * }
 * </pre>
 * A file may also wrap several cases in a {@code cases} array, or be a top-level array of cases.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class JsonCaseExecutor {

    protected final ObjectMapper mapper = JsonCaseCatalog.mapper();

    /** Classpath directory, for example {@code cases/allocation}. */
    protected abstract String caseDirectory();

    /** Run the subject under test on one case's input and return a JSON-serialisable result. */
    protected abstract Object execute(JsonNode input) throws Exception;

    @TestFactory
    @DisplayName("JSON cases")
    Stream<DynamicTest> jsonCases() {
        List<JsonCase> cases = JsonCaseCatalog.load(caseDirectory());
        return cases.stream().map(testCase -> DynamicTest.dynamicTest(testCase.name(), () -> {
            Object actual = execute(testCase.input());
            JsonNode actualNode = mapper.valueToTree(actual);
            JsonTreeAssert.assertMatches(testCase, actualNode);
        }));
    }
}
