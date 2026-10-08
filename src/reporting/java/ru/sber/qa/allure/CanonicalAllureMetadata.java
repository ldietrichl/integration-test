package ru.sber.qa.allure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.TestResult;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Pure metadata shared by functional execution and isolated registration. No stand access or file deletion. */
public final class CanonicalAllureMetadata {
    private static final Pattern TASK_PATTERN = Pattern.compile("(?i)\\b(EXPLAB|LG)[-_ ]?(\\d{3,6})\\b");
    private CanonicalAllureMetadata() { }

    public static void apply(TestResult result, String environment, String stage, String mode) {
        Optional<Class<?>> type = resolveTestClass(result);
        Optional<Method> method = resolveTestMethod(result, type);
        String service = resolveService(result, type);
        boolean critical = hasAnnotation(type, method, CriticalRegression.class);
        boolean regression = critical || hasAnnotation(type, method, Regression.class);
        boolean manual = hasAnnotation(type, method, ManualTest.class);
        replaceLabel(result, "system", "CI07963639");
        replaceLabel(result, "layer", "api");
        replaceLabel(result, "team", "EXPLAB");
        replaceLabel(result, "appType", "backend");
        replaceLabel(result, "functionalArea", service);
        replaceLabel(result, "serviceUnderTest", service);
        replaceLabel(result, "testStage", stage);
        replaceLabel(result, "testEnvironment", environment);
        replaceLabel(result, "testFramework", "platform-v-at-framework");
        if ("splitter-service".equals(service)) {
            if (!Set.of("rest", "kafka").contains(mode)) throw new IllegalArgumentException("Invalid splitter mode");
            addParameter(result, "splitter.config.load.mode", mode);
            replaceLabel(result, "splitterConfigLoadMode", mode);
            String history = result.getHistoryId();
            if (history != null && !history.endsWith("::splitter.config.load.mode=" + mode))
                result.setHistoryId(history + "::splitter.config.load.mode=" + mode);
        }
        applySchedulerInfrastructureMetadata(result, type, method, environment);
        applySchedulerRegressionMetadata(result, type, method, environment);
        applyWorkloadMetadata(result, type, method, environment);
        if (regression) replaceLabel(result, "regress", "true"); else removeLabel(result, "regress");
        if (critical) replaceLabel(result, "criticalRegress", "true"); else removeLabel(result, "criticalRegress");
        Set<String> tasks = canonicalTaskIds(result, type);
        removeLabel(result, "tag");
        tasks.forEach(task -> addLabel(result, "tag", task));
        addLabel(result, "tag", service);
        if (regression) addLabel(result, "tag", "regress");
        if (critical) addLabel(result, "tag", "critical-regress");
        addLabel(result, "tag", manual ? "manual" : "automated");
    }

    private static JsonNode catalog(int number) {
        try (InputStream input = CanonicalAllureMetadata.class.getResourceAsStream("/scheduler/scenarios.json")) {
            if (input == null) throw new IllegalStateException("Missing scheduler scenario catalog");
            JsonNode item = new ObjectMapper().readTree(input).path("tests").get(number - 1);
            if (item == null || !String.format(Locale.ROOT, "SCH-%03d", number).equals(item.path("id").asText()))
                throw new IllegalStateException("Scheduler catalog identity mismatch");
            return item;
        } catch (Exception error) { throw new IllegalStateException("Cannot load scheduler scenario metadata", error); }
    }

    private static void applySchedulerInfrastructureMetadata(
            TestResult testResult,
            Optional<Class<?>> testClass,
            Optional<Method> testMethod, String environment) {
        if (!"ru.sber.qa.scheduler.infrastructure.SchedulerInfrastructureFlowTest"
                .equals(testClass.map(Class::getName).orElse(""))) return;
        String methodName = testMethod.map(Method::getName).orElse("");
        if (!methodName.matches("inf\\d{3}")) return;
        replaceLabel(testResult, "scenarioId", "SCH-INF-" + methodName.substring(3));
        addParameter(testResult, "Environment", environment);
        testResult.setDescription("Supporting infrastructure hypothesis, not business regression coverage. "
                + "No task creation/deletion, SQL writes, pod restart, exec or ConfigMap changes.");
    }

