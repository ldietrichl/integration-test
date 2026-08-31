package ru.sber.qa.tools.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the list of tests that must be excluded before JUnit discovery reaches Allure.
 *
 * <p>A test is excluded when it is explicitly outdated, disabled, not applicable to the configured
 * splitter config load mode, or has no verification evidence.
 * Verification evidence includes direct assertions, framework matcher calls and transitive calls to
 * local helper methods containing such checks.</p>
 */
public final class TestReportEligibilityScanner {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|static\\s+)*class\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(?s)@(?:org\\.junit\\.jupiter\\.api\\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b" +
                    ".*?" +
                    "(?:public|protected|private|static|final|synchronized|native|strictfp|\\s)+" +
                    "(?:<[^>{}]+>\\s*)?" +
                    "[A-Za-z_$][\\w$<>\\[\\].?,\\s]*\\s+" +
                    "([A-Za-z_$][\\w$]*)\\s*\\([^;{}]*\\)\\s*" +
                    "(?:throws\\s+[^;{}]+)?\\{");
    private static final Pattern HELPER_METHOD_PATTERN = Pattern.compile(
            "(?ms)^\\s*(?:public|protected|private|static|final|synchronized|native|strictfp|default|\\s)+" +
                    "(?:<[^>{}]+>\\s*)?" +
                    "[A-Za-z_$][\\w$<>\\[\\].?, @]*\\s+" +
                    "([A-Za-z_$][\\w$]*)\\s*\\([^;{}]*\\)\\s*" +
                    "(?:throws\\s+[^;{}]+)?\\{");
    private static final Pattern PROFILE_ANNOTATION_PATTERN = Pattern.compile(
            "@(?:[a-zA-Z_][\\w.]*\\.)?SplitterTestProfileOnly\\s*(?:\\(([^)]*)\\))?",
            Pattern.DOTALL);
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"([^\"]+)\"");

    private static final Pattern VERIFICATION_PATTERN = Pattern.compile(
            "\\bassert[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\bAssertions\\s*\\." +
                    "|\\bMatcherAssert\\s*\\." +
                    "|\\bJsonAssert\\s*\\." +
                    "|\\bassertThat\\s*\\(" +
                    "|\\bfail\\s*\\(" +
                    "|\\bverify\\s*\\(" +
                    "|\\bshould[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\.should\\s*\\(" +
                    "|\\.should[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\bcheck[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\.check[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\bverify[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\.verify[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\bvalidate[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\.validate[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\bexpect[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\.expect[A-Z_$][\\w$]*\\s*\\(" +
                    "|\\b[\\w$]*Status(?:Ok|Created|Accepted|NoContent|BadRequest|Unauthorized|Forbidden|NotFound|Conflict|UnprocessableEntity|InternalServerError)[\\w$]*\\s*\\(" +
                    "|\\.statusCode\\s*\\(" +
                    "|\\.untilAsserted\\s*\\(" +
                    "|\\.isEqualTo\\s*\\(" +
                    "|\\.isTrue\\s*\\(" +
                    "|\\.isFalse\\s*\\(" +
                    "|\\b[\\w$]*Assertions\\.[A-Za-z_$][\\w$]*\\s*\\(" +
                    "|\\b[\\w$]*Assert\\.[A-Za-z_$][\\w$]*\\s*\\(" +
                    "|\\bhave(?:Status|Body|Json|Cell|Table|Text)[A-Za-z_$][\\w$]*\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private TestReportEligibilityScanner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected arguments: <source-test-java-dir> <outdated-rules-file> <output-dir>");
        }

        Path sourceRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path outdatedRulesFile = Path.of(args[1]).toAbsolutePath().normalize();
        Path outputDir = Path.of(args[2]).toAbsolutePath().normalize();

        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source test directory does not exist: " + sourceRoot);
        }

        Files.createDirectories(outputDir);
        List<OutdatedRule> outdatedRules = loadOutdatedRules(outdatedRulesFile);
        List<TestDecision> decisions = new ArrayList<>();
        ScanOptions options = ScanOptions.fromSystemProperties();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            decisions.addAll(scanFile(sourceRoot, path, outdatedRules, options));
                        } catch (IOException exception) {
                            throw new RuntimeException("Cannot scan " + path, exception);
                        }
                    });
        }

        decisions.sort(Comparator.comparing(TestDecision::fullName));
        writeOutputs(outputDir, decisions, outdatedRules, options);

        long excluded = decisions.stream().filter(decision -> !decision.eligible()).count();
        System.out.println("Report eligibility: discovered=" + decisions.size()
                + ", eligible=" + (decisions.size() - excluded)
                + ", excluded=" + excluded
                + ", splitter.config.load.mode=" + options.splitterConfigLoadMode()
                + ", splitter.test.profile=" + options.splitterTestProfile()
                + ", includeManualTests=" + options.includeManualTests()
                + ", includeSplitterDataOperatorTests=" + options.includeSplitterDataOperatorTests());
    }

    private static List<TestDecision> scanFile(Path sourceRoot,
                                               Path sourceFile,
                                               List<OutdatedRule> outdatedRules,
                                               ScanOptions options) throws IOException {
        String rawSource = Files.readString(sourceFile, StandardCharsets.UTF_8);
        String source = stripComments(rawSource);

        String packageName = findFirst(PACKAGE_PATTERN, source).orElse("");
        Matcher typeMatcher = TYPE_PATTERN.matcher(source);
        if (!typeMatcher.find()) {
            return List.of();
        }
        String className = typeMatcher.group(1);
        String fqcn = packageName.isBlank() ? className : packageName + "." + className;
        String classAnnotationBlock = findAnnotationBlockBefore(source, typeMatcher.start());
        boolean classDisabled = containsAnnotation(classAnnotationBlock, "Disabled");
        boolean classManual = containsAnnotation(classAnnotationBlock, "ManualTest");
        Optional<ModeRestriction> classModeRestriction = modeRestriction(classAnnotationBlock);
        Optional<ProfileRestriction> classProfileRestriction = profileRestriction(classAnnotationBlock);
        boolean splitterTest = isSplitterTest(fqcn);

        Map<String, MethodSource> methods = findMethods(source);
        List<TestDecision> result = new ArrayList<>();
        Matcher testMatcher = TEST_METHOD_PATTERN.matcher(source);
        Set<String> seen = new LinkedHashSet<>();

        while (testMatcher.find()) {
            String methodName = testMatcher.group(1);
            if (!seen.add(methodName)) {
                continue;
            }
            String annotationBlock = findAnnotationBlockBefore(source, testMatcher.start()) + testMatcher.group();
            MethodSource method = methods.get(methodName);
            String relativePath = sourceRoot.relativize(sourceFile).toString().replace('\\', '/');
            String fullName = fqcn + "." + methodName;

            Optional<OutdatedRule> outdatedRule = outdatedRules.stream()
                    .filter(rule -> rule.matches(fullName) || rule.matches(fqcn))
                    .findFirst();
            if (outdatedRule.isPresent()) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.OUTDATED,
                        outdatedRule.get().reason()));
                continue;
            }

            if (isSplitterDataOperatorTest(fqcn) && !options.includeSplitterDataOperatorTests()) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.SERVICE_SCOPE,
                        "Data-operator scenarios are not part of the splitter service suite. "
                                + "Enable includeSplitterDataOperatorTests only for a prepared data-operator stand."));
                continue;
            }

            if (isStrictDocumentProfileTest(fqcn) && !options.isDocumentSplitterProfile()) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.SPLITTER_TEST_PROFILE,
                        "Strict document-profile scenarios require splitter.test.profile=document. "
                                + "Current profile is " + options.splitterTestProfile()));
                continue;
            }

            Optional<ModeRestriction> modeRestriction = modeRestriction(annotationBlock)
                    .or(() -> classModeRestriction);
            if (splitterTest && modeRestriction.isEmpty()) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.SPLITTER_CONFIG_LOAD_MODE,
                        "Splitter test has no config load mode marker. Add @AnyConfigLoadMode, "
                                + "@KafkaConfigLoadModeOnly or @RestConfigLoadModeOnly."));
                continue;
            }

            if (modeRestriction.isPresent() && !modeRestriction.get().allows(options.splitterConfigLoadMode())) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.SPLITTER_CONFIG_LOAD_MODE,
                        "Allowed only for splitter.config.load.mode="
                                + String.join("/", modeRestriction.get().allowedModes())
                                + ", current mode is " + options.splitterConfigLoadMode()));
                continue;
            }

            Optional<ProfileRestriction> profileRestriction = profileRestriction(annotationBlock)
                    .or(() -> classProfileRestriction);
            if (profileRestriction.isPresent() && !profileRestriction.get().allows(options.splitterTestProfile())) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.SPLITTER_TEST_PROFILE,
                        "Allowed only for splitter.test.profile="
                                + String.join("/", profileRestriction.get().allowedProfiles())
                                + ", current profile is " + options.splitterTestProfile()));
                continue;
            }

            if ((containsAnnotation(classAnnotationBlock, "KafkaConfigStatusRequiredOnly")
                    || containsAnnotation(annotationBlock, "KafkaConfigStatusRequiredOnly"))
                    && !options.splitterConfigKafkaStatusRequired()) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.SPLITTER_KAFKA_STATUS_REQUIRED,
                        "Requires splitter.config.kafka.status.required=true. Current value is false."));
                continue;
            }

            if ((classManual || containsAnnotation(annotationBlock, "ManualTest"))
                    && !options.includeManualTests()) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.MANUAL,
                        "Manual/profile-specific test is omitted from regular functional reports"));
                continue;
            }

            if (classDisabled || containsAnnotation(annotationBlock, "Disabled")) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.DISABLED,
                        "JUnit @Disabled test is intentionally omitted from functional reports"));
                continue;
            }

            if (method == null || !containsVerification(methodName, methods, new HashSet<>())) {
                result.add(TestDecision.excluded(fullName, relativePath, Reason.NO_ASSERTION,
                        "No assertion, matcher, expected-status check or verified local helper call was found"));
                continue;
            }

            result.add(TestDecision.eligible(fullName, relativePath));
        }
        return result;
    }

    private static Map<String, MethodSource> findMethods(String source) {
        Map<String, MethodSource> methods = new LinkedHashMap<>();
        Matcher matcher = HELPER_METHOD_PATTERN.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            int openBrace = source.indexOf('{', matcher.start());
            if (openBrace < 0 || openBrace > matcher.end()) {
                openBrace = matcher.end() - 1;
            }
            int closeBrace = findMatchingBrace(source, openBrace);
            if (closeBrace < 0) {
                continue;
            }
            String body = source.substring(openBrace, closeBrace + 1);
            methods.putIfAbsent(name, new MethodSource(name, body));
        }

        // The test regex is more tolerant of package-private JUnit methods; add any missed methods.
        Matcher testMatcher = TEST_METHOD_PATTERN.matcher(source);
        while (testMatcher.find()) {
            String name = testMatcher.group(1);
            int openBrace = testMatcher.end() - 1;
            int closeBrace = findMatchingBrace(source, openBrace);
            if (closeBrace >= 0) {
                methods.put(name, new MethodSource(name, source.substring(openBrace, closeBrace + 1)));
            }
        }
        return methods;
    }

    private static boolean containsVerification(String methodName,
                                                Map<String, MethodSource> methods,
                                                Set<String> visiting) {
        if (!visiting.add(methodName)) {
            return false;
        }
        MethodSource method = methods.get(methodName);
        if (method == null) {
            return false;
        }
        if (VERIFICATION_PATTERN.matcher(method.body()).find()) {
            return true;
        }

        for (String candidate : methods.keySet()) {
            if (candidate.equals(methodName)) {
                continue;
            }
            Pattern invocation = Pattern.compile("\\b" + Pattern.quote(candidate) + "\\s*\\(");
            if (invocation.matcher(method.body()).find()
                    && containsVerification(candidate, methods, visiting)) {
                return true;
            }
        }
        return false;
    }

    private static List<OutdatedRule> loadOutdatedRules(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<OutdatedRule> rules = new ArrayList<>();
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            String pattern = separator >= 0 ? line.substring(0, separator).trim() : line;
            String reason = separator >= 0 ? line.substring(separator + 1).trim() : "Explicitly outdated";
            if (!pattern.isEmpty()) {
                rules.add(new OutdatedRule(pattern, reason));
            }
        }
        return rules;
    }

    private static void writeOutputs(Path outputDir,
                                     List<TestDecision> decisions,
                                     List<OutdatedRule> outdatedRules,
                                     ScanOptions options) throws IOException {
        List<String> excluded = decisions.stream()
                .filter(decision -> !decision.eligible())
                .map(TestDecision::fullName)
                .toList();
        List<String> excludedWithDisabledIncluded = decisions.stream()
                .filter(decision -> !decision.eligible())
                .filter(decision -> decision.reason() != Reason.DISABLED)
                .map(TestDecision::fullName)
                .toList();
        List<String> eligible = decisions.stream()
                .filter(TestDecision::eligible)
                .map(TestDecision::fullName)
                .toList();
        List<String> modeExcluded = decisions.stream()
                .filter(decision -> decision.reason() == Reason.SPLITTER_CONFIG_LOAD_MODE)
                .map(TestDecision::fullName)
                .toList();
        List<String> kafkaStatusRequiredExcluded = decisions.stream()
                .filter(decision -> decision.reason() == Reason.SPLITTER_KAFKA_STATUS_REQUIRED)
                .map(TestDecision::fullName)
                .toList();

        Files.write(outputDir.resolve("excluded-tests.txt"), excluded, StandardCharsets.UTF_8);
        Files.write(outputDir.resolve("excluded-tests-include-disabled.txt"),
                excludedWithDisabledIncluded, StandardCharsets.UTF_8);
        Files.write(outputDir.resolve("eligible-tests.txt"), eligible, StandardCharsets.UTF_8);
        Files.write(outputDir.resolve("excluded-tests-splitter-config-load-mode.txt"),
                modeExcluded, StandardCharsets.UTF_8);
        Files.write(outputDir.resolve("excluded-tests-splitter-kafka-status-required.txt"),
                kafkaStatusRequiredExcluded, StandardCharsets.UTF_8);

        Map<Reason, Long> reasonCounts = new HashMap<>();
        decisions.stream().filter(decision -> !decision.eligible())
                .forEach(decision -> reasonCounts.merge(decision.reason(), 1L, Long::sum));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Report eligibility\n\n")
                .append("Generated from `src/test/java` before the functional and bypass runs.\n\n")
                .append("- splitter.config.load.mode: **").append(options.splitterConfigLoadMode()).append("**\n")
                .append("- splitter.test.profile: **").append(options.splitterTestProfile()).append("**\n")
                .append("- includeManualTests: **").append(options.includeManualTests()).append("**\n")
                .append("- includeSplitterDataOperatorTests: **")
                .append(options.includeSplitterDataOperatorTests()).append("**\n")
                .append("- Discovered tests: **").append(decisions.size()).append("**\n")
                .append("- Eligible for report: **").append(eligible.size()).append("**\n")
                .append("- Excluded from report: **").append(excluded.size()).append("**\n")
                .append("- Excluded when disabled tests are included: **")
                .append(excludedWithDisabledIncluded.size()).append("**\n")
                .append("- Disabled: **").append(reasonCounts.getOrDefault(Reason.DISABLED, 0L)).append("**\n")
                .append("- Other splitter config load mode: **")
                .append(reasonCounts.getOrDefault(Reason.SPLITTER_CONFIG_LOAD_MODE, 0L)).append("**\n")
                .append("- Other service scope: **").append(reasonCounts.getOrDefault(Reason.SERVICE_SCOPE, 0L)).append("**\n")
                .append("- Other splitter test profile: **")
                .append(reasonCounts.getOrDefault(Reason.SPLITTER_TEST_PROFILE, 0L)).append("**\n")
                .append("- Kafka status required: **")
                .append(reasonCounts.getOrDefault(Reason.SPLITTER_KAFKA_STATUS_REQUIRED, 0L)).append("**\n")
                .append("- Manual/profile-specific: **").append(reasonCounts.getOrDefault(Reason.MANUAL, 0L)).append("**\n")
                .append("- Without verification evidence: **").append(reasonCounts.getOrDefault(Reason.NO_ASSERTION, 0L)).append("**\n")
                .append("- Explicitly outdated: **").append(reasonCounts.getOrDefault(Reason.OUTDATED, 0L)).append("**\n\n")
                .append("## Explicit outdated rules\n\n");

        if (outdatedRules.isEmpty()) {
            markdown.append("No explicit rules.\n\n");
        } else {
            outdatedRules.forEach(rule -> markdown.append("- `")
                    .append(rule.pattern()).append("` — ")
                    .append(escapeMarkdown(rule.reason())).append("\n"));
            markdown.append('\n');
        }

        markdown.append("## Excluded tests\n\n")
                .append("| Test | Reason | Details | Source |\n")
                .append("|---|---|---|---|\n");
        decisions.stream().filter(decision -> !decision.eligible()).forEach(decision -> markdown
                .append("| `").append(decision.fullName()).append("` | ")
                .append(decision.reason().code).append(" | ")
                .append(escapeMarkdown(decision.details())).append(" | `")
                .append(decision.sourceFile()).append("` |\n"));

        Files.writeString(outputDir.resolve("report-eligibility.md"), markdown.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private static boolean containsAnnotation(String block, String annotation) {
        return Pattern.compile("@(?:[a-zA-Z_][\\w.]*\\.)?" + Pattern.quote(annotation) + "(?:\\s|\\(|$)")
                .matcher(block)
                .find();
    }

    private static Optional<ModeRestriction> modeRestriction(String annotationBlock) {
        Set<String> allowed = new LinkedHashSet<>();
        if (containsAnnotation(annotationBlock, "AnyConfigLoadMode")) {
            allowed.add("rest");
            allowed.add("kafka");
        }
        if (containsAnnotation(annotationBlock, "KafkaConfigLoadModeOnly")) {
            allowed.add("kafka");
        }
        if (containsAnnotation(annotationBlock, "RestConfigLoadModeOnly")) {
            allowed.add("rest");
        }
        return allowed.isEmpty() ? Optional.empty() : Optional.of(new ModeRestriction(allowed));
    }

    private static Optional<ProfileRestriction> profileRestriction(String annotationBlock) {
        Matcher matcher = PROFILE_ANNOTATION_PATTERN.matcher(annotationBlock);
        if (!matcher.find()) {
            return Optional.empty();
        }

        Set<String> allowed = new LinkedHashSet<>();
        String args = matcher.group(1);
        if (args != null) {
            Matcher valueMatcher = STRING_LITERAL_PATTERN.matcher(args);
            while (valueMatcher.find()) {
                allowed.add(normalizeProfile(valueMatcher.group(1)));
            }
        }
        return allowed.isEmpty() ? Optional.empty() : Optional.of(new ProfileRestriction(allowed));
    }

    private static boolean isSplitterTest(String fqcn) {
        return fqcn.equals("ru.sber.qa.splitter") || fqcn.startsWith("ru.sber.qa.splitter.");
    }

    private static boolean isSplitterDataOperatorTest(String fqcn) {
        return fqcn.startsWith("ru.sber.qa.splitter.EXPLAB_2729.");
    }

    private static boolean isStrictDocumentProfileTest(String fqcn) {
        return fqcn.startsWith("ru.sber.qa.splitter.tests_v9.strict.");
    }

    private static String splitterConfigLoadMode() {
        String raw = firstNonBlank(
                System.getProperty("splitter.config.load.mode"),
                System.getenv("SPLITTER_CONFIG_LOAD_MODE"),
                "rest");
        String mode = raw.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        if (!Set.of("rest", "kafka").contains(mode)) {
            throw new IllegalArgumentException(
                    "Unsupported splitter.config.load.mode=" + raw + ". Expected one of: rest, kafka");
        }
        return mode;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String splitterTestProfile() {
        return firstNonBlank(
                System.getProperty("splitter.test.profile"),
                System.getenv("SPLITTER_TEST_PROFILE"),
                "current")
                .transform(TestReportEligibilityScanner::normalizeProfile);
    }

    private static String normalizeProfile(String value) {
        return value.trim()
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
    }

    private static boolean featureFlag(String propertyName, String environmentName) {
        String raw = firstNonBlank(System.getProperty(propertyName), System.getenv(environmentName));
        if (raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("1");
    }

    private static String findAnnotationBlockBefore(String source, int index) {
        int currentLineStart = source.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int scanEnd = currentLineStart;
        int scanStart = currentLineStart;

        while (scanStart > 0) {
            int previousLineEnd = scanStart - 1;
            int previousLineStart = source.lastIndexOf('\n', Math.max(0, previousLineEnd - 1)) + 1;
            String line = source.substring(previousLineStart, previousLineEnd).trim();

            if (line.isEmpty()) {
                scanStart = previousLineStart;
                continue;
            }
            if (line.startsWith("@") || line.startsWith("}") || line.startsWith(")")
                    || line.startsWith("\"") || line.startsWith("+") || line.startsWith(",")) {
                scanStart = previousLineStart;
                continue;
            }
            break;
        }

        String candidate = source.substring(scanStart, scanEnd);
        int firstAnnotation = candidate.indexOf('@');
        return firstAnnotation < 0 ? "" : candidate.substring(firstAnnotation);
    }

    private static int findMatchingBrace(String source, int openBrace) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '\'') {
                inChar = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static Optional<String> findFirst(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String stripComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    result.append(current);
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    index++;
                } else if (current == '\n' || current == '\r') {
                    result.append(current);
                }
                continue;
            }
            if (inString) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                inLineComment = true;
                index++;
                continue;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '\'') {
                inChar = true;
            }
            result.append(current);
        }
        return result.toString();
    }

    private record MethodSource(String name, String body) {
    }

    private record OutdatedRule(String pattern, String reason, Pattern compiledPattern) {
        private OutdatedRule(String pattern, String reason) {
            this(pattern, reason, Pattern.compile(wildcardToRegex(pattern)));
        }

        private boolean matches(String value) {
            return compiledPattern.matcher(value).matches();
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

    private record ModeRestriction(Set<String> allowedModes) {
        private boolean allows(String mode) {
            return allowedModes.contains(mode);
        }
    }

    private record ProfileRestriction(Set<String> allowedProfiles) {
        private boolean allows(String profile) {
            return allowedProfiles.contains(profile);
        }
    }

    private record ScanOptions(String splitterConfigLoadMode,
                               String splitterTestProfile,
                               boolean splitterConfigKafkaStatusRequired,
                               boolean includeManualTests,
                               boolean includeSplitterDataOperatorTests) {

        private static ScanOptions fromSystemProperties() {
            return new ScanOptions(
                    TestReportEligibilityScanner.splitterConfigLoadMode(),
                    TestReportEligibilityScanner.splitterTestProfile(),
                    featureFlag("splitter.config.kafka.status.required", "SPLITTER_CONFIG_KAFKA_STATUS_REQUIRED"),
                    featureFlag("includeManualTests", "INCLUDE_MANUAL_TESTS"),
                    featureFlag("includeSplitterDataOperatorTests", "INCLUDE_SPLITTER_DATA_OPERATOR_TESTS"));
        }

        private boolean isDocumentSplitterProfile() {
            return Set.of("document", "document-profile", "strict-document-profile")
                    .contains(splitterTestProfile);
        }
    }

    private enum Reason {
        DISABLED("disabled"),
        SPLITTER_CONFIG_LOAD_MODE("splitter-config-load-mode"),
        SPLITTER_TEST_PROFILE("splitter-test-profile"),
        SPLITTER_KAFKA_STATUS_REQUIRED("splitter-kafka-status-required"),
        SERVICE_SCOPE("service-scope"),
        MANUAL("manual"),
        NO_ASSERTION("no-assertion"),
        OUTDATED("outdated");

        private final String code;

        Reason(String code) {
            this.code = code;
        }
    }

    private record TestDecision(String fullName,
                                String sourceFile,
                                boolean eligible,
                                Reason reason,
                                String details) {
        private static TestDecision eligible(String fullName, String sourceFile) {
            return new TestDecision(fullName, sourceFile, true, null, "");
        }

        private static TestDecision excluded(String fullName,
                                             String sourceFile,
                                             Reason reason,
                                             String details) {
            return new TestDecision(fullName, sourceFile, false, reason, details);
        }
    }
}
