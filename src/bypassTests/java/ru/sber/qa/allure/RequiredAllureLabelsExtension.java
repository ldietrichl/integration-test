package ru.sber.qa.allure;

import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.TestResult;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Applies the project-wide TestOps labels and replaces arbitrary JUnit/Allure tags with the
 * canonical reporting taxonomy.
 *
 * <p>The bypassTests source set generates lightweight passing tests, but their results still need
 * the same labels and report-exclusion rules as regular tests.</p>
 */
public class RequiredAllureLabelsExtension implements TestLifecycleListener {

    private static final String SYSTEM = "CI07963639";
    private static final String LAYER = "api";
    private static final String TEAM = "EXPLAB";
    private static final String APP_TYPE = "backend";
    private static final String TEST_FRAMEWORK = "platform-v-at-framework";
    private static final String DEFAULT_TEST_STAGE = "ift";
    private static final String DEFAULT_ALLURE_RESULTS_DIRECTORY = "build/allure-results";

    private static final Pattern TASK_PATTERN = Pattern.compile(
            "(?i)\\b(EXPLAB|LG)[-_ ]?(\\d{3,6})\\b");

    private static final Set<String> ALLOWED_TEST_STAGES = Set.of(
            "code", "dev", "devBarier", "st", "ift", "lt", "psi", "prom"
    );
    private static final Pattern JSON_FULL_NAME_PATTERN = Pattern.compile(
            "\"fullName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("user.dir", "."))
            .toAbsolutePath()
            .normalize();
    private static final AtomicBoolean PRUNE_HOOK_REGISTERED = new AtomicBoolean(false);
    private static final Set<String> RESULT_UUIDS_TO_PRUNE = ConcurrentHashMap.newKeySet();
    private static final Set<String> EXCLUDED_TEST_NAMES = loadExcludedTestNames();
    private static final List<OutdatedRule> OUTDATED_RULES = loadOutdatedRules();

    @Override
    public void beforeTestWrite(TestResult testResult) {
        Optional<Class<?>> testClass = resolveTestClass(testResult);
        Optional<Method> testMethod = resolveTestMethod(testResult, testClass);
        registerPruneHook();
        if (mustBeExcludedFromAllure(testResult, testClass, testMethod)) {
            Optional.ofNullable(testResult.getUuid())
                    .filter(uuid -> !uuid.isBlank())
                    .ifPresent(RESULT_UUIDS_TO_PRUNE::add);
        }

        String testStage = resolveTestStage();
        String service = resolveService(testResult, testClass);
        boolean criticalRegression = hasAnnotation(testClass, testMethod, CriticalRegression.class);
        boolean regression = criticalRegression || hasAnnotation(testClass, testMethod, Regression.class);
        boolean manual = hasAnnotation(testClass, testMethod, ManualTest.class);

        replaceLabel(testResult, "system", SYSTEM);
        replaceLabel(testResult, "layer", LAYER);
        replaceLabel(testResult, "team", TEAM);
        replaceLabel(testResult, "appType", APP_TYPE);
        replaceLabel(testResult, "functionalArea", service);
        replaceLabel(testResult, "serviceUnderTest", service);
        replaceLabel(testResult, "testStage", testStage);
        addLabel(testResult, "testFramework", TEST_FRAMEWORK);

        if (regression) {
            replaceLabel(testResult, "regress", "true");
        } else {
            removeLabel(testResult, "regress");
        }
        if (criticalRegression) {
            replaceLabel(testResult, "criticalRegress", "true");
        } else {
            removeLabel(testResult, "criticalRegress");
        }

        Set<String> taskIds = canonicalTaskIds(testResult, testClass);
        removeLabel(testResult, "tag");
        taskIds.forEach(task -> addLabel(testResult, "tag", task));
        addLabel(testResult, "tag", service);
        if (regression) {
            addLabel(testResult, "tag", "regress");
        }
        if (criticalRegression) {
            addLabel(testResult, "tag", "critical-regress");
        }
        addLabel(testResult, "tag", manual ? "manual" : "automated");
    }

    private static boolean mustBeExcludedFromAllure(
            TestResult testResult,
            Optional<Class<?>> testClass,
            Optional<Method> testMethod) {
        String fullName = resolveCanonicalFullName(testResult, testClass, testMethod);
        if (fullName.isBlank()) {
            return false;
        }

        String className = testClass
                .map(Class::getName)
                .orElseGet(() -> classNameFromFullName(fullName));

        if (EXCLUDED_TEST_NAMES.contains(fullName) || EXCLUDED_TEST_NAMES.contains(className)) {
            return true;
        }
        return OUTDATED_RULES.stream()
                .anyMatch(rule -> rule.matches(fullName) || rule.matches(className));
    }

    private static String resolveCanonicalFullName(
            TestResult testResult,
            Optional<Class<?>> testClass,
            Optional<Method> testMethod) {
        Optional<String> fullName = Optional.ofNullable(testResult.getFullName())
                .filter(value -> !value.isBlank())
                .map(RequiredAllureLabelsExtension::stripInvocationSuffix);
        if (fullName.isPresent()) {
            return fullName.get();
        }
        if (testClass.isPresent() && testMethod.isPresent()) {
            return testClass.get().getName() + "." + testMethod.get().getName();
        }
        return "";
    }

    private static String classNameFromFullName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot > 0 ? fullName.substring(0, lastDot) : fullName;
    }