    private static void applySchedulerRegressionMetadata(
            TestResult result, Optional<Class<?>> testClass, Optional<Method> testMethod, String environment) {
        if (!testClass.map(Class::getPackageName).orElse("")
                .equals("ru.sber.qa.scheduler.regression")) return;
        String method = testMethod.map(Method::getName).orElse("");
        String identity;
        if (method.matches("sch[0-9]{3}")) {
            int number = Integer.parseInt(method.substring(3));
            var metadata = catalog(number);
            identity = String.format(Locale.ROOT, "SCH-%03d", number);
            replaceLabel(result, "requirement", metadata.path("req").asText());
            replaceLabel(result, "story", metadata.path("req").asText());
            result.setName(identity + ". " + metadata.path("title").asText());
            result.setDescription(metadata.path("steps").asText()
                    + "\nОжидаемый результат: " + metadata.path("expected").asText());
            addParameter(result, "Specification", "ExpLab v17 scheduler; see scenario catalog source pages");
        } else if (testClass.map(Class::getSimpleName).orElse("")
                .equals("SchedulerReadOnlyRegressionFlowTest") && method.matches("ro00[1-7]")) {
            identity = "SCH-RO-" + method.substring(2);
            replaceLabel(result, "coverageRole", "supplementary-read-only");
            addParameter(result, "Fixture mode", "Observed data; no writes or stand control");
            result.setDescription("Read-only scheduler regression using observed corporate data. "
                    + "Empty/insufficient witnesses are skipped, not passed. "
                    + "Supplementary checks do not increase the original requirement denominator. "
                    + "See docs/services/scheduler/regression/AUTOMATED_TUNNELS.md.");
        } else return;
        replaceLabel(result, "scenarioId", identity);
        addParameter(result, "Environment", environment);
        result.setTestCaseId(java.util.UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8)).toString());
        result.setHistoryId(java.util.UUID.nameUUIDFromBytes(
                (identity + ":" + environment).getBytes(StandardCharsets.UTF_8)).toString());
    }

    private static void applyWorkloadMetadata(TestResult result, Optional<Class<?>> type, Optional<Method> method, String environment) {
        String name = type.map(Class::getName).orElse("");
        String scenario = method.map(Method::getName).orElse("");
        String identity;
        if (name.equals("ru.sber.qa.scheduler.infrastructure.SchedulerWorkloadDiagnosticFlowTest")
                && scenario.matches("ops[0-9]{3}")) identity = "SCH-OPS-" + scenario.substring(3);
        else if (name.equals("ru.sber.qa.scheduler.regression.SchedulerWorkloadRegressionFlowTest")
                && scenario.matches("pod[0-9]{3}")) identity = "SCH-POD-" + scenario.substring(3);
        else return;
        replaceLabel(result, "scenarioId", identity);
        replaceLabel(result, "coverageRole", identity.startsWith("SCH-OPS") ? "supporting-diagnostics" : "supplementary-recovery");
        addParameter(result, "Environment", environment);
        result.setTestCaseId(java.util.UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString());
        result.setHistoryId(java.util.UUID.nameUUIDFromBytes((identity + ":" + environment)
                .getBytes(StandardCharsets.UTF_8)).toString());
        result.setDescription("Requires explicit dedicated-stand and operation permission. Original resource pre-images "
                + "are retained as private artifacts; restoration is checked. Not counted as additional original D/C requirements.");
    }

    private static <A extends java.lang.annotation.Annotation> boolean hasAnnotation(
            Optional<Class<?>> testClass,
            Optional<Method> testMethod,
            Class<A> annotationType) {
        return testMethod.map(method -> method.isAnnotationPresent(annotationType)).orElse(false)
                || testClass.map(clazz -> clazz.isAnnotationPresent(annotationType)).orElse(false);
    }

    private static Set<String> canonicalTaskIds(TestResult testResult, Optional<Class<?>> testClass) {
        Set<String> result = new TreeSet<>();
        StringBuilder source = new StringBuilder();
        source.append(Optional.ofNullable(testResult.getFullName()).orElse("")).append(' ')
                .append(Optional.ofNullable(testResult.getName()).orElse("")).append(' ')
                .append(testClass.map(Class::getName).orElse(""));

        testResult.getLabels().stream()
                .filter(label -> "tag".equals(label.getName())
                        || "story".equals(label.getName())
                        || "issue".equals(label.getName()))
                .map(Label::getValue)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> source.append(' ').append(value));

        Matcher matcher = TASK_PATTERN.matcher(source);
        while (matcher.find()) {
            result.add(matcher.group(1).toUpperCase(Locale.ROOT) + "-" + matcher.group(2));
        }
        return result;
    }

    private static String resolveService(TestResult testResult, Optional<Class<?>> testClass) {
        String packageName = testClass
                .map(Class::getPackageName)
                .orElseGet(() -> findLabel(testResult, "package").orElse(""));

        if (packageName.startsWith("ru.sber.qa.splitter.EXPLAB_2729")) {
            return "data-operator-service";
        }
        if (packageName.startsWith("ru.sber.qa.configurations")) {
            return "configuration-service";
        }
        if (packageName.startsWith("ru.sber.qa.scheduler")) {
            return "scheduler-service";
        }
        if (packageName.startsWith("ru.sber.qa.experiments")
                || packageName.startsWith("ru.sber.qa.controllers")) {
            return "experiment-service";
        }
        if (packageName.startsWith("ru.sber.qa.dictionaries")) {
            return "dictionaries-service";
        }
        if (packageName.startsWith("ru.sber.qa.splitter")) {
            return "splitter-service";
        }
        if (packageName.startsWith("ru.sber.qa.dataoperator")) {
            return "data-operator-service";
        }
        if (packageName.startsWith("ru.sber.qa.messages")) {
            return "message-service";
        }
        if (packageName.startsWith("config.services.core")) {
            return "integration-test";
        }
        return "abtm-backend";
    }

    private static Optional<Class<?>> resolveTestClass(TestResult testResult) {
        Optional<String> fullName = Optional.ofNullable(testResult.getFullName())
                .filter(value -> !value.isBlank());
        if (fullName.isPresent()) {
            String withoutInvocation = stripInvocationSuffix(fullName.get());
            int lastDot = withoutInvocation.lastIndexOf('.');
            if (lastDot > 0) {
                Optional<Class<?>> loadedClass = loadClass(withoutInvocation.substring(0, lastDot));
                if (loadedClass.isPresent()) {
                    return loadedClass;
                }
            }
        }

        Optional<String> testClassLabel = findLabel(testResult, "testClass")
                .or(() -> findLabel(testResult, "class"));
        return testClassLabel.flatMap(CanonicalAllureMetadata::loadClass);
    }

    private static Optional<Method> resolveTestMethod(TestResult testResult, Optional<Class<?>> testClass) {
        if (testClass.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> methodName = Optional.ofNullable(testResult.getFullName())
                .filter(value -> !value.isBlank())
                .map(CanonicalAllureMetadata::stripInvocationSuffix)
                .map(value -> {
                    int lastDot = value.lastIndexOf('.');
                    return lastDot >= 0 ? value.substring(lastDot + 1) : value;
                });
        if (methodName.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(testClass.get().getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName.get()))
                .findFirst();
    }

    private static String stripInvocationSuffix(String value) {
        int bracketIndex = value.indexOf('[');
        int parenthesisIndex = value.indexOf('(');
        int cutIndex = value.length();
        if (bracketIndex >= 0) {
            cutIndex = Math.min(cutIndex, bracketIndex);
        }
        if (parenthesisIndex >= 0) {
            cutIndex = Math.min(cutIndex, parenthesisIndex);
        }
        return value.substring(0, cutIndex);
    }

    private static Optional<Class<?>> loadClass(String className) {
        try {
            return Optional.of(Class.forName(className, false, Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> findLabel(TestResult testResult, String name) {
        return testResult.getLabels().stream()
                .filter(label -> name.equals(label.getName()))
                .map(Label::getValue)
                .findFirst();
    }

    private static void replaceLabel(TestResult testResult, String name, String value) {
        removeLabel(testResult, name);
        addLabel(testResult, name, value);
    }

    private static void addLabel(TestResult testResult, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Set<String> existing = new LinkedHashSet<>();
        testResult.getLabels().stream()
                .filter(label -> name.equals(label.getName()))
                .map(Label::getValue)
                .forEach(existing::add);
        if (!existing.contains(value)) {
            testResult.getLabels().add(new Label().setName(name).setValue(value));
        }
    }

    private static void addParameter(TestResult testResult, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        List<Parameter> parameters = new ArrayList<>(Optional.ofNullable(testResult.getParameters())
                .orElseGet(List::of));
        parameters.removeIf(parameter -> name.equals(parameter.getName()));
        parameters.add(new Parameter().setName(name).setValue(value));
        testResult.setParameters(parameters);
    }

    private static void removeLabel(TestResult testResult, String name) {
        testResult.setLabels(new ArrayList<>(testResult.getLabels().stream()
                .filter(label -> !name.equals(label.getName()))
                .toList()));
    }
}
