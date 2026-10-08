package ru.sber.qa.allure;

import config.services.core.TestEnvironment;
import config.services.core.TestConfigurationFiles;
import infrastructure.scheduler.SchedulerAllurePresentation;
import infrastructure.scheduler.SchedulerOutcomeClassification;
import infrastructure.scheduler.SchedulerRegressionPolicy;
import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.TestResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Preserve all observed results. Eligibility belongs before execution, never in a shutdown deleter. */
public class RequiredAllureLabelsExtension implements TestLifecycleListener {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    @Override public void beforeTestWrite(TestResult result) {
        String env = TestEnvironment.current();
        String stage = resolveTestStage();
        String mode = System.getProperty("splitter.config.load.mode",
                TestConfigurationFiles.load("test.properties").getProperty("splitter.config.load.mode", "rest"));
        CanonicalAllureMetadata.apply(result, env, stage, mode);
        String fullName = result.getFullName() == null ? "" : result.getFullName();
        Set<String> excluded = loadExcludedTestNames(PROJECT, System.getProperty("report.exclusions.file"));
        int dot = fullName.lastIndexOf('.');
        String type = dot < 0 ? fullName : fullName.substring(0, dot);
        if (excluded.contains(fullName) || excluded.contains(type)) {
            result.getLabels().add(new Label().setName("reportEligibility").setValue("explicitly-excluded-but-executed"));
        }
        if (fullName.startsWith("ru.sber.qa.scheduler.")) {
            SchedulerAllurePresentation.steps(result.getSteps());
            String message = result.getStatusDetails() == null ? "" : result.getStatusDetails().getMessage();
            String status = result.getStatus() == null ? "unknown" : result.getStatus().value();
            result.getLabels().removeIf(label -> Set.of("outcomeKind", "schedulerPhase").contains(label.getName()));
            result.getLabels().add(new Label().setName("outcomeKind").setValue(SchedulerOutcomeClassification.classify(status, message)));
            result.getLabels().add(new Label().setName("schedulerPhase").setValue(SchedulerRegressionPolicy.selected().name()));
        }
    }


    static Set<String> loadExcludedTestNames(Path projectDirectory, String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        Path candidate = Path.of(configured.trim());
        if (!candidate.isAbsolute()) candidate = projectDirectory.resolve(candidate);
        if (!Files.isRegularFile(candidate)) throw new IllegalStateException("Configured report exclusions are missing: " + candidate);
        try {
            Set<String> result = new LinkedHashSet<>();
            Files.readAllLines(candidate, StandardCharsets.UTF_8).stream().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(result::add);
            return result;
        } catch (IOException error) { throw new IllegalStateException("Cannot read report exclusions: " + candidate, error); }
    }
    private static final String DEFAULT_TEST_STAGE = "ift";
    private static final Set<String> ALLOWED_TEST_STAGES = Set.of("code", "dev", "devBarier", "st", "ift", "lt", "psi", "prom");
    private static String resolveTestStage() {
        String rawStage = firstNotBlank(
                System.getProperty("allure.testStage"),
                System.getProperty("testStage"),
                System.getenv("allure.testStage"),
                System.getenv("testStage"),
                System.getenv("TEST_STAGE"),
                TestEnvironment.current(),
                DEFAULT_TEST_STAGE
        );

        String normalizedStage = normalizeTestStage(rawStage);
        if (!ALLOWED_TEST_STAGES.contains(normalizedStage)) {
            throw new IllegalArgumentException("Unsupported Allure testStage value: '" + rawStage
                    + "'. Allowed values: " + ALLOWED_TEST_STAGES);
        }
        return normalizedStage;
    }

    private static String normalizeTestStage(String value) {
        String trimmed = value.trim();
        if ("ift-dm".equalsIgnoreCase(trimmed)) return "ift";
        if ("local".equalsIgnoreCase(trimmed)) return "code";
        if ("devbarier".equals(trimmed.toLowerCase(Locale.ROOT))) {
            return "devBarier";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return DEFAULT_TEST_STAGE;
    }
}