    private static void registerPruneHook() {
        if (PRUNE_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    RequiredAllureLabelsExtension::pruneExcludedAllureResults,
                    "allure-excluded-results-pruner"));
        }
    }

    private static void pruneExcludedAllureResults() {
        Path resultsDirectory = resolveAllureResultsDirectory();
        if (!Files.isDirectory(resultsDirectory)) {
            return;
        }

        try (Stream<Path> files = Files.list(resultsDirectory)) {
            files.filter(path -> path.getFileName().toString().endsWith("-result.json"))
                    .forEach(RequiredAllureLabelsExtension::pruneResultIfExcluded);
        } catch (IOException exception) {
            System.err.println("Cannot prune excluded Allure results: " + exception.getMessage());
        }
    }

    private static void pruneResultIfExcluded(Path resultFile) {
        try {
            String fileName = resultFile.getFileName().toString();
            String uuid = fileName.substring(0, fileName.length() - "-result.json".length());
            String content = Files.readString(resultFile, StandardCharsets.UTF_8);
            String fullName = extractJsonFullName(content)
                    .map(RequiredAllureLabelsExtension::stripInvocationSuffix)
                    .orElse("");

            if (RESULT_UUIDS_TO_PRUNE.contains(uuid)
                    || EXCLUDED_TEST_NAMES.contains(fullName)
                    || OUTDATED_RULES.stream().anyMatch(rule -> rule.matches(fullName)
                    || rule.matches(classNameFromFullName(fullName)))) {
                Files.deleteIfExists(resultFile);
            }
        } catch (IOException exception) {
            System.err.println("Cannot delete excluded Allure result " + resultFile + ": "
                    + exception.getMessage());
        }
    }

    private static Optional<String> extractJsonFullName(String content) {
        Matcher matcher = JSON_FULL_NAME_PATTERN.matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(unescapeJsonString(matcher.group(1)));
    }

    private static String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static Path resolveAllureResultsDirectory() {
        String configured = firstNotBlank(
                System.getProperty("allure.results.directory"),
                System.getenv("ALLURE_RESULTS_DIRECTORY"),
                readPropertyFromAllureProperties("allure.results.directory"),
                DEFAULT_ALLURE_RESULTS_DIRECTORY
        );
        Path path = Path.of(configured.trim());
        return path.isAbsolute() ? path.normalize() : PROJECT_DIRECTORY.resolve(path).normalize();
    }

    private static Set<String> loadExcludedTestNames() {
        Set<String> result = new LinkedHashSet<>();
        List<Path> candidates = Arrays.asList(
                optionalPath(System.getProperty("report.exclusions.file")),
                PROJECT_DIRECTORY.resolve("build/report-eligibility/excluded-tests.txt")
        );
        for (Path candidate : candidates) {
            if (candidate == null || !Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                Files.readAllLines(candidate, StandardCharsets.UTF_8).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(result::add);
            } catch (IOException exception) {
                System.err.println("Cannot read report exclusions from " + candidate + ": "
                        + exception.getMessage());
            }
        }
        return result;
    }

    private static List<OutdatedRule> loadOutdatedRules() {
        List<OutdatedRule> result = new ArrayList<>();
        List<Path> candidates = Arrays.asList(
                optionalPath(System.getProperty("report.outdated.tests.file")),
                PROJECT_DIRECTORY.resolve("config/reporting/outdated-tests.properties")
        );
        for (Path candidate : candidates) {
            if (candidate == null || !Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                Files.readAllLines(candidate, StandardCharsets.UTF_8).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(RequiredAllureLabelsExtension::parseOutdatedRule)
                        .forEach(result::add);
            } catch (IOException exception) {
                System.err.println("Cannot read outdated report rules from " + candidate + ": "
                        + exception.getMessage());
            }
        }
        return result;
    }

    private static OutdatedRule parseOutdatedRule(String line) {
        int separator = line.indexOf('=');
        String pattern = separator >= 0 ? line.substring(0, separator).trim() : line;
        return new OutdatedRule(pattern);
    }

    private static Path optionalPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value.trim());
        return path.isAbsolute() ? path.normalize() : PROJECT_DIRECTORY.resolve(path).normalize();
    }

    private static String readPropertyFromAllureProperties(String propertyName) {
        Properties properties = new Properties();
        try (InputStream inputStream = RequiredAllureLabelsExtension.class
                .getClassLoader()
                .getResourceAsStream("allure.properties")) {
            if (inputStream == null) {
                return null;
            }
            properties.load(inputStream);
            return properties.getProperty(propertyName);
        } catch (IOException exception) {
            return null;
        }
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

    private static String resolveTestStage() {
        String rawStage = firstNotBlank(
                System.getProperty("allure.testStage"),
                System.getProperty("testStage"),
                System.getProperty("env"),
                System.getenv("allure.testStage"),
                System.getenv("testStage"),
                System.getenv("TEST_STAGE"),
                System.getenv("env"),
                System.getenv("ENV"),
                readPropertyFromTestProperties("env"),
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

    private static String readPropertyFromTestProperties(String propertyName) {
        Properties properties = new Properties();
        try (InputStream inputStream = RequiredAllureLabelsExtension.class
                .getClassLoader()
                .getResourceAsStream("test.properties")) {
            if (inputStream == null) {
                return null;
            }
            properties.load(inputStream);
            return properties.getProperty(propertyName);
        } catch (IOException exception) {
            return null;
        }
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
        return testClassLabel.flatMap(RequiredAllureLabelsExtension::loadClass);
    }

    private static Optional<Method> resolveTestMethod(TestResult testResult, Optional<Class<?>> testClass) {
        if (testClass.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> methodName = Optional.ofNullable(testResult.getFullName())
                .filter(value -> !value.isBlank())
                .map(RequiredAllureLabelsExtension::stripInvocationSuffix)
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
            return Optional.of(Class.forName(className));
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

    private static void removeLabel(TestResult testResult, String name) {
        testResult.setLabels(new ArrayList<>(testResult.getLabels().stream()
                .filter(label -> !name.equals(label.getName()))
                .toList()));
    }

    private record OutdatedRule(String pattern, Pattern compiledPattern) {
        private OutdatedRule(String pattern) {
            this(pattern, Pattern.compile(wildcardToRegex(pattern)));
        }

        private boolean matches(String value) {
            return value != null && !value.isBlank() && compiledPattern.matcher(value).matches();
        }

        private static String wildcardToRegex(String wildcard) {
            StringBuilder regex = new StringBuilder("^");
            for (char character : wildcard.toCharArray()) {
                if (character == '*') {
                    regex.append(".*");
                } else if (character == '?') {
                    regex.append('.');
                } else {
                    regex.append(Pattern.quote(String.valueOf(character)));
                }
            }
            return regex.append('$').toString();
        }
    }
}
