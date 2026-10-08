import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import request.dataoperator.v2.DataOperatorLinksInvalidRequests;
import request.dataoperator.v2.DataOperatorLinksFunctionalCases;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Runs the actual integration-test class against the separately started local service. */
public class LocalScenarioRunner {
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Expected output directory and run-manifest JSON path");
        ObjectMapper mapper = new ObjectMapper();
        var manifest = mapper.readTree(Path.of(args[1]).toFile());
        String serviceUrl = manifest.path("serviceUrl").asText();
        var serviceUri = java.net.URI.create(serviceUrl);
        if (!"http".equals(serviceUri.getScheme()) || !"127.0.0.1".equals(serviceUri.getHost()))
            throw new IllegalArgumentException("This runner only accepts a local HTTP service");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        System.setProperty("env", "local");
        System.setProperty("rest.local.gateway.base-uri", "http://127.0.0.1:1");
        System.setProperty("rest.local.data-operator.base-uri", serviceUrl);
        System.setProperty("allure.results.directory", output.resolve("allure-results").toString());
        System.setProperty("links.evidence.directory", output.resolve("http-evidence").toString());
        boolean fixtureSuite = "fixture".equals(manifest.path("suite").asText());
        boolean functionalSuite = "functional-full".equals(manifest.path("suite").asText());
        if (functionalSuite) {
            System.setProperty("links.fixture.local.enabled", "true");
            System.setProperty("links.fixture.local.ignite.addresses", "127.0.0.1:" + manifest.path("config").path("ignitePort").asInt());
            System.setProperty("links.fixture.local.ignite.ssl.enabled", "false");
            System.setProperty("links.fixture.local.output.directory", output.resolve("fixtures").toString());
            manifest = manifest.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).put("functionalCases", DataOperatorLinksFunctionalCases.all().size());
        }
        if (fixtureSuite) System.setProperty("links.fixture.manifest", manifest.path("fixtureManifest").asText());
        int expected = fixtureSuite ? 1 : DataOperatorLinksInvalidRequests.all().size() +
                (functionalSuite ? DataOperatorLinksFunctionalCases.all().size() : 0);
        List<Map<String, Object>> cases = new ArrayList<>();
        List<Map<String, Object>> containerFailures = new ArrayList<>();
        var details = new TestExecutionListener() {
            public void executionStarted(TestIdentifier test) {
                if (test.isTest()) System.out.println("CASE_START " + test.getUniqueId());
            }
            public void executionFinished(TestIdentifier test, TestExecutionResult result) {
                if (!test.isTest()) {
                    if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL)
                        containerFailures.add(Map.of("uniqueId", test.getUniqueId(), "status", result.getStatus().name(),
                                "error", result.getThrowable().map(Throwable::toString).orElse("")));
                    return;
                }
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("name", test.getDisplayName());
                record.put("uniqueId", test.getUniqueId());
                record.put("status", result.getStatus().name());
                result.getThrowable().ifPresent(error -> {
                    record.put("errorType", error.getClass().getName());
                    record.put("message", error.getMessage());
                });
                cases.add(record);
                System.out.println("CASE_END " + test.getUniqueId());
            }
            public void executionSkipped(TestIdentifier test, String reason) {
                if (test.isTest()) cases.add(Map.of("name",test.getDisplayName(),"uniqueId",test.getUniqueId(),"status","SKIPPED","message",reason));
            }
        };
        var builder = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass("ru.sber.qa.dataoperator.EXPLAB_2974." +
                        (fixtureSuite ? "DataOperatorLinksFixtureFlowTest" : "DataOperatorLinksValidationFlowTest")));
        if (functionalSuite) builder.selectors(selectClass("ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksFunctionalFlowTest"));
        var request = builder
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "false")
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build();
        var summary = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, summary, details);
        summary.getSummary().printTo(new PrintWriter(System.out));
        try (PrintWriter writer = new PrintWriter(output.resolve("failures.txt").toFile(), "UTF-8")) {
            summary.getSummary().printFailuresTo(writer);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("results.json").toFile(),
                Map.of("schemaVersion",2,"finishedAt", Instant.now().toString(), "service", serviceUrl,
                        "environment", manifest, "expectedCases", expected, "containerFailures",containerFailures,"cases", cases));
        if (summary.getSummary().getTestsFoundCount() != expected || summary.getSummary().getTestsStartedCount() != expected
                || !containerFailures.isEmpty() || summary.getSummary().getTestsAbortedCount() > 0) {
            System.exit(2); // Incomplete/infrastructure execution is distinct from contract failures.
        }
        System.exit(summary.getSummary().getTotalFailureCount() == 0 ? 0 : 1);
    }
}
